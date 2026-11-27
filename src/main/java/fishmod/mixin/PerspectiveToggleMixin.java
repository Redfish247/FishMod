package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class PerspectiveToggleMixin {

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;cycle()Lnet/minecraft/client/CameraType;"))
    private CameraType fishmod$cycleSkippingFrontFacing(CameraType type) {
        if (FishSettings.disableFrontFacingCamera && type == CameraType.THIRD_PERSON_BACK) {
            return CameraType.FIRST_PERSON;
        }
        return type.cycle();
    }
}
