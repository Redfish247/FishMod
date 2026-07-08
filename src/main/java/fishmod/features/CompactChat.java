package fishmod.features;

import fishmod.mixin.ChatHudInvoker;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

/**
 * Collapses repeated chat lines. When a message identical to one shown within the last
 * {@link #WINDOW_TICKS} arrives, the older line is removed and re-added at the bottom with a
 * trailing "§7(N)" count (e.g. {@code Hi im RedFish2471 (2)}) instead of stacking duplicates.
 *
 * <p>Runs at display time from {@link fishmod.mixin.ChatHudMixin} (after chat-filter/command
 * parsing), so packet-level parsers are unaffected. It manipulates {@link ChatComponent}'s backing
 * {@code messages} list and re-wraps via {@link ChatHudInvoker#invokeRefresh()} — the same
 * approach used by {@link fishmod.cosmetic.ChatNickRefresher}.
 */
public final class CompactChat {
    private CompactChat() {}

    /** Duplicate window ≈ 1 minute (20 ticks/second). */
    private static final int WINDOW_TICKS = 60 * 20;
    /** Trailing " (N)" count we previously appended. */
    private static final Pattern COUNT_SUFFIX = Pattern.compile("\\s\\((\\d+)\\)$");

    /**
     * @return true if the message duplicated a recent line and was collapsed into a count (in which
     *         case {@code ci} is cancelled and the caller must stop processing this add).
     */
    public static boolean tryCompact(Component message, ChatComponent hud, CallbackInfo ci) {
        String incoming = stripKey(message.getString());
        if (incoming.isEmpty()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return false;
        int nowTick = mc.gui.getGuiTicks();

        ChatHudInvoker acc = (ChatHudInvoker) hud;
        List<GuiMessage> messages = acc.getMessages();
        if (messages == null || messages.isEmpty()) return false;

        // messages are newest-first, so once we pass a line older than the window we can stop.
        for (int i = 0; i < messages.size(); i++) {
            GuiMessage line = messages.get(i);
            if (nowTick - line.addedTime() > WINDOW_TICKS) break;
            if (!incoming.equals(stripKey(line.content().getString()))) continue;

            int next = extractCount(line.content().getString()) + 1;
            messages.remove(i);
            acc.invokeRefresh();          // drop the stale line's wrapped copies from visibleMessages
            ci.cancel();                  // suppress the un-counted add…
            hud.addClientSystemMessage(withCount(message, next)); // …and re-add it at the bottom with the count
            return true;
        }
        return false;
    }

    /** Message content minus color codes and any trailing " (N)" count, trimmed. */
    private static String stripKey(String s) {
        String plain = s.replaceAll("§.", "");
        Matcher m = COUNT_SUFFIX.matcher(plain);
        if (m.find()) plain = plain.substring(0, m.start());
        return plain.trim();
    }

    /** Current count baked into a line (1 if it carries no "(N)" suffix yet). */
    private static int extractCount(String s) {
        Matcher m = COUNT_SUFFIX.matcher(s.replaceAll("§.", ""));
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    /** Original message with a gray " (N)" appended, preserving its styling. */
    private static Component withCount(Component message, int n) {
        return message.copy().append(Component.literal(" (" + n + ")").withStyle(ChatFormatting.GRAY));
    }
}
