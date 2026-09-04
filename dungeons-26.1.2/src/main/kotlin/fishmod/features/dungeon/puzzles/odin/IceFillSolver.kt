package fishmod.features.dungeon.puzzles.odin

import com.google.gson.Gson
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/** Ice Fill solver — with a tick retry until every floor resolves. */
object IceFillSolver {

    private class Pt { @JvmField var x = 0; @JvmField var y = 0; @JvmField var z = 0; fun pos() = BlockPos(x, y, z) }
    private class IceFillData {
        @JvmField var identifier: List<List<List<Pt>>> = emptyList()
        @JvmField var easy: List<List<List<Pt>>> = emptyList()
        @JvmField var hard: List<List<List<Pt>>> = emptyList()
    }

    private val floors: IceFillData = try {
        IceFillSolver::class.java.getResourceAsStream("/iceFillFloors.json")!!.use { s ->
            Gson().fromJson(InputStreamReader(s, StandardCharsets.UTF_8), IceFillData::class.java)
        }
    } catch (e: Exception) {
        Debug.LOGGER.error("Ice Fill floors failed to load", e); IceFillData()
    }

    private val currentPatterns = ArrayList<Vec3>()
    private var scanned = false
    private var attempts = 0

    fun onRoomEnter(room: ORoom?, optimize: Boolean) {
        if (room?.data?.name != "Ice Fill") { reset(); return }
        reset()
        scan(room, optimize)
    }

    fun onTick(optimize: Boolean) {
        if (scanned || OdinScan.currentRoomName != "Ice Fill") return
        if (attempts++ > 300) { scanned = true; return }
        OdinScan.currentRoom?.let { scan(it, optimize) }
    }

    private fun scan(room: ORoom, optimize: Boolean) {
        if (currentPatterns.isNotEmpty()) { scanned = true; return }
        val patterns = if (optimize) floors.hard else floors.easy
        val found = BooleanArray(3)
        repeat(3) { index ->
            val ids = floors.identifier.getOrNull(index) ?: run { found[index] = true; return@repeat }
            for (pIdx in ids.indices) {
                if (isRealAir(room, ids[pIdx][0]) && !isRealAir(room, ids[pIdx][1])) {
                    patterns.getOrNull(index)?.getOrNull(pIdx)?.forEach {
                        val w = room.getRealCoords(it.pos())
                        currentPatterns.add(Vec3(w.x + 0.5, w.y + 0.1, w.z + 0.5))
                    }
                    found[index] = true
                    return@repeat
                }
            }
        }
        if (found.all { it }) scanned = true else currentPatterns.clear()
    }

    private fun isRealAir(room: ORoom, p: Pt): Boolean =
        Minecraft.getInstance().level?.getBlockState(room.getRealCoords(p.pos()))?.isAir == true

    fun onRenderWorld() {
        if (currentPatterns.size < 2 || OdinScan.currentRoomName != "Ice Fill") return
        ORender.line(currentPatterns, FishSettings.iceFillColor)
    }

    fun reset() {
        currentPatterns.clear()
        scanned = false
        attempts = 0
    }
}
