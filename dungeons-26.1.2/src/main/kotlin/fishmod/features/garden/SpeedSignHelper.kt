package fishmod.features.garden

import fishmod.features.mining.MiningHuds
import fishmod.mixin.accessors.AbstractSignEditScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import fishmod.features.garden.GardenSettings as S

// Best speed and yaw/pitch for the held crop, drawn over the speed sign screen
object SpeedSignHelper {

    // Suggested speed per block count; wheat/carrot/potato/wart/pumpkin from the in-game speed list, melon assumed like pumpkin
    private val PUMP = listOf("3 Blocks" to "155", "2 Blocks" to "265 or 400")
    private val SPEEDS = mapOf(
        GardenCrops.Crop.WHEAT to listOf("5 Blocks" to "93", "4 Blocks" to "116"),
        GardenCrops.Crop.CARROT to listOf("5 Blocks" to "93", "4 Blocks" to "116"),
        GardenCrops.Crop.POTATO to listOf("5 Blocks" to "93", "4 Blocks" to "116"),
        GardenCrops.Crop.NETHER_WART to listOf("5 Blocks" to "93", "4 Blocks" to "116"),
        GardenCrops.Crop.PUMPKIN to PUMP, GardenCrops.Crop.MELON to PUMP,
    )

    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, h ->
            if (screen !is AbstractSignEditScreen) return@AfterInit
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, _, _, _ ->
                if (!S.gardenSpeedSign || !Garden.inGarden() || !isSpeedSign(screen)) return@AfterExtract
                val held = GardenCrops.heldCrop()
                val col = Garden.legacy(S.gardenSpeedSignColor)
                val lines = ArrayList<String>()
                for (c in if (held != null) listOf(held) else GardenCrops.Crop.entries) {
                    lines += "$col§l${c.color}${c.display} §7- Suggested farm speed:"
                    for ((blocks, txt) in SPEEDS[c] ?: listOf("" to "${c.speed}")) lines += (if (blocks.isEmpty()) "" else "§e$blocks: ") + "§f✦ $txt speed"
                    if (held != null) lines += "§7Yaw §f${fmt(c.yaw)} §7Pitch §f${fmt(c.pitch)}"
                }
                // Bottom left so the sign itself stays readable
                MiningHuds.draw(ctx, lines, 4, (h - lines.size * 10 - 4).coerceAtLeast(2), 1.0)
            })
        })
    }

    // Only the crop speed sign ("Set your crop speed cap!"), not every sign in the Garden
    private fun isSpeedSign(screen: AbstractSignEditScreen): Boolean {
        val msgs = (screen as? AbstractSignEditScreenAccessor)?.`fishmod$getMessages`() ?: return false
        return msgs.joinToString(" ").replace(Regex("\\s+"), " ").contains("crop speed cap", ignoreCase = true)
    }

    private fun fmt(d: Double) = if (d == Math.rint(d)) d.toInt().toString() else "%.1f".format(d)
}
