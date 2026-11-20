package fishmod.features.extras

import fishmod.features.FishHudEditor
import fishmod.features.diana.DianaTracker
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import fishmod.features.extras.ExtrasSettings as S

// Session tracker for opened winter gifts: count by tier, coins and North Stars
object GiftTracker {
    // from SkyHanni (GiftProfitTracker), colour codes stripped
    private val GIFT = Regex("""^(COMMON|RARE|SWEET|SANTA TIER|PARTY TIER)! (.+) gift with .+!$""")
    private val COINS = Regex("""^\+([\d,]+) Coins$""")
    private val STARS = Regex("""^EXTRA! \+([\d,]+) North Stars?$""")

    private val tiers = LinkedHashMap<String, Int>()
    private var coins = 0L
    private var stars = 0L
    private var lastSeen = 0L

    fun init() {
        FishEvents.chat.register { text ->
            if (!S.giftTracker) return@register false
            val s = text.chatPlain.trim()
            GIFT.find(s)?.let {
                val t = it.groupValues[1].removeSuffix(" TIER")
                tiers[t] = (tiers[t] ?: 0) + 1
                COINS.find(it.groupValues[2])?.let { c -> coins += c.groupValues[1].replace(",", "").toLong() }
                lastSeen = System.currentTimeMillis()
            }
            STARS.find(s)?.let { stars += it.groupValues[1].replace(",", "").toLong(); lastSeen = System.currentTimeMillis() }
            false
        }
        FishHudEditor.register("Gift Tracker", { S.giftHudX }, { S.giftHudX = it }, { S.giftHudY }, { S.giftHudY = it }, 140, 40,
            { S.giftHudScale }, { S.giftHudScale = it })
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "gift_tracker_hud")) { ctx, _ ->
            try { if (!FishHudEditor.isOpen()) render(ctx) } catch (t: Throwable) { FishDiag.fail("GiftTracker.1", "gift hud render failed", t) }
        }
    }

    private fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor) {
        if (!S.giftTracker || !SkyblockLocation.inSkyblock() || tiers.isEmpty()) return
        if (System.currentTimeMillis() - lastSeen > 120_000L) return
        val lines = mutableListOf("§cGifts opened: §f" + tiers.values.sum())
        tiers.forEach { (t, n) -> lines += "§7$t: §f$n" }
        if (coins > 0) lines += "§6Coins: §f" + DianaTracker.short(coins.toDouble())
        if (stars > 0) lines += "§dNorth Stars: §f$stars"
        val mc = Minecraft.getInstance()
        val sc = S.giftHudScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(S.giftHudX.toFloat(), S.giftHudY.toFloat())
            ctx.pose().scale(sc, sc)
            lines.forEachIndexed { i, l -> ctx.text(mc.font, Component.literal(l), 0, i * 10, S.giftHudColor, true) }
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
