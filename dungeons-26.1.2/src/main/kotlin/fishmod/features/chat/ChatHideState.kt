package fishmod.features.chat

import net.minecraft.network.chat.Component

/**
 * Tiny shared state for the chat DISPLAY-layer hide path (ChatFilter + chat-rule "Hide Original
 * Message"). Hidden lines are cancelled at [fishmod.mixin.ChatHudMixin] so vanilla still logs them
 * to logs/latest.log — they just aren't drawn. Hypixel usually pads a hidden line with a blank
 * spacer line right after it; [shouldSwallowBlank] eats that one trailing blank so hiding a line
 * doesn't leave a gap in chat.
 */
object ChatHideState {

    @Volatile private var suppressedAtNanos = 0L

    /** Call right after cancelling a hidden line's display. */
    @JvmStatic
    fun noteSuppressed() {
        suppressedAtNanos = System.nanoTime()
    }

    /** True if [message] is a blank line arriving in the same burst as a just-hidden line. */
    @JvmStatic
    fun shouldSwallowBlank(message: Component?): Boolean {
        if (message == null) return false
        val s = message.string?.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")?.trim() ?: return false
        if (s.isNotEmpty()) return false
        // Same packet bundle is processed back-to-back; 40ms is generous slack.
        val hit = (System.nanoTime() - suppressedAtNanos) <= 40_000_000L
        if (hit) suppressedAtNanos = System.nanoTime() // allow a run of blanks to all go
        return hit
    }
}
