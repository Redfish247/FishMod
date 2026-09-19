package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.item.ArmorTrimCache;
import fishmod.features.item.ItemCustomizationStore;
import fishmod.utils.data.ItemUtil;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Merges the former separate TRIM and ITEM_MODEL intercepts into one, since every {@code get()} call on ANY component pays this mixin's cost and the two branches don't depend on each other. */
@Mixin(DataComponentHolder.class)
public interface ItemTrimMixin {

    @SuppressWarnings("unchecked")
    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    private <T> T fishmod$customItemData(T original, DataComponentType<? extends T> type) {
        if (type == DataComponents.TRIM) {
            if (((Object) this) instanceof ItemStack stack) {
                String uuid = ItemUtil.getUuid(stack);
                if (uuid != null) {
                    ItemCustomizationStore.ArmorTrimId trimId = ItemCustomizationStore.getArmorTrim(uuid);
                    if (trimId != null) {
                        ArmorTrim trim = ArmorTrimCache.get(trimId);
                        if (trim != null) return (T) trim;
                    }
                }
            }
        } else if (type == DataComponents.ITEM_MODEL) {
            if (((Object) this) instanceof ItemStack stack) {
                String uuid = ItemUtil.getUuid(stack);
                if (uuid != null) {
                    String modelId = ItemCustomizationStore.getModelId(uuid);
                    if (modelId != null) {
                        Identifier id = Identifier.tryParse(modelId);
                        if (id != null) return (T) id;
                    }
                }
            }
        }
        return original;
    }
}
