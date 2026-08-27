package fishmod.features.dungeon.puzzles

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.dungeon.map.Room
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import fishmod.utils.rendering.RenderUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Oruo trivia ("Quiz") solver — ported from Odin's QuizSolver. Question -> valid-answer list comes
 * from the bundled `/quizAnswers.json` (lifted from odtheking/OdinLegacy). Oruo announces each
 * option as `ⓐ/ⓑ/ⓒ <text>`; when the text matches a valid answer for the current question, that
 * podium is flagged and boxed. Podium positions via [Room.offset] (== Odin `getRealCoords`).
 */
class QuizSolver : PuzzleSolver {

    override val roomName = "Quiz"

    private class Option { var pos: BlockPos? = null; var correct = false }
    private val options = Array(3) { Option() }
    private var answers: List<String>? = null

    override fun onEnter(room: Room) {
        reset()
        options[0].pos = room.offset(BlockPos(20, 70, 6))
        options[1].pos = room.offset(BlockPos(15, 70, 9))
        options[2].pos = room.offset(BlockPos(10, 70, 6))
    }

    override fun onExit() = reset()

    override fun reset() {
        options.forEach { it.correct = false }
        answers = null
    }

    override fun onChat(message: String): Boolean {
        if (!FishSettings.quizSolver) return false
        val msg = message.trim()

        if (msg.startsWith("[STATUE] Oruo the Omniscient: ") && msg.endsWith("correctly!")) {
            if (msg.contains("answered Question #")) options.forEach { it.correct = false }
        }

        val letter = msg.firstOrNull()
        if (letter == 'ⓐ' || letter == 'ⓑ' || letter == 'ⓒ') {
            val ans = answers
            if (ans != null && ans.any { msg.endsWith(it) }) {
                options[letter - 'ⓐ'].correct = true
            }
            return false
        }

        // Otherwise treat the line as a possible question.
        answers = when {
            msg == "What SkyBlock year is it?" ->
                listOf("Year ${(((System.currentTimeMillis() / 1000) - 1560276000) / 446400).toInt() + 1}")
            else -> QUIZ[QUIZ.keys.firstOrNull { msg.contains(it) } ?: return false]
        }
        return false
    }

    override fun renderWorld(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.quizSolver || answers == null) return
        val argb = FishSettings.quizColor
        for (o in options) {
            if (!o.correct) continue
            val p = o.pos ?: continue
            // box the podium block + a thin tall marker upward (pseudo-beacon-beam).
            box(matrices, vc, AABB(p.x.toDouble(), p.y - 1.0, p.z.toDouble(), p.x + 1.0, p.y.toDouble(), p.z + 1.0), argb)
            box(matrices, vc, AABB(p.x + 0.35, p.y.toDouble(), p.z + 0.35, p.x + 0.65, p.y + 8.0, p.z + 0.65), argb and 0x40FFFFFF)
        }
    }

    private fun box(matrices: PoseStack, vc: VertexConsumer, aabb: AABB, argb: Int) {
        RenderUtils.renderFilled(matrices, vc, aabb, RenderUtils.toFloats(argb))
        RenderUtils.renderOutline(matrices, vc, aabb, RenderUtils.toFloats(0xFF000000.toInt() or (argb and 0xFFFFFF)))
    }

    companion object {
        private val QUIZ: Map<String, List<String>> = try {
            QuizSolver::class.java.getResourceAsStream("/quizAnswers.json")!!.use { s ->
                Gson().fromJson(
                    InputStreamReader(s, StandardCharsets.UTF_8),
                    object : TypeToken<Map<String, List<String>>>() {}.type,
                )
            }
        } catch (e: Exception) {
            Debug.LOGGER.error("Quiz answers failed to load", e)
            emptyMap()
        }
    }
}
