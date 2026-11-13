package fishmod.features.chat

import fishmod.compat.hud
import net.minecraft.network.chat.Component

object ChatHideState {

    @Volatile private var suppressedAtNanos = 0L

    @JvmStatic
    fun noteSuppressed() {
        suppressedAtNanos = System.nanoTime()
    }

    @JvmStatic
    fun dropPrecedingBlank(hud: net.minecraft.client.gui.components.ChatComponent) {
        try {
            val now = net.minecraft.client.Minecraft.getInstance().hud?.guiTicks ?: return
            val acc = hud as? fishmod.mixin.FishChatInvoker ?: return
            val messages = acc.messages ?: return
            val top = messages.firstOrNull() ?: return
            if (now - top.addedTime() > 1) return
            if (top.content().string.replace(fishmod.utils.FishConstants.STRIP_COLOR_REGEX, "").isNotBlank()) return
            messages.removeAt(0)
            fishmod.utils.ChatRefresh.refreshKeepScroll(acc)
        } catch (e: Exception) {
            fishmod.utils.debug.FishDiag.fail("ChatHideState.1", "dropping blank before hidden line failed", e)
        }
    }

    @JvmStatic
    fun shouldSwallowBlank(message: Component?): Boolean {
        if (message == null) return false
        val s = message.string?.replace(fishmod.utils.FishConstants.STRIP_COLOR_REGEX, "")?.trim() ?: return false
        if (s.isNotEmpty()) return false
        val hit = (System.nanoTime() - suppressedAtNanos) <= 40_000_000L
        if (hit) suppressedAtNanos = System.nanoTime()
        return hit
    }
}
