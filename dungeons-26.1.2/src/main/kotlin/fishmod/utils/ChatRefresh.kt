package fishmod.utils

import fishmod.mixin.ChatHudInvoker
import net.minecraft.client.gui.components.ChatComponent

object ChatRefresh {
    // Vanilla refreshTrimmedMessages() calls scrollChat(1) per rebuilt line while scrolled up, flinging the view.
    @JvmStatic
    fun refreshKeepScroll(acc: ChatHudInvoker) {
        val pos = acc.scrolledLines
        val newSince = acc.newMessageSinceScroll
        acc.scrolledLines = 0
        acc.invokeRefresh()
        if (pos <= 0) return
        acc.scrolledLines = pos
        acc.newMessageSinceScroll = newSince
        (acc as ChatComponent).scrollChat(0)
    }
}
