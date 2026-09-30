package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.TimeChanger;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public abstract class LevelTimeMixin {

    @ModifyReturnValue(method = "getDefaultClockTime", at = @At("RETURN"))
    private long fishmod$defaultClock(long original) {
        try {
            return TimeChanger.active() ? TimeChanger.overrideTicks() : original;
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("LevelTimeMixin.1", "time changer default clock override failed", t);
            return original;
        }
    }

    @ModifyReturnValue(method = "getOverworldClockTime", at = @At("RETURN"))
    private long fishmod$overworldClock(long original) {
        try {
            return TimeChanger.active() ? TimeChanger.overrideTicks() : original;
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("LevelTimeMixin.2", "time changer overworld clock override failed", t);
            return original;
        }
    }
}
