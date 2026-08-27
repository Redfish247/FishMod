package fishmod.features.dungeon.puzzles

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.dungeon.map.Room
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Teleport Maze solver — ported from Odin's TPMazeSolver. The 30 end-portal-frame slots (Odin's
 * `endPortalFrameLocations`) become world tp-pad positions via [Room.offset]. Pads you've stood on
 * are marked visited (red); un-visited pads roughly in front of you are the candidates to walk to
 * next (green) — a simpler facing-dot heuristic in place of Odin's `isXZInterceptable` look-cone.
 */
class TPMazeSolver : PuzzleSolver {

    override val roomName = "Teleport Maze"

    private var room: Room? = null
    private var pads: List<BlockPos> = emptyList()
    private val visited = HashSet<BlockPos>()

    override fun onEnter(room: Room) {
        this.room = room
        visited.clear()
        pads = FRAMES.mapNotNull { room.offset(it) }
    }

    override fun onExit() { pads = emptyList(); visited.clear(); room = null }
    override fun reset() { visited.clear() }

    override fun onTick(mc: Minecraft) {
        if (!FishSettings.tpMazeSolver || pads.isEmpty()) return
        val p = mc.player ?: return
        val px = p.x; val pz = p.z
        for (pad in pads) {
            val dx = px - (pad.x + 0.5)
            val dz = pz - (pad.z + 0.5)
            if (dx * dx + dz * dz < 0.5) visited.add(pad)
        }
    }

    /** Un-visited pads within ~35° of where the player is looking, nearest first. */
    private fun candidates(mc: Minecraft): List<BlockPos> {
        val p = mc.player ?: return emptyList()
        val yaw = Math.toRadians(p.yRot.toDouble())
        val fx = -sin(yaw); val fz = cos(yaw)
        return pads.filter { it !in visited }.mapNotNull { pad ->
            val dx = (pad.x + 0.5) - p.x
            val dz = (pad.z + 0.5) - p.z
            val len = sqrt(dx * dx + dz * dz)
            if (len < 0.5) return@mapNotNull null
            val dot = (dx / len) * fx + (dz / len) * fz
            if (dot > 0.82) pad to len else null
        }.sortedBy { it.second }.map { it.first }
    }

    override fun renderWorld(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.tpMazeSolver || pads.isEmpty()) return
        val mc = Minecraft.getInstance()
        val next = candidates(mc).toHashSet()
        for (pad in pads) {
            val argb = when {
                pad in next -> FishSettings.tpMazeNextColor
                pad in visited -> FishSettings.tpMazeVisitedColor
                else -> 0x40FFFFFF
            }
            val box = AABB(pad.x.toDouble(), pad.y.toDouble(), pad.z.toDouble(),
                pad.x + 1.0, pad.y + 1.0, pad.z + 1.0).inflate(0.002)
            RenderUtils.renderFilled(matrices, vc, box, RenderUtils.toFloats(argb))
            RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(0xFF000000.toInt() or (argb and 0xFFFFFF)))
        }
    }

    companion object {
        private val FRAMES: List<BlockPos> = listOf(
            BlockPos(4, 69, 28), BlockPos(4, 69, 22), BlockPos(4, 69, 20),
            BlockPos(4, 69, 14), BlockPos(4, 69, 12), BlockPos(4, 69, 6),
            BlockPos(10, 69, 28), BlockPos(10, 69, 22), BlockPos(10, 69, 20),
            BlockPos(10, 69, 14), BlockPos(10, 69, 12), BlockPos(10, 69, 6),
            BlockPos(12, 69, 28), BlockPos(12, 69, 22), BlockPos(15, 69, 14),
            BlockPos(15, 69, 12), BlockPos(18, 69, 28), BlockPos(18, 69, 22),
            BlockPos(20, 69, 28), BlockPos(20, 69, 22), BlockPos(20, 69, 20),
            BlockPos(20, 69, 14), BlockPos(20, 69, 12), BlockPos(20, 69, 6),
            BlockPos(26, 69, 28), BlockPos(26, 69, 22), BlockPos(26, 69, 20),
            BlockPos(26, 69, 14), BlockPos(26, 69, 12), BlockPos(26, 69, 6),
        )
    }
}
