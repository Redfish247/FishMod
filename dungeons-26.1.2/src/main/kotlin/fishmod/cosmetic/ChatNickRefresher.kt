package fishmod.cosmetic

import fishmod.mixin.ChatHudInvoker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Component

/**
 * Retroactively re-styles messages already sitting in the chat history when a cosmetic nick becomes
 * known. [fishmod.mixin.CosmeticChatMixin] swaps IGN→nick when a message is first added, so a
 * message that arrives before its sender's nick has loaded gets baked with the plain IGN. Once
 * [RemoteNicks] learns that nick (via the periodic [RemoteSync] poll or a chat-driven
 * lookup) it calls [requestRefresh], which re-runs the swap over the stored [ChatComponent]
 * messages and re-wraps them — so the past line flips from the IGN to the styled nick in place.
 *
 * Re-styling is idempotent ([NameRewriter.replaceName] no-ops on already-decorated text) and
 * uses [RemoteNicks.applyResolvedOnly] so re-scanning history never triggers fresh lookups.
 */
object ChatNickRefresher {
    // Coalesces a burst of newly-resolved nicks (e.g. a whole tab-list sync) into one refresh.
    @Volatile
    private var scheduled = false

    /** Request a retroactive chat re-style. Thread-safe; the actual work runs on the client thread. */
    @JvmStatic
    fun requestRefresh() {
        if (scheduled) return
        scheduled = true
        val mc = Minecraft.getInstance()
        mc.execute {
            scheduled = false
            reapply(mc)
        }
    }

    private fun reapply(mc: Minecraft) {
        if (mc.gui == null) return
        val hud = mc.gui.chat ?: return
        val invoker = hud as ChatHudInvoker
        val messages: MutableList<GuiMessage>? = invoker.messages
        if (messages == null || messages.isEmpty()) return

        var changed = false
        for (i in messages.indices) {
            val line = messages[i]
            val content = line.content()
            val swapped = swapKnown(content)
            if (swapped !== content) {
                messages[i] = GuiMessage(line.addedTime(), swapped, line.signature(), line.source(), line.tag())
                changed = true
            }
        }
        if (changed) invoker.invokeRefresh()
    }

    /** Mirrors CosmeticChatMixin's swap but without firing new lookups (own nick + known remote nicks). */
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
