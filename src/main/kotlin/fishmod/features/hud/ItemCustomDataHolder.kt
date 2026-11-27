package fishmod.features.hud

import net.minecraft.nbt.CompoundTag

interface ItemCustomDataHolder {
    fun `fishmod$getCustomDataSource`(): Any?
    fun `fishmod$getCachedCustomData`(): CompoundTag?
    fun `fishmod$setCachedCustomData`(source: Any?, tag: CompoundTag?)
}
