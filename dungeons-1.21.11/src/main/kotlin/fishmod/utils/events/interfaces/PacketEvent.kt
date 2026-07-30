package fishmod.utils.events.interfaces

import net.minecraft.network.packet.Packet

fun interface PacketEvent {
    fun onPacket(packet: Packet<*>): Boolean
}
