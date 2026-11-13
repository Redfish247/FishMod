/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.GardenData
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.garden.GardenUi
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_AFTER
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_CROP
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_FARM
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_GARDEN
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_LABEL_GAP
import fishmod.pv.gui.garden.GardenUi.Companion.CHIP_CELL
import fishmod.pv.gui.garden.GardenUi.Companion.CHIP_GAP
import fishmod.pv.gui.garden.GardenUi.Companion.COMPLETED_C
import fishmod.pv.gui.garden.GardenUi.Companion.COPPER
import fishmod.pv.gui.garden.GardenUi.Companion.CREDIT_C
import fishmod.pv.gui.garden.GardenUi.Companion.GAP
import fishmod.pv.gui.garden.GardenUi.Companion.ICON
import fishmod.pv.gui.garden.GardenUi.Companion.PAD
import fishmod.pv.gui.garden.GardenUi.Companion.SLOT
import fishmod.pv.gui.garden.GardenUi.Companion.SLOT_GAP
import fishmod.pv.gui.garden.GardenUi.Companion.STAT_ROW
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.overview.SkillStatRows
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class GardenOverviewPage {
	private var scroll = 0
	private var maxScroll = 0
	private var scrollTop = 0
	private var scrollH = 0
	private var fortuneRow: IntArray? = null

	fun fortuneRowHit(mx: Double, my: Double): Boolean {
		val r = fortuneRow ?: return false
		return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]
	}

	fun render(
		snap: GardenSnapshot, stats: PlayerStatsSnapshot?, ui: GardenUi,
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int, partial: Boolean
	) {
		val rightW = maxOf(200, w * 55 / 100)
		val leftW = w - rightW - GAP
		PvDraw.innerPanel(g, x, y, leftW, h)
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h)

		val lx = x + PAD
		var ly = y + PAD
		val lw = leftW - PAD * 2
		val rowH = GardenUi.barRowH(font)

		val gardenOverflow = snap.gardenOverflowLevel()
		val gardenValue = if (gardenOverflow > snap.gardenLevel) "${snap.gardenLevel} ($gardenOverflow)" else snap.gardenLevel.toString()
		ly = ui.drawBar(
			g, font, "Garden", gardenValue, snap.gardenFill, snap.gardenMaxed, BAR_GARDEN,
			snap.gardenHover, lx, ly, lw, mx, my
		) + BAR_AFTER
		ly = ui.drawBar(
			g, font, "Farming", snap.farmingLevel.toString(), snap.farmingFill, snap.farmingMaxed, BAR_FARM,
			snap.farmingHover, lx, ly, lw, mx, my
		) + BAR_AFTER + 2

		ly = GardenUi.statLine(
			g, font, "Plots", if (snap.islandLoaded) snap.plotsUnlocked().toString() + " / " + snap.plotsMax else "...",
			lx, ly, lw, PvDraw.COLOR_TEXT
		) + 1
		ly = GardenUi.statLine(g, font, "Copper", FormatUtil.commas(snap.copper), lx, ly, lw, COPPER) + 1
		ly = GardenUi.statLine(g, font, "Larva consumed", FormatUtil.commas(snap.larvaConsumed), lx, ly, lw, PvDraw.COLOR_TEXT) + 2

		val weight = snap.farmingWeight
		val weightValue = when {
			weight.loading -> "..."
			weight.loaded -> FormatUtil.oneDecimal(weight.displayTotal())
			else -> "-"
		}
		ly = GardenUi.statLine(g, font, "Farming weight", weightValue, lx, ly, lw, PvDraw.COLOR_GOLD) + 1
		if (weight.loaded) {
			ui.zones.add(GardenUi.HoverZone(lx, ly - STAT_ROW, lw, STAT_ROW, farmingWeightHover(weight)))
			ly += 2
		} else if (weight.error.isNotBlank()) {
			ly = GardenUi.statLine(g, font, "Weight", GardenUi.trim(font, weight.error, lw / 2), lx, ly, lw, PvDraw.COLOR_MUTED) + 3
		} else {
			ly += 2
		}
		var fortune = FarmingFortuneTip.row(stats, snap)
		fortuneRow = null
		if (fortune == null) {
			fortune = SkillStatRows.row(stats, "farming_fortune", FORTUNE_EXCLUSIONS)
		} else {
			fortuneRow = intArrayOf(lx - 2, ly - 1, lw + 4, STAT_ROW)
			if (mx >= lx - 2 && mx < lx + lw + 2 && my >= ly - 1 && my < ly - 1 + STAT_ROW) {
				PvDraw.fill(g, lx - 2, ly - 1, lw + 4, STAT_ROW, 0x14FFFFFF)
			}
		}
		ui.zones.add(GardenUi.HoverZone(lx, ly, lw, STAT_ROW, fortune.tip.orEmpty()))
		GardenUi.drawPair(g, font, fortune.label, fortune.value, lx, ly, lw, fortune.labelColor, fortune.valueColor)
		ly += STAT_ROW + 2

		if (!snap.islandLoaded && partial) {
			val msg = if (snap.islandLoading || snap.islandError.isBlank()) "Loading island..." else snap.islandError
			PvDraw.text(g, font, GardenUi.trim(font, msg, lw), lx, ly, PvDraw.COLOR_MUTED)
			ly += font.lineHeight + 4
		}

		val chips = snap.gardenChips
		if (chips.isNotEmpty()) {
			ly += 8
			PvDraw.text(g, font, "Garden chips", lx, ly, PvDraw.COLOR_MUTED)
			ly += font.lineHeight + 3
			val cols = minOf(5, maxOf(4, (lw + CHIP_GAP) / (CHIP_CELL + CHIP_GAP)))
			val rowsNeeded = maxOf(1, (chips.size + cols - 1) / cols)
			// Leave room for farming toolkit below; shrink row height so every chip fits (do not clip to 1 row).
			val toolkitReserve = 52
			val avail = maxOf(CHIP_CELL + font.lineHeight + 2, (y + h - PAD - toolkitReserve) - ly)
			val naturalCellH = CHIP_CELL + font.lineHeight + 2
			val cellH = minOf(naturalCellH, maxOf(CHIP_CELL + 2, (avail - (rowsNeeded - 1) * CHIP_GAP) / rowsNeeded))
			val iconSize = minOf(ICON, maxOf(10, cellH - font.lineHeight - 2))
			val gridW = cols * CHIP_CELL + (cols - 1) * CHIP_GAP
			val gridX = lx + maxOf(0, (lw - gridW) / 2)
			for ((i, chip) in chips.withIndex()) {
				val bx = gridX + (i % cols) * (CHIP_CELL + CHIP_GAP)
				val by = ly + (i / cols) * (cellH + CHIP_GAP)
				val boxH = minOf(CHIP_CELL, cellH - font.lineHeight)
				GardenUi.drawCellBorder(g, bx, by, CHIP_CELL, boxH)
				val iconX = bx + (CHIP_CELL - iconSize) / 2
				val iconY = by + maxOf(0, (boxH - iconSize) / 2)
				GardenUi.drawIcon(g, chip.iconId, iconX, iconY, iconSize, chipPackModel(chip.id))
				val lvl = chip.level.toString()
				val lvlY = by + boxH + 1
				PvDraw.text(g, font, lvl, bx + (CHIP_CELL - font.width(lvl)) / 2, lvlY, PvDraw.COLOR_ACCENT)
				val sowdust = chip.sowdust()
				ui.zones.add(GardenUi.HoverZone(
					bx, by, CHIP_CELL, cellH, listOf(
						PvTooltip.Line.of(chip.name, PvDraw.COLOR_TEXT),
						PvTooltip.Line.of("Level " + chip.level, PvDraw.COLOR_ACCENT),
						PvTooltip.Line.row(
							"Sowdust", PvDraw.COLOR_MUTED,
							FormatUtil.commas(sowdust.spent) + " / " + FormatUtil.commas(sowdust.total) +
								" (" + FormatUtil.percent(sowdust.fraction()) + ")",
							if (sowdust.maxed()) COMPLETED_C else PvDraw.COLOR_TEXT
						)
					)
				))
			}
			ly += rowsNeeded * (cellH + CHIP_GAP) + 4
		}

		drawFarmingToolkit(snap, ui, g, font, lx, ly, lw, y + h - PAD, mx, my)

		val rx = x + leftW + GAP + PAD
		var ry = y + PAD
		val rw = rightW - PAD * 2
		PvDraw.text(g, font, "Crop milestones", rx, ry, PvDraw.COLOR_MUTED)
		if (snap.islandLoaded && snap.crops.isNotEmpty()) {
			val totals = milestoneTotals(snap.crops)
			val maxed = totals[0].toString() + "/" + snap.crops.size + " maxed"
			val allMaxed = totals[0] == snap.crops.size
			PvDraw.textRight(g, font, maxed, rx + rw, ry, if (allMaxed) COMPLETED_C else PvDraw.COLOR_MUTED)
			ui.zones.add(GardenUi.HoverZone(
				rx + rw - font.width(maxed), ry, font.width(maxed), font.lineHeight, listOf(
					PvTooltip.Line.row(
						"Crops maxed", PvDraw.COLOR_MUTED, totals[0].toString() + "/" + snap.crops.size,
						if (allMaxed) COMPLETED_C else PvDraw.COLOR_TEXT
					),
					PvTooltip.Line.row("Milestone levels", PvDraw.COLOR_MUTED, FormatUtil.commas(totals[1].toLong()), PvDraw.COLOR_TEXT)
				)
			))
		}
		ry += font.lineHeight + 4

		if (!snap.islandLoaded) {
			PvDraw.textCentered(
				g, font, if (partial) "..." else "No crop data",
				x + leftW + GAP + rightW / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED
			)
			maxScroll = 0
			return
		}

		val crops = snap.crops
		val cols = 2
		val iconGap = 3
		val colW = (rw - GAP) / cols
		val barW = maxOf(40, colW - ICON - iconGap)
		val gridTop = ry
		val gridH = y + h - PAD - gridTop
		scrollTop = gridTop
		scrollH = gridH
		maxScroll = maxOf(0, ((crops.size + 1) / cols) * rowH - gridH)
		scroll = minOf(scroll, maxScroll)

		g.enableScissor(rx, gridTop, rx + rw + 3, gridTop + gridH)
		val yy = gridTop - scroll
		for ((i, crop) in crops.withIndex()) {
			val bx = rx + (i % cols) * (colW + GAP)
			val by = yy + (i / cols) * rowH
			if (by + rowH < gridTop || by > gridTop + gridH) {
				continue
			}
			val iconY = by + maxOf(0, (font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT - ICON) / 2)
			GardenUi.drawIcon(g, crop.iconId, bx, iconY, ICON, GardenData.cropPackModel(crop.id))
			ui.drawBar(
				g, font, crop.name, crop.milestone.toString(), crop.milestoneFill,
				crop.milestoneMaxed, BAR_CROP, null, bx + ICON + iconGap, by, barW, mx, my
			)
			val barBottom = by + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT
			ui.zones.add(GardenUi.HoverZone(bx, by, colW, maxOf(rowH, barBottom - by), ui.cropMilestoneTip(snap, crop)))
		}
		g.disableScissor()
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
		private val FORTUNE_EXCLUSIONS = listOf(
			"Excludes held tool, crop-specific fortune,",
			"set bonuses and temporary buffs."
		)

		@JvmStatic
		fun milestoneTotals(crops: List<GardenSnapshot.CropRow>): IntArray =
			intArrayOf(crops.count { it.milestoneMaxed }, crops.sumOf { maxOf(0, it.milestone) })

		@JvmStatic
		fun stockedCrops(slots: List<GardenSnapshot.FarmingToolkitSlot>): IntArray {
			val sections = HashSet<String>()
			val stocked = HashSet<String>()
			var tools = 0
			for (slot in slots) {
				sections.add(slot.cropId)
				val item = slot.item
				if (item != null && !item.isEmpty()) {
					stocked.add(slot.cropId)
					tools++
				}
			}
			return intArrayOf(stocked.size, sections.size, tools)
		}

		private fun drawFarmingToolkit(
			snap: GardenSnapshot, ui: GardenUi, g: GuiGraphicsExtractor, font: Font,
			x: Int, startY: Int, w: Int, bottom: Int, mx: Int, my: Int
		) {
			if (!snap.farmingToolkitUnlocked && snap.farmingToolkitSlots.isEmpty()) {
				return
			}
			if (startY + font.lineHeight + STAT_ROW > bottom) {
				return
			}
			var y = startY
			PvDraw.text(g, font, "Farming toolkit", x, y, PvDraw.COLOR_MUTED)
			y += font.lineHeight + 3
			y = GardenUi.statLine(
				g, font, "Unlocked", if (snap.farmingToolkitUnlocked) "Yes" else "No",
				x, y, w, if (snap.farmingToolkitUnlocked) 0xFF55FF55.toInt() else PvDraw.COLOR_MUTED
			) + 1
			val stocked = stockedCrops(snap.farmingToolkitSlots)
			ui.zones.add(GardenUi.HoverZone(
				x, y, w, STAT_ROW, listOf(
					PvTooltip.Line.title("Crops with tools", PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.of("Crop sections holding at least one tool.", PvDraw.COLOR_TEXT),
					PvTooltip.Line.row("Tools stored", PvDraw.COLOR_MUTED, stocked[2].toString(), PvDraw.COLOR_TEXT)
				)
			))
			y = GardenUi.statLine(g, font, "Crops with tools", "${stocked[0]} / ${stocked[1]}", x, y, w, PvDraw.COLOR_ACCENT) + 3

			val slots = snap.farmingToolkitSlots
			if (slots.isEmpty() || y + SLOT > bottom) {
				return
			}
			val cols = maxOf(3, minOf(5, (w + SLOT_GAP) / (SLOT + SLOT_GAP)))
			var col = 0
			var rowY = y
			var lastCrop = ""
			for (slot in slots) {
				if (slot.cropId != lastCrop) {
					if (lastCrop.isNotEmpty()) {
						rowY += SLOT + SLOT_GAP + 2
						col = 0
					}
					if (rowY + font.lineHeight + SLOT > bottom) {
						break
					}
					PvDraw.text(g, font, slot.label, x, rowY, PvDraw.COLOR_MUTED)
					rowY += font.lineHeight + 2
					lastCrop = slot.cropId
				}
				if (col >= cols) {
					col = 0
					rowY += SLOT + SLOT_GAP
				}
				if (rowY + SLOT > bottom) {
					break
				}
				val bx = x + col * (SLOT + SLOT_GAP)
				val by = rowY
				val hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT
				GardenUi.drawCellBorder(g, bx, by, SLOT, SLOT)
				if (hovered) {
					g.outline(bx, by, SLOT, SLOT, PvDraw.COLOR_ACCENT)
				}
				val item = slot.item
				val filled = item != null && !item.isEmpty()
				var stack: ItemStack? = ItemStack.EMPTY
				if (item != null && filled) {
					stack = SkyBlockItemFactory.toStack(item)
					if (stack.isNullOrEmpty()) {
						stack = SkyBlockItemFactory.iconStack(item.id)
					}
				}
				if (stack.isNullOrEmpty()) {
					stack = SkyBlockItemFactory.iconStack(GardenData.cropIconId(slot.cropId))
				}
				val drawn = stack?.takeIf { !it.isEmpty } ?: ItemStack(Items.WHEAT)
				g.item(drawn, bx + 1, by + 1)
				val tip = ArrayList<PvTooltip.Line>()
				tip.add(PvTooltip.Line.title(slot.label, PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.divider())
				if (item != null && filled) {
					val name = if (item.displayName.isNullOrBlank()) GardenData.prettyCrop(item.id) else item.displayName
					tip.add(PvTooltip.Line.row("Item", PvDraw.COLOR_MUTED, name, PvDraw.COLOR_ACCENT))
				} else {
					tip.add(PvTooltip.Line.meta("Empty"))
				}
				ui.zones.add(GardenUi.HoverZone(bx, by, SLOT, SLOT, tip))
				col++
			}
		}

		private fun ItemStack?.isNullOrEmpty(): Boolean = this == null || this.isEmpty

		private fun chipPackModel(id: String?): String {
			val key = GardenData.normalizeChipKey(id)
			if (key.isBlank()) {
				return ""
			}
			val file = when (key) {
				"vermin_vaporize" -> "vermin_vaporizer_chip"
				else -> key + "_chip"
			}
			return "hypixel_skyblock:item/island_relevant/garden/chips/$file"
		}

		@JvmStatic
		fun farmingWeightHover(weight: GardenSnapshot.FarmingWeightInfo): List<PvTooltip.Line> {
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.of("Farming weight", PvDraw.COLOR_GOLD))
			tip.add(PvTooltip.Line.of("Total: " + FormatUtil.oneDecimal(weight.displayTotal()), PvDraw.COLOR_TEXT))
			val shownBonus = weight.bonusWeight.entries.sortedByDescending { it.value }.filter { it.value > 0 }
			if (shownBonus.isNotEmpty()) {
				tip.add(PvTooltip.Line.of("Bonus", PvDraw.COLOR_ACCENT))
				for ((key, value) in shownBonus) {
					tip.add(PvTooltip.Line.of(prettyBonusWeight(key) + ": " + FormatUtil.oneDecimal(value), PvDraw.COLOR_MUTED))
				}
			}
			val shownCrops = weight.cropWeight.entries.sortedByDescending { it.value }.filter { it.value > 0 }
			if (shownCrops.isNotEmpty()) {
				tip.add(PvTooltip.Line.of("Crops", PvDraw.COLOR_ACCENT))
				for ((key, value) in shownCrops.take(12)) {
					tip.add(PvTooltip.Line.of(GardenData.prettyCrop(key) + ": " + FormatUtil.oneDecimal(value), PvDraw.COLOR_MUTED))
				}
			}
			tip.add(PvTooltip.Line.of("Provided by elitebot.dev", CREDIT_C))
			return tip
		}

		private fun prettyBonusWeight(key: String?): String {
			if (key.isNullOrBlank()) {
				return "?"
			}
			return when (key.lowercase(Locale.ROOT)) {
				"farming_level", "farminglevel" -> "Farming level"
				"anita" -> "Anita"
				"contests", "jacob_contests", "jacob" -> "Contests"
				"minions" -> "Minions"
				"pests" -> "Pests"
				else -> GardenUi.title(key.replace('-', '_'))
			}
		}
	}
}
