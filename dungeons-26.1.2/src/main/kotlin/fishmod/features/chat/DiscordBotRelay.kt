package fishmod.features.chat

import com.google.gson.JsonParser
import fishmod.pv.api.HypixelApiClient
import fishmod.utils.FishUtil
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// Polls a Discord channel (bot token) and prints new messages into game chat. Game -> Discord is DiscordChat.kt.
object DiscordBotRelay {

    private const val API = "https://discord.com/api/v10"

    private val POLLER = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "FishMod-DiscordBotRelay").also { it.isDaemon = true } }
    private val running = AtomicBoolean(false)
    @Volatile private var botUserId: String? = null
    @Volatile private var lastId: String = "0"
    @Volatile private var authFailed = false

    @JvmStatic
    fun init() {
        POLLER.scheduleWithFixedDelay({ tick() }, 3, 3, TimeUnit.SECONDS)
    }

    private fun wanted() = FishSettings.discordBotEnabled &&
        FishSettings.discordBotToken.trim().isNotEmpty() &&
        FishSettings.discordBotChannelId.trim().isNotEmpty()

    private fun tick() {
        if (!wanted()) { running.set(false); return }
        if (authFailed) return
        try {
            if (!running.getAndSet(true)) {
                // first run after (re)enable: seed lastId to the newest message so old history doesn't flood chat
                if (!seed()) return
            }
            poll()
        } catch (e: Exception) {
            FishDiag.fail("DiscordBotRelay.1", "poll failed", e)
        }
    }

    private fun headers(req: HttpRequest.Builder): HttpRequest.Builder =
        req.header("Authorization", "Bot " + FishSettings.discordBotToken.trim())
            .header("User-Agent", "FishModDungeons (https://fishmod.dev, 1.0)")

    private fun seed(): Boolean {
        val channel = FishSettings.discordBotChannelId.trim()
        val meReq = headers(HttpRequest.newBuilder(URI.create("$API/users/@me"))).GET().build()
        val meRes = HypixelApiClient.http().send(meReq, HttpResponse.BodyHandlers.ofString())
        if (meRes.statusCode() == 401) { authFailed = true; FishDiag.fail("DiscordBotRelay.2", "bad bot token (401)", null); return false }
        if (meRes.statusCode() !in 200..299) { FishDiag.fail("DiscordBotRelay.3", "/@me returned ${meRes.statusCode()}", null); return false }
        botUserId = JsonParser.parseString(meRes.body()).asJsonObject.get("id").asString

        val msgReq = headers(HttpRequest.newBuilder(URI.create("$API/channels/$channel/messages?limit=1"))).GET().build()
        val msgRes = HypixelApiClient.http().send(msgReq, HttpResponse.BodyHandlers.ofString())
        if (msgRes.statusCode() == 403 || msgRes.statusCode() == 404) {
            FishDiag.fail("DiscordBotRelay.4", "bot has no access to channel (${msgRes.statusCode()})", null); authFailed = true; return false
        }
        if (msgRes.statusCode() !in 200..299) { FishDiag.fail("DiscordBotRelay.5", "seed messages returned ${msgRes.statusCode()}", null); return false }
        val arr = JsonParser.parseString(msgRes.body()).asJsonArray
        if (arr.size() > 0) lastId = arr[0].asJsonObject.get("id").asString
        return true
    }

    private fun poll() {
        val channel = FishSettings.discordBotChannelId.trim()
        val req = headers(HttpRequest.newBuilder(URI.create("$API/channels/$channel/messages?after=$lastId&limit=50"))).GET().build()
        val res = HypixelApiClient.http().send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() == 401) { authFailed = true; FishDiag.fail("DiscordBotRelay.6", "bad bot token (401)", null); return }
        if (res.statusCode() == 429) {
            val wait = Regex("\"retry_after\"\\s*:\\s*([0-9.]+)").find(res.body())?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            Thread.sleep((wait * 1000).toLong().coerceIn(200, 10_000)); return
        }
        if (res.statusCode() !in 200..299) { FishDiag.fail("DiscordBotRelay.7", "poll returned ${res.statusCode()}", null); return }
        val arr = JsonParser.parseString(res.body()).asJsonArray
        if (arr.size() == 0) return
        // Discord returns newest-first; show oldest-first.
        val msgs = (0 until arr.size()).map { arr[it].asJsonObject }.sortedBy { it.get("id").asString.toLong() }
        for (m in msgs) {
            lastId = m.get("id").asString
            val author = m.getAsJsonObject("author")
            if (author.get("id").asString == botUserId) continue
            if (author.get("bot")?.asBoolean == true) continue
            val content = m.get("content")?.asString.orEmpty()
            if (content.isBlank()) continue
            val name = author.get("global_name")?.takeIf { !it.isJsonNull }?.asString ?: author.get("username").asString
            FishUtil.addChatMessage(Component.literal("§5Discord > §r$name§r: $content"))
        }
    }
}
