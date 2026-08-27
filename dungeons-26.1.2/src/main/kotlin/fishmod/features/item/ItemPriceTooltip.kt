package fishmod.features.item

import fishmod.features.croesus.CroesusPrices
import fishmod.utils.Location
import fishmod.utils.SkyblockItems
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.network.chat.Component

/**
 * Adds a value line (Bazaar/BIN blended, via [CroesusPrices]) and an optional NPC-sell line to
 * item tooltips in SkyBlock — the price half of NoammAddons' ItemTooltip. Prices are lazily
 * refreshed on a TTL while the feature is on.
 */
object ItemPriceTooltip {

    private var lastRefresh = 0L

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!FishSettings.itemTooltipPrices || !Location.inSkyblock()) return@register
            val now = System.currentTimeMillis()
            if (now - lastRefresh < 60_000L) return@register
            lastRefresh = now
            CroesusPrices.refreshIfStale()
        }

        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!FishSettings.itemTooltipPrices || !Location.inSkyblock() || stack.isEmpty) return@ItemTooltipCallback
            val id = ItemUtil.getId(stack) ?: return@ItemTooltipCallback
            val count = stack.count

            val base = CroesusPrices.price(id)
            val mods = ModifierValue.calc(stack)
            val unit = base + mods
            if (unit > 0.0) {
                val each = "§eValue: §6${abbr(unit)}"
                val stackPart = if (count > 1) " §7(×$count = §6${abbr(unit * count)}§7)" else ""
                lines.add(Component.literal("$each$stackPart"))
                if (mods > 0.0 && base > 0.0) lines.add(Component.literal("§7  base §6${abbr(base)} §7+ modifiers §6${abbr(mods)}"))
            }

            if (FishSettings.itemTooltipNpcSell) {
                val npc = SkyblockItems.npcSellPriceFor(id)
                if (npc > 0.0) {
                    val total = if (count > 1) npc * count else npc
                    lines.add(Component.literal("§eNPC Sell: §6${abbr(total)}"))
                }
            }
        })
    }

    private fun abbr(v: Double): String = when {
        v >= 1_000_000_000 -> "%.2fB".format(v / 1_000_000_000)
        v >= 1_000_000 -> "%.2fM".format(v / 1_000_000)
        v >= 1_000 -> "%.1fk".format(v / 1_000)
        else -> "%,d".format(v.toLong())
    }
}
