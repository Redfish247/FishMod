package fishmod.cosmetic
import fishmod.compat.hud

import fishmod.utils.debug.FishDiag
import fishmod.mixin.ChatHudInvoker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component

object ChatNickRefresher {
    @Volatile
    private var scheduled = false

    // Chat Patches keeps thousands of lines; only re-style the recent ones, debounced
    private const val MAX_LINES = 200
    private const val DEBOUNCE_MS = 750L
    @Volatile private var lastRunMs = 0L

    @JvmStatic
    fun requestRefresh() {
        if (scheduled) return
        scheduled = true
        val wait = (lastRunMs + DEBOUNCE_MS - System.currentTimeMillis()).coerceAtLeast(0)
        val mc = Minecraft.getInstance()
        val run = Runnable {
            mc.execute {
                scheduled = false
                lastRunMs = System.currentTimeMillis()
                try { reapply(mc) } catch (t: Throwable) { FishDiag.fail("ChatNickRefresher.1", "chat nick reapply failed", t) }
            }
        }
        if (wait <= 0) run.run()
        else java.util.concurrent.CompletableFuture.delayedExecutor(wait, java.util.concurrent.TimeUnit.MILLISECONDS).execute(run)
    }

    private fun reapply(mc: Minecraft) {
        if (mc.hud == null) return
        val hud = mc.hud.chat ?: return
        val invoker = hud as ChatHudInvoker
        val messages: MutableList<GuiMessage>? = invoker.messages
        if (messages == null || messages.isEmpty()) return

        var changed = false
        for (i in 0 until minOf(messages.size, MAX_LINES)) {
            val line = messages[i]
            val content = line.content()
            val swapped = swapKnown(content)
            if (swapped !== content) {
                messages[i] = GuiMessage(line.addedTime(), swapped, line.signature(), line.source(), line.tag())
                changed = true
            }
        }
        if (changed) fishmod.utils.ChatRefresh.refreshKeepScroll(invoker)
    }

    private fun swapKnown(text: Component): Component {
        var out: Component = text
        if (NickState.isActive()) {
            val real = NickState.realName()
            if (real.isNotEmpty() && out.string.contains(real))
                out = NameRewriter.replaceName(out, real, NickState.asComponent()) ?: out
        }
        return RemoteNicks.applyResolvedOnly(out) ?: out
    }
}
