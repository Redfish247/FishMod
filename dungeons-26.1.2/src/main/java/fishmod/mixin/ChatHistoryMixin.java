package fishmod.mixin;

import fishmod.features.general.ChatSearch;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatHistoryMixin {

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void fishmod$filterBySearch(GuiMessage message, CallbackInfo ci) {
        try {
            if (ChatSearch.getActive() && !ChatSearch.matches(message.content())) {
                ci.cancel();
            } else if (fishmod.features.general.ChatTabs.getActive() && !fishmod.features.general.ChatTabs.matches(message.content())) {
                ci.cancel();
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ChatHistoryMixin.1", "chat search filter failed", t);
        }
    }
}
