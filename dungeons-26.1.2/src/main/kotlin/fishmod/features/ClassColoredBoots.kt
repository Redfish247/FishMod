package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.component.DyedItemColor

/** Recolors the local player's worn boots to match their detected dungeon class. Client-side only — re-applied every tick since server slot updates would otherwise wipe it. Only visible on leather/dyeable boots. */
object ClassColoredBoots {

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
