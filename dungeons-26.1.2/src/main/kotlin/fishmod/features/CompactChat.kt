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
 * Collapses repeated chat lines. When a message identical to one shown within the last
 * [WINDOW_TICKS] arrives, the older line is removed and re-added at the bottom with a
 * trailing "§7(N)" count (e.g. `Hi im RedFish2471 (2)`) instead of stacking duplicates.
 *
 * Runs at display time from [fishmod.mixin.ChatHudMixin] (after chat-filter/command
 * parsing), so packet-level parsers are unaffected. It manipulates [ChatComponent]'s backing
 * `messages` list and re-wraps via [ChatHudInvoker.invokeRefresh] — the same
 * approach used by `fishmod.cosmetic.ChatNickRefresher`.
 */
object CompactChat {

    /** Duplicate window ~ 1 minute (20 ticks/second). */
    private const val WINDOW_TICKS = 60 * 20

    /** Trailing " (N)" count we previously appended. */
    private val COUNT_SUFFIX: Pattern = Pattern.compile("\\s\\((\\d+)\\)$")

    /**
     * @return true if the message duplicated a recent line and was collapsed into a count (in which
     *         case `ci` is cancelled and the caller must stop processing this add).
     */
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
            acc.invokeRefresh() // drop the stale line's wrapped copies from visibleMessages
            ci.cancel() // suppress the un-counted add…
            hud.addClientSystemMessage(withCount(message, next)) // …and re-add it at the bottom with the count
            return true
        }
        return false
    }

    /** Message content minus color codes and any trailing " (N)" count, trimmed. */
    private fun stripKey(s: String): String {
        var plain = s.replace(Regex("§."), "")
        val m: Matcher = COUNT_SUFFIX.matcher(plain)
        if (m.find()) plain = plain.substring(0, m.start())
        return plain.trim()
    }

    /** Current count baked into a line (1 if it carries no "(N)" suffix yet). */
    private fun extractCount(s: String): Int {
        val m = COUNT_SUFFIX.matcher(s.replace(Regex("§."), ""))
        return if (m.find()) m.group(1).toInt() else 1
    }

    /** Original message with a gray " (N)" appended, preserving its styling. */
    private fun withCount(message: Component, n: Int): Component {
        return message.copy().append(Component.literal(" ($n)").withStyle(ChatFormatting.GRAY))
    }
}
