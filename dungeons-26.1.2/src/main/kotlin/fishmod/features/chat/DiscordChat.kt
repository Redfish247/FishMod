package fishmod.features.chat

import com.google.gson.JsonObject
import fishmod.pv.api.HypixelApiClient
import fishmod.utils.TextStrip
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import java.net.URI
import java.net.http.HttpRequest
import java.time.Duration
import java.util.concurrent.Executors
import java.util.regex.Pattern

// Forwards guild/officer/party (and optionally DM) chat to a Discord webhook, with a blocked-word filter.
object DiscordChat {

    private val WEBHOOK_OK = Regex("^https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+$")

    private val GUILD = Pattern.compile("^Guild > (?:\\[[^\\]]+] )?(\\w{1,16})(?: \\[[^\\]]+])?: (.+)$")
    private val OFFICER = Pattern.compile("^Officer > (?:\\[[^\\]]+] )?(\\w{1,16})(?: \\[[^\\]]+])?: (.+)$")
    private val PARTY = Pattern.compile("^Party > (?:\\[[^\\]]+] )?(\\w{1,16}): (.+)$")
    private val DM_FROM = Pattern.compile("^From (?:\\[[^\\]]+] )?(\\w{1,16}): (.+)$")
    private val DM_TO = Pattern.compile("^To (?:\\[[^\\]]+] )?(\\w{1,16}): (.+)$")

    private val SENDER = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "FishMod-DiscordChat").also { it.isDaemon = true } }

    @Volatile private var blockedKey: String? = null
    @Volatile private var blocked: List<Pattern> = emptyList()

    @JvmStatic
    fun validWebhook(url: String): Boolean = WEBHOOK_OK.matches(url.trim())

    // Blocked words: comma or newline separated, whole-word, case-insensitive.
    private fun blockedPatterns(): List<Pattern> {
        val raw = FishSettings.discordBlockedWords
        if (raw != blockedKey) {
            blocked = raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                .map { Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(it) + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE) }
            blockedKey = raw
        }
        return blocked
    }

    // Returns null when the message must be dropped.
    internal fun applyFilter(text: String): String? {
        var out = text
        for (p in blockedPatterns()) {
            val m = p.matcher(out)
            if (!m.find()) continue
            if (!FishSettings.discordCensor) return null
            out = m.replaceAll { "*".repeat(it.group().length) }
        }
        return out
    }

    @JvmStatic
    fun init() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay || !FishSettings.discordChatEnabled) return@register
            val url = FishSettings.discordWebhookUrl.trim()
            if (!validWebhook(url)) return@register
            val plain = TextStrip.plain(message.string).trim()
            val (tag, m) = match(plain) ?: return@register
            val clean = applyFilter(m.second) ?: return@register
            val user = m.first
            SENDER.execute { post(url, "$tag $user", clean) }
        }
    }

    private fun match(plain: String): Pair<String, Pair<String, String>>? {
        fun hit(p: Pattern, tag: String, on: Boolean): Pair<String, Pair<String, String>>? {
            if (!on) return null
            val m = p.matcher(plain)
            return if (m.matches()) tag to (m.group(1) to m.group(2)) else null
        }
        return hit(GUILD, "[Guild]", FishSettings.discordGuild)
            ?: hit(OFFICER, "[Officer]", FishSettings.discordOfficer)
            ?: hit(PARTY, "[Party]", FishSettings.discordParty)
            ?: hit(DM_FROM, "[DM from]", FishSettings.discordDms)
            ?: hit(DM_TO, "[DM to]", FishSettings.discordDms)
    }

    private fun post(url: String, name: String, content: String) {
        try {
            val body = JsonObject().apply {
                addProperty("username", name.take(80))
                addProperty("content", content.take(1900))
                add("allowed_mentions", JsonObject().apply { add("parse", com.google.gson.JsonArray()) })
            }
            val req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build()
            val res = HypixelApiClient.http().send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() == 429) {
                val wait = Regex("\"retry_after\"\\s*:\\s*([0-9.]+)").find(res.body())?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                Thread.sleep((wait * 1000).toLong().coerceIn(200, 10_000))
                HypixelApiClient.http().send(req, java.net.http.HttpResponse.BodyHandlers.discarding())
            } else if (res.statusCode() !in 200..299) {
                FishDiag.fail("DiscordChat.1", "webhook returned ${res.statusCode()}", null)
            }
            Thread.sleep(400)
        } catch (e: Exception) {
            FishDiag.fail("DiscordChat.2", "webhook post failed", e)
        }
    }
}
