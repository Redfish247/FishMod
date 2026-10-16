package fishmod.features.garden

import fishmod.features.mining.MiningHuds
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import fishmod.features.garden.GardenSettings as S

// Best speed and yaw/pitch for the held crop, drawn over the speed sign screen
object SpeedSignHelper {

    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, w, _ ->
            if (screen !is AbstractSignEditScreen) return@AfterInit
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, _, _, _ ->
                if (!S.gardenSpeedSign || !Garden.inGarden()) return@AfterExtract
                val c = GardenCrops.heldCrop() ?: return@AfterExtract
                val col = Garden.legacy(S.gardenSpeedSignColor)
                val lines = listOf(
                    "$col§lOptimal for ${c.color}${c.display}",
                    "§7Speed §f${c.speed}",
                    "§7Yaw §f${fmt(c.yaw)} §7Pitch §f${fmt(c.pitch)}",
                )
                MiningHuds.draw(ctx, lines, w / 2 - 55, 20, 1.0)
            })
        })
    }

    private fun fmt(d: Double) = if (d == Math.rint(d)) d.toInt().toString() else "%.1f".format(d)
}
