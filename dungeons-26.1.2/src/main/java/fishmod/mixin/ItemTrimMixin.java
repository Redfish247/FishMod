package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.item.ArmorTrimCache;
import fishmod.features.item.ItemCustomizationStore;
import fishmod.features.item.NeuItems;
import fishmod.utils.data.ItemUtil;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DataComponentHolder.class)
public interface ItemTrimMixin {

    @SuppressWarnings("unchecked")
    @ModifyReturnValue(method = "get", at = @At("RETURN"))
    private <T> T fishmod$customItemData(T original, DataComponentType<? extends T> type) {
        if (!ItemCustomizationStore.hasAny()) return original;
        if (type != DataComponents.TRIM && type != DataComponents.ITEM_MODEL && type != DataComponents.PROFILE) return original;
        if (!(((Object) this) instanceof ItemStack stack)) return original;
        try {
            String uuid = ItemUtil.getUuid(stack);
            if (uuid == null) return original;
            if (type == DataComponents.TRIM) {
                ItemCustomizationStore.ArmorTrimId trimId = ItemCustomizationStore.getArmorTrim(uuid);
                if (trimId != null) {
                    ArmorTrim trim = ArmorTrimCache.get(trimId);
                    if (trim != null) return (T) trim;
                }
            } else if (type == DataComponents.ITEM_MODEL) {
                String modelId = ItemCustomizationStore.getModelId(uuid);
                if (modelId != null) {
                    String neu = NeuItems.idOf(modelId);
                    Identifier id = neu != null ? NeuItems.model(neu) : ArmorTrimCache.modelId(modelId);
                    if (id != null) return (T) id;
                }
            } else {
                String neu = NeuItems.idOf(ItemCustomizationStore.getModelId(uuid));
                if (neu != null) {
                    ResolvableProfile profile = NeuItems.profile(neu);
                    if (profile != null) return (T) profile;
                }
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ItemTrimMixin.1", "custom trim/model lookup failed type=" + type, t);
        }
        return original;
    }
}
