package fishmod.features.dungeon.puzzles.odin

import fishmod.features.dungeon.puzzles.PuzzleSolvers
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Blaze

object BlazeSolver {

    private val blazes = mutableListOf<ArmorStand>()
    private var seenBlazes = false
    private var completed = false
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
        if (blazes.isNotEmpty()) seenBlazes = true
        else if (seenBlazes && !completed) {
            completed = true
            PuzzleSolvers.onPuzzleComplete(name)
        }
    }

    fun onRenderWorld() {
        val name = OdinScan.currentRoomName
        if (name != "Lower Blaze" && name != "Higher Blaze") return
        val level = Minecraft.getInstance().level
        blazes.removeAll { level?.getEntity(it.id) == null }
        if (blazes.isEmpty()) return
        val style = ORender.style()
        val shown = if (FishSettings.blazeThirdEnabled) 3 else 2
        val pt = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
        val boxes = blazes.take(shown).map { e ->
            e.boundingBox.inflate(0.5, 1.0, 0.5).move(0.0, -1.0, 0.0).move(e.getPosition(pt).subtract(e.position()))
        }
        boxes.forEachIndexed { index, aabb ->
            val color = when (index) {
                0 -> FishSettings.blazeFirstColor
                1 -> FishSettings.blazeSecondColor
                else -> FishSettings.blazeThirdColor
            }
            ORender.styledBox(aabb, color, style)
            if (FishSettings.blazeLine && index in 1..FishSettings.blazeLineCount) {
                ORender.wideLine(boxes[index - 1].center, aabb.center, color or (0xFF shl 24), 3f)
            }
        }
    }

    fun shouldHideMob(entity: Entity): Boolean {
        if (!FishSettings.blazeSolver || !FishSettings.blazeHideMobs || entity !is Blaze) return false
        val name = OdinScan.currentRoomName
        return name == "Lower Blaze" || name == "Higher Blaze"
    }

    fun reset() {
        seenBlazes = false
        completed = false
        blazes.clear()
    }
}
