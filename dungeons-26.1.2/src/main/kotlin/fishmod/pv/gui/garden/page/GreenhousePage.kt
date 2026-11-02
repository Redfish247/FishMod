/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page

import fishmod.pv.data.GardenData
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.garden.GardenUi
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_COMPOST
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_FARM
import fishmod.pv.gui.garden.GardenUi.Companion.COMPLETED_C
import fishmod.pv.gui.garden.GardenUi.Companion.GAP
import fishmod.pv.gui.garden.GardenUi.Companion.GH_CELL_W
import fishmod.pv.gui.garden.GardenUi.Companion.GOLD
import fishmod.pv.gui.garden.GardenUi.Companion.ICON
import fishmod.pv.gui.garden.GardenUi.Companion.PAD
import fishmod.pv.gui.garden.GardenUi.Companion.SLOT_GAP
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Garden greenhouse subpage. */
class GreenhousePage {
	private var scroll = 0
	private var maxScroll = 0
	private var scrollTop = 0
	private var scrollH = 0

	fun render(snap: GardenSnapshot, ui: GardenUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
		val leftW = maxOf(150, w * 32 / 100)
		val rightW = w - leftW - GAP
		PvDraw.innerPanel(g, x, y, leftW, h)
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h)

		val meta = snap.greenhouseMeta
		val rows = snap.greenhouse
		val analyzed = rows.count { it.analyzed }

		val lx = x + PAD
		var ly = y + PAD
		val lw = leftW - PAD * 2
		PvDraw.text(g, font, "Greenhouse", lx, ly, PvDraw.COLOR_MUTED)
		ly += font.lineHeight + 3
		ly = GardenUi.statLine(g, font, "Analyzed", analyzed.toString(), lx, ly, lw, COMPLETED_C) + 1
		ly = GardenUi.statLine(g, font, "Discovered", maxOf(0, rows.size - analyzed).toString(), lx, ly, lw, GOLD) + 1
		ly = GardenUi.statLine(g, font, "Tracked", rows.size.toString(), lx, ly, lw, PvDraw.COLOR_TEXT) + 4

		val island = snap.islandLoaded
		PvDraw.text(g, font, "Desk upgrades", lx, ly, PvDraw.COLOR_MUTED)
		ly += font.lineHeight + 2
		ly = GardenUi.statLine(g, font, "Slots unlocked", if (island) meta.slotsUnlocked.toString() else "...", lx, ly, lw, PvDraw.COLOR_TEXT) + 1
		ly = GardenUi.statLine(g, font, "Yield", if (island) meta.yieldLevel.toString() else "...", lx, ly, lw, BAR_COMPOST) + 1
		ly = GardenUi.statLine(g, font, "Plot limit", if (island) meta.plotLimitLevel.toString() else "...", lx, ly, lw, PvDraw.COLOR_ACCENT) + 1
		ly = GardenUi.statLine(g, font, "Growth speed", if (island) meta.growthSpeedLevel.toString() else "...", lx, ly, lw, BAR_FARM) + 3
		if (island && meta.lastGrowthStageMs > 0L) {
			val raw = meta.lastGrowthStageMs
			val ms = if (raw > 1_000_000_000_000L) raw else raw * 1000L
			val agoSec = maxOf(0L, (System.currentTimeMillis() - ms) / 1000L)
			val ago = when {
				agoSec < 60 -> agoSec.toString() + "s ago"
				agoSec < 3600 -> (agoSec / 60).toString() + "m ago"
				else -> (agoSec / 3600).toString() + "h ago"
			}
			GardenUi.statLine(g, font, "Last growth", ago, lx, ly, lw, PvDraw.COLOR_MUTED)
		}

		val rx = x + leftW + GAP + PAD
		var ry = y + PAD
		val rw = rightW - PAD * 2
		PvDraw.text(g, font, "Mutations", rx, ry, PvDraw.COLOR_MUTED)
		ry += font.lineHeight + 8

		val cols = maxOf(8, minOf(13, (rw + SLOT_GAP) / (GH_CELL_W + SLOT_GAP)))
		val cellW = maxOf(GH_CELL_W, (rw - (cols - 1) * SLOT_GAP) / cols)
		val cellH = cellW
		val gridW = cols * cellW + (cols - 1) * SLOT_GAP
		val gridX = rx + maxOf(0, (rw - gridW) / 2)
		val gridRows = if (rows.isEmpty()) 0 else (rows.size + cols - 1) / cols
		val gridTop = ry
		val gridH = y + h - PAD - gridTop
		scrollTop = gridTop
		scrollH = gridH
		maxScroll = maxOf(0, gridRows * (cellH + SLOT_GAP) - gridH)
		scroll = minOf(scroll, maxScroll)

		if (rows.isEmpty()) {
			PvDraw.textCentered(g, font, "No greenhouse crops", x + leftW + GAP + rightW / 2, y + h / 2, PvDraw.COLOR_MUTED)
			return
		}

		g.enableScissor(rx, gridTop, rx + rw + 3, gridTop + gridH)
		val baseY = gridTop - scroll
		for ((i, gh) in rows.withIndex()) {
			val bx = gridX + (i % cols) * (cellW + SLOT_GAP)
			val by = baseY + (i / cols) * (cellH + SLOT_GAP)
			if (by + cellH < gridTop || by > gridTop + gridH) {
				continue
			}
			PvDraw.fill(g, bx, by, cellW, cellH, if (gh.analyzed) 0xFF1E5A2A.toInt() else 0xFF2A2A32.toInt())
			GardenUi.drawCellBorder(g, bx, by, cellW, cellH)
			val iconX = bx + (cellW - ICON) / 2
			GardenUi.drawIcon(g, gh.iconId, iconX, by + (cellH - ICON) / 2, ICON, GardenData.greenhousePackModel(gh.id))
			val status = if (gh.analyzed) "Analyzed" else "Discovered"
			val statusColor = if (gh.analyzed) COMPLETED_C else GOLD
			ui.zones.add(GardenUi.HoverZone(
				bx, by, cellW, cellH, listOf(
					PvTooltip.Line.of(gh.name, PvDraw.COLOR_TEXT),
					PvTooltip.Line.of(status, statusColor)
				)
			))
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
}
