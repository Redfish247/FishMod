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
 * Makes a FishMod-customized item BEHAVE like the item it borrows its model from, not just look like
 * it. When a stack carries an ITEM_MODEL override that resolves to a different vanilla item — e.g. a
 * Terminator (a bow) given the crossbow model — the client adopts that model item's {@link ItemUseAnimation}
 * so the hold / draw pose matches the model (crossbow load pose instead of a bow pull). Without this
 * the item is "a bow retextured as a crossbow"; with it, it actually acts like a crossbow.
 *
 * <p>Purely client-side and cosmetic: item use on Hypixel is server-authoritative, so this only
 * changes how the held item is posed and animated on your own screen. The override keys off the
 * ITEM_MODEL component itself, wherever it's set (including the local override painted on by the
 * ITEM_MODEL branch of {@link ItemTrimMixin}).
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
