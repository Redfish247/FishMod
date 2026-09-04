package fishmod.features.item

/**
 * Duck interface stamped onto `ItemStack` (via `ItemStackMixin`) to cache an item's
 * parsed rarity so the lore isn't re-scanned every frame for every visible slot. Lives outside
 * the mixin package so Mixin never pulls it into init.
 */
interface ItemRarityHolder {
    fun `fishmod$getItemRarity`(): ItemRarity
    fun `fishmod$hasItemRarity`(): Boolean
    fun `fishmod$setItemRarity`(itemRarity: ItemRarity)
    fun `fishmod$hasScanned`(): Boolean
}
