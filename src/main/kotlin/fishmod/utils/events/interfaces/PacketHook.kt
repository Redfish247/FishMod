package fishmod.utils.events.interfaces

import net.minecraft.network.protocol.Packet

fun interface PacketHook {
    fun onPacket(packet: Packet<*>): Boolean
}
