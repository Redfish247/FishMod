package fishmod.features.item

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

// per-stack cached copy of CUSTOM_DATA
fun ItemStack.fishmodCustomDataTag(): CompoundTag? {
    val holder = this as ItemCustomDataHolder
    if (!holder.`fishmod$hasScannedCustomData`()) {
        holder.`fishmod$setCachedCustomData`(get(DataComponents.CUSTOM_DATA)?.copyTag())
    }
    return holder.`fishmod$getCachedCustomData`()
}
