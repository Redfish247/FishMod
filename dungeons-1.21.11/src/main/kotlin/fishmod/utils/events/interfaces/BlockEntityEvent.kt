package fishmod.utils.events.interfaces

import net.minecraft.block.entity.BlockEntity

fun interface BlockEntityEvent {
    fun on(blockEntity: BlockEntity): Boolean
}
