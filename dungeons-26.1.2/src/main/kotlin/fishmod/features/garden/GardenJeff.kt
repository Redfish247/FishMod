package fishmod.features.garden

import fishmod.utils.rendering.DrawEvents
import fishmod.features.garden.GardenExtraSettings as X

// Buy-order HUD shown only while Jeff's chip shop is open
object GardenJeff {

    private val LEVEL = Regex("""(\d+)\s*/\s*(\d+)""")

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
            val maxed = lore.any { it.contains("MAX", true) } || lore.firstNotNullOfOrNull { LEVEL.find(it) }?.let { it.groupValues[1] == it.groupValues[2] } == true
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
        Garden.hud("Jeff Buy Order", "garden_jeff", 120, 80,
            { X.gardenJeffHudX }, { X.gardenJeffHudX = it }, { X.gardenJeffHudY }, { X.gardenJeffHudY = it },
            { X.gardenJeffHudScale }, { X.gardenJeffHudScale = it },
            { X.gardenJeff && GardenExtras.inMenu("Jeff", "Garden Chips") }) { lines() }
    }
}
