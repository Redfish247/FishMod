package fishmod.mixin;

import fishmod.features.chat.ChatSearch;
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
        if (ChatSearch.getActive() && !ChatSearch.matches(message.content())) {
            ci.cancel();
        }
    }
}
