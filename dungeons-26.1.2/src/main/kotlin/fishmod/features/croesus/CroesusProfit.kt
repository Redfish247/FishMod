package fishmod.features.croesus

import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Croesus chest profit. On the Croesus chest-preview screen it values every chest's contents
 * (via [CroesusPrices]) minus its coin cost, highlights the two most profitable slots, and lists
 * each chest's profit beside the GUI.
 */
object CroesusProfit {

    private val PREVIEW_TITLE = Regex("^(?:Master )?Catacombs - .*")
    private val COST = Regex("([\\d,]+) Coins?")
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    private class Chest(val slot: Int, val name: String, val value: Double, val cost: Double) {
        val profit get() = value - cost
    }

    private var chests: List<Chest> = emptyList()
    private var bestSlots: List<Int> = emptyList()

    private fun on(screen: AbstractContainerScreen<*>): Boolean =
        FishSettings.croesusProfitEnabled && PREVIEW_TITLE.matches(screen.title.string)

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>) {
        if (!on(screen)) {
            if (chests.isNotEmpty()) { chests = emptyList(); bestSlots = emptyList() }
            return
        }
        CroesusPrices.refreshIfStale()
        scan(screen)
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
        for (c in chests.sortedByDescending { it.profit }) {
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
            if (tooltip.none { COLOR.replace(it, "").contains("Contents") }) continue

            val cost = tooltip.firstNotNullOfOrNull {
                COST.find(COLOR.replace(it, ""))?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            } ?: 0.0
            val info = CroesusRewardParser.parseRewards(tooltip, null) ?: continue
            var value = 0.0
            for (ri in info.items) value += CroesusPrices.price(ri.id) * ri.qty.coerceAtLeast(1)
            out.add(Chest(i, COLOR.replace(stack.hoverName.string, "").trim(), value, cost))
        }
        chests = out
        bestSlots = out.filter { it.profit > 0 }.sortedByDescending { it.profit }.take(2).map { it.slot }
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
