package fishmod.features.dungeon.puzzles.odin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.Debug
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Water Board solver. Clay-origin coords via [ORoom.getRealCoords]; solution schema
 * `waterSolutions.json` = { optimized(true/false) -> pattern(0-3) -> extendedSlots(3 digits) ->
 * lever -> [click times, seconds] }.
 */
object WaterSolver {

    private val waterSolutions: Map<String, Map<String, Map<String, Map<String, List<Double>>>>> = try {
        WaterSolver::class.java.getResourceAsStream("/waterSolutions.json")!!.use { s ->
            Gson().fromJson(
                InputStreamReader(s, StandardCharsets.UTF_8),
                object : TypeToken<Map<String, Map<String, Map<String, Map<String, List<Double>>>>>>() {}.type,
            )
        }
    } catch (e: Exception) {
        Debug.LOGGER.error("Water solutions failed to load", e); emptyMap()
    }

    private val solutions = HashMap<LeverBlock, List<Double>>()
    private var patternIdentifier = -1
    private var openedWaterTicks = -1
    private var tickCounter = 0
    private var failed = false

    fun onRoomEnter(room: ORoom?) {
        if (room?.data?.name != "Water Board") reset()
    }

    fun onTick() = scan(FishSettings.waterOptimized)

    fun onServerTick() { tickCounter++ }

    private fun scan(optimized: Boolean) {
        if (failed) return
        val room = OdinScan.currentRoom ?: return
        if (room.data?.name != "Water Board" || patternIdentifier != -1) return
        val level = Minecraft.getInstance().level ?: return

        val extendedSlots = WoolColor.entries
            .joinToString("") { if (it.isExtended) it.ordinal.toString() else "" }
            .takeIf { it.length == 3 } ?: return

        patternIdentifier = when {
            level.getBlockState(room.getRealCoords(BlockPos(14, 77, 27))).block == Blocks.TERRACOTTA -> 0
            level.getBlockState(room.getRealCoords(BlockPos(16, 78, 27))).block == Blocks.EMERALD_BLOCK -> 1
            level.getBlockState(room.getRealCoords(BlockPos(14, 78, 27))).block == Blocks.DIAMOND_BLOCK -> 2
            level.getBlockState(room.getRealCoords(BlockPos(14, 78, 27))).block == Blocks.QUARTZ_BLOCK -> 3
            else -> {
                failed = true
                Misc.addChatMessage(Component.literal("§cFailed to get Water Board pattern. Was the puzzle already started?"))
                return
            }
        }

        solutions.clear()
        waterSolutions[optimized.toString()]?.get(patternIdentifier.toString())?.get(extendedSlots)?.forEach { (key, times) ->
            LeverBlock.fromKey(key)?.let { solutions[it] = times }
        }
    }

    fun onRenderWorld() {
        if (patternIdentifier == -1 || solutions.isEmpty() || OdinScan.currentRoomName != "Water Board") return

        val solutionList = solutions
            .flatMap { (lever, times) -> times.drop(lever.i).map { lever to it } }
            .sortedWith(
                compareBy(
                    { it.second != 0.0 },
                    { if (it.second == 0.0) it.first.ordinal else Int.MAX_VALUE },
                    { if (it.second != 0.0) it.second else 0.0 },
                ),
            )

        solutionList.firstOrNull()?.first?.let { first ->
            val fp = first.leverPos
            ORender.tracer(Vec3(fp.x + 0.5, fp.y + 0.5, fp.z + 0.5), FishSettings.waterFirstColor)
            if (solutionList.size > 1 && fp != solutionList[1].first.leverPos) {
                val sp = solutionList[1].first.leverPos
                ORender.line(
                    Vec3(fp.x + 0.5, fp.y + 0.5, fp.z + 0.5),
                    Vec3(sp.x + 0.5, sp.y + 0.5, sp.z + 0.5),
                    FishSettings.waterSecondColor,
                )
            }
        }

        solutions.forEach { (lever, times) ->
            val lp = lever.leverPos
            times.drop(lever.i).forEachIndexed { index, time ->
                val timeInTicks = (time * 20).toInt()
                val label = when {
                    openedWaterTicks == -1 && timeInTicks == 0 -> "§a§lCLICK ME!"
                    openedWaterTicks == -1 -> "§e${time}s"
                    else -> (openedWaterTicks + timeInTicks - tickCounter).takeIf { it > 0 }
                        ?.let { "§e%.1fs".format(it / 20f) } ?: "§a§lCLICK ME!"
                }
                ORender.text(label, Vec3(lp.x + 0.5, lp.y + (index + lever.i) * 0.5 + 1.5, lp.z + 0.5), 1f)
            }
        }
    }

    fun waterInteract(clicked: BlockPos) {
        if (solutions.isEmpty()) return
        LeverBlock.entries.find { it.leverPos == clicked }?.let {
            if (it == LeverBlock.WATER && openedWaterTicks == -1) openedWaterTicks = tickCounter
            it.i++
        }
    }

    fun reset() {
        LeverBlock.entries.forEach { it.i = 0 }
        patternIdentifier = -1
        solutions.clear()
        openedWaterTicks = -1
        tickCounter = 0
        failed = false
    }

    private enum class WoolColor(val relativePosition: BlockPos) {
        PURPLE(BlockPos(15, 56, 19)),
        ORANGE(BlockPos(15, 56, 18)),
        BLUE(BlockPos(15, 56, 17)),
        GREEN(BlockPos(15, 56, 16)),
        RED(BlockPos(15, 56, 15));

        val isExtended: Boolean
            get() = OdinScan.currentRoom?.let {
                Minecraft.getInstance().level?.getBlockState(it.getRealCoords(relativePosition))?.isAir == true
            } == false
    }

    private enum class LeverBlock(val relativePosition: BlockPos, var i: Int = 0) {
        COAL(BlockPos(20, 61, 10)),
        GOLD(BlockPos(20, 61, 15)),
        QUARTZ(BlockPos(20, 61, 20)),
        DIAMOND(BlockPos(10, 61, 20)),
        EMERALD(BlockPos(10, 61, 15)),
        CLAY(BlockPos(10, 61, 10)),
        WATER(BlockPos(15, 60, 5)),
        NONE(BlockPos(0, 0, 0));

        val leverPos: BlockPos
            get() = OdinScan.currentRoom?.getRealCoords(relativePosition) ?: BlockPos(0, 0, 0)

        companion object {
            fun fromKey(key: String) = when (key) {
                "diamond_block" -> DIAMOND
                "emerald_block" -> EMERALD
                "hardened_clay" -> CLAY
                "quartz_block" -> QUARTZ
                "gold_block" -> GOLD
                "coal_block" -> COAL
                "water" -> WATER
                else -> null
            }
        }
    }
}
