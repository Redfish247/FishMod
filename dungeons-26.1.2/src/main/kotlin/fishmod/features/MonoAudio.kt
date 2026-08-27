package fishmod.features

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.lwjgl.openal.AL10

/**
 * Mono Audio (ported from NoammAddons' MonoAudio + MixinChannel/MixinSoundEngine). Pins every
 * OpenAL source straight in front of the listener at its true distance, so panning collapses to a
 * single centred channel while volume falloff still works.
 */
object MonoAudio {

    @JvmStatic
    fun distanceToListener(pos: Vec3): Double =
        pos.distanceTo(Minecraft.getInstance().soundManager.listenerTransform.position())

    @JvmStatic
    fun applyCenteredPosition(source: Int, distance: Double) {
        AL10.alSourcefv(source, AL10.AL_POSITION, floatArrayOf(0f, 0f, -distance.toFloat()))
    }
}
