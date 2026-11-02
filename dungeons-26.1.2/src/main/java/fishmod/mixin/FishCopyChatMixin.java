package fishmod.mixin;

import fishmod.utils.config.values.MiscSettings;
import fishmod.utils.data.TextTools;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
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
        boolean smart = fishmod.utils.config.values.FishSettings.chatFeatureEnabled
                && fishmod.utils.config.values.FishSettings.smartCopyChat;
        if ((!MiscSettings.copyChat && !smart) || click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            FishChatInvoker hudInvoker = (FishChatInvoker) fishmod.compat.McCompatKt.getHud(mc).getChat();

            double x = toChatLineX(hudInvoker, click.x());
            double y = toChatLineY(hudInvoker, click.y(), mc);

            String string;
            if (MiscSettings.copyLineOnly && !smart) {
                int index = getMessageLineIndex(hudInvoker, mc, x, y);
                List<GuiMessage.Line> visibleMessages = hudInvoker.getVisibleMessages();
                if (visibleMessages == null) {
                    fishmod.utils.debug.FishDiag.fail("FishCopyChatMixin.2", "chat visible messages list is null");
                    return;
                }
                if (index < 0 || index >= visibleMessages.size()) return;

                GuiMessage.Line msg = visibleMessages.get(index);
                string = TextTools.orderedTextToString(msg.content());
            } else {
                string = copyChat(hudInvoker, mc, x, y);
            }

            if (string == null) return;

            if (MiscSettings.removeColorCodes || smart) {
                string = string.replaceAll("§.", "");
            } else if (MiscSettings.replaceColorChars) {
                string = string.replaceAll("§", "&");
            }

            string = cleanCopied(string);
            if (string == null || string.isEmpty()) return;

            mc.keyboardHandler.setClipboard(string);
            fishmod.utils.FishMsg.send("§aChat Message Copied");
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishCopyChatMixin.1", "chat copy failed button=" + click.button(), t);
        }
    }

    @Unique
    private static String cleanCopied(String s) {
        if (s == null) return null;
        s = s.replaceAll("[-=_~─━▬—⎯]{4,}", " ");
        s = s.replaceAll("\\s{2,}", " ").trim();
        return s;
    }

    @Unique
    private static double toChatLineX(FishChatInvoker hud, double screenX) {
        return screenX / hud.invokeChatScale() - 2.0;
    }

    @Unique
    private static double toChatLineY(FishChatInvoker hud, double screenY, Minecraft mc) {
        int scaledHeight = mc.getWindow().getGuiScaledHeight();
        return ((double)(scaledHeight - 40) - screenY) / hud.invokeChatScale();
    }

    @Unique
    private static int getMessageLineIndex(FishChatInvoker hud, Minecraft mc, double chatX, double chatY) {
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
    private static String copyChat(FishChatInvoker hudInvoker, Minecraft mc, double x, double y) {
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
            TextTools.acceptOrderedText(builder, chatHudLine.content());
        }
        return builder.toString();
    }
}
