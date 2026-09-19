package fishmod.features.item

import fishmod.features.croesus.CroesusPrices
import fishmod.utils.Location
import fishmod.utils.Misc.abbr
import fishmod.utils.networth.ItemsDb
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

/** Registered on a dedicated LAST phase so these always render below every other mod's tooltip additions instead of landing in the middle of them. */
object ItemPriceTooltip {

    private var lastRefresh = 0L
    private val LAST_PHASE = Identifier.fromNamespaceAndPath("fishmod", "item_price_tooltip_last")

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!FishSettings.itemTooltipPrices || !Location.inSkyblock()) return@register
            val now = System.currentTimeMillis()
            if (now - lastRefresh < 60_000L) return@register
            lastRefresh = now
            CroesusPrices.refreshIfStale()
        }

        ItemTooltipCallback.EVENT.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, LAST_PHASE)
        ItemTooltipCallback.EVENT.register(LAST_PHASE, ItemTooltipCallback { stack, _, _, lines ->
            if (!FishSettings.itemTooltipPrices || !Location.inSkyblock() || stack.isEmpty) return@ItemTooltipCallback
            val id = ItemUtil.getId(stack) ?: return@ItemTooltipCallback
            val count = stack.count

            val unit = ItemValue.estimate(stack)
            if (unit > 0.0) {
                val each = "§eValue: §6${abbr(unit)}"
                val stackPart = if (count > 1) " §7(×$count = §6${abbr(unit * count)}§7)" else ""
                lines.add(Component.literal("$each$stackPart"))
            }

            val avg = CroesusPrices.threeDayAvg(id)
            if (avg > 0.0) lines.add(Component.literal("§e3 Day Avg: §6${abbr(avg)}"))

            val lowBin = CroesusPrices.currentLowBin(id)
            if (lowBin > 0.0) lines.add(Component.literal("§eCurrent Low BIN: §6${abbr(lowBin)}"))

            if (FishSettings.itemTooltipNpcSell) {
                val npc = ItemsDb.npcSellPriceFor(id)
                if (npc > 0.0) {
                    val total = if (count > 1) npc * count else npc
                    lines.add(Component.literal("§eNPC Sell: §6${abbr(total)}"))
                }
            }
        })
    }

}
