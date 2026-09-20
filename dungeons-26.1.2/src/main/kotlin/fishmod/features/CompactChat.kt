package fishmod.features

import fishmod.mixin.ChatHudInvoker
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.regex.Matcher
import java.util.regex.Pattern

object CompactChat {

    private const val WINDOW_TICKS = 60 * 20

    private val COUNT_SUFFIX: Pattern = Pattern.compile("\\s\\((\\d+)\\)$")

    @JvmStatic
    fun tryCompact(message: Component, hud: ChatComponent, ci: CallbackInfo): Boolean {
        val incoming = stripKey(message.string)
        if (incoming.isEmpty()) return false

        val mc = Minecraft.getInstance()
        if (mc.gui == null) return false
        val nowTick = mc.gui.guiTicks

        val acc = hud as ChatHudInvoker
        val messages = acc.messages
        if (messages == null || messages.isEmpty()) return false

        for (i in messages.indices) {
            val line = messages[i]
            if (nowTick - line.addedTime() > WINDOW_TICKS) break
            if (incoming != stripKey(line.content().string)) continue

            val next = extractCount(line.content().string) + 1
            messages.removeAt(i)
            acc.invokeRefresh()
            ci.cancel()
            hud.addClientSystemMessage(withCount(message, next))
            return true
        }
        return false
    }

    private fun stripKey(s: String): String {
        var plain = s.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")
        val m: Matcher = COUNT_SUFFIX.matcher(plain)
        if (m.find()) plain = plain.substring(0, m.start())
        return plain.trim()
    }

    private fun extractCount(s: String): Int {
        val m = COUNT_SUFFIX.matcher(s.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, ""))
        return if (m.find()) m.group(1).toInt() else 1
    }

    private fun withCount(message: Component, n: Int): Component {
        return message.copy().append(Component.literal(" ($n)").withStyle(ChatFormatting.GRAY))
    }
}
