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
import net.minecraft.world.item.ItemStack

object ItemPriceTooltip {

    private var lastRefresh = 0L
    private val LAST_PHASE = Identifier.fromNamespaceAndPath("fishmod", "item_price_tooltip_last")

    private const val CACHE_TTL_MS = 1000L
    private var cachedStack: ItemStack? = null
    private var cachedAt = 0L
    private var cachedLines: List<Component> = emptyList()

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

            val now = System.currentTimeMillis()
            if (cachedStack === stack && now - cachedAt < CACHE_TTL_MS) {
                lines.addAll(cachedLines)
                return@ItemTooltipCallback
            }

            val id = ItemUtil.getId(stack) ?: return@ItemTooltipCallback
            val count = stack.count
            val built = ArrayList<Component>(4)

            val unit = ItemValue.estimate(stack)
            if (unit > 0.0) {
                val each = "§eValue: §6${abbr(unit)}"
                val stackPart = if (count > 1) " §7(×$count = §6${abbr(unit * count)}§7)" else ""
                built.add(Component.literal("$each$stackPart"))
            }

            val avg = CroesusPrices.threeDayAvg(id)
            if (avg > 0.0) built.add(Component.literal("§e3 Day Avg: §6${abbr(avg)}"))

            val lowBin = CroesusPrices.currentLowBin(id)
            if (lowBin > 0.0) built.add(Component.literal("§eCurrent Low BIN: §6${abbr(lowBin)}"))

            if (FishSettings.itemTooltipNpcSell) {
                val npc = ItemsDb.npcSellPriceFor(id)
                if (npc > 0.0) {
                    val total = if (count > 1) npc * count else npc
                    built.add(Component.literal("§eNPC Sell: §6${abbr(total)}"))
                }
            }

            cachedStack = stack
            cachedAt = now
            cachedLines = built
            lines.addAll(built)
        })
    }

}
