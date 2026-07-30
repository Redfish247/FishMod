package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.component.DyedItemColor

/**
 * Recolors the local player's worn boots (leather dye) to a color that matches their detected dungeon
 * class. Client-side only — re-applied every tick to the equipped feet stack so server slot updates
 * don't wipe it (same approach as [ItemCustomizer]). Class is detected by [DungeonClass]
 * (chiefly the "Your <class> stats are doubled…" message + the dungeon tab list).
 *
 * Registered AFTER ItemCustomizer.init() so, while enabled, the class color wins over any per-item dye
 * the player set on those boots. Only shows on leather/dyeable boots (the DYED_COLOR tint is ignored
 * by non-dyeable models).
 */
object ClassColoredBoots {

    /** Boot dye color per dungeon class (RGB), per the requested palette. */
    private fun colorFor(c: DungeonClass?): Int {
        if (c == null) return -1
        return when (c) {
            DungeonClass.HEALER -> 0xFF99FF
            DungeonClass.BERSERK -> 0xE89149
            DungeonClass.ARCHER -> 0xFF5555
            DungeonClass.MAGE -> 0x99FFFF
            DungeonClass.TANK -> 0x99FF99
        }
    }

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!FishSettings.classColoredBootsEnabled || mc.player == null) return@register
            val rgb = colorFor(DungeonClass.currentClass)
            if (rgb < 0) return@register
            try {
                val boots = mc.player!!.getItemBySlot(EquipmentSlot.FEET)
                if (boots == null || boots.isEmpty) return@register
                boots.set(DataComponents.DYED_COLOR, DyedItemColor(rgb and 0xFFFFFF))
            } catch (ignored: Exception) {
            }
        }
    }
}
