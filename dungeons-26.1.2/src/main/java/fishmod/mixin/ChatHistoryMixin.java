package fishmod.mixin;

import fishmod.features.chat.ChatSearch;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two chat-buffer tweaks, both keyed off the scrollback:
 * <ul>
 *   <li><b>Infinite Chat History</b> — raises vanilla's hard-coded 100-line cap on the scrollback
 *       buffer ({@code allMessages}), the wrapped display buffer ({@code trimmedMessages}) and the
 *       sent-message history ({@code recentChat}, the up-arrow list) to a configurable limit.</li>
 *   <li><b>Chat Search</b> — while {@link ChatSearch#active} is true, drops any line that doesn't
 *       match the query from the display rebuild (the line stays in {@code allMessages}).</li>
 * </ul>
 * Both off by default.
 */
@Mixin(ChatComponent.class)
public class ChatHistoryMixin {

    private static int fishmod$limit(int vanilla) {
        return (FishSettings.chatFeatureEnabled && FishSettings.infiniteChatHistory)
                ? Math.max(vanilla, FishSettings.infiniteChatHistoryLimit) : vanilla;
    }

    @ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100))
    private int fishmod$expandScrollback(int cap) {
        return fishmod$limit(cap);
    }

    @ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100))
    private int fishmod$expandTrimmed(int cap) {
        return fishmod$limit(cap);
    }

    @ModifyConstant(method = "addRecentChat", constant = @Constant(intValue = 100))
    private int fishmod$expandRecentChat(int cap) {
        return fishmod$limit(cap);
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void fishmod$filterBySearch(GuiMessage message, CallbackInfo ci) {
        if (ChatSearch.getActive() && !ChatSearch.matches(message.content())) {
            ci.cancel();
        }
    }
}
