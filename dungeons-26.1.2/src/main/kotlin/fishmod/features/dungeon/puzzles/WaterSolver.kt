package fishmod.features.dungeon.puzzles

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.features.dungeon.map.Room
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import fishmod.utils.rendering.RenderUtils
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Water Board solver — ported from Odin's WaterSolver. Lookup-table (not a live sim): the room's
 * pattern id (0-3) + which 3 of 5 wool slots are extended index into `/waterSolutions.json`, giving
 * per-lever click-time lists. Timers count down above each lever once the water lever is pulled.
 * Lever clicks are tracked via [net.fabricmc.fabric.api.event.player.UseBlockCallback] (registered
 * in [PuzzleSolvers]).
 */
class WaterSolver : PuzzleSolver {

    override val roomName = "Water Board"

    private var room: Room? = null
    private var patternId = -1
    private var openedTick = -1
    private var tick = 0
    private var tickAcc = 0
    private val solution = LinkedHashMap<Lever, DoubleArray>()

    override fun onEnter(room: Room) { this.room = room; reset() }
    override fun onExit() = reset()

    override fun reset() {
        Lever.entries.forEach { it.clicks = 0 }
        patternId = -1
        openedTick = -1
        tick = 0
        solution.clear()
    }

    override fun onTick(mc: Minecraft) {
        if (!FishSettings.waterSolver) return
        tick++
        if (patternId != -1) return
        if (++tickAcc < 10) return
        tickAcc = 0
        scan(mc)
    }

    override fun onBlockClick(pos: BlockPos) {
        if (solution.isEmpty()) return
        val r = room ?: return
        Lever.entries.firstOrNull { r.offset(it.local) == pos }?.let {
            if (it == Lever.WATER && openedTick == -1) openedTick = tick
            it.clicks++
        }
    }

    private fun blockAt(mc: Minecraft, local: BlockPos) =
        room?.offset(local)?.let { mc.level?.getBlockState(it)?.block }

    private fun scan(mc: Minecraft) {
        val r = room ?: return
        val extended = Wool.entries.filter { isWool(mc, it.local) }
        if (extended.size != 3) return
        val extendedSlots = extended.joinToString("") { it.ordinal.toString() }

        patternId = when {
            blockAt(mc, BlockPos(14, 77, 27)) == Blocks.TERRACOTTA -> 0
            blockAt(mc, BlockPos(16, 78, 27)) == Blocks.EMERALD_BLOCK -> 1
            blockAt(mc, BlockPos(14, 78, 27)) == Blocks.DIAMOND_BLOCK -> 2
            blockAt(mc, BlockPos(14, 78, 27)) == Blocks.QUARTZ_BLOCK -> 3
            else -> return
        }

        solution.clear()
        val node = runCatching {
            SOLUTIONS.getAsJsonObject(FishSettings.waterOptimized.toString())
                .getAsJsonObject(patternId.toString())
                .getAsJsonObject(extendedSlots)
        }.getOrNull() ?: return
        for ((key, value) in node.entrySet()) {
            val lever = Lever.byBlockName(key) ?: continue
            solution[lever] = value.asJsonArray.map { it.asDouble }.toDoubleArray()
        }
    }

    private fun isWool(mc: Minecraft, local: BlockPos): Boolean {
        val b = blockAt(mc, local) ?: return false
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).path.endsWith("_wool")
    }

    /** (lever, time) pairs still to click, soonest first. */
    private fun pending(): List<Pair<Lever, Double>> =
        solution.flatMap { (lever, times) ->
            times.drop(lever.clicks).map { lever to it }
        }.sortedBy { (lever, t) -> t + if (lever == Lever.WATER) 0.01 else 0.0 }

    override fun renderWorld(matrices: PoseStack, vc: VertexConsumer) {
        if (!FishSettings.waterSolver || solution.isEmpty()) return
        val r = room ?: return
        val list = pending()
        val first = list.firstOrNull()?.first ?: return
        r.offset(first.local)?.let { p ->
            val box = AABB(p.x - 0.05, p.y - 0.05, p.z - 0.05, p.x + 1.05, p.y + 1.05, p.z + 1.05)
            RenderUtils.renderFilled(matrices, vc, box, RenderUtils.toFloats(FishSettings.waterFirstColor))
            RenderUtils.renderOutline(matrices, vc, box, RenderUtils.toFloats(0xFF000000.toInt() or (FishSettings.waterFirstColor and 0xFFFFFF)))
        }
        if (list.size > 1 && list[1].first != first) {
            val a = r.offset(first.local); val b = r.offset(list[1].first.local)
            if (a != null && b != null) {
                RenderUtils.renderLine(matrices, vc,
                    Vec3(a.x + 0.5, a.y + 0.5, a.z + 0.5), Vec3(b.x + 0.5, b.y + 0.5, b.z + 0.5),
                    RenderUtils.toFloats(FishSettings.waterSecondColor))
            }
        }
    }

    override fun renderWorldText(ctx: LevelRenderContext, matrices: PoseStack) {
        if (!FishSettings.waterSolver || solution.isEmpty()) return
        val r = room ?: return
        for ((lever, times) in solution) {
            val base = r.offset(lever.local) ?: continue
            times.drop(lever.clicks).forEachIndexed { idx, time ->
                val ticks = (time * 20).toInt()
                val label = when {
                    openedTick == -1 && ticks == 0 -> "§a§lCLICK"
                    openedTick == -1 -> "§e${trim(time)}s"
                    else -> {
                        val left = openedTick + ticks - tick
                        if (left > 0) "§e${trim(left / 20.0)}s" else "§a§lCLICK"
                    }
                }
                RenderUtils.renderText(ctx, matrices, Component.literal(label),
                    base.x + 0.5, base.y + (idx + lever.clicks) * 0.5 + 1.5, base.z + 0.5, 0.04f)
            }
        }
    }

    private fun trim(v: Double) = String.format("%.1f", v)

    private enum class Wool(val local: BlockPos) {
        PURPLE(BlockPos(15, 56, 19)), ORANGE(BlockPos(15, 56, 18)), BLUE(BlockPos(15, 56, 17)),
        GREEN(BlockPos(15, 56, 16)), RED(BlockPos(15, 56, 15)),
    }

    private enum class Lever(val local: BlockPos) {
        QUARTZ(BlockPos(20, 61, 20)), GOLD(BlockPos(20, 61, 15)), COAL(BlockPos(20, 61, 10)),
        DIAMOND(BlockPos(10, 61, 20)), EMERALD(BlockPos(10, 61, 15)), CLAY(BlockPos(10, 61, 10)),
        WATER(BlockPos(15, 60, 5)), NONE(BlockPos(0, 0, 0));

        @JvmField var clicks = 0

        companion object {
            fun byBlockName(name: String) = when (name) {
                "diamond_block" -> DIAMOND; "emerald_block" -> EMERALD; "hardened_clay" -> CLAY
                "quartz_block" -> QUARTZ; "gold_block" -> GOLD; "coal_block" -> COAL
                "water" -> WATER; else -> null
            }
        }
    }

    companion object {
        private val SOLUTIONS: JsonObject = try {
            WaterSolver::class.java.getResourceAsStream("/waterSolutions.json")!!.use { s ->
                JsonParser.parseReader(InputStreamReader(s, StandardCharsets.UTF_8)).asJsonObject
            }
        } catch (e: Exception) {
            Debug.LOGGER.error("Water solutions failed to load", e)
            JsonObject()
        }
    }
}
