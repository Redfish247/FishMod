package fishmod.features.rift

import fishmod.compat.CB
import fishmod.compat.CI

import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import fishmod.features.rift.RiftSettings as S

object RiftSnake {

    // Ported from SkyHanni LivingCaveSnakeFeatures: a snake is a chain of lapis blocks, newest at the head;
    // the server puts the original block back at the tail, which is the one to hit first.
    private class Snake(val blocks: MutableList<BlockPos>) {
        val head get() = blocks.first()
        var invalidHeadSince = 0L
        fun invalidShape() = blocks.isEmpty() || blocks.zipWithNext().any { (a, b) -> a.distSqr(b) > 9.0 }
    }

    private val snakes = ArrayList<Snake>()
    private val added = java.util.concurrent.ConcurrentLinkedQueue<BlockPos>()
    private val removed = java.util.concurrent.ConcurrentLinkedQueue<BlockPos>()
    // Colour-fade stages the server also sends; they are not snake blocks and must not count as the tail leaving
    private val FADE = setOf(
        CB.BLUE_STAINED_GLASS, CB.LIGHT_BLUE_STAINED_GLASS,
        CB.CYAN_TERRACOTTA, CB.BLUE_TERRACOTTA, CB.LIGHT_BLUE_TERRACOTTA,
    )

    // Recent block updates by type, for /fm rift debug
    private val seen = java.util.concurrent.ConcurrentLinkedQueue<Pair<Long, String>>()
    fun debug(): String {
        val now = System.currentTimeMillis(); seen.removeIf { now - it.first > 15_000L }
        val g = seen.groupingBy { it.second }.eachCount().entries.sortedByDescending { it.value }.take(8)
        return "snake: snakes=${snakes.size} blocks=${snakes.sumOf { it.blocks.size }} updates(15s)=" + g.joinToString(", ") { "${it.key}x${it.value}" }.ifEmpty { "none" }
    }

    fun init() {
        Events.ON_PACKET.register { packet ->
            if (S.riftSnake && Rift.inArea("Living Cave", "Living Stillness")) when (packet) {
                is ClientboundBlockUpdatePacket -> onBlock(packet.pos, packet.blockState)
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { p, st -> onBlock(p, st) }
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { snakes.clear(); added.clear(); removed.clear(); false }
        ClientTickEvents.END_CLIENT_TICK.register { try { tick() } catch (_: Throwable) { snakes.clear() } }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    private fun onBlock(pos: BlockPos, state: BlockState) {
        seen.add(System.currentTimeMillis() to net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.block).path)
        if (seen.size > 400) seen.poll()
        val p = pos.immutable()
        if (state.block == Blocks.LAPIS_BLOCK) added.add(p)
        else if (state.block !in FADE) removed.add(p)
    }

    private fun tick() {
        if (!S.riftSnake || !Rift.inRift()) { snakes.clear(); added.clear(); removed.clear(); return }
        while (true) { addSnakeBlock(added.poll() ?: break) }
        while (true) {
            val loc = removed.poll() ?: break
            snakes.removeIf { s -> loc in s.blocks && run { s.blocks.remove(loc); s.blocks.isEmpty() } }
        }
        val level = Minecraft.getInstance().level ?: return
        val now = System.currentTimeMillis()
        snakes.removeIf { s ->
            if (s.invalidShape()) return@removeIf true
            if (level.getBlockState(s.head).block != Blocks.LAPIS_BLOCK) {
                if (s.invalidHeadSince == 0L) s.invalidHeadSince = now
                now - s.invalidHeadSince > 1000L
            } else { s.invalidHeadSince = 0L; false }
        }
    }

    private fun addSnakeBlock(p: BlockPos) {
        if (snakes.any { p in it.blocks }) return
        val near = snakes.filter { it.head.distSqr(p) < 3.01 }.minByOrNull { it.head.distSqr(p) }
        if (near == null) snakes.add(Snake(mutableListOf(p))) else near.blocks.add(0, p)
    }

    private fun render() {
        if (!S.riftSnake || snakes.isEmpty() || !Rift.inRift()) return
        for (s in snakes.toList()) {
            val head = s.blocks.firstOrNull() ?: continue
            val tail = s.blocks.lastOrNull() ?: continue
            if (s.blocks.size > 1) box(tail, S.riftSnakeColor)
            box(head, S.riftSnakeHeadColor)
        }
    }

    private fun box(pos: BlockPos, c: Int) = RenderUtils.gizmoBox(AABB(pos), (c and 0xFFFFFF) or 0x30000000, c, false)
}
