package fishmod.utils.events.interfaces

import net.minecraft.network.protocol.Packet

fun interface PacketEvent {
    fun onPacket(packet: Packet<*>): Boolean
}
