package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.TimeChanger;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Time Changer: override the world clock getters so the celestial cycle is frame-consistent. */
@Mixin(Level.class)
public abstract class LevelTimeMixin {

    @ModifyReturnValue(method = "getDefaultClockTime", at = @At("RETURN"))
    private long fishmod$defaultClock(long original) {
        return TimeChanger.active() ? TimeChanger.overrideTicks() : original;
    }

    @ModifyReturnValue(method = "getOverworldClockTime", at = @At("RETURN"))
    private long fishmod$overworldClock(long original) {
        return TimeChanger.active() ? TimeChanger.overrideTicks() : original;
    }
}
