package fishmod.utils.events.interfaces

import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket

fun interface ParticleEvent {
    fun onParticle(packet: ClientboundLevelParticlesPacket): Boolean
}
