package fishmod.utils.events.interfaces

import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket

fun interface PlayerListEvent {
    fun onNewPlayerEntry(receivedEntry: PlayerListS2CPacket.Entry): Boolean
}
