package fishmod.features.diana

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

// Table of saved Diana events (current one first)
class DianaPastEventsScreen : Screen(Component.literal("Diana Past Events")) {

    private val cols = listOf("Year", "Burrows", "Mobs", "Inqs", "Chimeras", "Playtime", "Profit")
    private var scroll = 0

    override fun isPauseScreen(): Boolean = false

    private fun rows(): List<List<String>> {
        val cur = DianaTracker.event
        val list = ArrayList<List<String>>()
        list += row("${cur.year} §a(now)", cur.items, cur.mobs, cur.timeMs, DianaTracker.profit(cur).toLong())
        for (e in DianaTracker.pastEvents.reversed()) list += row("${e.year}", e.items, e.mobs, e.timeMs, e.profit)
        return list
    }

    private fun row(year: String, items: Map<String, Long>, mobs: Map<String, Long>, time: Long, profit: Long): List<String> {
        val chim = (items["CHIMERA"] ?: 0L)
        val chimLs = (items["CHIMERA_LS"] ?: 0L)
        return listOf(
            year,
            "%,d".format(items["TOTAL_BURROWS"] ?: 0L),
            "%,d".format(mobs["TOTAL_MOBS"] ?: 0L),
            "${mobs["MINOS_INQUISITOR"] ?: 0L}",
            "$chim" + if (chimLs > 0) " §7+$chimLs LS" else "",
            DianaTracker.fmtTime(time),
            "§6" + DianaTracker.short(profit.toDouble()),
        )
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(ctx, mouseX, mouseY, delta)
        val colW = ((width - 40) / cols.size).coerceAtLeast(50)
        val left = (width - colW * cols.size) / 2
        ctx.centeredText(font, "§e§lDiana Past Events", width / 2, 12, -1)
        ctx.fill(left - 4, 30, left + colW * cols.size + 4, height - 20, 0x90000000.toInt())
        cols.forEachIndexed { i, c -> ctx.text(font, "§e$c", left + i * colW, 36, -1, true) }
        val data = rows()
        val visible = ((height - 70) / 12).coerceAtLeast(1)
        scroll = scroll.coerceIn(0, (data.size - visible).coerceAtLeast(0))
        data.drop(scroll).take(visible).forEachIndexed { r, row ->
            row.forEachIndexed { i, v -> ctx.text(font, "§f$v", left + i * colW, 52 + r * 12, -1, true) }
        }
        if (data.size == 1 && DianaTracker.pastEvents.isEmpty()) {
            ctx.centeredText(font, "§7No past events saved yet.", width / 2, 52 + 2 * 12, -1)
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll -= verticalAmount.toInt().coerceIn(-1, 1)
        return true
    }
}
