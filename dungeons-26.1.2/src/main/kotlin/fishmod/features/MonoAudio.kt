package fishmod.features

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.lwjgl.openal.AL10

object MonoAudio {

    @JvmStatic
    fun distanceToListener(pos: Vec3): Double =
        pos.distanceTo(Minecraft.getInstance().soundManager.listenerTransform.position())

    @JvmStatic
    fun applyCenteredPosition(source: Int, distance: Double) {
        AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, -distance.toFloat())
    }
}
