package fishmod.mixin;

import fishmod.utils.Keybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat Peek uses chatHeightUnfocused-sized chat (vanilla's always-on HUD box) since it never opens
 * a real ChatScreen; without this, peeking only ever showed the small unfocused box instead of the
 * full focused-height view a real chat screen gets. getHeight() is the single choke point vanilla
 * uses to pick between the two options (via isChatFocused(), which only ever sees a real ChatScreen).
 */
@Mixin(ChatComponent.class)
public abstract class ChatPeekMixin {

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void fishmod$chatPeekHeight(CallbackInfoReturnable<Integer> cir) {
        if (!Keybinds.chatPeekActive()) return;
        double focusedHeight = Minecraft.getInstance().options.chatHeightFocused().get();
        cir.setReturnValue(ChatComponent.getHeight(focusedHeight));
    }
}
