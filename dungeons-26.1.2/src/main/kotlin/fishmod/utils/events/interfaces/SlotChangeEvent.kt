package fishmod.utils.events.interfaces

import net.minecraft.world.item.ItemStack

fun interface SlotChangeEvent {
    fun onSlotChange(slot: Int, item: ItemStack): Boolean
}
