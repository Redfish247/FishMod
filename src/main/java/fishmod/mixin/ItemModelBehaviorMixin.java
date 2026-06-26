package fishmod.mixin;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a FishMod-customized item BEHAVE like the item it borrows its model from, not just look like
 * it. When a stack carries an ITEM_MODEL override that resolves to a different vanilla item — e.g. a
 * Terminator (a bow) given the crossbow model — the client adopts that model item's {@link UseAction}
 * so the hold / draw pose matches the model (crossbow load pose instead of a bow pull). Without this
 * the item is "a bow retextured as a crossbow"; with it, it actually acts like a crossbow.
 *
 * <p>Purely client-side and cosmetic: item use on Hypixel is server-authoritative, so this only
 * changes how the held item is posed and animated on your own screen. The override keys off the
 * ITEM_MODEL component itself (the same component {@link fishmod.features.ItemCustomizer} sets), so
 * it applies to your items and to other players' shared customs alike (see
 * {@link fishmod.cosmetic.RemoteItems}).
 */
@Mixin(ItemStack.class)
public abstract class ItemModelBehaviorMixin {

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void fishmod$useModelItemUseAction(CallbackInfoReturnable<UseAction> cir) {
        ItemStack self = (ItemStack) (Object) this;
        Identifier modelId = self.get(DataComponentTypes.ITEM_MODEL);
        if (modelId == null) return;

        Item modelItem = Registries.ITEM.get(modelId);
        // Skip when the model id isn't a real item (custom resource-pack model) or is the item's own
        // model (a no-op swap) — otherwise we'd recurse and/or change nothing.
        if (modelItem == null || modelItem == Items.AIR || modelItem == self.getItem()) return;

        // The model item's default stack reports its own model id, so this call hits the guard above
        // and returns its real use action without re-entering for this stack.
        cir.setReturnValue(modelItem.getDefaultStack().getUseAction());
    }
}
