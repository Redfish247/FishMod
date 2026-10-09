package fishmod.features.item

import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.event.Event
import net.minecraft.resources.Identifier

// Keeps Hypixel's "and N other players" line as the last tooltip line after other features append theirs.
object TooltipPinBottom {

    private val FINAL_PHASE = Identifier.fromNamespaceAndPath("fishmod", "tooltip_pin_bottom")
    private val AFTER = listOf(
        Event.DEFAULT_PHASE,
        Identifier.fromNamespaceAndPath("fishmod", "item_price_tooltip_last"),
        Identifier.fromNamespaceAndPath("fishmod", "party_finder_tooltip_last"),
        Identifier.fromNamespaceAndPath("fishmod", "recipe_viewer_last"),
    )
    private val PINNED = Regex("^and [0-9,]+ other players?[.]?$")

    @JvmStatic
    fun init() {
        AFTER.forEach { ItemTooltipCallback.EVENT.addPhaseOrdering(it, FINAL_PHASE) }
        ItemTooltipCallback.EVENT.register(FINAL_PHASE, ItemTooltipCallback { _, _, _, lines ->
            try {
                val i = lines.indexOfLast { PINNED.matches(it.string.trim()) }
                if (i >= 0 && i != lines.lastIndex) lines.add(lines.removeAt(i))
            } catch (e: Exception) {
                FishDiag.fail("TooltipPinBottom.1", "pinning tooltip line failed", e)
            }
        })
    }
}
