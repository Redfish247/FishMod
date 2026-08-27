package fishmod.features.dungeon.puzzles

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.dungeon.map.Room
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.phys.AABB
import kotlin.math.floor

/**
 * Three Weirdos solver (logic ported from Odin's WeirdosSolver). Each of the 3 NPCs says one line;
 * a line matching [SOLUTIONS] means that NPC's chest is the reward, a line matching [WRONG] rules
 * that NPC's chest out. The chest is found by scanning next to the speaking NPC's armor stand
 * (rotation-independent — sturdier than Odin's hardcoded room offset).
 */
class ThreeWeirdosSolver : PuzzleSolver {

    override val roomName = "Three Weirdos"

    private var correct: BlockPos? = null
    private val wrong = HashSet<BlockPos>()
    private val LINE = Regex("^\\[NPC] ([^:]+): (.+)$")

    override fun onEnter(room: Room) = reset()
    override fun onExit() = reset()

    override fun reset() {
        correct = null
        wrong.clear()
    }

    override fun onChat(message: String): Boolean {
        if (!FishSettings.weirdosSolver) return false
        val m = LINE.matchEntire(message.trim()) ?: return false
        val npc = m.groupValues[1].trim()
        val said = m.groupValues[2].trim()
        val isSolution = SOLUTIONS.any { it.matches(said) }
        val isWrong = WRONG.any { it.matches(said) }
        if (!isSolution && !isWrong) return false

        val chest = chestNearNpc(npc) ?: return false
        if (isSolution) correct = chest else wrong.add(chest)
        return false
    }

    private fun chestNearNpc(npc: String): BlockPos? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val stand = level.entitiesForRendering().firstOrNull {
            it is ArmorStand && it.hasCustomName() &&
                it.customName!!.string.replace(Regex("§."), "").trim() == npc
        } ?: return null

        val base = BlockPos(floor(stand.x).toInt(), 69, floor(stand.z).toInt())
        var best: BlockPos? = null
        var bestD = Int.MAX_VALUE
        for (dx in -2..2) for (dy in -1..2) for (dz in -2..2) {
            val p = base.offset(dx, dy, dz)
            if (level.getBlockState(p).block is ChestBlock) {
                val d = dx * dx + dy * dy + dz * dz
                if (d < bestD) { bestD = d; best = p.immutable() }
            }
        }
        return best
    }

    override fun renderWorld(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.weirdosSolver) return
        val style = FishSettings.puzzleSolverStyle
        correct?.let { drawBox(matrices, vc, it, FishSettings.weirdosCorrectColor, style) }
        for (w in wrong) drawBox(matrices, vc, w, FishSettings.weirdosWrongColor, style)
    }

    private fun drawBox(matrices: PoseStack, vc: VertexConsumer, pos: BlockPos, argb: Int, style: String) {
        val box = AABB(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0).inflate(0.002)
        if (style != "Outline") RenderUtils.renderFilled(matrices, vc, box, RenderUtils.toFloats(argb))
        if (style != "Filled") RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(0xFF000000.toInt() or (argb and 0xFFFFFF)))
    }

    companion object {
        private val SOLUTIONS = listOf(
            Regex("The reward is not in my chest!"),
            Regex("At least one of them is lying, and the reward is not in .+'s chest\\.?"),
            Regex("My chest doesn't have the reward\\. We are all telling the truth\\.?"),
            Regex("My chest has the reward and I'm telling the truth!"),
            Regex("The reward isn't in any of our chests\\.?"),
            Regex("Both of them are telling the truth\\. Also, .+ has the reward in their chest\\.?"),
        )
        private val WRONG = listOf(
            Regex("One of us is telling the truth!"),
            Regex("They are both telling the truth\\. The reward isn't in .+'s chest\\."),
            Regex("We are all telling the truth!"),
            Regex(".+ is telling the truth and the reward is in his chest\\."),
            Regex("My chest doesn't have the reward\\. At least one of the others is telling the truth!"),
            Regex("One of the others is lying\\."),
            Regex("They are both telling the truth, the reward is in .+'s chest\\."),
            Regex("They are both lying, the reward is in my chest!"),
            Regex("The reward is in my chest\\."),
            Regex("The reward is not in my chest\\. They are both lying\\."),
            Regex(".+ is telling the truth\\."),
            Regex("My chest has the reward\\."),
        )
    }
}
