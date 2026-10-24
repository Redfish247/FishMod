package fishmod.utils.debug

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * In-game bug reports / suggestions and deduplicated auto error reports, sent to the test board inbox.
 * Sends only: kind, title, text, dedup key, mod + MC version, area, recent FishDiag lines for bugs only (player name scrubbed).
 */
object BoardReport {

    // fishmod.dev worker → /incomming-features review queue; 404 until that route is deployed.
    private const val ENDPOINT = "https://fishmod.dev/api/report"
    private const val MANUAL_COOLDOWN_MS = 30_000L
    private const val HOUR_MS = 3_600_000L

    private val sentFile get() = FabricLoader.getInstance().configDir.resolve("FishMod").resolve("board-reported.txt")
    private val sentKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var loaded = false
    private val autoTimes = ArrayDeque<Long>()
    @Volatile private var lastManualAt = 0L

    fun command(kind: String): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal(kind)
            .executes { Misc.addChatMessage(Component.literal("§7Usage: /fm $kind <text>")); 1 }
            .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes { c ->
                manual(if (kind == "bug") "bug" else "suggest", StringArgumentType.getString(c, "text")); 1
            })

    private fun manual(kind: String, text: String) {
        val label = if (kind == "bug") "Bug report" else "Suggestion"
        submit(kind, text.take(80), text) { ok, msg ->
            Misc.addChatMessage(Component.literal(if (ok) "§a[FishMod] $label sent, thanks!" else "§c[FishMod] $label not sent: $msg"))
        }
    }

    /** Player-written report; [done] runs off-thread with (ok, reason). */
    fun submit(kind: String, title: String, text: String, done: (Boolean, String) -> Unit) {
        if (!FishSettings.boardReports) return done(false, "board reports are off (General > Board Reports)")
        if (title.isBlank() && text.isBlank()) return done(false, "write something first")
        val now = System.currentTimeMillis()
        if (now - lastManualAt < MANUAL_COOLDOWN_MS) return done(false, "wait ${(MANUAL_COOLDOWN_MS - (now - lastManualAt)) / 1000}s before sending another")
        lastManualAt = now
        send(kind, title.take(120), text, null, done)
    }

    /** Called by FishDiag on the first hit of each code; deduped and rate limited. */
    @JvmStatic
    fun auto(code: String, message: String, t: Throwable?) {
        try {
            if (!FishSettings.boardReports || !FishSettings.boardAutoSend) return
            val key = dedupKey(code, t)
            loadSent()
            if (key in sentKeys) return
            val now = System.currentTimeMillis()
            synchronized(autoTimes) {
                while (autoTimes.isNotEmpty() && now - autoTimes.first() > HOUR_MS) autoTimes.removeFirst()
                if (autoTimes.size >= FishSettings.boardAutoPerHour.coerceAtLeast(1)) return
                autoTimes.addLast(now)
            }
            sentKeys.add(key)
            val text = if (t == null) message else "$message\n${t.javaClass.name}: ${scrub(t.message ?: "")}\n" +
                t.stackTrace.take(8).joinToString("\n") { "  at $it" }
            val title = if (t == null) "[$code] $message" else "[$code] ${t.javaClass.simpleName}: $message"
            send("auto", title.take(120), text, key) { ok, _ ->
                if (ok) saveSent() else sentKeys.remove(key)
            }
        } catch (_: Throwable) {
        }
    }

    // Code + exception type + first FishMod frame (no line number, so it survives version bumps).
    private fun dedupKey(code: String, t: Throwable?): String {
        if (t == null) return code
        val frame = t.stackTrace.firstOrNull { it.className.startsWith("fishmod.") }
        return "$code|${t.javaClass.name}|${frame?.let { it.className + "." + it.methodName } ?: "?"}"
    }

    private fun send(kind: String, title: String, text: String, key: String?, done: (Boolean, String) -> Unit) {
        val body = JsonObject().apply {
            addProperty("kind", kind)
            addProperty("title", scrub(title))
            addProperty("text", scrub(text).take(2000))
            if (key != null) addProperty("key", key)
            addProperty("mod", modVersion())
            addProperty("mc", mcVersion())
            addProperty("loc", area())
            if (kind != "suggest") addProperty("log", scrub(FishDiag.recentLines(15).joinToString("\n")).take(4000))
        }
        val req = HttpRequest.newBuilder(URI.create(ENDPOINT))
            .timeout(Duration.ofSeconds(15))
            .header("content-type", "application/json")
            .header("X-FishMod-Token", fishmod.utils.HypixelApi.MOD_TOKEN)
            .header("user-agent", "FishModDungeons/${modVersion()}")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        fishmod.utils.Http.CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete { r, e ->
            when {
                e != null -> done(false, "couldn't reach the board")
                r.statusCode() == 429 -> done(false, "too many reports, try later")
                r.statusCode() == 404 -> done(false, "report inbox isn't live yet")
                r.statusCode() == 413 -> done(false, "report too large")
                r.statusCode() !in 200..299 -> done(false, "HTTP ${r.statusCode()}")
                else -> done(isOk(r.body()), "board said no")
            }
        }
    }

    private fun isOk(s: String): Boolean = try {
        JsonParser.parseString(s).asJsonObject.get("ok")?.asBoolean == true
    } catch (_: Throwable) { false }

    // Drops the player's own name and anything that looks like an IP.
    private fun scrub(s: String): String {
        var out = s
        try {
            val name = Minecraft.getInstance().user.name
            if (name.length >= 3) out = out.replace(name, "<player>", ignoreCase = true)
        } catch (_: Throwable) {
        }
        return out.replace(Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b"), "<ip>")
    }

    private fun loadSent() {
        if (loaded) return
        loaded = true
        try {
            val f = sentFile
            if (Files.exists(f)) Files.readAllLines(f).filter { it.isNotBlank() }.forEach { sentKeys.add(it) }
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    private fun saveSent() {
        try {
            val f = sentFile
            Files.createDirectories(f.parent)
            Files.write(f, sentKeys.toList().takeLast(500))
        } catch (_: Throwable) {
        }
    }

    private fun area(): String = try { fishmod.utils.Location.getCurrentLocation().toString() } catch (_: Throwable) { "?" }

    private fun modVersion(): String = try {
        FabricLoader.getInstance().getModContainer("fishmod-dungeons").map { it.metadata.version.friendlyString }.orElse("?")
    } catch (_: Throwable) { "?" }

    private fun mcVersion(): String = try { SharedConstants.getCurrentVersion().name() } catch (_: Throwable) { "?" }
}
