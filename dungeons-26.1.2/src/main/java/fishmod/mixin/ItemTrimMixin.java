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

/**
 * Paints locally-stored item-data overrides onto the {@code DataComponentHolder.get()} read path:
 * armor TRIM (was {@code ItemTrimMixin}) and the ITEM_MODEL id (was {@code ItemModelOverrideMixin},
 * read by vanilla item rendering and by {@link ItemModelBehaviorMixin}). Merged into one
 * {@code @ModifyReturnValue} intercept on the hottest item-data read path — every {@code get()}
 * call on ANY component pays this mixin's cost, so two separate intercepts here would double it
 * for no reason: neither branch depends on the other's result or ordering, they just dispatch on
 * the component type.
 */
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
