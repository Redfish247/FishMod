package fishmod.cosmetic;

import fishmod.mixin.ChatHudInvoker;
import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

/**
 * Retroactively re-styles messages already sitting in the chat history when a cosmetic nick becomes
 * known. {@link fishmod.mixin.CosmeticChatMixin} swaps IGN→nick when a message is first added, so a
 * message that arrives before its sender's nick has loaded gets baked with the plain IGN. Once
 * {@link RemoteNicks} learns that nick (via the periodic {@link RemoteSync} poll or a chat-driven
 * lookup) it calls {@link #requestRefresh()}, which re-runs the swap over the stored {@link ChatComponent}
 * messages and re-wraps them — so the past line flips from the IGN to the styled nick in place.
 *
 * Re-styling is idempotent ({@link NameRewriter#replaceName} no-ops on already-decorated text) and
 * uses {@link RemoteNicks#applyResolvedOnly} so re-scanning history never triggers fresh lookups.
 */
public final class ChatNickRefresher {
    private ChatNickRefresher() {}

    // Coalesces a burst of newly-resolved nicks (e.g. a whole tab-list sync) into one refresh.
    private static volatile boolean scheduled = false;

    /** Request a retroactive chat re-style. Thread-safe; the actual work runs on the client thread. */
    public static void requestRefresh() {
        if (scheduled) return;
        scheduled = true;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { scheduled = false; reapply(mc); });
    }

    private static void reapply(Minecraft mc) {
        if (mc.gui == null) return;
        ChatComponent hud = mc.gui.hud.getChat();
        if (hud == null) return;
        List<GuiMessage> messages = ((ChatHudInvoker) hud).getMessages();
        if (messages == null || messages.isEmpty()) return;

        boolean changed = false;
        for (int i = 0; i < messages.size(); i++) {
            GuiMessage line = messages.get(i);
            Component content = line.content();
            Component swapped = swapKnown(content);
            if (swapped != content) {
                messages.set(i, new GuiMessage(line.addedTime(), swapped, line.signature(), line.source(), line.tag()));
                changed = true;
            }
        }
        if (changed) ((ChatHudInvoker) hud).invokeRefresh();
    }

    /** Mirrors CosmeticChatMixin's swap but without firing new lookups (own nick + known remote nicks). */
    private static Component swapKnown(Component text) {
        Component out = text;
        if (NickState.isActive()) {
            String real = NickState.realName();
            if (!real.isEmpty() && out.getString().contains(real))
                out = NameRewriter.replaceName(out, real, NickState.asComponent());
        }
        return RemoteNicks.applyResolvedOnly(out);
    }
}
