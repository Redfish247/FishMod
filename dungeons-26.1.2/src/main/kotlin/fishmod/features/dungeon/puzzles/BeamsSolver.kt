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
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Creeper Beams solver — ported from Odin's BeamsSolver. Each `[x1,y1,z1,x2,y2,z2]` in the bundled
 * `/creeperBeamsSolutions.json` is a lantern pair to connect; a pair is boxed (matching colour) +
 * an optional tracer while both of its sea lanterns are still present. Instead of Odin's
 * BlockChangeEvent this polls the lantern blocks each tick — a pair whose lantern is gone is dropped.
 */
class BeamsSolver : PuzzleSolver {

    override val roomName = "Creeper Beams"

    private var room: Room? = null
    private var scanned = false
    private var tickAcc = 0
    private val pairs = ArrayList<Triple<BlockPos, BlockPos, Int>>()

    private val COLORS = intArrayOf(
        0xFFFFAA00.toInt(), 0xFF55FF55.toInt(), 0xFFFF55FF.toInt(), 0xFF55FFFF.toInt(),
        0xFFFFFF55.toInt(), 0xFFAA0000.toInt(), 0xFFFFFFFF.toInt(), 0xFFAA00AA.toInt(),
    )

    override fun onEnter(room: Room) { this.room = room; reset() }
    override fun onExit() = reset()
    override fun reset() { scanned = false; pairs.clear() }

    override fun onTick(mc: Minecraft) {
        if (!FishSettings.beamsSolver) return
        val r = room ?: return
        if (!scanned) { scan(mc, r); return }
        if (++tickAcc < 10) return
        tickAcc = 0
        pairs.removeAll { (a, b, _) ->
            mc.level?.getBlockState(a)?.block != Blocks.SEA_LANTERN ||
                mc.level?.getBlockState(b)?.block != Blocks.SEA_LANTERN
        }
    }

    private fun scan(mc: Minecraft, r: Room) {
        val level = mc.level ?: return
        scanned = true
        pairs.clear()
        for (list in SOLUTIONS) {
            val a = r.offset(BlockPos(list[0], list[1], list[2])) ?: continue
            val b = r.offset(BlockPos(list[3], list[4], list[5])) ?: continue
            if (level.getBlockState(a).block != Blocks.SEA_LANTERN) continue
            if (level.getBlockState(b).block != Blocks.SEA_LANTERN) continue
            pairs.add(Triple(a, b, COLORS[pairs.size % COLORS.size]))
        }
    }

    override fun renderWorld(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.beamsSolver || pairs.isEmpty()) return
        val style = FishSettings.puzzleSolverStyle
        for ((a, b, argb) in pairs) {
            drawBox(matrices, vc, a, argb, style)
            drawBox(matrices, vc, b, argb, style)
            if (FishSettings.beamsTracer) {
                RenderUtils.renderLine(matrices, vc,
                    Vec3(a.x + 0.5, a.y + 0.5, a.z + 0.5), Vec3(b.x + 0.5, b.y + 0.5, b.z + 0.5),
                    RenderUtils.toFloats(argb))
            }
        }
    }

    private fun drawBox(matrices: PoseStack, vc: VertexConsumer, p: BlockPos, argb: Int, style: String) {
        val box = AABB(p.x.toDouble(), p.y.toDouble(), p.z.toDouble(), p.x + 1.0, p.y + 1.0, p.z + 1.0).inflate(0.002)
        val fill = (0x66 shl 24) or (argb and 0xFFFFFF)
        if (style != "Outline") RenderUtils.renderFilled(matrices, vc, box, RenderUtils.toFloats(fill))
        if (style != "Filled") RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(argb))
    }

    companion object {
        private val SOLUTIONS: List<List<Int>> = try {
            BeamsSolver::class.java.getResourceAsStream("/creeperBeamsSolutions.json")!!.use { s ->
                Gson().fromJson(
                    InputStreamReader(s, StandardCharsets.UTF_8),
                    object : TypeToken<List<List<Int>>>() {}.type,
                )
            }
        } catch (e: Exception) {
            Debug.LOGGER.error("Creeper beams solutions failed to load", e)
            emptyList()
        }
    }
}
