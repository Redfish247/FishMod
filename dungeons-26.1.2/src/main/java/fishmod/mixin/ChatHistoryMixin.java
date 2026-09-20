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
