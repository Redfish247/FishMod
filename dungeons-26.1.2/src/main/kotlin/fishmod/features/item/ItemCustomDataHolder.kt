package fishmod.features.item

import net.minecraft.nbt.CompoundTag

// ItemStack duck: cached CUSTOM_DATA copy
interface ItemCustomDataHolder {
    fun `fishmod$getCachedCustomData`(): CompoundTag?
    fun `fishmod$setCachedCustomData`(tag: CompoundTag?)
    fun `fishmod$hasScannedCustomData`(): Boolean
}
