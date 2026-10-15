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
        GardenWidgets.init()
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

    fun hud(name: String, id: String, w: Int, h: Int,
            gx: () -> Int, sx: (Int) -> Unit, gy: () -> Int, sy: (Int) -> Unit,
            gs: () -> Double, ss: (Double) -> Unit, on: () -> Boolean, lines: () -> List<String>) =
        MiningHuds.reg(name, id, w, h, gx, sx, gy, sy, gs, ss, on, lines)

}
