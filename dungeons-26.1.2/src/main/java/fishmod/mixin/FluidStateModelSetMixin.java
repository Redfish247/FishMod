package fishmod.mixin;

import fishmod.features.LavaToWater;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidStateModelSet.class)
public abstract class FluidStateModelSetMixin {

    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private void fishmod$lavaToWater(FluidState state, CallbackInfoReturnable<FluidModel> cir) {
        try {
            LavaToWater.modelHook((FluidStateModelSet) (Object) this, state, cir);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FluidStateModelSetMixin.1", "lava-to-water model hook failed", t);
        }
    }
}
