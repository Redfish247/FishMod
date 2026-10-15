package fishmod.features.rift

import fishmod.utils.Fmt
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import fishmod.features.rift.RiftSettings as S

// Agaricus Cap countdown + Wilted Berberis highlight (particle mode)
object RiftDreadfarm {

    private var capPos: BlockPos? = null
    private var capStart = 0L
    private var capReady = false

    @Volatile private var berberis: BlockPos? = null
    @Volatile private var berberisAt = 0L

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { tickCap() }
        Events.ON_WORLD_CHANGE.register { capPos = null; berberis = null; false }
        Events.ON_PARTICLE.register { p ->
            if (S.riftBerberis && p.particle.type === ParticleTypes.FIREWORK && Rift.inArea("Dreadfarm")) {
                val me = Minecraft.getInstance().player
                if (me != null && me.distanceToSqr(p.x, p.y, p.z) < 400.0) {
                    berberis = BlockPos.containing(p.x, p.y, p.z); berberisAt = System.currentTimeMillis()
                }
            }
            false
        }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    private fun tickCap() {
        if (!S.riftAgaricus || !Rift.inArea("Dreadfarm", "West Village")) { capPos = null; return }
        val mc = Minecraft.getInstance()
        val hit = mc.hitResult as? BlockHitResult ?: return
        val level = mc.level ?: return
        val pos = hit.blockPos
        when (level.getBlockState(pos).block) {
            Blocks.BROWN_MUSHROOM -> if (pos != capPos || capReady) { capPos = pos; capStart = System.currentTimeMillis(); capReady = false }
            Blocks.RED_MUSHROOM -> if (pos == capPos) capReady = true
            else -> {}
        }
    }

    private fun render() {
        if (!Rift.inRift()) return
        capPos?.takeIf { S.riftAgaricus }?.let { pos ->
            val at = Vec3(pos.x + 0.5, pos.y + 1.1, pos.z + 0.5)
            val left = S.riftAgaricusSeconds - (System.currentTimeMillis() - capStart) / 1000.0
            val t = if (capReady) "§c§lCLICK!" else if (left > 0) "§b" + Fmt.f1(left) + "s" else "§eWaiting..."
            RenderUtils.gizmoText(Component.literal(t), at, 1.5f, -1, true)
        }
        val b = berberis
        if (S.riftBerberis && b != null) {
            if (System.currentTimeMillis() - berberisAt > 3000L) { berberis = null; return }
            val c = S.riftBerberisColor
            RenderUtils.gizmoBox(AABB(b), (c and 0xFFFFFF) or 0x40000000, c, true)
            RenderUtils.gizmoText(Component.literal("§eWilted Berberis"), Vec3(b.x + 0.5, b.y + 1.4, b.z + 0.5), 1.2f, -1, true)
        }
    }
}
