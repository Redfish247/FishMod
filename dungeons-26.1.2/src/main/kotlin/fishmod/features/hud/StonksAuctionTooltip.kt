package fishmod.features.hud

import fishmod.compat.curScreen
import fishmod.utils.FishConstants
import fishmod.utils.FishUtil.abbr
import fishmod.utils.ScreenTitle
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object StonksAuctionTooltip {

    // "TOP 100 - Stock of Stonks x25"
    private val TIER = Regex("""Stock of Stonks x(\d[\d,]*)\s*$""", RegexOption.IGNORE_CASE)
    // "Minimum Bid: 55,000,000 Coins"
    private val MIN_BID = Regex("""Minimum Bid:\s*(\d[\d,]*)""", RegexOption.IGNORE_CASE)

    @JvmStatic
    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!FishSettings.stonksAuctionPricePer || stack.isEmpty || lines.isEmpty() || !SkyblockLocation.inSkyblock()) return@ItemTooltipCallback
            try {
                val screen = Minecraft.getInstance().curScreen ?: return@ItemTooltipCallback
                if (!ScreenTitle.plain(screen).contains("Stonks Auction", ignoreCase = true)) return@ItemTooltipCallback
                var amount = 0L
                for (i in lines.indices) {
                    val plain = FishConstants.STRIP_COLOR_REGEX.replace(lines[i].string, "").trim()
                    TIER.find(plain)?.let { amount = it.groupValues[1].replace(",", "").toLongOrNull() ?: 0L; continue }
                    val bid = MIN_BID.find(plain)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: continue
                    if (amount <= 0L || bid <= 0.0) continue
                    lines[i] = lines[i].copy().append(Component.literal(" §7(§6${abbr(bid / amount)}§7 each)"))
                    amount = 0L
                }
            } catch (e: Exception) {
                FishDiag.fail("StonksAuctionTooltip.1", "stonks auction tooltip failed", e)
            }
        })
    }
}
