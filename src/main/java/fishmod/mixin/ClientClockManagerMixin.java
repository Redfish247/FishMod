package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.visuals.TimeChanger;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if <26.3 {
@Mixin(ClientClockManager.class)
//?} else {
/*@Mixin(ClientClockManager.ClientClockInstance.class)
*///?}
public abstract class ClientClockManagerMixin {

    //? if <26.3 {
    @ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
    //?} else {
    /*@ModifyReturnValue(method = "totalTicks", at = @At("RETURN"))
    *///?}
    private long fishmod$timeChangerOverride(long original) {
        try {
            return TimeChanger.active() ? TimeChanger.overrideTicks() : original;
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientClockManagerMixin.1", "time changer override failed", t);
            return original;
        }
    }
}
