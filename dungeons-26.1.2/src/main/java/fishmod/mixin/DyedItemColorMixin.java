package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import fishmod.features.item.AnimatedDyeAnimator;
import fishmod.features.item.ItemCustomizationStore;
import fishmod.utils.data.ItemUtil;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DyedItemColor.class)
public class DyedItemColorMixin {

    @ModifyReturnValue(method = "getOrDefault", at = @At("RETURN"))
    private static int fishmod$customDyeColor(int originalColor, @Local(name = "itemStack") ItemStack stack) {
        try {
            String uuid = ItemUtil.getUuid(stack);
            if (uuid == null) return originalColor;

            ItemCustomizationStore.AnimatedDye animated = ItemCustomizationStore.getAnimatedDye(uuid);
            if (animated != null) return ARGB.opaque(AnimatedDyeAnimator.colorFor(uuid, animated));

            Integer solid = ItemCustomizationStore.getDyeColor(uuid);
            if (solid != null) return ARGB.opaque(solid);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("DyedItemColorMixin.1", "custom dye color lookup failed", t);
        }
        return originalColor;
    }
}
