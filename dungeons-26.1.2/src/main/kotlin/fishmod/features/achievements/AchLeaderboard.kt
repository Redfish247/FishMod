package fishmod.features.achievements

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import fishmod.utils.FishMsg
import fishmod.utils.Http
import fishmod.utils.Location
import fishmod.utils.config.FishConfig
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Global achievement leaderboard: totals submit, fetch and the in-world hologram board
object AchLeaderboard {

    class Entry(val name: String, val points: Int, val secrets: Int)
    class Me(val points: Int, val secrets: Int, val pointsRank: Int, val secretsRank: Int)

    private const val BASE = "https://fishmod.dev/api/achievements"
    private const val TOKEN = "fishmod123"
    private const val SUBMIT_GAP_MS = 3 * 60_000L
    private const val FETCH_GAP_MS = 2 * 60_000L
    private const val FETCH_RETRY_MS = 30_000L
    private const val RANGE = 48.0
    private const val LINE_H = 0.25

    @Volatile var points: List<Entry> = emptyList(); private set
    @Volatile var secrets: List<Entry> = emptyList(); private set
    @Volatile var me: Me? = null; private set
    @Volatile var generatedAt = 0L; private set
    @Volatile var offline = false; private set
    @Volatile var loaded = false; private set

    @Volatile private var fetching = false
    @Volatile private var submitting = false
    @Volatile private var lastFetch = 0L
    @Volatile private var lastSubmit = 0L
    @Volatile private var lastSubmittedKey = ""
    @Volatile private var wantedAt = 0L
    private var registered = false

    // Secret achievements unlocked (the Secret Achievements section, max 13)
    fun secretCount(): Int = AchRegistry.ALL.count { it.section == "Secret Achievements" && AchEngine.unlocked(it) }

    private fun key(): String = "${AchEngine.points()}/${secretCount()}/${AchStore.data.unlocked.size}"

    // Called by the board renderer and the achievements screen while they are visible
    fun want() { wantedAt = System.currentTimeMillis() }

    fun init() {
        if (registered) return
        registered = true
        RenderingEvents.NO_DEPTH_LINE.register { ctx, m, _ ->
            if (!AchSettings.achEnabled || !AchSettings.achLbEnabled) return@register
            FishDiag.guard("AchLeaderboard.1", "board render") { renderBoard(ctx, m) }
        }
    }

    fun onTick(mc: Minecraft) {
        if (mc.player == null) return
        val now = System.currentTimeMillis()
        if (!submitting && now - lastSubmit > SUBMIT_GAP_MS && Location.inSkyblock() && key() != lastSubmittedKey) submit(false)
        val gap = if (offline) FETCH_RETRY_MS else FETCH_GAP_MS
        if (!fetching && now - wantedAt < 3000 && now - lastFetch > gap) fetch()
    }

    private fun uuid(): String = Minecraft.getInstance().player?.uuid?.toString()?.replace("-", "") ?: ""

    private fun req(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder().uri(URI.create(BASE + path))
            .header("X-FishMod-Token", TOKEN).header("X-FishMod-Caller", uuid()).header("User-Agent", "Mozilla/5.0")
            .timeout(Duration.ofSeconds(10))

    fun submit(manual: Boolean) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (submitting) return
        submitting = true
        lastSubmit = System.currentTimeMillis()
        val k = key()
        val body = JsonObject().apply {
            addProperty("uuid", uuid())
            addProperty("name", player.gameProfile.name)
            addProperty("points", AchEngine.points())
            addProperty("secrets", secretCount())
            addProperty("unlocked", AchStore.data.unlocked.size)
        }
        val r = try { req("/submit").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build() }
        catch (t: Throwable) { submitting = false; return }
        Http.CLIENT.sendAsync(r, HttpResponse.BodyHandlers.ofString()).thenAccept { resp ->
            submitting = false
            val ok = resp.statusCode() == 200
            // A 429 means the server already holds a recent copy; retry later
            if (ok) { lastSubmittedKey = k; lastFetch = 0L }
            if (manual) mc.execute {
                FishMsg.send(
                    if (ok) "§aLeaderboard score submitted. §7(${AchEngine.points()} AP, ${secretCount()} secrets)"
                    else if (resp.statusCode() == 429) "§7Score was already submitted recently."
                    else "§cLeaderboard submit failed (${resp.statusCode()})."
                )
            }
        }.exceptionally { _ ->
            submitting = false
            if (manual) mc.execute { FishMsg.send("§cLeaderboard offline.") }
            null
        }
    }

