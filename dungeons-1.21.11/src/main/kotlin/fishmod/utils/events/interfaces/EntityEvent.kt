package fishmod.utils.events.interfaces

import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity

fun interface EntityEvent {
    fun onEntity(entity: Entity, world: ClientWorld): Boolean
}
