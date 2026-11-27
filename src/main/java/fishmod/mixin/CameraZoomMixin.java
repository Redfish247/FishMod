package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.general.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class CameraZoomMixin {

    // A cancellable @Inject that always calls setReturnValue forces an early return, which skipped other mods'
    // handlers on the same return (Zoomify's @ModifyReturnValue never ran when ours came first, so it only zoomed
    // the hand). @ModifyReturnValue chains with them instead.
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float fishmod$zoom(float fov) {
        return Zoom.modifyFov(fov);
    }
}
