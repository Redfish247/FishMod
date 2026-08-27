package fishmod.features.dungeon.puzzles

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.dungeon.map.Room
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.phys.AABB
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Boulder (Sokoban) solver — logic from Odin's BoulderSolver + its bundled `/boulderSolutions.json`
 * (42-char air/solid pattern -> ordered list of `[renderX, renderZ, clickX, clickZ]` centre-relative
 * offsets). Coords go through [Room.realCoord] (the NoammAddons/ScanUtils centre-relative transform).
 *
 * NOTE: the room-centre/rotation calibration is unverified headless — box positions may need a small
 * nudge once tested in-game.
 */
class BoulderSolver : PuzzleSolver {

    override val roomName = "Boulder"

    private var room: Room? = null
    private val remaining = ArrayList<Pair<BlockPos, BlockPos>>() // render, click
    private var solved = false

    override fun onEnter(room: Room) { this.room = room; reset() }
    override fun onExit() = reset()
    override fun reset() { remaining.clear(); solved = false }

    override fun onTick(mc: Minecraft) {
        if (!FishSettings.boulderSolver || solved) return
        if (remaining.isEmpty()) solve(mc)
    }

    override fun onBlockClick(pos: BlockPos) {
        if (remaining.isEmpty()) return
        val mc = Minecraft.getInstance()
        val block = mc.level?.getBlockState(pos)?.block
        when (block) {
            is ButtonBlock, is LeverBlock -> remaining.removeAll { it.second == pos }
            is ChestBlock -> { solved = true; remaining.clear() }
        }
    }

    private fun solve(mc: Minecraft) {
        val r = room ?: return
        val level = mc.level ?: return
        val sb = StringBuilder(42)
        for (z in -3..2) for (x in -3..3) {
            val p = r.realCoord(BlockPos(x * 3, 65, z * 3)) ?: return
            sb.append(if (level.getBlockState(p).isAir) '0' else '1')
        }
        val sols = SOLUTIONS[sb.toString()] ?: return
        remaining.clear()
        for (s in sols) {
            val render = r.realCoord(BlockPos(s[0], 64, s[1])) ?: continue
            val click = r.realCoord(BlockPos(s[2], 65, s[3])) ?: continue
            remaining.add(render to click)
        }
    }

    override fun renderWorld(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.boulderSolver || remaining.isEmpty()) return
        val list = if (FishSettings.boulderShowAll) remaining else remaining.take(1)
        val argb = FishSettings.boulderColor
        for ((render, _) in list) {
            val box = AABB(render.x.toDouble(), render.y.toDouble(), render.z.toDouble(),
                render.x + 1.0, render.y + 1.0, render.z + 1.0)
            RenderUtils.renderFilled(matrices, vc, box, RenderUtils.toFloats((0x66 shl 24) or (argb and 0xFFFFFF)))
            RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(0xFF000000.toInt() or (argb and 0xFFFFFF)))
        }
    }

    companion object {
        private val SOLUTIONS: Map<String, List<List<Int>>> = try {
            BoulderSolver::class.java.getResourceAsStream("/boulderSolutions.json")!!.use { s ->
                Gson().fromJson(InputStreamReader(s, StandardCharsets.UTF_8),
                    object : TypeToken<Map<String, List<List<Int>>>>() {}.type)
            }
        } catch (e: Exception) {
            Debug.LOGGER.error("Boulder solutions failed to load", e); emptyMap()
        }
    }
}
