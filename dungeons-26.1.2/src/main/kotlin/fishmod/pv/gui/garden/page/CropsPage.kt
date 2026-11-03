/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.GardenCosts
import fishmod.pv.data.GardenData
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.garden.GardenUi
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_COMPOST
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_CROP
import fishmod.pv.gui.garden.GardenUi.Companion.COMPLETED_C
import fishmod.pv.gui.garden.GardenUi.Companion.COPPER
import fishmod.pv.gui.garden.GardenUi.Companion.GAP
import fishmod.pv.gui.garden.GardenUi.Companion.ICON
import fishmod.pv.gui.garden.GardenUi.Companion.PAD
import fishmod.pv.gui.garden.GardenUi.Companion.REJECTED_C
import fishmod.pv.gui.garden.GardenUi.Companion.STAT_ROW
import fishmod.pv.price.ItemPricer
import java.util.Locale
import java.util.function.ToDoubleFunction
import java.util.function.ToIntFunction
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Garden crops + composter subpage. */
class CropsPage {
	private var scroll = 0
	private var maxScroll = 0
	private var scrollTop = 0
	private var scrollH = 0

	fun render(snap: GardenSnapshot, ui: GardenUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
		val leftW = maxOf(140, w * 40 / 100)
		val rightW = w - leftW - GAP
		PvDraw.innerPanel(g, x, y, leftW, h)
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h)

		val lx = x + PAD
		var ly = y + PAD
		val lw = leftW - PAD * 2

		val crops = snap.crops
		PvDraw.text(g, font, "Crop milestones", lx, ly, PvDraw.COLOR_MUTED)
		if (crops.isNotEmpty()) {
			val sum = crops.sumOf { it.milestone.toLong() }
			val avg = "Average: " + FormatUtil.oneDecimal(sum / crops.size.toDouble())
			if (font.width("Crop milestones") + 10 + font.width(avg) <= lw) {
				PvDraw.textRight(g, font, avg, lx + lw, ly, PvDraw.COLOR_MUTED)
			}
		}
		ly += font.lineHeight + 4

		// Readable 2-col Overview-style rows; last odd item centered; row gap fills panel height.
		val cols = 2
		val colGap = GAP
		val colW = (lw - colGap) / cols
		val miniBarH = 3
		val labelGap = 2
		val blockH = font.lineHeight + labelGap + miniBarH
		val minRowH = maxOf(ICON, blockH) + 2
		val gridRows = if (crops.isEmpty()) 0 else (crops.size + cols - 1) / cols
		val gridTop = ly
		val gridH = y + h - PAD - gridTop
		val rowH = if (gridRows <= 0) minRowH else maxOf(minRowH, gridH / gridRows)
		scrollTop = gridTop
		scrollH = gridH
		maxScroll = 0
		scroll = 0

		g.enableScissor(lx, gridTop, lx + lw + 3, gridTop + gridH)
		val lastRow = maxOf(0, gridRows - 1)
		val lastRowCount = if (crops.isEmpty()) 0 else ((crops.size - 1) % cols) + 1
		for ((i, crop) in crops.withIndex()) {
			val col = i % cols
			val row = i / cols
			val rowItems = if (row == lastRow) lastRowCount else cols
			val rowW = rowItems * colW + maxOf(0, rowItems - 1) * colGap
			val rowX = lx + maxOf(0, (lw - rowW) / 2)
			val bx = rowX + col * (colW + colGap)
			val by = gridTop + row * rowH
			if (by + rowH < gridTop || by > gridTop + gridH) {
				continue
			}
			val textX = bx + ICON + 3
			val barW = maxOf(24, colW - ICON - 3)
			val iconY = by + maxOf(0, (blockH - ICON) / 2)
			GardenUi.drawIcon(g, crop.iconId, bx, iconY, ICON, GardenData.cropPackModel(crop.id))
			val lvl = crop.milestone.toString()
			val nameMax = maxOf(8, barW - font.width(lvl) - 4)
			PvDraw.text(g, font, GardenUi.trim(font, crop.name, nameMax), textX, by, PvDraw.COLOR_TEXT)
			PvDraw.textRight(g, font, lvl, textX + barW, by, if (crop.milestoneMaxed) COMPLETED_C else PvDraw.COLOR_MUTED)
			PvDraw.progressBar(g, textX, by + font.lineHeight + labelGap, barW, miniBarH, crop.milestoneFill, BAR_CROP, crop.milestoneMaxed)
			ui.zones.add(GardenUi.HoverZone(bx, by, colW, blockH + 1, ui.cropMilestoneTip(snap, crop)))
		}
		g.disableScissor()

