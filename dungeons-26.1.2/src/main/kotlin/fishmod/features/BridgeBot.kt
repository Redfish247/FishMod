package fishmod.features

import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * Guild bridge bot cleanup. Matches guild chat routed through a Discord bridge bot, e.g.
 * "Guild > [rank] BotName [grank]: PlayerName » message", and reprints it as a tidy
 * "Guild > [Bridge] PlayerName: message" while hiding the raw bot line.
 */
object BridgeBot {

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    @Volatile private var pattern: Pattern = buildPattern()

    private fun buildPattern(): Pattern {
        val name = FishSettings.bridgeBotName.trim()
        if (name.isBlank()) return Pattern.compile("(?!)") // never matches
        val bot = Pattern.quote(name)
        // strict \w{1,16} player name keeps the "name : message" separator colon out of the capture (it was doubling the colon on reprint)
        return Pattern.compile("^Guild > (?:\\[\\S+] )?$bot(?:\\s+\\[[^\\]]+])?[:\\s]+(\\w{1,16})[^\\w]+(.+)$")
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
            Misc.addChatMessage(Component.literal("§2Guild > §r§a[Bridge] §r${m.group(1)}§r: ${m.group(2)}"))
            false // swallow the raw bot message
        }
    }
}
