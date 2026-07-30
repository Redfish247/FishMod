package fishmod.utils.events.interfaces

import net.minecraft.network.packet.s2c.play.ParticleS2CPacket

fun interface ParticleEvent {
    fun onParticle(packet: ParticleS2CPacket): Boolean
}
