package fishmod.features.dungeon.puzzles.odin

import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs
import kotlin.math.atan2

object TPMazeSolver {

    private var tpPads = listOf<BlockPos>()
    private var correctPortals = listOf<BlockPos>()
    private val visited = CopyOnWriteArraySet<BlockPos>()
    private var best: BlockPos? = null

    fun onRoomEnter(room: ORoom?) {
        if (room?.data?.name == "Teleport Maze")
            tpPads = endPortalFrameLocations.map { room.getRealCoords(it) }
    }

    fun tpPacket(packet: ClientboundPlayerPositionPacket) {
        val change = packet.change
        val pos = change.position
        if (OdinScan.currentRoomName != "Teleport Maze" || pos.x % 0.5 != 0.0 || pos.y != 69.5 ||
            pos.z % 0.5 != 0.0 || tpPads.isEmpty()) return

        val mc = Minecraft.getInstance()
        val posAABB = AABB.unitCubeFromLowerCorner(pos).inflate(1.0, 0.0, 1.0)
        visited.addAll(tpPads.filter {
            posAABB.intersects(AABB(it)) ||
                mc.player?.boundingBox?.inflate(1.0, 0.0, 1.0)?.intersects(AABB(it)) == true
        })
        getCorrectPortals(pos, change.yRot, change.xRot)

        val currentPad = tpPads.firstOrNull { posAABB.intersects(AABB(it)) } ?: return
        val index = tpPads.indexOf(currentPad)
        if (index in 28..29) { best = null; return }

        val groupStart = index / 4 * 4
        if (groupStart + 4 > tpPads.size) return
        val candidates = tpPads.slice(groupStart until groupStart + 4).filter { it != currentPad && it !in visited }

        best = candidates.firstOrNull { it in correctPortals }
            ?: candidates.minByOrNull {
                val yaw = (atan2(it.center.z - pos.z, it.center.x - pos.x) * 180.0 / Math.PI).toFloat() - 90f
                abs(Mth.wrapDegrees(yaw) - Mth.wrapDegrees(change.yRot))
            }
    }

    private fun getCorrectPortals(pos: Vec3, yaw: Float, pitch: Float) {
        if (correctPortals.isEmpty()) correctPortals = tpPads
        val player = Minecraft.getInstance().player
        correctPortals = correctPortals.filter {
            it !in visited &&
                OVecUtil.isXZInterceptable(
                    AABB(it.x.toDouble(), it.y.toDouble(), it.z.toDouble(), it.x + 1.0, it.y + 4.0, it.z + 1.0)
                        .inflate(0.75, 0.0, 0.75),
                    32.0, pos, yaw, pitch,
                ) && player?.boundingBox?.let { pb -> !AABB(it).inflate(0.5, 0.0, 0.5).intersects(pb) } != false
        }
    }

    fun onRenderWorld() {
        if (OdinScan.currentRoomName != "Teleport Maze") return
        tpPads.forEach {
            val aabb = AABB(it)
            when {
                it in correctPortals -> ORender.filledBox(aabb,
                    if (correctPortals.size == 1) FishSettings.tpMazeNextColor else 0x80FFAA00.toInt())
                it in visited -> ORender.filledBox(aabb, FishSettings.tpMazeVisitedColor)
                else -> ORender.filledBox(aabb, 0x80FFFFFF.toInt())
            }
        }
        val target = best ?: return
        ORender.tracer(Vec3(target.x + 0.5, target.y + 0.8, target.z + 0.5), 0xFF55FFFF.toInt())
    }

    fun reset() {
        correctPortals = listOf()
        visited.clear()
        best = null
    }

    private val endPortalFrameLocations = listOf(
        BlockPos(4, 69, 12), BlockPos(4, 69, 6), BlockPos(10, 69, 12), BlockPos(10, 69, 6),
        BlockPos(4, 69, 20), BlockPos(4, 69, 14), BlockPos(10, 69, 20), BlockPos(10, 69, 14),
        BlockPos(4, 69, 28), BlockPos(4, 69, 22), BlockPos(10, 69, 28), BlockPos(10, 69, 22),
        BlockPos(12, 69, 28), BlockPos(12, 69, 22), BlockPos(18, 69, 28), BlockPos(18, 69, 22),
        BlockPos(20, 69, 28), BlockPos(20, 69, 22), BlockPos(26, 69, 28), BlockPos(26, 69, 22),
        BlockPos(26, 69, 20), BlockPos(26, 69, 14), BlockPos(20, 69, 20), BlockPos(20, 69, 14),
        BlockPos(26, 69, 12), BlockPos(26, 69, 6), BlockPos(20, 69, 12), BlockPos(20, 69, 6),
        BlockPos(15, 69, 14), BlockPos(15, 69, 12),
    )
}
