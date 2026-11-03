package fishmod.utils.events.interfaces

import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket

fun interface ParticleHook {
    fun onParticle(packet: ClientboundLevelParticlesPacket): Boolean
}
