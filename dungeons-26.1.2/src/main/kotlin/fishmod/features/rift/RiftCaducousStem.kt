package fishmod.features.rift

import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import fishmod.features.rift.RiftSettings as S

// Caducous Stem = Rose Bush column in Dreadfarm, wiki: harvest top to bottom with the Farming Wand (no SkyHanni/Skyblocker equivalent exists)
object RiftCaducousStem {

    private const val RADIUS = 6
    @Volatile private var tops: List<BlockPos> = emptyList()
    private var tick = 0

    fun count() = tops.size

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { if (tick++ % 5 == 0) scan() }
        fishmod.utils.events.Events.ON_WORLD_CHANGE.register { tops = emptyList(); false }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    private fun scan() {
        val mc = Minecraft.getInstance()
        val me = mc.player; val level = mc.level
        if (!S.riftStem || me == null || level == null || !Rift.inRift() || !RiftDreadfarm.wandHeld()) { tops = emptyList(); return }
        val ar = Rift.area(); if (ar.isNotEmpty() && !ar.contains("Dreadfarm", true)) { tops = emptyList(); return }
        val base = me.blockPosition()
        val found = ArrayList<BlockPos>()
        for (dx in -RADIUS..RADIUS) for (dz in -RADIUS..RADIUS) {
            var top: BlockPos? = null
            for (dy in -RADIUS..RADIUS + 2) {
                val p = BlockPos(base.x + dx, base.y + dy, base.z + dz)
                if (level.getBlockState(p).`is`(Blocks.ROSE_BUSH)) top = p
            }
            if (top != null) found.add(top)
        }
        tops = found
    }

    private fun render() {
        if (!S.riftStem) return
        val c = S.riftStemColor
        for (p in tops) RenderUtils.gizmoBox(AABB(p), (c and 0xFFFFFF) or 0x60000000, c, false)
    }
}
