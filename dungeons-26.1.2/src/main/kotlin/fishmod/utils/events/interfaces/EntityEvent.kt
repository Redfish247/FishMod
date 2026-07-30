package fishmod.utils.events.interfaces

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity

fun interface EntityEvent {
    fun onEntity(entity: Entity, world: ClientLevel): Boolean
}
