package fishmod.mixin;

import fishmod.utils.Misc;
import fishmod.utils.config.values.ExtraOptions;
import fishmod.utils.data.TextUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Mixin(ChatScreen.class)
public class FishCopyChatMixin extends Screen {

    protected FishCopyChatMixin(Component title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private static void mouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        boolean smart = fishmod.utils.config.values.FishSettings.smartCopyChat;
        if ((!ExtraOptions.copyChat && !smart) || click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        Minecraft mc = Minecraft.getInstance();
        ChatHudInvoker hudInvoker = (ChatHudInvoker) mc.gui.getChat();
        if (hudInvoker == null) return;

        double x = toChatLineX(hudInvoker, click.x());
        double y = toChatLineY(hudInvoker, click.y(), mc);

        String string;
        // Smart mode always copies the full wrapped message (ignores the line-only option).
        if (ExtraOptions.copyLineOnly && !smart) {
            int index = getMessageLineIndex(hudInvoker, mc, x, y);
            List<GuiMessage.Line> visibleMessages = hudInvoker.getVisibleMessages();
            if (visibleMessages == null || index < 0 || index >= visibleMessages.size()) return;

            GuiMessage.Line msg = visibleMessages.get(index);
            string = TextUtil.orderedTextToString(msg.content());
        } else {
            string = copyChat(hudInvoker, mc, x, y);
        }

        if (string == null) return;

        // Smart copy always strips codes: Minecraft's chat input drops the "§" on paste, which would
        // otherwise leave bare code digits behind (e.g. "§9Party" → "9Party").
        if (ExtraOptions.removeColorCodes || smart) {
            string = string.replaceAll("§.", "");
        } else if (ExtraOptions.replaceColorChars) {
            string = string.replaceAll("§", "&");
        }

        string = cleanCopied(string);
        if (string == null || string.isEmpty()) return;

        mc.keyboardHandler.setClipboard(string);
        fishmod.utils.FishMsg.send("§aChat Message Copied");
    }

    /** Strips chat-divider runs (----, ▬▬▬, ═══ …) and collapses whitespace left by joining wraps. */
    @Unique
    private static String cleanCopied(String s) {
        if (s == null) return null;
        s = s.replaceAll("[-=_~─━▬—⎯]{4,}", " "); // divider lines
        s = s.replaceAll("\\s{2,}", " ").trim();    // collapse joined-wrap whitespace
        return s;
    }

    @Unique
    private static double toChatLineX(ChatHudInvoker hud, double screenX) {
        return screenX / hud.invokeChatScale() - 2.0;
    }

    @Unique
    private static double toChatLineY(ChatHudInvoker hud, double screenY, Minecraft mc) {
        int scaledHeight = mc.getWindow().getGuiScaledHeight();
        return ((double)(scaledHeight - 40) - screenY) / hud.invokeChatScale();
    }

    @Unique
    private static int getMessageLineIndex(ChatHudInvoker hud, Minecraft mc, double chatX, double chatY) {
        if (chatX < 0.0 || chatY < 0.0) return -1;
        List<GuiMessage.Line> messages = hud.getVisibleMessages();
        int lh = hud.invokeLineHeight();
        int visible = Math.min(((ChatComponent)(Object)hud).getLinesPerPage(), messages.size());
        if (chatX <= hud.invokeWidth() && chatY < (double)(lh * visible + lh)) {
            int j = (int)(chatY / lh) + hud.getScrolledLines();
            if (j >= 0 && j < messages.size()) return j;
        }
        return -1;
    }

    @Unique
    private static String copyChat(ChatHudInvoker hudInvoker, Minecraft mc, double x, double y) {
        List<GuiMessage.Line> messages = hudInvoker.getVisibleMessages();
        int endIndex = getMessageLineIndex(hudInvoker, mc, x, y);

        if (messages == null || endIndex < 0 || endIndex >= messages.size()) return null;

        int startIndex = endIndex;

        for (int i = endIndex; i >= 0; i--) {
            GuiMessage.Line chatHudLine = messages.get(i);
            if (chatHudLine.endOfEntry()) {
                startIndex = i;
                break;
            }
        }

        if (!messages.get(endIndex).endOfEntry()) {
            for (int i = endIndex + 1; i < messages.size(); i++) {
                GuiMessage.Line chatHudLine = messages.get(i);
                if (chatHudLine.endOfEntry()) {
                    endIndex = i - 1;
                    break;
                }
            }
        }
        StringBuilder builder = new StringBuilder();
        for (int i = endIndex; i >= startIndex; i--) {
            GuiMessage.Line chatHudLine = messages.get(i);
            TextUtil.acceptOrderedText(builder, chatHudLine.content());
        }
        return builder.toString();
    }
}
