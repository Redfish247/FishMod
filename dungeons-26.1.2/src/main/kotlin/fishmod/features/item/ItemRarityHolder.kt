package fishmod.features.item

interface ItemRarityHolder {
    fun `fishmod$getItemRarity`(): ItemRarity
    fun `fishmod$hasItemRarity`(): Boolean
    fun `fishmod$setItemRarity`(itemRarity: ItemRarity)
    fun `fishmod$hasScanned`(): Boolean
}
