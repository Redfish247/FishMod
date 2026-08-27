package fishmod.features.dungeon.f7.dragons

import fishmod.features.dungeon.Blessings
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass

/**
 * Which spawning dragon to kill first (ported from NoammAddons' DragonPriority). With the priority
 * toggle off this is just the fixed Red > Orange > Blue > Purple > Green order; on, it factors in
 * blessing power and your class the same way NoammAddons does.
 */
object DragonPriority {

    private val FIXED = listOf(WitherDragon.RED, WitherDragon.ORANGE, WitherDragon.BLUE, WitherDragon.PURPLE, WitherDragon.GREEN)

    fun findPriority(spawning: MutableList<WitherDragon>): WitherDragon {
        if (spawning.isEmpty()) return WitherDragon.NONE
        if (!FishSettings.witherDragonsPriority) {
            spawning.sortBy { FIXED.indexOf(it) }
            return spawning.first()
        }
        return sortPriority(spawning)
    }

    private fun sortPriority(spawning: MutableList<WitherDragon>): WitherDragon {
        val totalPower = Blessings.Type.POWER.current + (if (Blessings.Type.TIME.current > 0) 2.5 else 0.0)
        val clazz = DungeonClass.currentClass

        val order = listOf(WitherDragon.ORANGE, WitherDragon.GREEN, WitherDragon.RED, WitherDragon.BLUE, WitherDragon.PURPLE)
        val priorityList = when {
            totalPower >= FishSettings.witherDragonsNormalPower ||
                (spawning.any { it == WitherDragon.PURPLE } && totalPower >= FishSettings.witherDragonsEasyPower) ->
                if (clazz == DungeonClass.BERSERK || clazz == DungeonClass.MAGE) order else order.reversed()
            else -> FIXED
        }

        spawning.sortBy { priorityList.indexOf(it) }

        if (totalPower >= FishSettings.witherDragonsEasyPower) {
            val solo = FishSettings.witherDragonsSoloDebuff // 0 Tank, 1 Healer
            val onAll = FishSettings.witherDragonsSoloDebuffAll
            val hasPurple = spawning.any { it == WitherDragon.PURPLE }
            if (solo == 0 && clazz == DungeonClass.TANK && (hasPurple || onAll))
                spawning.sortByDescending { priorityList.indexOf(it) }
            else if (solo == 1 && clazz == DungeonClass.HEALER && (hasPurple || onAll))
                spawning.sortByDescending { priorityList.indexOf(it) }
        }

        return spawning.first()
    }
}
