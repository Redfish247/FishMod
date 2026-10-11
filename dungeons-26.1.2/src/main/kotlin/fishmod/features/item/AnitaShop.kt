package fishmod.features.item

import fishmod.features.croesus.CroesusPrices
import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.Misc.abbr
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.networth.ItemsDb
import fishmod.utils.rendering.DrawEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

// Anita's shop: coins per bronze medal for each item (gold = 8, silver = 2), best one highlighted (SkyHanni AnitaMedalProfit).
object AnitaShop {

    private class Row(val slot: Int, val name: String, val perBronze: Double, val color: Int)

    private val CODE = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val MEDAL = Regex("^(Gold|Silver|Bronze) [Mm]edal(?: x([\\d,]+))?$")
    private val AMOUNT = Regex("^(.+?) x([\\d,]+)$")
    private val ROMAN = Regex("^(.+) (I|II|III|IV|V|VI|VII|VIII|IX|X)$")
    private val FACTOR = mapOf("Gold" to 8, "Silver" to 2, "Bronze" to 1)
    private const val TITLE = "Anita"
    private const val RECOMPUTE_MS = 500L
    private const val MAX_LINES = 20

    private var rows: List<Row> = emptyList()
    private var bestSlot = -1
    private var lastCompute = 0L

    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_BEFORE.register { ctx, stack, x, y ->
            if (!FishSettings.anitaProfitEnabled || bestSlot < 0 || stack.isEmpty) return@register
            val scr = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return@register
            if (fishmod.utils.ScreenTitle.plain(scr).trim() != TITLE) return@register
            val slot = DrawEvents.currentSlot ?: return@register
            if (slot.index == bestSlot && slot.container !is Inventory) ctx.fill(x, y, x + 16, y + 16, 0x8055FF55.toInt())
        }
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>) {
        if (!FishSettings.anitaProfitEnabled) return
        if (fishmod.utils.ScreenTitle.plain(screen).trim() != TITLE) { bestSlot = -1; return }
        FishDiag.guard("AnitaShop.1", "anita shop recompute failed") { recompute(screen) }
        if (rows.isEmpty()) return

        val font = Minecraft.getInstance().font
        val shown = rows.take(MAX_LINES)
        val valW = shown.maxOf { font.width(abbr(it.perBronze)) }
        val w = maxOf(font.width("Profit per Bronze Medal"), shown.maxOf { valW + font.width(" | ${it.name} ✔") })
        val acc = screen as HandledScreenAccessor
        val left = acc.bgX - w - 6
        val x = if (left >= 2) left else acc.bgX + acc.bgWidth + 6
        var y = acc.bgY
        ctx.text(font, "§eProfit per Bronze Medal", x, y, -1, true); y += font.lineHeight + 1
        for ((i, r) in shown.withIndex()) {
            val v = abbr(r.perBronze)
            ctx.text(font, (if (r.perBronze > 0) "§6" else "§c") + v, x + valW - font.width(v), y, -1, true)
            val nx = x + valW
            ctx.text(font, "§7 | ", nx, y, -1, true)
            val tx = nx + font.width(" | ")
            ctx.text(font, r.name, tx, y, 0xFF000000.toInt() or r.color, true)
            if (i == 0 && r.perBronze > 0) ctx.text(font, "§a ✔", tx + font.width(r.name), y, -1, true)
            y += font.lineHeight + 1
        }
    }

    private fun recompute(screen: AbstractContainerScreen<*>) {
        val now = System.currentTimeMillis()
        if (now - lastCompute < RECOMPUTE_MS) return
        lastCompute = now
        CroesusPrices.refreshIfStale()

        val out = ArrayList<Row>()
        for (slot in screen.menu.slots) {
            if (slot.container is Inventory) continue
            val stack = slot.item
            if (stack.isEmpty) continue
            val lore = stack.get(DataComponents.LORE)?.lines()?.map { CODE.replace(it.string, "").trim() } ?: continue
            if (lore.none { it == "Click to trade!" }) continue
            val head = lore.indexOfLast { it == "Cost" || it == "Cost:" }
            if (head < 0) continue
            var bronze = 0L
            var extra = 0.0
            for (l in lore.drop(head + 1).takeWhile { it.isNotEmpty() }) {
                val m = MEDAL.find(l)
                if (m != null) { bronze += FACTOR.getValue(m.groupValues[1]) * (m.groupValues[2].replace(",", "").toLongOrNull() ?: 1L); continue }
                val am = AMOUNT.find(l)
                val n = am?.groupValues?.get(1) ?: l
                val k = am?.groupValues?.get(2)?.replace(",", "")?.toLongOrNull() ?: 1L
                extra += price(ItemsDb.idFor(n) ?: if (n == "Jacob's Ticket") "JACOBS_TICKET" else null) * k
            }
            if (bronze <= 0) continue
            val name = CODE.replace(stack.hoverName.string, "").trim()
            val am = AMOUNT.find(name)
            val count = am?.groupValues?.get(2)?.replace(",", "")?.toLongOrNull() ?: stack.count.toLong()
            val value = itemValue(stack, am?.groupValues?.get(1) ?: name, lore) * count
            if (value <= 0) continue
            out.add(Row(slot.index, name, (value - extra) / bronze, fishmod.features.ItemRarityHotbar.getRarity(stack).color.takeIf { it != 0 } ?: 0xFFFFFF))
        }
        rows = out.sortedByDescending { it.perBronze }
        bestSlot = rows.firstOrNull()?.takeIf { it.perBronze > 0 }?.slot ?: -1
    }

    private fun price(id: String?): Double = if (id == null) 0.0 else CroesusPrices.price(id).takeIf { !it.isNaN() } ?: 0.0

    // Books show as "Enchanted Book" with the enchant on the first lore line
    private fun itemValue(stack: ItemStack, name: String, lore: List<String>): Double {
        val id = ItemUtil.getId(stack) ?: ItemsDb.idFor(name)
        if (id != null && id != "ENCHANTED_BOOK") return price(id)
        val ench = lore.firstOrNull { ROMAN.matches(it) } ?: return 0.0
        val m = ROMAN.find(ench)!!
        val lvl = fishmod.utils.data.Roman.toInt(m.groupValues[2])
        val base = m.groupValues[1].uppercase().replace(' ', '_').replace("'", "")
        for (cand in listOf("ENCHANTMENT_${base}_$lvl", "ENCHANTMENT_ULTIMATE_${base}_$lvl")) price(cand).takeIf { it > 0 }?.let { return it }
        return 0.0
    }
}
