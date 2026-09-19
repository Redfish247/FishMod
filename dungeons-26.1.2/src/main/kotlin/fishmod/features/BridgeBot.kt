package fishmod.features

import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * Guild bridge bot cleanup. Matches guild chat routed through a Discord bridge bot, e.g.
 * "Guild > [rank] BotName [grank]: PlayerName » message", and reprints it as a tidy
 * "Bridge > PlayerName: message" while hiding the raw bot line.
 */
object BridgeBot {

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    @Volatile private var pattern: Pattern = buildPattern()

    private fun buildPattern(): Pattern {
        val name = FishSettings.bridgeBotName.trim()
        if (name.isBlank()) return Pattern.compile("(?!)") // never matches
        val bot = Pattern.quote(name)
        // name excludes whitespace/:/» so it stops cleanly at the separator without swallowing it (was \w{1,16},
        // which broke on reply-formatted names like "Levis⇾myvk" since the reply arrow isn't a word char)
        // separator is an explicit »/: token, not a generic non-word run — a generic run would also swallow a leading "!" (or other punctuation) off the message itself
        return Pattern.compile("^Guild > (?:\\[\\S+] )?$bot(?:\\s+\\[[^\\]]+])?[:\\s]+([^\\s:»]+)\\s*[»:]\\s*(.+)$")
    }

    @JvmStatic
    fun rebuildPattern() { pattern = buildPattern() }

    @JvmStatic
    fun init() {
        rebuildPattern()
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            if (overlay || !FishSettings.bridgeBotEnabled || FishSettings.bridgeBotName.isBlank()) return@register true
            val plain = message.string.replace(COLOR, "").trim()
            val m = pattern.matcher(plain)
            if (!m.matches()) return@register true
            Misc.addChatMessage(Component.literal("§2Bridge > §r${m.group(1)}§r: ${m.group(2)}"))
            false // swallow the raw bot message
        }
    }
}
