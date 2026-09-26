package fishmod.features.item

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

fun ItemStack.fishmodCustomDataTag(): CompoundTag? {
    val data = get(DataComponents.CUSTOM_DATA) ?: return null
    val holder = this as ItemCustomDataHolder
    if (holder.`fishmod$getCustomDataSource`() !== data) {
        holder.`fishmod$setCachedCustomData`(data, data.copyTag())
    }
    return holder.`fishmod$getCachedCustomData`()
}
