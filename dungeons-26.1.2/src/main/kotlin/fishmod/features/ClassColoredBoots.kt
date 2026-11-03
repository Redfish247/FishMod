package fishmod.features

import fishmod.utils.debug.FishDiag
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonRole
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.component.DyedItemColor

object ClassColoredBoots {

    private var loggedError = false

    private fun colorFor(c: DungeonRole?): Int {
        if (c == null) return -1
        return when (c) {
            DungeonRole.HEALER -> 0xFF99FF
            DungeonRole.BERSERK -> 0xE89149
            DungeonRole.ARCHER -> 0xFF5555
            DungeonRole.MAGE -> 0x99FFFF
            DungeonRole.TANK -> 0x99FF99
        }
    }

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!FishSettings.classColoredBootsEnabled) return@register
            val player = mc.player ?: return@register
            val rgb = colorFor(DungeonRole.currentClass)
            if (rgb < 0) return@register
            try {
                val boots = player.getItemBySlot(EquipmentSlot.FEET)
                if (boots == null || boots.isEmpty) return@register
                val color = rgb and 0xFFFFFF
                if (boots.get(DataComponents.DYED_COLOR)?.rgb() != color) boots.set(DataComponents.DYED_COLOR, DyedItemColor(color))
            } catch (ex: Exception) {
                if (!loggedError) { loggedError = true; fishmod.utils.debug.FishDebug.LOGGER.warn("[ClassColoredBoots] failed to dye boots: {}", ex.message) }
                FishDiag.fail("ClassColoredBoots.1", "failed to dye boots for ${DungeonRole.currentClass}", ex)
            }
        }
    }
}
