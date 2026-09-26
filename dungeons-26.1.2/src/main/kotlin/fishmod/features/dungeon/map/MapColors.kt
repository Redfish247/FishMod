package fishmod.features.dungeon.map

import fishmod.utils.Addons
import fishmod.utils.config.values.DungeonMapSettings

internal val MAP_COLOR_CODES: Regex = Regex("(?i)[&§][0-9a-fk-or]")

object MapColors {

    @JvmStatic
    fun darker(argb: Int, mult: Float): Int {
        val a = argb ushr 24 and 255
        val r = ((argb shr 16 and 255) * mult).toInt().coerceAtLeast(0)
        val g = ((argb shr 8 and 255) * mult).toInt().coerceAtLeast(0)
        val b = ((argb and 255) * mult).toInt().coerceAtLeast(0)
        return a shl 24 or (r shl 16) or (g shl 8) or b
    }

    @JvmStatic
    fun legit(): Boolean {
        if (!Addons.fishModAddonsInstalled) return true
        return DungeonMapSettings.mapLegitMode
    }

    @JvmStatic
    fun darkenMultiplier(): Float = DungeonMapSettings.mapDarkenMultiplier

    @JvmStatic
    fun roomColor(type: Room.Type): Int = when (type) {
        Room.Type.BLOOD -> DungeonMapSettings.mapBloodRoomColor
        Room.Type.NORMAL -> DungeonMapSettings.mapNormalRoomColor
        Room.Type.PUZZLE -> DungeonMapSettings.mapPuzzleRoomColor
        Room.Type.CHAMPION -> DungeonMapSettings.mapChampionRoomColor
        Room.Type.TRAP -> DungeonMapSettings.mapTrapRoomColor
        Room.Type.ENTRANCE -> DungeonMapSettings.mapEntranceRoomColor
        Room.Type.FAIRY -> DungeonMapSettings.mapFairyRoomColor
        Room.Type.RARE -> DungeonMapSettings.mapRareRoomColor
        else -> DungeonMapSettings.mapUnopenedRoomColor
    }
}
