package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.item.ArmorTrimCache;
import fishmod.features.item.ItemCustomizationStore;
import fishmod.utils.data.ItemUtil;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Paints a locally-stored armor trim override onto the DYED_COLOR/TRIM data component read path. Adapted from Skyblocker's DataComponentHolderMixin (github.com/SkyblockerMod/Skyblocker, MIT). */
@Mixin(DataComponentHolder.class)
public interface ItemTrimMixin {

    @SuppressWarnings("unchecked")
    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    private <T> T fishmod$customTrim(T original, DataComponentType<? extends T> type) {
        if (type == DataComponents.TRIM && ((Object) this) instanceof ItemStack stack) {
            String uuid = ItemUtil.getUuid(stack);
            if (uuid != null) {
                ItemCustomizationStore.ArmorTrimId trimId = ItemCustomizationStore.getArmorTrim(uuid);
                if (trimId != null) {
                    ArmorTrim trim = ArmorTrimCache.get(trimId);
                    if (trim != null) return (T) trim;
                }
            }
        }
        return original;
    }
}
