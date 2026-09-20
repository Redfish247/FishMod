package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.TimeChanger;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {

    @ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
    private long fishmod$timeChangerOverride(long original) {
        return TimeChanger.active() ? TimeChanger.overrideTicks() : original;
    }
}
