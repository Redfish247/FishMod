package fishmod.cosmetic

import fishmod.mixin.ChatHudInvoker
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.hud.ChatHud
import net.minecraft.client.gui.hud.ChatHudLine
import net.minecraft.text.Text

/**
 * Retroactively re-styles messages already sitting in the chat history when a cosmetic nick becomes
 * known. [fishmod.mixin.CosmeticChatMixin] swaps IGN→nick when a message is first added, so a
 * message that arrives before its sender's nick has loaded gets baked with the plain IGN. Once
 * [RemoteNicks] learns that nick (via the periodic [RemoteSync] poll or a chat-driven
 * lookup) it calls [requestRefresh], which re-runs the swap over the stored [ChatHud]
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
        val mc = MinecraftClient.getInstance()
        mc.execute {
            scheduled = false
            reapply(mc)
        }
    }

    private fun reapply(mc: MinecraftClient) {
        if (mc.inGameHud == null) return
        val hud = mc.inGameHud.chatHud ?: return
        val invoker = hud as ChatHudInvoker
        val messages: MutableList<ChatHudLine>? = invoker.messages
        if (messages == null || messages.isEmpty()) return

        var changed = false
        for (i in messages.indices) {
            val line = messages[i]
            val content = line.content()
            val swapped = swapKnown(content)
            if (swapped !== content) {
                messages[i] = ChatHudLine(line.creationTick(), swapped, line.signature(), line.indicator())
                changed = true
            }
        }
        if (changed) invoker.invokeRefresh()
    }

    /** Mirrors CosmeticChatMixin's swap but without firing new lookups (own nick + known remote nicks). */
    private fun swapKnown(text: Text): Text {
        var out: Text = text
        if (NickState.isActive()) {
            val real = NickState.realName()
            if (real.isNotEmpty() && out.string.contains(real))
                out = NameRewriter.replaceName(out, real, NickState.asComponent()) ?: out
        }
        return RemoteNicks.applyResolvedOnly(out) ?: out
    }
}
