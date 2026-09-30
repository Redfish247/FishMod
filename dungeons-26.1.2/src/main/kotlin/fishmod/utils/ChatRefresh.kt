package fishmod.utils

import fishmod.mixin.ChatHudInvoker
import fishmod.utils.debug.FishDiag
import net.minecraft.client.gui.components.ChatComponent

object ChatRefresh {
    // Vanilla refreshTrimmedMessages() calls scrollChat(1) per rebuilt line while scrolled up, flinging the view.
    @JvmStatic
    fun refreshKeepScroll(acc: ChatHudInvoker) {
        val pos = acc.scrolledLines
        val newSince = acc.newMessageSinceScroll
        try {
            acc.scrolledLines = 0
            acc.invokeRefresh()
            if (pos <= 0) return
            acc.scrolledLines = pos
            acc.newMessageSinceScroll = newSince
            (acc as ChatComponent).scrollChat(0)
        } catch (e: Exception) {
            FishDiag.fail("ChatRefresh.1", "chat refresh failed (scroll=$pos)", e)
        }
    }
}
