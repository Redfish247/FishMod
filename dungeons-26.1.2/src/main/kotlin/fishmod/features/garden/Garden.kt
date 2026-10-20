package fishmod.features.garden

import fishmod.features.croesus.CroesusPrices
import fishmod.features.mining.Mining
import fishmod.features.mining.MiningHuds
import fishmod.utils.Location
import fishmod.utils.networth.ItemsDb
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

// Shared Garden helpers and init for all garden parts
object Garden {

    fun inGarden(): Boolean = Location.`in`(Location.GARDEN)

    @JvmStatic
    fun init() {
        Visitors.init()
        Pests.init()
        FarmingProfitTracker.init()
        GardenWidgets.init()
        GardenAlerts.init()
        GardenTitles.init()
        GardenMilestones.init()
        GardenLevels.init()
        GardenMisc.init()
        CropFever.init()
        FortuneBonus.init()
        SensitivityReducer.init()
        GardenKeybinds.init()
        SpeedSignHelper.init()
        GardenExtras.init()
    }

    fun strip(s: String) = Mining.strip(s)

    fun lore(stack: ItemStack): List<String> =
        stack.get(DataComponents.LORE)?.lines()?.map { strip(it.string) } ?: emptyList()

    fun priceOf(name: String): Double {
        val id = ItemsDb.idFor(name) ?: ItemsDb.idFor(name.trim()) ?: return 0.0
        CroesusPrices.bazaarPrice(id, true)?.let { if (it > 0) return it }
        return CroesusPrices.price(id)
    }

    fun short(v: Double) = Mining.short(v)

    private val LEGACY = listOf(
        '0' to 0x000000, '1' to 0x0000AA, '2' to 0x00AA00, '3' to 0x00AAAA, '4' to 0xAA0000, '5' to 0xAA00AA, '6' to 0xFFAA00, '7' to 0xAAAAAA,
        '8' to 0x555555, '9' to 0x5555FF, 'a' to 0x55FF55, 'b' to 0x55FFFF, 'c' to 0xFF5555, 'd' to 0xFF55FF, 'e' to 0xFFFF55, 'f' to 0xFFFFFF,
    )
    // Nearest of the 16 chat colours, so HUD/title text (which uses § codes) can follow a colour picker
    fun legacy(argb: Int): String {
        val r = argb shr 16 and 255; val g = argb shr 8 and 255; val b = argb and 255
        val best = LEGACY.minByOrNull { (_, c) -> sq(r - (c shr 16 and 255)) + sq(g - (c shr 8 and 255)) + sq(b - (c and 255)) }!!
        return "§" + best.first
    }
    private fun sq(v: Int) = v * v

    fun hud(name: String, id: String, w: Int, h: Int,
            gx: () -> Int, sx: (Int) -> Unit, gy: () -> Int, sy: (Int) -> Unit,
            gs: () -> Double, ss: (Double) -> Unit, on: () -> Boolean, enabled: (() -> Boolean)? = null, lines: () -> List<String>) =
        MiningHuds.reg(name, id, w, h, gx, sx, gy, sy, gs, ss, on, enabled, lines)

}
