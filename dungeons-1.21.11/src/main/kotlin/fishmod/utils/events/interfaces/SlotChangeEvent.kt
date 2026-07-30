package fishmod.utils.events.interfaces

import net.minecraft.item.ItemStack

fun interface SlotChangeEvent {
    fun onSlotChange(slot: Int, item: ItemStack): Boolean
}
