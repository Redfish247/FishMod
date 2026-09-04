package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.TimeChanger;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Time Changer (26.1.2 path). The sky/celestial renderer no longer reads {@code Level.getDayTime()}
 * — it samples {@code EnvironmentAttribute}s off a {@code Timeline}, and {@code AttributeTrackSampler}
 * feeds that timeline from {@link ClientClockManager#getTotalTicks}. Overriding that return value is
 * the single chokepoint that moves the visible sun/moon/sky (and it also flows back through
 * {@code Level.getOverworldClockTime()}). Read per-sample, so no per-tick push and no flicker.
 */
@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {

    @ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
    private long fishmod$timeChangerOverride(long original) {
        return TimeChanger.active() ? TimeChanger.overrideTicks() : original;
    }
}
