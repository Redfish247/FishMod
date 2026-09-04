package fishmod.mixin.accessors;

import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Snappy (non-lerped) boss-bar fill, used for the boss health number. */
@Mixin(LerpingBossEvent.class)
public interface LerpingBossEventAccessor {
    @Accessor("targetPercent")
    float getTargetPercent();
}
