package fishmod.mixin;

import fishmod.utils.Keybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public abstract class ChatPeekMixin {

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void fishmod$chatPeekHeight(CallbackInfoReturnable<Integer> cir) {
        if (!Keybinds.chatPeekActive()) return;
        double focusedHeight = Minecraft.getInstance().options.chatHeightFocused().get();
        cir.setReturnValue(ChatComponent.getHeight(focusedHeight));
    }
}
