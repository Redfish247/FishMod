package fishmod.features.rift

import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import java.util.concurrent.ConcurrentHashMap
import fishmod.features.rift.RiftSettings as S

// Living Cave snakes: outline lapis snake blocks, head = newest block
object RiftSnake {

    private const val HEAD_MS = 1200L
    // The snake fades through these as it ages; stone means it left
    private val SNAKE = setOf(
        Blocks.LAPIS_BLOCK, Blocks.BLUE_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS,
        Blocks.CYAN_TERRACOTTA, Blocks.BLUE_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA,
    )
    private val blocks = ConcurrentHashMap<BlockPos, Long>()

    fun init() {
        Events.ON_PACKET.register { packet ->
            if (S.riftSnake && Rift.inArea("Living Cave", "Living Stillness")) when (packet) {
                is ClientboundBlockUpdatePacket -> onBlock(packet.pos, packet.blockState)
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { p, st -> onBlock(p, st) }
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { blocks.clear(); false }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    // Recent block updates by type, for /fm rift debug
    private val seen = java.util.concurrent.ConcurrentLinkedQueue<Pair<Long, String>>()
    fun debug(): String {
        val now = System.currentTimeMillis(); seen.removeIf { now - it.first > 15_000L }
        val g = seen.groupingBy { it.second }.eachCount().entries.sortedByDescending { it.value }.take(8)
        return "snake: tracked=${blocks.size} updates(15s)=" + g.joinToString(", ") { "${it.key}x${it.value}" }.ifEmpty { "none" }
    }

    private fun onBlock(pos: BlockPos, state: BlockState) {
        seen.add(System.currentTimeMillis() to net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.block).path)
        if (seen.size > 400) seen.poll()
        if (state.block in SNAKE) blocks.putIfAbsent(pos.immutable(), System.currentTimeMillis())
        else blocks.remove(pos)
    }

    private fun render() {
        if (!S.riftSnake || blocks.isEmpty() || !Rift.inRift()) return
        val now = System.currentTimeMillis()
        val tail = if (blocks.size >= 2) blocks.entries.minByOrNull { it.value }?.key else null
        for ((pos, at) in blocks) {
            val head = now - at < HEAD_MS
            if (!head && pos != tail) continue
            val c = if (pos == tail) S.riftSnakeColor else S.riftSnakeHeadColor
            RenderUtils.gizmoBox(AABB(pos), (c and 0xFFFFFF) or 0x30000000, c, false)
        }
    }
}