    fun fetch() {
        if (fetching) return
        fetching = true
        lastFetch = System.currentTimeMillis()
        val r = try { req("/leaderboard?uuid=" + uuid()).GET().build() } catch (t: Throwable) { fetching = false; offline = true; return }
        Http.CLIENT.sendAsync(r, HttpResponse.BodyHandlers.ofString()).thenAccept { resp ->
            try {
                val o = JsonParser.parseString(resp.body()).asJsonObject
                if (resp.statusCode() != 200 || !o.get("success").asBoolean) throw IllegalStateException("status ${resp.statusCode()}")
                points = parse(o, "points"); secrets = parse(o, "secrets")
                generatedAt = o.get("generatedAt")?.asLong ?: System.currentTimeMillis()
                me = o.get("me")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
                    Me(it.get("points").asInt, it.get("secrets").asInt, it.get("pointsRank").asInt, it.get("secretsRank").asInt)
                }
                offline = false; loaded = true
            } catch (t: Throwable) { offline = true }
            fetching = false
        }.exceptionally { _ -> offline = true; fetching = false; null }
    }

    private fun parse(o: JsonObject, k: String): List<Entry> = o.getAsJsonArray(k).map {
        val e = it.asJsonObject
        Entry(e.get("name").asString, e.get("points").asInt, e.get("secrets").asInt)
    }

    fun ago(): String {
        if (generatedAt == 0L) return ""
        val m = ((System.currentTimeMillis() - generatedAt) / 60_000L).coerceAtLeast(0)
        return if (m < 1) "updated just now" else "updated ${m}m ago"
    }

    fun medal(rank: Int): String = when (rank) { 1 -> "§6"; 2 -> "§f"; 3 -> "§c"; else -> "§7" }

    fun value(e: Entry, secretList: Boolean): String = if (secretList) "${e.secrets}/13" else "%,d AP".format(e.points)

    // Own rank line when outside the top 10, else null
    fun meLine(secretList: Boolean): String? {
        val m = me ?: return null
        val rank = if (secretList) m.secretsRank else m.pointsRank
        if (rank <= 10) return null
        val v = if (secretList) "${m.secrets}/13" else "%,d AP".format(m.points)
        return "§a#$rank §fYou §e$v"
    }

    private fun lines(secretList: Boolean): List<String> {
        val list = if (secretList) secrets else points
        val out = ArrayList<String>()
        for ((i, e) in list.withIndex()) out.add("${medal(i + 1)}#${i + 1} §f${e.name} §e${value(e, secretList)}")
        meLine(secretList)?.let { out.add("§8..."); out.add(it) }
        return out
    }

    private fun areaOk(): Boolean {
        val a = AchSettings.achLbArea
        return a.isEmpty() || a == "Any" || Location.getCurrentLocation().name2 == a
    }

    private fun renderBoard(ctx: LevelRenderContext, m: PoseStack) {
        val p = Minecraft.getInstance().player ?: return
        val base = Vec3(AchSettings.achLbX, AchSettings.achLbY, AchSettings.achLbZ)
        val eye = p.position()
        if (eye.distanceToSqr(base) > RANGE * RANGE || !areaOk()) return
        want()
        val sc = AchSettings.achLbScale.coerceIn(0.3, 5.0)
        val lh = LINE_H * sc
        val status = when {
            !loaded && offline -> "§cLeaderboard offline"
            !loaded -> "§7Loading..."
            offline -> "§c(offline) §8${ago()}"
            else -> "§8${ago()}"
        }
        // Panels sit side by side, perpendicular to the viewer's line to the board
        val dx = eye.x - base.x
        val dz = eye.z - base.z
        val len = Math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
        val rx = dz / len
        val rz = -dx / len
        val off = 2.3 * sc

        val panels = listOf(Triple("§b§lTop Achievement Points", lines(false), -off), Triple("§d§lTop Secret Hunters", lines(true), off))
        val rows = if (loaded) panels.maxOf { it.second.size } else 0
        // The board floats above the configured spot
        var y = base.y + 0.8 + (rows + 3) * lh
        RenderUtils.renderText(ctx, m, Component.literal("§6§l✦ FishMod Achievements ✦"), base.x, y, base.z, (sc * 1.25).toFloat())
        y -= lh * 1.4
        for ((title, ls, side) in panels) {
            var ly = y
            val px = base.x + rx * side
            val pz = base.z + rz * side
            RenderUtils.renderText(ctx, m, Component.literal(title), px, ly, pz, sc.toFloat())
            ly -= lh * 1.2
            for (l in ls) { RenderUtils.renderText(ctx, m, Component.literal(l), px, ly, pz, sc.toFloat()); ly -= lh }
        }
        RenderUtils.renderText(ctx, m, Component.literal(status), base.x, base.y + 0.8, base.z, (sc * 0.9).toFloat())
    }

    fun here() {
        val p = Minecraft.getInstance().player ?: return
        AchSettings.achLbX = Math.round(p.x * 100) / 100.0
        AchSettings.achLbY = Math.round(p.y * 100) / 100.0
        AchSettings.achLbZ = Math.round(p.z * 100) / 100.0
        try { FishConfig.manager.save() } catch (t: Throwable) { FishDiag.fail("AchLeaderboard.2", "config save failed", t) }
        FishMsg.send("§aLeaderboard board moved to §f${AchSettings.achLbX}, ${AchSettings.achLbY}, ${AchSettings.achLbZ}")
    }
}
