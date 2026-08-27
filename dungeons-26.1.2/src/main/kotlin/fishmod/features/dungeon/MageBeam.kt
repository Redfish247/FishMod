package fishmod.features.dungeon

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Mage Beam — recolours the Mage ultimate beam (ported from Odin's MageBeam). Collects the
 * `FIREWORK` particle packets, groups the ~collinear runs into beams and draws each as a 3D line;
 * can hide the vanilla particles.
 */
object MageBeam {

    private class Beam(val points: CopyOnWriteArrayList<Vec3> = CopyOnWriteArrayList(), var lastTick: Int = 0)

    private val beams = CopyOnWriteArrayList<Beam>()
    private var tick = 0

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            tick++
            beams.removeAll { tick - it.lastTick > FishSettings.mageBeamDurationTicks.coerceIn(1, 100) }
        }

        Events.ON_PARTICLE.register { packet ->
            if (!FishSettings.mageBeamEnabled || !Location.inDungeon()) return@register false
            if (packet.particle.type !== ParticleTypes.FIREWORK) return@register false
            val p = Vec3(packet.x, packet.y, packet.z)
            val recent = beams.lastOrNull()
            if (recent != null && tick - recent.lastTick < 2 && inLine(recent.points, p)) {
                recent.points.add(p); recent.lastTick = tick
            } else {
                beams.add(Beam(CopyOnWriteArrayList<Vec3>().apply { add(p) }, tick))
            }
            FishSettings.mageBeamHideParticles
        }

        RenderingEvents.LINE.register { _, m, vc -> if (FishSettings.mageBeamDepth) render(m, vc) }
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> if (!FishSettings.mageBeamDepth) render(m, vc) }
    }

    private fun inLine(points: List<Vec3>, next: Vec3): Boolean {
        if (points.size <= 1) return true
        val last = points.last()
        return last.subtract(points[0]).normalize().dot(next.subtract(last).normalize()) > 0.99
    }

    private fun render(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.mageBeamEnabled) return
        val rgba = RenderUtils.toFloats(FishSettings.mageBeamColor)
        for (beam in beams) {
            if (beam.points.size < 8) continue
            for (i in 1 until beam.points.size) {
                RenderUtils.renderLine(matrices, vc, beam.points[i - 1], beam.points[i], rgba)
            }
        }
    }
}