		val c = snap.composter
		val rx = x + leftW + GAP + PAD
		var ry = y + PAD
		val rw = rightW - PAD * 2
		PvDraw.text(g, font, "Composter", rx, ry, PvDraw.COLOR_MUTED)
		ry += font.lineHeight + 4

		val cColGap = 8
		val cColW = (rw - cColGap) / 2
		val leftCol = rx
		val rightCol = rx + cColW + cColGap
		val row1 = ry
		val row2 = ry + STAT_ROW + 1
		GardenUi.statLine(g, font, "Organic Matter", FormatUtil.shortXp(c.organicMatter.toDouble()), leftCol, row1, cColW, PvDraw.COLOR_TEXT)
		GardenUi.statLine(g, font, "Fuel", FormatUtil.shortXp(c.fuelUnits.toDouble()), leftCol, row2, cColW, PvDraw.COLOR_TEXT)
		GardenUi.statLine(g, font, "Compost", FormatUtil.shortXp(c.compostUnits.toDouble()), rightCol, row1, cColW, BAR_COMPOST)
		GardenUi.statLine(g, font, "Items", FormatUtil.shortXp(c.compostItems.toDouble()), rightCol, row2, cColW, PvDraw.COLOR_MUTED)
		ry = row2 + STAT_ROW + 4

		PvDraw.fill(g, rx, ry, rw, 1, PvDraw.COLOR_DIVIDER)
		ry += 5

		val mapAvailH = maxOf(0, y + h - PAD - (ry + font.lineHeight + 4))
		val mapCell = maxOf(10, minOf(16, (minOf(mapAvailH, rw * 2 / 5) - PLOT_GAP * 4) / 5))
		val mapW = mapCell * 5 + PLOT_GAP * 4
		val showMap = GardenCosts.plots().isNotEmpty() && rw - mapW - GAP >= 110 && mapAvailH >= mapW
		val listW = if (showMap) rw - mapW - GAP else rw
		val mapX = rx + listW + GAP

		PvDraw.text(g, font, "Upgrades", rx, ry, PvDraw.COLOR_MUTED)
		drawCostToMax(snap, ui, g, font, rx, ry, listW)
		if (showMap) {
			PvDraw.text(g, font, "Plots", mapX, ry, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, snap.plotsUnlocked().toString() + "/" + snap.plotsMax, mapX + mapW, ry, PvDraw.COLOR_TEXT)
		}
		ry += font.lineHeight + 4
		if (showMap) {
			drawPlotMap(snap, ui, g, mapX, ry, mapCell, mx, my, font)
		}

