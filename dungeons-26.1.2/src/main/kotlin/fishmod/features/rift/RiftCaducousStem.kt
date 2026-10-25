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
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("fm").then(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("rift").then(
                    net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("stem").executes { debug(); 1 })))
        }
        ClientTickEvents.END_CLIENT_TICK.register { if (tick++ % 4 == 0) scan() }
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
        val m = BlockPos.MutableBlockPos()
        for (dx in -RADIUS..RADIUS) for (dz in -RADIUS..RADIUS) {
            var y = base.y + RADIUS + 2
            val low = base.y - RADIUS
            // First bush from the top of the window, then climb past the window to the stem's real top
            while (y >= low && !level.getBlockState(m.set(base.x + dx, y, base.z + dz)).`is`(Blocks.ROSE_BUSH)) y--
            if (y < low) continue
            var n = 0
            while (n++ < 64 && climbable(level, m.set(base.x + dx, y + 1, base.z + dz))) y++
            found.add(BlockPos(base.x + dx, y, base.z + dz))
        }
        tops = found
    }

    // Stem continues upward through any non-solid plant block (the vine above the bush is not a Rose Bush)
    private fun climbable(level: net.minecraft.world.level.Level, p: BlockPos): Boolean {
        val s = level.getBlockState(p)
        return !s.isAir && s.fluidState.isEmpty && s.getCollisionShape(level, p).isEmpty
    }

    // /fm rift stem: block ids in the column above the targeted block
    private fun debug() {
        val mc = Minecraft.getInstance(); val level = mc.level ?: return
        val hit = mc.hitResult as? net.minecraft.world.phys.BlockHitResult
        val say = { t: String -> fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal(t)) }
        if (hit == null || hit.type != net.minecraft.world.phys.HitResult.Type.BLOCK) { say("§7[stem] not looking at a block"); return }
        for (dy in 0..14) {
            val p = hit.blockPos.above(dy)
            say("§7[stem] +$dy ${p.toShortString()} ${level.getBlockState(p).block.descriptionId}")
        }
    }

    private fun render() {
        if (!S.riftStem) return
        val c = S.riftStemColor
        for (p in tops) RenderUtils.gizmoBox(AABB(p), (c and 0xFFFFFF) or 0x60000000, c, false)
    }
}
