package fishmod.mixin;

import fishmod.features.item.ItemRarity;
import fishmod.features.item.ItemRarityHolder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Caches the parsed {@link ItemRarity} on each ItemStack so the rarity background skips re-scanning. */
@Mixin(ItemStack.class)
public class ItemStackMixin implements ItemRarityHolder {

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
