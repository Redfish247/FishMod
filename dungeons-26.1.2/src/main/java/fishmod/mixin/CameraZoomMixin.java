package fishmod.mixin;

import fishmod.features.other.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraZoomMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void fishmod$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(Zoom.modifyFov(cir.getReturnValueF()));
    }
}