		val upgrades = c.upgrades
		val upBarH = 3
		val upLabelGap = 2
		val upBlockH = font.lineHeight + upLabelGap + upBarH
		var upRowH = maxOf(ICON, upBlockH) + 3
		val availH = maxOf(upRowH, y + h - PAD - ry)
		if (upgrades.isNotEmpty()) {
			upRowH = maxOf(maxOf(ICON, upBlockH) + 1, availH / upgrades.size)
		}
		for ((i, u) in upgrades.withIndex()) {
			val by = ry + i * upRowH
			val textX = rx + ICON + 4
			val barW = maxOf(20, listW - ICON - 4)
			val iconY = by + maxOf(0, (upBlockH - ICON) / 2)
			GardenUi.drawIcon(g, u.iconId, rx, iconY, ICON, GardenData.composterUpgradePackModel(u.id))
			val name = shortComposterName(u)
			val lvl = "${u.level} / ${u.maxLevel}"
			val nameMax = maxOf(8, barW - font.width(lvl) - 4)
			PvDraw.text(g, font, GardenUi.trim(font, name, nameMax), textX, by, PvDraw.COLOR_TEXT)
			PvDraw.textRight(g, font, lvl, textX + barW, by, if (u.maxed()) COMPLETED_C else PvDraw.COLOR_MUTED)
			PvDraw.progressBar(g, textX, by + font.lineHeight + upLabelGap, barW, upBarH, u.fill(), BAR_COMPOST, u.maxed())
			val tip = mutableListOf(
				PvTooltip.Line.of(u.name, PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("Purchased: " + u.level, COMPLETED_C),
				PvTooltip.Line.of("Missing: " + u.missing(), REJECTED_C),
				PvTooltip.Line.of(if (u.maxed()) "MAX" else FormatUtil.oneDecimal((u.fill() * 100).toDouble()) + "%", PvDraw.COLOR_MUTED)
			)
			if (!u.maxed() && GardenCosts.hasComposterCosts(u.id)) {
				tip.add(PvTooltip.Line.blank())
				tip.add(PvTooltip.Line.of("Cost to max", PvDraw.COLOR_TEXT))
				GardenUi.addComposterRemaining(tip, GardenCosts.composterRemaining(u.id, u.level))
			}
			ui.zones.add(GardenUi.HoverZone(rx, by, listW, upBlockH + 1, tip))
		}
	}

	fun resetScroll() {
		scroll = 0
	}

	fun clearScrollExtents() {
		maxScroll = 0
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (maxScroll <= 0) {
			return false
		}
		if (mouseY < scrollTop || mouseY >= scrollTop + scrollH) {
			return false
		}
		val next = maxOf(0, minOf(maxScroll, scroll + (if (scrollY > 0) -14 else 14)))
		if (next == scroll) {
			return false
		}
		scroll = next
		return true
	}

