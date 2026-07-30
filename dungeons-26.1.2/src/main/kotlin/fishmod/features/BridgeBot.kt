package fishmod.features

import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

object BridgeBot {

    // Matches guild chat after stripping colors:
    // "Guild > [optional rank] BotName: [optional rank] PlayerName: message"
    // Also handles Discord bridge style "Guild > BotName: Player > message"
    @JvmStatic
    private var pattern: Pattern = buildPattern()

    private fun buildPattern(): Pattern {
        if (FishSettings.bridgeBotName.isBlank()) return Pattern.compile("(?!)") // never matches
        val bot = Pattern.quote(FishSettings.bridgeBotName.trim())
        // "Guild > [rank] BotName [guild_rank]: PlayerName » message"
        // rank before bot and guild_rank after bot are both optional
        // separator between player and message can be »  :  >  |  etc.
        return Pattern.compile(
            "^Guild > (?:\\[\\S+\\] )?$bot(?:\\s+\\[[^\\]]+\\])?: (\\S+)[^\\w]+(.+)$"
        )
    }

    @JvmStatic
    fun rebuildPattern() {
        pattern = buildPattern()
    }

    @JvmStatic
    fun init() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            if (overlay || !FishSettings.bridgeBotEnabled || FishSettings.bridgeBotName.isBlank()) {
                return@register true
            }

            val plain = message.string.replace(Regex("§."), "").trim()
            val m = pattern.matcher(plain)
            if (!m.matches()) return@register true

            val player = m.group(1)
            val text = m.group(2)
            Misc.addChatMessage(Component.literal("§2Guild > §r§a[Bridge] §r$player§r: $text"))
            false // suppress original bot message
        }
    }
}
