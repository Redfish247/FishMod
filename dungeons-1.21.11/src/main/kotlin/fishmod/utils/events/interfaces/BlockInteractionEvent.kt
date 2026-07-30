package fishmod.utils.events.interfaces

import net.minecraft.item.ItemStack
import net.minecraft.util.hit.BlockHitResult

fun interface BlockInteractionEvent {
    fun interact(result: BlockHitResult, item: ItemStack): Boolean
}
