package fishmod.mixin;

import fishmod.features.item.ItemCustomizationStore;
import fishmod.features.item.ItemRarity;
import fishmod.features.item.ItemRarityHolder;
import fishmod.utils.data.ItemUtil;
import fishmod.utils.data.LegacyFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Caches the parsed {@link ItemRarity} on each ItemStack so the rarity background skips re-scanning. */
@Mixin(ItemStack.class)
public class ItemStackMixin implements ItemRarityHolder {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void fishmod$customItemName(CallbackInfoReturnable<Component> cir) {
        String uuid = ItemUtil.getUuid((ItemStack) (Object) this);
        if (uuid == null) return;
        String name = ItemCustomizationStore.getItemName(uuid);
        if (name != null) cir.setReturnValue(LegacyFormatting.parse(name).setStyle(cir.getReturnValue().getStyle()));
    }

    @Unique
    private ItemRarity fishmod$itemRarity = null;

    @Override
    public ItemRarity fishmod$getItemRarity() { return fishmod$itemRarity; }

    @Override
    public boolean fishmod$hasItemRarity() { return fishmod$itemRarity != ItemRarity.NONE; }

    @Override
    public void fishmod$setItemRarity(ItemRarity itemRarity) { this.fishmod$itemRarity = itemRarity; }

    @Override
    public boolean fishmod$hasScanned() { return fishmod$itemRarity != null; }
}
