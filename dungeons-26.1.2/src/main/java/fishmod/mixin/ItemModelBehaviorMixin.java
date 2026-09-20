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

@Mixin(ItemStack.class)
public abstract class ItemModelBehaviorMixin {

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void fishmod$useModelItemUseAction(CallbackInfoReturnable<ItemUseAnimation> cir) {
        ItemStack self = (ItemStack) (Object) this;
        Identifier modelId = self.get(DataComponents.ITEM_MODEL);
        if (modelId == null) return;

        Item modelItem = BuiltInRegistries.ITEM.getValue(modelId);
        if (modelItem == null || modelItem == Items.AIR || modelItem == self.getItem()) return;

        cir.setReturnValue(modelItem.getDefaultInstance().getUseAnimation());
    }
}
