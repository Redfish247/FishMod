package fishmod.mixin;

import fishmod.features.item.ItemCustomDataHolder;
import fishmod.features.item.ItemCustomizationStore;
import fishmod.features.item.RarityTier;
import fishmod.features.item.RarityTierCache;
import fishmod.utils.data.ItemTools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class FishItemStackMixin implements RarityTierCache, ItemCustomDataHolder {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void fishmod$customItemName(CallbackInfoReturnable<Component> cir) {
        if (!ItemCustomizationStore.hasAny()) return;
        try {
            String uuid = ItemTools.getUuid((ItemStack) (Object) this);
            if (uuid == null) return;
            net.minecraft.network.chat.MutableComponent name = ItemCustomizationStore.parsedItemName(uuid);
            if (name != null) cir.setReturnValue(name.setStyle(cir.getReturnValue().getStyle()));
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishItemStackMixin.1", "custom item name lookup failed", t);
        }
    }

    @Unique
    private RarityTier fishmod$itemRarity = null;

    @Override
    public RarityTier fishmod$getItemRarity() { return fishmod$itemRarity; }

    @Override
    public boolean fishmod$hasItemRarity() { return fishmod$itemRarity != RarityTier.NONE; }

    @Override
    public void fishmod$setItemRarity(RarityTier itemRarity) { this.fishmod$itemRarity = itemRarity; }

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
