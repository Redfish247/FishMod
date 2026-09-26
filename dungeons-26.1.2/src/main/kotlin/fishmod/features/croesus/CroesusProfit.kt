package fishmod.features.croesus

import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object CroesusProfit {

    private val PREVIEW_TITLE = Regex("^(?:Master )?Catacombs - .*")
    private val COST = Regex("([\\d,]+) Coins?")
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    private class Chest(val slot: Int, val name: String, val value: Double, val cost: Double) {
        val profit get() = value - cost
    }

    private var chests: List<Chest> = emptyList()
    private var bestSlots: List<Int> = emptyList()

    private const val SCAN_INTERVAL_MS = 300L
    private var lastScanMs = 0L

    private var lastTitle: net.minecraft.network.chat.Component? = null
    private var lastTitleMatch = false

    private fun on(screen: AbstractContainerScreen<*>): Boolean {
        if (!FishSettings.croesusProfitEnabled) return false
        val title = screen.title
        if (title !== lastTitle) {
            lastTitle = title
            lastTitleMatch = PREVIEW_TITLE.matches(title.string)
        }
        return lastTitleMatch
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>) {
        if (!on(screen)) {
            if (chests.isNotEmpty()) { chests = emptyList(); bestSlots = emptyList() }
            return
        }
        CroesusPrices.refreshIfStale()
        val now = System.currentTimeMillis()
        if (now - lastScanMs >= SCAN_INTERVAL_MS) {
            lastScanMs = now
            scan(screen)
        }
        if (chests.isEmpty()) return

        val acc = screen as HandledScreenAccessor
        for ((rank, slotIdx) in bestSlots.withIndex()) {
            val s = screen.menu.slots.getOrNull(slotIdx) ?: continue
            val x = acc.bgX + s.x
            val y = acc.bgY + s.y
            ctx.fill(x, y, x + 16, y + 16, if (rank == 0) 0x8000AA00.toInt() else 0x80FFFF55.toInt())
        }

        val font = Minecraft.getInstance().font
        val lx = acc.bgX - 150
        var ly = acc.bgY + 4
        for (c in chests) {
            val pc = if (c.profit >= 0) "§a" else "§c"
            ctx.text(font, "§e${c.name}§7: $pc${fmt(c.profit)}", lx, ly, -1, true)
            ly += font.lineHeight + 1
        }
    }

    private fun scan(screen: AbstractContainerScreen<*>) {
        val menu = screen.menu
        val out = ArrayList<Chest>(6)
        for (i in menu.slots.indices) {
            if (i > 16) break
            val stack = menu.slots[i].item
            if (stack.isEmpty || !stack.`is`(Items.PLAYER_HEAD)) continue
            val tooltip = tooltip(stack)
            val plain = tooltip.map { COLOR.replace(it, "") }
            if (plain.none { it.contains("Contents") }) continue

            val cost = plain.firstNotNullOfOrNull {
                COST.find(it)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            } ?: 0.0
            val info = CroesusRewardParser.parseRewards(tooltip, null) ?: continue
            var value = 0.0
            for (ri in info.items) value += CroesusPrices.price(ri.id) * ri.qty.coerceAtLeast(1)
            out.add(Chest(i, COLOR.replace(stack.hoverName.string, "").trim(), value, cost))
        }
        out.sortByDescending { it.profit }
        chests = out
        bestSlots = out.asSequence().filter { it.profit > 0 }.take(2).map { it.slot }.toList()
    }

    private fun tooltip(stack: ItemStack): MutableList<String> {
        val out = ArrayList<String>()
        out.add(stack.hoverName.string)
        stack.get(DataComponents.LORE)?.lines()?.forEach { out.add(it.string) }
        return out
    }

    private fun fmt(v: Double): String {
        val a = Math.abs(v)
        val s = when {
            a >= 1e9 -> "%.2fb".format(v / 1e9)
            a >= 1e6 -> "%.2fm".format(v / 1e6)
            a >= 1e3 -> "%.0fk".format(v / 1e3)
            else -> "%.0f".format(v)
        }
        return s
    }
}
