package fishmod.features.item

import fishmod.utils.debug.FishDiag
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

fun ItemStack.fishmodCustomDataTag(): CompoundTag? {
    val data = get(DataComponents.CUSTOM_DATA) ?: return null
    val holder = this as? ItemCustomDataHolder ?: run {
        FishDiag.fail("ItemCustomData.1", "ItemStack missing custom data cache mixin (${this.javaClass.name})")
        return data.copyTag()
    }
    if (holder.`fishmod$getCustomDataSource`() !== data) {
        holder.`fishmod$setCachedCustomData`(data, data.copyTag())
    }
    return holder.`fishmod$getCachedCustomData`()
}
