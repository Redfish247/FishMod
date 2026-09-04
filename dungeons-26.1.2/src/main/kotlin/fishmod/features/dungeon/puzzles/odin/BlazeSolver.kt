package fishmod.features.dungeon.puzzles.odin

import fishmod.features.dungeon.puzzles.PuzzleSolvers
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand

object BlazeSolver {

    private val blazes = mutableListOf<ArmorStand>()
    private var lastBlazeCount = 10
    // Hypixel injects a custom-font glyph (U+F07C) between the level tag and "Blaze", so match only
    // the health readout — in a Blaze room the only named mobs are the 10 puzzle blazes.
    private val blazeHealthRegex = Regex("Blaze [\\d,]+/([\\d,]+)❤")
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    fun getBlaze() {
        val name = OdinScan.currentRoom?.data?.name
        if (name != "Lower Blaze" && name != "Higher Blaze") return
        val hpMap = mutableMapOf<ArmorStand, Int>()
        blazes.clear()
        Minecraft.getInstance().level?.entitiesForRendering()?.forEach { entity ->
            if (entity !is ArmorStand || entity in blazes) return@forEach
            val tag = COLOR.replace(entity.name.string, "")
            val hp = blazeHealthRegex.find(tag)?.groups?.get(1)?.value?.replace(",", "")?.toIntOrNull() ?: return@forEach
            hpMap[entity] = hp
            blazes.add(entity)
        }
        if (name == "Lower Blaze") blazes.sortByDescending { hpMap[it] } else blazes.sortBy { hpMap[it] }
    }

    fun onRenderWorld() {
        val name = OdinScan.currentRoomName
        if (name != "Lower Blaze" && name != "Higher Blaze") return
        if (blazes.isEmpty()) return
        val level = Minecraft.getInstance().level
        blazes.removeAll { level?.getEntity(it.id) == null }
        if (blazes.isEmpty() && lastBlazeCount == 1) {
            PuzzleSolvers.onPuzzleComplete(name)
            lastBlazeCount = 0
            return
        }
        lastBlazeCount = blazes.size
        val style = ORender.style()
        blazes.forEachIndexed { index, entity ->
            val color = when (index) {
                0 -> FishSettings.blazeFirstColor
                1 -> FishSettings.blazeSecondColor
                else -> FishSettings.blazeOtherColor
            }
            val aabb = entity.boundingBox.inflate(0.5, 1.0, 0.5).move(0.0, -1.0, 0.0)
            ORender.styledBox(aabb, color, style)
            if (FishSettings.blazeLine && index in 1..FishSettings.blazeLineCount) {
                val prev = blazes[index - 1].boundingBox.inflate(0.5, 1.0, 0.5).move(0.0, -1.0, 0.0).center
                ORender.line(prev, aabb.center, color)
            }
        }
    }

    fun reset() {
        lastBlazeCount = 10
        blazes.clear()
    }
}
