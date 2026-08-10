package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.item.ItemCustomizationStore;
import fishmod.utils.data.ItemUtil;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Paints a locally-stored model-id override (e.g. reskinning an AOTV as a trident) onto the
 *  ITEM_MODEL data component read path, the same way {@link ItemTrimMixin} does for TRIM. Read
 *  by vanilla item rendering and by {@link ItemModelBehaviorMixin} (which also adopts the model
 *  item's use animation/pose). */
@Mixin(DataComponentHolder.class)
public interface ItemModelOverrideMixin {

    @SuppressWarnings("unchecked")
    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    private <T> T fishmod$customModel(T original, DataComponentType<? extends T> type) {
        if (type == DataComponents.ITEM_MODEL && ((Object) this) instanceof ItemStack stack) {
            String uuid = ItemUtil.getUuid(stack);
            if (uuid != null) {
                String modelId = ItemCustomizationStore.getModelId(uuid);
                if (modelId != null) {
                    Identifier id = Identifier.tryParse(modelId);
                    if (id != null) return (T) id;
                }
            }
        }
        return original;
    }
}
