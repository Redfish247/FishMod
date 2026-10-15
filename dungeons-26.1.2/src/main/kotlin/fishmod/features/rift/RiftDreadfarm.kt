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

    // Wilted Berberis tracking ported from SkyHanni RiftWiltedBerberisHelper (particle mode)
    private class Berb(var pos: Vec3) { var prev: Vec3? = null; var moving = true; var y = 0.0; var last = System.currentTimeMillis() }
    private val berbs = java.util.concurrent.CopyOnWriteArrayList<Berb>()
    @Volatile private var hasWand = false
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            tickCap()
            if (tick++ % 5 == 0) {
                hasWand = Rift.heldId() == "FARMING_WAND"
                val now = System.currentTimeMillis(); berbs.removeIf { now - it.last > 500L }
            }
        }
        Events.ON_WORLD_CHANGE.register { capPos = null; berbs.clear(); false }
        Events.ON_PARTICLE.register { p -> onParticle(p.particle.type, Vec3(p.x, p.y, p.z)); false }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    private fun onParticle(type: net.minecraft.core.particles.ParticleType<*>, at: Vec3) {
        if (!S.riftBerberis || !hasWand || type !== ParticleTypes.FIREWORK || !Rift.inArea("Dreadfarm")) return
        val b = berbs.filter { it.pos.distanceToSqr(at) < 8 }.minByOrNull { it.pos.distanceToSqr(at) }
        if (b == null) { berbs.add(Berb(at)); return }
        val isMoving = b.pos != at
        if (isMoving) {
            if (b.pos.distanceTo(at) > 3) { b.prev = null; b.moving = true }
            if (!b.moving) b.prev = b.pos
        } else b.y = at.y - 1
        b.moving = isMoving; b.pos = at; b.last = System.currentTimeMillis()
    }

    private fun tickCap() {
        if (!S.riftAgaricus || !Rift.inArea("Dreadfarm", "West Village") || Rift.heldId() != "FARMING_WAND") { capPos = null; return }
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
        if (!S.riftBerberis || !hasWand) return
        val me = Minecraft.getInstance().player ?: return
        val c = S.riftBerberisColor
        for (b in berbs) {
            if (b.y == 0.0 || me.distanceToSqr(b.pos) > 400.0) continue
            val box = cell(b.pos, b.y)
            if (!b.moving) {
                RenderUtils.gizmoBox(box, (c and 0xFFFFFF) or 0x60000000, c, false)
                RenderUtils.gizmoText(Component.literal("§eWilted Berberis"), Vec3(box.center.x, box.maxY + 0.6, box.center.z), 1.5f, -1, false)
            } else {
                RenderUtils.gizmoBox(box, 0x50FFFFFF, 0xFFFFFFFF.toInt(), false)
                b.prev?.let { pv ->
                    val pb = cell(pv, b.y)
                    RenderUtils.gizmoBox(pb, 0x30AAAAAA, 0xFFAAAAAA.toInt(), false)
                    RenderUtils.gizmoLine(pb.center, box.center, 0xFFFFFFFF.toInt(), 3f, false)
                }
            }
        }
    }

    private fun cell(p: Vec3, y: Double): AABB {
        val x = Math.floor(p.x - 0.5 + 0.01); val z = Math.floor(p.z - 0.5 + 0.01); val yy = Math.floor(y + 0.01)
        return AABB(x + 0.1, yy - 0.1, z + 0.1, x + 0.9, yy + 0.9, z + 0.9)
    }
}
