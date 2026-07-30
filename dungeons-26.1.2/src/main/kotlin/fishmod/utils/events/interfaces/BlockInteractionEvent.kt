package fishmod.utils.events.interfaces

import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult

fun interface BlockInteractionEvent {
    fun interact(result: BlockHitResult, item: ItemStack): Boolean
}
