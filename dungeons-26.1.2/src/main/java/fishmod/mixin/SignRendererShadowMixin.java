package fishmod.mixin;

import fishmod.utils.SignTextState;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Marks sign text submits so Text Shadow leaves signs alone
@Mixin(AbstractSignRenderer.class)
public abstract class SignRendererShadowMixin {

    @Inject(method = "submit", at = @At("HEAD"), require = 0)
    private void fishmod$signStart(CallbackInfo ci) {
        SignTextState.inSign = true;
    }

    @Inject(method = "submit", at = @At("RETURN"), require = 0)
    private void fishmod$signEnd(CallbackInfo ci) {
        SignTextState.inSign = false;
    }
}
