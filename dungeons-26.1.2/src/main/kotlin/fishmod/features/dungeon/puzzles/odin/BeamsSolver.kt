package fishmod.features.dungeon.puzzles.odin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Creeper Beams solver. Re-scans every 10 ticks from [onTick] (pairs whose lantern is gone drop out).
 */
object BeamsSolver {

    private val lanternPairs: List<List<Int>> = try {
        BeamsSolver::class.java.getResourceAsStream("/creeperBeamsSolutions.json")!!.use { s ->
            Gson().fromJson(InputStreamReader(s, StandardCharsets.UTF_8),
                object : TypeToken<List<List<Int>>>() {}.type)
        }
    } catch (e: Exception) {
        Debug.LOGGER.error("Creeper beams solutions failed to load", e); emptyList()
    }

    // key lantern -> (partner lantern, colour)
    private val current = ConcurrentHashMap<BlockPos, Pair<BlockPos, Int>>()
    private var tickAcc = 0

    private val colors = intArrayOf(
        0xFFFFAA00.toInt(), 0xFF55FF55.toInt(), 0xFFFF55FF.toInt(), 0xFF00AAAA.toInt(),
        0xFFFFFF55.toInt(), 0xFFAA0000.toInt(), 0xFFFFFFFF.toInt(), 0xFFAA00AA.toInt(),
    )

    fun onRoomEnter(room: ORoom?) {
        if (room?.data?.name != "Creeper Beams") return reset()
        recalculate(room)
    }

    fun onTick() {
        if (OdinScan.currentRoomName != "Creeper Beams") return
        if (++tickAcc < 10) return
        tickAcc = 0
        OdinScan.currentRoom?.let { recalculate(it) }
    }

    private fun recalculate(room: ORoom) {
        val level = Minecraft.getInstance().level ?: return
        current.clear()
        lanternPairs.forEachIndexed { index, list ->
            val pos = room.getRealCoords(BlockPos(list[0], list[1], list[2]))
                .takeIf { level.getBlockState(it).block == Blocks.SEA_LANTERN } ?: return@forEachIndexed
            val pos2 = room.getRealCoords(BlockPos(list[3], list[4], list[5]))
                .takeIf { level.getBlockState(it).block == Blocks.SEA_LANTERN } ?: return@forEachIndexed
            current[pos] = pos2 to colors[index % colors.size]
        }
    }

    fun onRenderWorld() {
        if (OdinScan.currentRoomName != "Creeper Beams" || current.isEmpty()) return
        val style = ORender.style()
        for ((a, v) in current) {
            val (b, color) = v
            ORender.styledBox(AABB(a), color, style)
            ORender.styledBox(AABB(b), color, style)
            if (FishSettings.beamsTracer) ORender.line(a.center, b.center, color)
        }
    }

    fun reset() {
        current.clear()
        tickAcc = 0
    }
}
