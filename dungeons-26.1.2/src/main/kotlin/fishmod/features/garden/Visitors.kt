package fishmod.features.garden

import fishmod.features.mining.Mining
import fishmod.utils.FishMsg
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import fishmod.features.garden.GardenSettings as S

// Visitor timer, arrival alert, shopping list, menu info, rare reward warning, drop stats
object Visitors {

    private val ARRIVED = Regex("""^(.+?) has arrived on your Garden!$""")
    private val ACCEPTED = Regex("""^OFFER ACCEPTED with (.+?) \((\w+)\)""")
    private val COUNT = Regex("""Visitors: \((\d+)\)""")
    private val REQ = Regex("""^(.+?)(?: x([\d,]+))?$""")
    private val COPPER = Regex("""^\+([\d,]+) Copper""")
    private val GXP = Regex("""^\+([\d,]+) Garden Experience""")

    class Req(val name: String, val amount: Int)

    var count = 0; private set
    var nextVisitor = ""; private set
    private var names: List<String> = emptyList()
    private val shopping = LinkedHashMap<String, List<Req>>()
    private var fullWarned = false
    private var rewardUntil = 0L
    private var warnedScreen: Any? = null
    private var tick = 0

    fun init() {
        Events.ON_GAME_MESSAGE.register { text -> onChat(Garden.strip(text.string).trim()) }
        Events.ON_WORLD_CHANGE.register { shopping.clear(); false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || tick++ % 10 != 0) return@register
            if (!Garden.inGarden()) { count = 0; nextVisitor = ""; return@register }
            count = Mining.tabLine("Visitors:")?.let { COUNT.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
            nextVisitor = Mining.tabLine("Next Visitor:")?.substringAfter(":")?.trim() ?: ""
            names = Mining.tabWidget("Visitors:")
            if (names.isNotEmpty()) shopping.keys.retainAll { k -> names.any { it.contains(k) } }
            else if (count == 0 && nextVisitor.isNotEmpty()) shopping.clear()
            val full = nextVisitor.contains("Full", true)
            if (full && !fullWarned && S.gardenVisitorSixthWarn && S.gardenVisitorTimer) GardenTitles.show(Garden.legacy(S.gardenVisitorFullColor) + "Visitor Queue Full!", "", 2000, true)
            fullWarned = full
            scanMenu(mc)
        }
        Garden.hud("Visitor Timer", "garden_visitor_timer", 140, 20,
            { S.gardenVisitorTimerHudX }, { S.gardenVisitorTimerHudX = it }, { S.gardenVisitorTimerHudY }, { S.gardenVisitorTimerHudY = it },
            { S.gardenVisitorTimerHudScale }, { S.gardenVisitorTimerHudScale = it },
            { S.gardenVisitorTimer && Garden.inGarden() && nextVisitor.isNotEmpty() }, { S.gardenVisitorTimer }) {
            listOf("§aVisitors: §f$count  §7Next: " + (if (nextVisitor.contains("Full", true)) "§cQueue Full" else "§b$nextVisitor"))
        }
        Garden.hud("Visitor Stats", "garden_visitor_stats", 140, 50,
            { S.gardenVisitorStatsHudX }, { S.gardenVisitorStatsHudX = it }, { S.gardenVisitorStatsHudY }, { S.gardenVisitorStatsHudY = it },
            { S.gardenVisitorStatsHudScale }, { S.gardenVisitorStatsHudScale = it },
            { S.gardenVisitorStats && Garden.inGarden() }, { S.gardenVisitorStats }) {
            listOf(Garden.legacy(S.gardenVisitorStatsColor) + "§lVisitor Stats", "§7Accepted: §f${S.gardenStatAccepted}", "§7Copper: §c${"%,d".format(S.gardenStatCopper)}",
                "§7Garden XP: §2${"%,d".format(S.gardenStatGardenXp)}", "§7Rare Rewards: §d${S.gardenStatRare}")
        }

        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines -> tooltip(stack, lines) })
        DrawEvents.INVENTORY_SLOT_BEFORE.register { ctx, stack, x, y ->
            if (!S.gardenRareRewardWarn || stack.isEmpty || !isVisitorMenu()) return@register
            if (Garden.strip(stack.hoverName.string) == "Accept Offer" && rareIn(Garden.lore(stack)) != null)
                ctx.fill(x, y, x + 16, y + 16, Mining.alpha(S.gardenRareRewardColor, 60))
        }
    }

    private fun onChat(s: String): Boolean {
        ARRIVED.find(s)?.let { m ->
            if (!Garden.inGarden()) return false
            if (S.gardenVisitorArrivalTitle) GardenTitles.show(S.gardenVisitorArrivalText.replace("{name}", m.groupValues[1]), "", 1500, S.gardenVisitorArrivalSound)
            return S.gardenVisitorHideArrival
        }
        ACCEPTED.find(s)?.let { m ->
            shopping.remove(m.groupValues[1])
            S.gardenStatAccepted++
            rewardUntil = System.currentTimeMillis() + 2000
            return false
        }
        if (System.currentTimeMillis() < rewardUntil) {
            COPPER.find(s)?.let { S.gardenStatCopper += num(it.groupValues[1]) }
            GXP.find(s)?.let { S.gardenStatGardenXp += num(it.groupValues[1]) }
            if (rareIn(listOf(s)) != null) S.gardenStatRare++
        }
        return false
    }

    private fun num(s: String) = s.replace(",", "").toIntOrNull() ?: 0

    private fun isVisitorMenu(): Boolean {
        val scr = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return false
        return Garden.inGarden() && scr.menu.slots.any { !it.item.isEmpty && Garden.strip(it.item.hoverName.string) == "Accept Offer" }
    }

    private fun acceptButton(): ItemStack? {
        val scr = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return null
        return scr.menu.slots.firstOrNull { !it.item.isEmpty && Garden.strip(it.item.hoverName.string) == "Accept Offer" }?.item
    }

    // Reads the visitor's request once per menu open
    private fun scanMenu(mc: Minecraft) {
        val scr = mc.screen as? AbstractContainerScreen<*>
        if (scr == null) { warnedScreen = null; return }
        if (!Garden.inGarden()) return
        val btn = acceptButton() ?: return
        val lore = Garden.lore(btn)
        val reqs = required(lore)
        if (reqs.isNotEmpty()) shopping[Garden.strip(scr.title.string).trim()] = reqs
        if (warnedScreen !== scr) {
            warnedScreen = scr
            val rare = rareIn(lore)
            if (rare != null && S.gardenRareRewardWarn) {
                val rc = GardenAlerts.rarityColor(rare, rare.replace(Regex("\\s*x[\\d,]+$"), ""), rare.replace(Regex("^[\\d,]+x?\\s+"), "")) ?: "§d"
                GardenAlerts.show(rc + "§lRARE REWARD!", rc + rare)
                FishMsg.send("§dThis visitor offers §f$rare§d!")
            }
        }
    }

    private fun required(lore: List<String>): List<Req> {
        val out = ArrayList<Req>()
        var inside = false
        for (l in lore) {
            val t = l.trim()
            if (!inside) { if (t.startsWith("Items Required")) inside = true; continue }
            if (t.isEmpty()) break
            val m = REQ.find(t) ?: continue
            out += Req(m.groupValues[1].trim(), m.groupValues[2].takeIf { it.isNotEmpty() }?.let(::num) ?: 1)
        }
        return out
    }

    private fun rewards(lore: List<String>): List<String> {
        val out = ArrayList<String>()
        var inside = false
        for (l in lore) {
            val t = l.trim()
            if (!inside) { if (t.startsWith("Rewards")) inside = true; continue }
            if (t.isEmpty()) break
            out += t
        }
        return out
    }

    private fun rareIn(lines: List<String>): String? {
        val list = S.gardenRareRewardList.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val rew = rewards(lines).ifEmpty { lines }
        for (l in rew) for (r in list) if (l.contains(r, true)) return l.trim().removePrefix("+").trim()
        return null
    }

    private fun tooltip(stack: ItemStack, lines: MutableList<Component>) {
        if (!S.gardenVisitorMenuInfo || stack.isEmpty || !Garden.inGarden()) return
        if (Garden.strip(stack.hoverName.string) != "Accept Offer") return
        val lore = Garden.lore(stack)
        val reqs = required(lore)
        if (reqs.isEmpty()) return
        var total = 0.0
        lines += Component.literal("")
        lines += Component.literal("§6§lFishMod Visitor Info")
        for (r in reqs) {
            val p = Garden.priceOf(r.name) * r.amount
            total += p
            lines += Component.literal("§7${r.name} §8x${r.amount}§7: §6${Garden.short(p)} §8(have ${have(r.name)})")
        }
        lines += Component.literal("§7Total cost: §6${Garden.short(total)}")
        val copper = rewards(lore).firstNotNullOfOrNull { COPPER.find(it) }?.let { num(it.groupValues[1]) } ?: 0
        if (copper > 0) lines += Component.literal("§7Copper: §c$copper §8(${Garden.short(total / copper)} coins/copper)")
    }

    private fun have(name: String): Int {
        val inv = Minecraft.getInstance().player?.inventory ?: return 0
        val id = fishmod.utils.networth.ItemsDb.idFor(name)
        var n = 0
        for (i in 0 until inv.containerSize) {
            val st = inv.getItem(i)
            if (st.isEmpty) continue
            if ((id != null && id.equals(ItemUtil.getId(st), true)) || Garden.strip(st.hoverName.string) == name) n += st.count
        }
        return n
    }

    private fun L(c: Int) = Garden.legacy(c)
    private fun hint() = L(S.gardenShoppingHintColor) + "Click: open + copy amount"

    private fun shoppingLines(): List<String> {
        val agg = LinkedHashMap<String, Int>()
        for (reqs in shopping.values) for (r in reqs) agg[r.name] = (agg[r.name] ?: 0) + r.amount
        val out = arrayListOf(Garden.legacy(S.gardenShoppingColor) + "§lShopping List")
        var total = 0.0
        for ((n, a) in agg) {
            val h = have(n)
            val c = if (h >= a) "§a" else L(S.gardenShoppingNameColor)
            var line = "$c$n ${L(S.gardenShoppingAmountColor)}x${"%,d".format(a)}"
            if (h > 0) line += " §7($h)"
            if (S.gardenShoppingPrices) { val p = Garden.priceOf(n) * a; total += p; line += " ${L(S.gardenShoppingPriceColor)}${Garden.short(p)}" }
            out += line
        }
        if (S.gardenShoppingPrices) out += "${L(S.gardenShoppingTotalColor)}Total: ${L(S.gardenShoppingPriceColor)}${Garden.short(total)}"
        return out
    }

    fun clear() = shopping.clear()

    // Shopping list lives in the Bazaar menu; clicking an entry opens it in /bz and copies the amount
    private class Hit(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val name: String, val amount: Int)
    private var hits: List<Hit> = emptyList()

    private fun isBazaar(screen: AbstractContainerScreen<*>): Boolean {
        val t = Garden.strip(screen.title.string)
        return t.contains("Bazaar") || t.contains("➜")
    }

    @JvmStatic
    fun renderShopping(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, screen: AbstractContainerScreen<*>) {
        hits = emptyList()
        if (!S.gardenShoppingList || shopping.isEmpty() || !isBazaar(screen)) return
        val font = Minecraft.getInstance().font
        val agg = LinkedHashMap<String, Int>()
        for (reqs in shopping.values) for (r in reqs) agg[r.name] = (agg[r.name] ?: 0) + r.amount
        val rows = ArrayList<Triple<String, String, Int>>()
        var total = 0.0
        for ((n, a) in agg) {
            val h = have(n)
            var line = (if (h >= a) "§a" else L(S.gardenShoppingNameColor)) + n + " ${L(S.gardenShoppingAmountColor)}x${"%,d".format(a)}"
            if (h > 0) line += " §7($h)"
            if (S.gardenShoppingPrices) { val p = Garden.priceOf(n) * a; total += p; line += " ${L(S.gardenShoppingPriceColor)}${Garden.short(p)}" }
            rows.add(Triple(n, line, maxOf(if (h in 1 until a) a - h else a, 1)))
        }
        val head = Garden.legacy(S.gardenShoppingColor) + "§lShopping List"
        val foot = if (S.gardenShoppingPrices) "${L(S.gardenShoppingTotalColor)}Total: ${L(S.gardenShoppingPriceColor)}${Garden.short(total)}" else null
        val w = (listOf(head, foot ?: "") + rows.map { it.second } + hint()).maxOf { font.width(Component.literal(it)) }
        val lh = font.lineHeight + 1
        val acc = screen as fishmod.mixin.accessors.HandledScreenAccessor
        val x = (acc.bgX - w - 6).coerceAtLeast(2)
        var y = acc.bgY.coerceAtLeast(2)
        ctx.text(font, Component.literal(head), x, y, -1, true); y += lh
        val out = ArrayList<Hit>()
        val mc = Minecraft.getInstance()
        val mx = mc.mouseHandler.xpos() * mc.window.guiScaledWidth / mc.window.screenWidth
        val my = mc.mouseHandler.ypos() * mc.window.guiScaledHeight / mc.window.screenHeight
        for ((n, line, amt) in rows) {
            val hover = mx >= x && mx <= x + w && my >= y && my < y + lh
            if (hover) ctx.fill(x - 2, y - 1, x + w + 2, y + lh - 1, 0x40FFFFFF)
            ctx.text(font, Component.literal(line), x, y, -1, true)
            out.add(Hit(x, y, x + w, y + lh, n, amt)); y += lh
        }
        if (foot != null) { ctx.text(font, Component.literal(foot), x, y, -1, true); y += lh }
        ctx.text(font, Component.literal(hint()), x, y, -1, true)
        hits = out
    }

    @JvmStatic
    fun mouseClicked(click: net.minecraft.client.input.MouseButtonEvent, screen: AbstractContainerScreen<*>): Boolean {
        if (click.button() != 0 || hits.isEmpty() || !isBazaar(screen)) return false
        val h = hits.firstOrNull { click.x() >= it.x0 && click.x() <= it.x1 && click.y() >= it.y0 && click.y() < it.y1 } ?: return false
        val mc = Minecraft.getInstance()
        mc.keyboardHandler.setClipboard(h.amount.toString())
        mc.player?.connection?.sendCommand("bz ${h.name}")
        FishMsg.send("§7Copied §f${h.amount} §7- search opened for §f${h.name}")
        return true
    }
}
