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
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.garden.GardenUi
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_AFTER
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_GARDEN
import fishmod.pv.gui.garden.GardenUi.Companion.BAR_VISITOR
import fishmod.pv.gui.garden.GardenUi.Companion.COMPLETED_C
import fishmod.pv.gui.garden.GardenUi.Companion.GAP
import fishmod.pv.gui.garden.GardenUi.Companion.ICON
import fishmod.pv.gui.garden.GardenUi.Companion.PAD
import fishmod.pv.gui.garden.GardenUi.Companion.REJECTED_C
import fishmod.pv.gui.garden.GardenUi.Companion.SLOT_GAP
import fishmod.pv.gui.garden.GardenUi.Companion.STAT_ROW
import fishmod.pv.gui.garden.GardenUi.Companion.VISITOR_CELL_H
import fishmod.pv.gui.garden.GardenUi.Companion.VISITOR_CELL_W
import fishmod.pv.gui.garden.GardenUi.Companion.VISITS_C
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class VisitorsPage {
	private var scroll = 0
	private var maxScroll = 0
	private var scrollTop = 0
	private var scrollH = 0

	fun render(snap: GardenSnapshot, ui: GardenUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
		val rightW = maxOf(140, w * 30 / 100)
		val leftW = w - rightW - GAP
		PvDraw.innerPanel(g, x, y, leftW, h)
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h)

		val lx = x + PAD
		val ly = y + PAD
		val lw = leftW - PAD * 2

		val list = snap.visitors
		val cols = maxOf(1, (lw + SLOT_GAP) / (VISITOR_CELL_W + SLOT_GAP))
		val cellW = VISITOR_CELL_W
		val cellH = VISITOR_CELL_H
		val rows = (list.size + cols - 1) / cols
		val gridTop = ly
		val gridH = y + h - PAD - gridTop
		scrollTop = gridTop
		scrollH = gridH
		maxScroll = maxOf(0, rows * (cellH + SLOT_GAP) - gridH)
		scroll = minOf(scroll, maxScroll)

		g.enableScissor(lx, gridTop, lx + lw + 3, gridTop + gridH)
		val baseY = gridTop - scroll
		for ((i, v) in list.withIndex()) {
			val bx = lx + (i % cols) * (cellW + SLOT_GAP)
			val by = baseY + (i / cols) * (cellH + SLOT_GAP)
			if (by + cellH < gridTop || by > gridTop + gridH) {
				continue
			}
			val state = v.state()
			PvDraw.fill(g, bx, by, cellW, cellH, if (state == GardenSnapshot.VisitorState.NEVER) 0xFF2A2A32.toInt() else 0xFF1E5A2A.toInt())
			GardenUi.drawCellBorder(g, bx, by, cellW, cellH)
			val iconX = bx + (cellW - ICON) / 2
			GardenUi.drawIcon(g, v.npcItemId, iconX, by + (cellH - ICON) / 2, ICON, null)
			val rarity = GardenData.visitorRarity(v.id)
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.of(v.name, PvDraw.COLOR_TEXT))
			if (rarity.isNotBlank()) {
				tip.add(PvTooltip.Line.of(GardenData.prettyVisitorRarity(rarity), rarityColor(rarity)))
			}
			tip.add(when (state) {
				GardenSnapshot.VisitorState.ACCEPTED -> PvTooltip.Line.of("Offer accepted", COMPLETED_C)
				GardenSnapshot.VisitorState.VISITED -> PvTooltip.Line.of("Visited, never accepted", 0xFFFFFF55.toInt())
				GardenSnapshot.VisitorState.NEVER -> PvTooltip.Line.of("Not encountered", PvDraw.COLOR_MUTED)
			})
			tip.add(PvTooltip.Line.of("Visits: " + FormatUtil.commas(v.visits), VISITS_C))
			tip.add(PvTooltip.Line.of("Completed: " + FormatUtil.commas(v.completed), COMPLETED_C))
			tip.add(PvTooltip.Line.of("Rejected: " + FormatUtil.commas(v.rejected), REJECTED_C))
			ui.zones.add(GardenUi.HoverZone(bx, by, cellW, cellH, tip))
		}
		g.disableScissor()

		val rx = x + leftW + GAP + PAD
		var ry = y + PAD
		val rw = rightW - PAD * 2
		ry = ui.drawBar(
			g, font, "Offers MS", snap.visitorMilestone.toString(),
			snap.visitorMilestoneFill, snap.visitorMilestoneMaxed, BAR_VISITOR,
			snap.visitorMilestoneHover, rx, ry, rw, mx, my
		) + BAR_AFTER + 2
		ry = ui.drawBar(
			g, font, "Unique MS", snap.uniqueVisitorMilestone.toString(),
			snap.uniqueVisitorMilestoneFill, snap.uniqueVisitorMilestoneMaxed, BAR_GARDEN,
			snap.uniqueVisitorMilestoneHover, rx, ry, rw, mx, my
		) + BAR_AFTER + 2
		ry = GardenUi.statLine(g, font, "Offers accepted", FormatUtil.commas(snap.visitorsCompleted), rx, ry, rw, COMPLETED_C) + 1
		ry = GardenUi.statLine(g, font, "Offers declined", FormatUtil.commas(snap.totalRejected), rx, ry, rw, REJECTED_C) + 1
		ry = GardenUi.statLine(g, font, "Total visits", FormatUtil.commas(snap.totalVisits), rx, ry, rw, VISITS_C) + 1
		ry = GardenUi.statLine(g, font, "Unique served", FormatUtil.commas(snap.uniqueVisitors), rx, ry, rw, PvDraw.COLOR_MUTED) + 1

		val active = snap.activeVisitors
		val activeValue = if (!snap.islandLoaded) "..." else FormatUtil.commas(active.size.toLong())
		val activeY = ry
		ry = GardenUi.statLine(
			g, font, "Active now", activeValue, rx, ry, rw, if (active.isEmpty()) PvDraw.COLOR_MUTED else COMPLETED_C
		) + 1
		if (snap.islandLoaded && active.isNotEmpty()) {
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title("Active visitors", PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.divider())
			for ((shown, v) in active.withIndex()) {
				if (shown >= 8) {
					tip.add(PvTooltip.Line.meta("+" + (active.size - shown) + " more"))
					break
				}
				val status = v.status
				val detail = v.detail
				val right = when {
					status.isNotBlank() && detail.isNotBlank() -> "$status · $detail"
					status.isNotBlank() -> status
					else -> detail
				}
				tip.add(PvTooltip.Line.row(v.name, PvDraw.COLOR_TEXT, right.ifBlank { "-" }, PvDraw.COLOR_MUTED))
			}
			ui.zones.add(GardenUi.HoverZone(rx, activeY, rw, STAT_ROW, tip))
		}

		ry += STAT_ROW + 1
		PvDraw.text(g, font, "Accepted Rarities", rx, ry, PvDraw.COLOR_MUTED)
		ry += font.lineHeight + 3
		val accepted = acceptedRarityTotals(snap.visitors)
		for (rarity in GardenData.visitorRarityOrder()) {
			val count = accepted[rarity] ?: 0L
			if (count <= 0L) {
				continue
			}
			ry = GardenUi.statLine(g, font, GardenData.prettyVisitorRarity(rarity), FormatUtil.commas(count), rx, ry, rw, rarityColor(rarity)) + 1
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

	private companion object {
		fun acceptedRarityTotals(visitors: List<GardenSnapshot.VisitorRow>): Map<String, Long> {
			val out = LinkedHashMap<String, Long>()
			for (v in visitors) {
				if (v.completed <= 0L) {
					continue
				}
				val rarity = GardenData.visitorRarity(v.id)
				if (rarity.isBlank()) {
					continue
				}
				out.merge(rarity.uppercase(Locale.ROOT), v.completed, Long::plus)
			}
			return out
		}

		fun rarityColor(rarity: String?): Int {
			if (rarity == null) {
				return PvDraw.COLOR_MUTED
			}
			return when (rarity.uppercase(Locale.ROOT)) {
				"COMMON" -> 0xFFFFFFFF.toInt()
				"UNCOMMON" -> 0xFF55FF55.toInt()
				"RARE" -> 0xFF5555FF.toInt()
				"EPIC" -> 0xFFAA00AA.toInt()
				"LEGENDARY" -> 0xFFFFAA00.toInt()
				"MYTHIC" -> 0xFFFF55FF.toInt()
				"SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555.toInt()
				else -> PvDraw.COLOR_MUTED
			}
		}
	}
}
