package fishmod.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a FishMod-customized item BEHAVE like the item it borrows its model from (e.g. a Terminator
 * bow given a crossbow model gets the crossbow's hold/draw pose), not just look like it. Purely
 * client-side/cosmetic — item use itself stays server-authoritative.
 */
@Mixin(ItemStack.class)
public abstract class ItemModelBehaviorMixin {

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void fishmod$useModelItemUseAction(CallbackInfoReturnable<ItemUseAnimation> cir) {
        ItemStack self = (ItemStack) (Object) this;
        Identifier modelId = self.get(DataComponents.ITEM_MODEL);
        if (modelId == null) return;

        Item modelItem = BuiltInRegistries.ITEM.getValue(modelId);
        // Skip non-items and self-model swaps; the guard also stops the call below from recursing
        if (modelItem == null || modelItem == Items.AIR || modelItem == self.getItem()) return;

        cir.setReturnValue(modelItem.getDefaultInstance().getUseAnimation());
    }
}
