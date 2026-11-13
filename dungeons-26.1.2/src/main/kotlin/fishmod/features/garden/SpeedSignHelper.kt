package fishmod.features.garden

import fishmod.features.mining.MiningHuds
import fishmod.mixin.accessors.AbstractSignEditScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import fishmod.features.garden.GardenSettings as S

object SpeedSignHelper {

    private val PUMP = listOf("3 Blocks" to "155", "2 Blocks" to "265", "2 Blocks" to "400")
    private val SPEEDS = mapOf(
        GardenCrops.Crop.WHEAT to listOf("5 Blocks" to "93", "4 Blocks" to "116"),
        GardenCrops.Crop.CARROT to listOf("5 Blocks" to "93", "4 Blocks" to "116"),
        GardenCrops.Crop.POTATO to listOf("5 Blocks" to "93", "4 Blocks" to "116"),
        GardenCrops.Crop.NETHER_WART to listOf("5 Blocks" to "93", "4 Blocks" to "116"),
        GardenCrops.Crop.PUMPKIN to PUMP, GardenCrops.Crop.MELON to PUMP,
    )

    private class Hit(val y0: Int, val y1: Int, val x1: Int, val speed: String)
    private val hits = ArrayList<Hit>()

    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, h ->
            if (screen !is AbstractSignEditScreen) return@AfterInit
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click ->
                if (fishmod.compat.GLFW.btn(click) != 0) return@AllowMouseClick true
                val hit = hits.firstOrNull { click.x() in 4.0..it.x1.toDouble() && click.y() >= it.y0 && click.y() < it.y1 } ?: return@AllowMouseClick true
                val msgs = (screen as? AbstractSignEditScreenAccessor)?.`fishmod$getMessages`() ?: return@AllowMouseClick true
                if (msgs.isEmpty()) return@AllowMouseClick true
                msgs[0] = hit.speed
                screen.onClose()
                false
            })
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, _, _, _ ->
                if (!S.gardenSpeedSign || !Garden.inGarden() || !isSpeedSign(screen)) return@AfterExtract
                val held = GardenCrops.heldCrop()
                val col = Garden.legacy(S.gardenSpeedSignColor)
                val lines = ArrayList<String>()
                val speedAt = HashMap<Int, String>()
                for (c in if (held != null) listOf(held) else GardenCrops.Crop.entries) {
                    lines += "$col§l${c.color}${c.display} §7- Suggested farm speed:"
                    for ((blocks, txt) in SPEEDS[c] ?: listOf("" to "${c.speed}")) {
                        speedAt[lines.size] = txt
                        lines += (if (blocks.isEmpty()) "" else "§e$blocks: ") + "§f✦ $txt speed §8(click)"
                    }
                    if (held != null) lines += "§7Yaw §f${fmt(c.yaw)} §7Pitch §f${fmt(c.pitch)}"
                }
                val y0 = (h - lines.size * 10 - 4).coerceAtLeast(2)
                MiningHuds.draw(ctx, lines, 4, y0, 1.0)
                hits.clear()
                val font = net.minecraft.client.Minecraft.getInstance().font
                for ((i, sp) in speedAt) hits += Hit(y0 + i * 10, y0 + i * 10 + 10, 4 + font.width(lines[i]), sp)
            })
        })
    }

    private fun isSpeedSign(screen: AbstractSignEditScreen): Boolean {
        val msgs = (screen as? AbstractSignEditScreenAccessor)?.`fishmod$getMessages`() ?: return false
        return msgs.joinToString(" ").replace(Regex("\\s+"), " ").contains("crop speed cap", ignoreCase = true)
    }

    private fun fmt(d: Double) = if (d == Math.rint(d)) d.toInt().toString() else "%.1f".format(d)
}
