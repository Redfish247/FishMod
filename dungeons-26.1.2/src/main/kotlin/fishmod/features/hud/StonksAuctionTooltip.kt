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

    // number with optional commas/decimals and k/m/b suffix
    private const val NUM = """(\d[\d,]*(?:\.\d+)?)\s*([kKmMbB])?"""
    private val BID = Regex("""bid[^\d]*$NUM""", RegexOption.IGNORE_CASE)
    private val AMOUNT = listOf(
        Regex("""(?:^|\s)x\s*(\d[\d,]*)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(\d[\d,]*)\s*x\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(\d[\d,]*)\s+stonks\b""", RegexOption.IGNORE_CASE),
        Regex("""amount[^\d]*(\d[\d,]*)""", RegexOption.IGNORE_CASE),
    )

    @JvmStatic
    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!FishSettings.stonksAuctionPricePer || stack.isEmpty || lines.isEmpty() || !SkyblockLocation.inSkyblock()) return@ItemTooltipCallback
            try {
                val screen = Minecraft.getInstance().curScreen ?: return@ItemTooltipCallback
                if (!ScreenTitle.plain(screen).contains("Stonks Auction", ignoreCase = true)) return@ItemTooltipCallback
                val plain = lines.map { FishConstants.STRIP_COLOR_REGEX.replace(it.string, "") }
                var amount = 0L
                for (i in plain.indices) {
                    val l = plain[i]
                    val m = BID.find(l)
                    if (m == null) {
                        AMOUNT.firstNotNullOfOrNull { it.find(l) }?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()?.let { amount = it }
                        continue
                    }
                    val bid = parse(m.groupValues[1], m.groupValues[2]) ?: continue
                    if (bid <= 0.0 || amount <= 0L) continue
                    lines[i] = lines[i].copy().append(Component.literal(" §7(§6${abbr(bid / amount)} §7per Stonk)"))
                }
            } catch (e: Exception) {
                FishDiag.fail("StonksAuctionTooltip.1", "stonks auction tooltip failed", e)
            }
        })
    }

    // "1.5" + "m" -> 1500000
    private fun parse(n: String, suffix: String): Double? {
        val v = n.replace(",", "").toDoubleOrNull() ?: return null
        return v * when (suffix.lowercase()) { "k" -> 1e3; "m" -> 1e6; "b" -> 1e9; else -> 1.0 }
    }
}
