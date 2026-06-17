package fishmod.features.item;

/**
 * Duck interface stamped onto {@code ItemStack} (via {@code ItemStackMixin}) to cache an item's
 * parsed rarity so the lore isn't re-scanned every frame for every visible slot. Ported from
 * blade-addons. Lives outside the mixin package so Mixin never pulls it into init.
 */
public interface ItemRarityHolder {
    ItemRarity fishmod$getItemRarity();
    boolean fishmod$hasItemRarity();
    void fishmod$setItemRarity(ItemRarity itemRarity);
    boolean fishmod$hasScanned();
}
