package fishmod.features.garden

import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.world.item.ItemStack
import fishmod.features.garden.GardenExtraSettings as X

// Buy-order HUD shown only while Jeff's chip shop is open
object GardenJeff {

    private val LEVEL = Regex("""(\d+)\s*/\s*(\d+)""")

    private var nextStack: ItemStack? = null

    private fun isMaxed(lore: List<String>) =
        lore.any { it.contains("MAX", true) } || lore.firstNotNullOfOrNull { LEVEL.find(it) }?.let { it.groupValues[1] == it.groupValues[2] } == true

    // First chip in the buy order that is not maxed
    private fun pickNext() {
        val slots = GardenExtras.slots()
        nextStack = order().firstNotNullOfOrNull { chip ->
            val st = slots.firstOrNull { GardenExtras.name(it.item).contains(chip, true) }?.item ?: return@firstNotNullOfOrNull null
            if (isMaxed(Garden.lore(st))) null else st
        }
    }

    private fun order() = X.gardenJeffOrder.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    // Level text per chip from the menu (guessed lore "x/y" or MAXED); null when not in the menu
    private fun lines(): List<String> {
        val slots = GardenExtras.slots()
        val head = Garden.legacy(X.gardenJeffColor) + "§lJeff Buy Order"
        val out = arrayListOf(head)
        var next = true
        for (chip in order()) {
            val st = slots.firstOrNull { GardenExtras.name(it.item).contains(chip, true) }?.item
            val lore = st?.let { Garden.lore(it) } ?: emptyList()
            val maxed = isMaxed(lore)
            val lv = lore.firstNotNullOfOrNull { LEVEL.find(it) }?.let { " §7(${it.value})" } ?: ""
            out += when {
                maxed -> "§8§m$chip"
                next -> { next = false; "§a▶ §f§l$chip$lv" }
                else -> "§7  $chip$lv"
            }
        }
        return out
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.screen == null || !X.gardenJeff || !GardenExtras.inMenu("Manage Chips", "Jeff")) nextStack = null else pickNext()
        }
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!stack.isEmpty && stack === nextStack && X.gardenJeff)
                ctx.fill(x, y, x + 16, y + 16, fishmod.features.mining.Mining.alpha(X.gardenJeffColor, 55))
        }
        Garden.hud("Jeff Buy Order", "garden_jeff", 120, 80,
            { X.gardenJeffHudX }, { X.gardenJeffHudX = it }, { X.gardenJeffHudY }, { X.gardenJeffHudY = it },
            { X.gardenJeffHudScale }, { X.gardenJeffHudScale = it },
            { X.gardenJeff && GardenExtras.inMenu("Manage Chips", "Jeff") }) { lines() }
    }
}
