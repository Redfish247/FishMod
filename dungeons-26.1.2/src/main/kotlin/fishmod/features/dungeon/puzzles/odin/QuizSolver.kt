package fishmod.features.dungeon.puzzles.odin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fishmod.features.dungeon.puzzles.PuzzleSolvers
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object QuizSolver {

    private val answers: Map<String, List<String>> = try {
        QuizSolver::class.java.getResourceAsStream("/quizAnswers.json")!!.use { s ->
            Gson().fromJson(InputStreamReader(s, StandardCharsets.UTF_8),
                object : TypeToken<Map<String, List<String>>>() {}.type)
        }
    } catch (e: Exception) {
        Debug.LOGGER.error("Quiz answers failed to load", e); emptyMap()
    }

    private val OPTION_LOCALS = arrayOf(BlockPos(20, 70, 6), BlockPos(15, 70, 9), BlockPos(10, 70, 6))

    @Volatile private var correctOption: Int = -1
    private var triviaAnswers: List<String>? = null
    private var quizRoom: ORoom? = null

    fun onMessage(msg: String) {
        if (msg.startsWith("[STATUE] Oruo the Omniscient: ") && msg.endsWith("correctly!")) {
            if (msg.contains("answered the final question")) {
                PuzzleSolvers.onPuzzleComplete("Quiz")
                reset()
                return
            }
            if (msg.contains("answered Question #")) correctOption = -1
        }

        val t = msg.trim()
        if ((t.startsWith("ⓐ") || t.startsWith("ⓑ") || t.startsWith("ⓒ")) && triviaAnswers?.any { msg.endsWith(it) } == true) {
            correctOption = when (t[0]) { 'ⓐ' -> 0; 'ⓑ' -> 1; 'ⓒ' -> 2; else -> -1 }
            Debug.LOGGER.info("[Quiz] answer option={} line={}", correctOption, t)
        }

        triviaAnswers = when {
            t == "What SkyBlock year is it?" ->
                listOf("Year ${(((System.currentTimeMillis() / 1000) - 1560276000) / 446400).toInt() + 1}")
            else -> answers.entries.find { msg.contains(it.key) }?.value ?: return
        }
    }

    fun onRenderWorld() {
        val opt = correctOption
        if (opt < 0) return
        val room = quizRoom ?: OdinScan.findRoom("Quiz")?.also { quizRoom = it } ?: return
        val pos = room.getRealCoords(OPTION_LOCALS[opt]).offset(0, -1, 0)
        val color = FishSettings.quizColor
        ORender.filledBox(AABB(pos), color)
        ORender.beaconBeam(Vec3(pos.x + 0.0, pos.y + 0.0, pos.z + 0.0), color)
    }

    fun reset() {
        correctOption = -1
        triviaAnswers = null
        quizRoom = null
    }
}
