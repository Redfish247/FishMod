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
    fun roomColor(type: Room.Type): Int {
        val custom = DungeonMapSettings.mapRoomColorsEnabled
        return when (type) {
            Room.Type.BLOOD -> bloodColor()
            Room.Type.NORMAL -> if (custom) DungeonMapSettings.mapNormalRoomColor else -9749999
            Room.Type.PUZZLE -> if (custom) DungeonMapSettings.mapPuzzleRoomColor else -9109371
            Room.Type.CHAMPION -> if (custom) DungeonMapSettings.mapChampionRoomColor else -73984
            Room.Type.TRAP -> if (custom) DungeonMapSettings.mapTrapRoomColor else -2588877
            Room.Type.ENTRANCE -> if (custom) DungeonMapSettings.mapEntranceRoomColor else -15432448
            Room.Type.FAIRY -> if (custom) DungeonMapSettings.mapFairyRoomColor else -781429
            Room.Type.RARE -> if (custom) DungeonMapSettings.mapRareRoomColor else -13479
            else -> unopenedColor()
        }
    }

    @JvmStatic
    fun unopenedColor(): Int = if (DungeonMapSettings.mapRoomColorsEnabled) DungeonMapSettings.mapUnopenedRoomColor else -14803426

    @JvmStatic
    fun bloodColor(): Int = if (DungeonMapSettings.mapRoomColorsEnabled) DungeonMapSettings.mapBloodRoomColor else -65536
}
