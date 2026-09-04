package fishmod.features.dungeon.puzzles.odin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/** Boulder solver — with a tick retry, since the grid blocks can still be loading. */
object BoulderSolver {

    private data class BoxPosition(val render: AABB, val click: BlockPos)
    private var currentPositions = mutableListOf<BoxPosition>()
    private var solved = false
    private var attempts = 0

    private val solutions: Map<String, List<List<Int>>> = try {
        BoulderSolver::class.java.getResourceAsStream("/boulderSolutions.json")!!.use { s ->
            Gson().fromJson(InputStreamReader(s, StandardCharsets.UTF_8),
                object : TypeToken<Map<String, List<List<Int>>>>() {}.type)
        }
    } catch (e: Exception) {
        Debug.LOGGER.error("Boulder solutions failed to load", e); emptyMap()
    }

    fun onRoomEnter(room: ORoom?) {
        if (room?.data?.name != "Boulder") return reset()
        reset()
        scan(room)
    }

    fun onTick() {
        if (OdinScan.currentRoomName != "Boulder" || solved || currentPositions.isNotEmpty()) return
        if (attempts++ > 200) return
        OdinScan.currentRoom?.let { scan(it) }
    }

    private fun scan(room: ORoom) {
        val level = Minecraft.getInstance().level ?: return
        var str = ""
        for (z in 24 downTo 9 step 3) {
            for (x in 24 downTo 6 step 3) {
                str += if (level.getBlockState(room.getRealCoords(BlockPos(x, 66, z))).isAir) "0" else "1"
            }
        }
        val sol = solutions[str] ?: return
        currentPositions = sol.map {
            BoxPosition(AABB(room.getRealCoords(BlockPos(it[0], 65, it[1]))), room.getRealCoords(BlockPos(it[2], 65, it[3])))
        }.toMutableList()
    }

    fun onRenderWorld() {
        if (OdinScan.currentRoomName != "Boulder" || currentPositions.isEmpty()) return
        val style = ORender.style()
        val color = FishSettings.boulderColor
        if (FishSettings.boulderShowAll) currentPositions.forEach { ORender.styledBox(it.render, color, style) }
        else currentPositions.firstOrNull()?.let { ORender.styledBox(it.render, color, style) }
    }

    fun playerInteract(clicked: BlockPos) {
        currentPositions.remove(currentPositions.firstOrNull { it.click == clicked })
        if (currentPositions.isEmpty()) solved = true
    }

    fun reset() {
        currentPositions = mutableListOf()
        solved = false
        attempts = 0
    }
}