	companion object {
		private const val PLOT_GAP = 2
		private const val PLOT_UNLOCKED = 0xFF1F3A1F.toInt()
		private const val PLOT_LOCKED = 0x1CFFFFFF
		private const val PLOT_BARN = 0xFF3A2C1E.toInt()

		// Header totals the coin value of the crops still needed; Copper can't be bought so it stays in the tooltip.
		// A "+" marks a lower bound when some items have no price, "?" when none do.
		@JvmStatic
		fun costToMaxLabel(
			total: GardenCosts.ComposterRemaining, price: ToDoubleFunction<String>, width: ToIntFunction<String>, avail: Int
		): String {
			if (total.none()) {
				return "Maxed"
			}
			if (!total.needsItems()) {
				return fit(listOf("To max: Copper only", "Copper only"), width, avail)
			}
			val coins = total.coins(price)
			val amount = if (coins <= 0.0) "?" else FormatUtil.shortCoins(coins) + (if (total.unpriced(price) > 0) "+" else "")
			return fit(listOf("To max: $amount coins", "$amount coins", amount), width, avail)
		}

		private fun fit(labels: List<String>, width: ToIntFunction<String>, avail: Int): String =
			labels.firstOrNull { width.applyAsInt(it) <= avail } ?: labels.last()

		private fun drawCostToMax(snap: GardenSnapshot, ui: GardenUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int) {
			val total = snap.composterCostToMax()
			val label = costToMaxLabel(total, { ItemPricer.materialPrice(it) }, { font.width(it) }, w - font.width("Upgrades") - 8)
			val labelW = font.width(label)
			val color = when {
				total.none() -> COMPLETED_C
				total.needsItems() -> PvDraw.COLOR_GOLD
				else -> COPPER
			}
			PvDraw.textRight(g, font, label, x + w, y, color)
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title("Composter cost to max", PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.divider())
			GardenUi.addComposterRemaining(tip, total)
			tip.add(PvTooltip.Line.blank())
			tip.add(PvTooltip.Line.meta("Remaining levels, all upgrades"))
			ui.zones.add(GardenUi.HoverZone(x + w - labelW, y, labelW, font.lineHeight, tip))
		}

		private fun drawPlotMap(
			snap: GardenSnapshot, ui: GardenUi, g: GuiGraphicsExtractor, x: Int, y: Int, cell: Int, mx: Int, my: Int, font: Font
		) {
			val step = cell + PLOT_GAP
			val barnX = x + 2 * step
			val barnY = y + 2 * step
			PvDraw.fill(g, barnX, barnY, cell, cell, PLOT_BARN)
			GardenUi.drawCellBorder(g, barnX, barnY, cell, cell)
			ui.zones.add(GardenUi.HoverZone(barnX, barnY, cell, cell, listOf(PvTooltip.Line.title("Barn", PvDraw.COLOR_TEXT))))

			for (plotCell in snap.plotCells()) {
				val plot = plotCell.plot
				if (plot.x < 0 || plot.x > 4 || plot.y < 0 || plot.y > 4) {
					continue
				}
				val cx = x + plot.x * step
				val cy = y + plot.y * step
				PvDraw.fill(g, cx, cy, cell, cell, if (plotCell.unlocked) PLOT_UNLOCKED else PLOT_LOCKED)
				val hovered = mx >= cx && mx < cx + cell && my >= cy && my < cy + cell
				if (hovered) {
					g.outline(cx, cy, cell, cell, PvDraw.COLOR_ACCENT)
				} else {
					GardenUi.drawCellBorder(g, cx, cy, cell, cell)
				}
				val num = plot.number.toString()
				if (font.width(num) <= cell - 2) {
					PvDraw.text(
						g, font, num, cx + (cell - font.width(num) + 1) / 2, cy + (cell - font.lineHeight) / 2 + 1,
						if (plotCell.unlocked) COMPLETED_C else PvDraw.COLOR_MUTED
					)
				}

				val tip = ArrayList<PvTooltip.Line>()
				tip.add(PvTooltip.Line.title("Plot " + plot.number, if (plotCell.unlocked) COMPLETED_C else PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.divider())
				tip.add(PvTooltip.Line.row(
					"Status", PvDraw.COLOR_MUTED,
					if (plotCell.unlocked) "Unlocked" else "Locked", if (plotCell.unlocked) COMPLETED_C else REJECTED_C
				))
				tip.add(PvTooltip.Line.row("Tier", PvDraw.COLOR_MUTED, GardenUi.title(plot.tier), PvDraw.COLOR_TEXT))
				val cost = plotCell.unlockCost
				if (cost != null) {
					tip.add(PvTooltip.Line.row(
						"Unlock cost", PvDraw.COLOR_MUTED,
						FormatUtil.commas(cost.amount) + "x " + GardenUi.costItemName(cost.itemId), BAR_COMPOST
					))
					tip.add(PvTooltip.Line.meta("Cost scales with " + GardenUi.title(plot.tier) + " plots owned"))
				}
				ui.zones.add(GardenUi.HoverZone(cx, cy, cell, cell, tip))
			}
		}

		private fun shortComposterName(u: GardenSnapshot.ComposterUpgrade): String = when (u.id.lowercase(Locale.ROOT)) {
			"organic_matter_cap" -> "Organic Cap"
			"cost_reduction" -> "Cost Reduction"
			"multi_drop" -> "Multi Drop"
			"fuel_cap" -> "Fuel Cap"
			"speed" -> "Speed"
			else -> u.name
		}
	}
}
