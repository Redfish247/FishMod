package fishmod.features.item

interface RarityTierCache {
    fun `fishmod$getItemRarity`(): RarityTier
    fun `fishmod$hasItemRarity`(): Boolean
    fun `fishmod$setItemRarity`(itemRarity: RarityTier)
    fun `fishmod$hasScanned`(): Boolean
}
