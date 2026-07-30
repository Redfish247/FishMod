package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.DyedColorComponent
import net.minecraft.entity.EquipmentSlot

/** Recolors the local player's worn boots (leather dye) to a color that matches their detected dungeon class. */
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
                val boots = mc.player!!.getEquippedStack(EquipmentSlot.FEET)
                if (boots == null || boots.isEmpty) return@register
                boots.set(DataComponentTypes.DYED_COLOR, DyedColorComponent(rgb and 0xFFFFFF))
            } catch (ignored: Exception) {
            }
        }
    }
}
