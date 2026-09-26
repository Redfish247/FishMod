package fishmod.mixin;

import fishmod.features.item.ItemCustomDataHolder;
import fishmod.features.item.ItemCustomizationStore;
import fishmod.features.item.ItemRarity;
import fishmod.features.item.ItemRarityHolder;
import fishmod.utils.data.ItemUtil;
import fishmod.utils.data.LegacyFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin implements ItemRarityHolder, ItemCustomDataHolder {

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

    @Unique
    private Object fishmod$customDataSource = null;
    @Unique
    private CompoundTag fishmod$cachedCustomData = null;

    @Override
    public Object fishmod$getCustomDataSource() { return fishmod$customDataSource; }

    @Override
    public CompoundTag fishmod$getCachedCustomData() { return fishmod$cachedCustomData; }

    @Override
    public void fishmod$setCachedCustomData(Object source, CompoundTag tag) {
        fishmod$customDataSource = source;
        fishmod$cachedCustomData = tag;
    }
}
