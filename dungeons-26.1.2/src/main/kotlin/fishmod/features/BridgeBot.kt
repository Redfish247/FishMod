package fishmod.features

import fishmod.utils.Misc
import fishmod.utils.TextStrip
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

object BridgeBot {


    @Volatile private var pattern: Pattern = buildPattern()

    private fun buildPattern(): Pattern {
        val name = FishSettings.bridgeBotName.trim()
        if (name.isBlank()) return Pattern.compile("(?!)")
        val bot = Pattern.quote(name)
        return Pattern.compile("^Guild > (?:\\[\\S+] )?$bot(?:\\s+\\[[^\\]]+])?[:\\s]+([^\\s:»]+)\\s*[»:]\\s*(.+)$")
    }

    @JvmStatic
    fun rebuildPattern() { pattern = buildPattern() }

    @JvmStatic
    fun init() {
        rebuildPattern()
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            if (overlay || !FishSettings.bridgeBotEnabled || FishSettings.bridgeBotName.isBlank()) return@register true
            val plain = TextStrip.plain(message.string).trim()
            val m = pattern.matcher(plain)
            if (!m.matches()) return@register true
            Misc.addChatMessage(Component.literal("§2Bridge > §r${m.group(1)}§r: ${m.group(2)}"))
            false
        }
    }
}
