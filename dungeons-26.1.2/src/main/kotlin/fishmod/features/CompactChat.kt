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

/**
 * Collapses repeated chat lines: a duplicate within [WINDOW_TICKS] removes the older line and
 * re-adds it at the bottom with a trailing "§7(N)" count instead of stacking duplicates.
 * Runs at display time from [fishmod.mixin.ChatHudMixin], so packet-level parsers are unaffected.
 */
object CompactChat {

    /** Duplicate window ~ 1 minute (20 ticks/second). */
    private const val WINDOW_TICKS = 60 * 20

    /** Trailing " (N)" count we previously appended. */
    private val COUNT_SUFFIX: Pattern = Pattern.compile("\\s\\((\\d+)\\)$")

    /** Returns true if collapsed into an existing line's count; `ci` is cancelled in that case. */
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

        // messages are newest-first, so once we pass a line older than the window we can stop.
        for (i in messages.indices) {
            val line = messages[i]
            if (nowTick - line.addedTime() > WINDOW_TICKS) break
            if (incoming != stripKey(line.content().string)) continue

            val next = extractCount(line.content().string) + 1
            messages.removeAt(i)
            acc.invokeRefresh() // drop stale wrapped copies from visibleMessages
            ci.cancel()
            hud.addClientSystemMessage(withCount(message, next))
            return true
        }
        return false
    }

    /** Message content minus color codes and any trailing " (N)" count, trimmed. */
    private fun stripKey(s: String): String {
        var plain = s.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")
        val m: Matcher = COUNT_SUFFIX.matcher(plain)
        if (m.find()) plain = plain.substring(0, m.start())
        return plain.trim()
    }

    /** Current count baked into a line (1 if it carries no "(N)" suffix yet). */
    private fun extractCount(s: String): Int {
        val m = COUNT_SUFFIX.matcher(s.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, ""))
        return if (m.find()) m.group(1).toInt() else 1
    }

    /** Original message with a gray " (N)" appended, preserving its styling. */
    private fun withCount(message: Component, n: Int): Component {
        return message.copy().append(Component.literal(" ($n)").withStyle(ChatFormatting.GRAY))
    }
}
