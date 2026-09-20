package fishmod.features.chat

import net.minecraft.network.chat.Component

object ChatHideState {

    @Volatile private var suppressedAtNanos = 0L

    @JvmStatic
    fun noteSuppressed() {
        suppressedAtNanos = System.nanoTime()
    }

    @JvmStatic
    fun shouldSwallowBlank(message: Component?): Boolean {
        if (message == null) return false
        val s = message.string?.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")?.trim() ?: return false
        if (s.isNotEmpty()) return false
        val hit = (System.nanoTime() - suppressedAtNanos) <= 40_000_000L
        if (hit) suppressedAtNanos = System.nanoTime()
        return hit
    }
}
