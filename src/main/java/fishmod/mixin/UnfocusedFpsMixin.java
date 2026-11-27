package fishmod.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import fishmod.features.extras.ExtrasSettings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class UnfocusedFpsMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void fishmod$unfocusedCap(CallbackInfoReturnable<Integer> cir) {
        if (!ExtrasSettings.unfocusedFps || Minecraft.getInstance().isWindowActive()) return;
        cir.setReturnValue(Math.min(cir.getReturnValue(), Math.max(1, ExtrasSettings.unfocusedFpsLimit)));
    }
}
