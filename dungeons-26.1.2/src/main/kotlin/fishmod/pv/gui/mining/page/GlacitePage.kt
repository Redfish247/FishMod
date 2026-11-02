/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.mining.page

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.MiningSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.mining.MiningUi
import fishmod.pv.gui.mining.MiningUi.HoverZone
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Glacite tunnels subtab: mineshafts, fossils, corpses, milestones. */
class GlacitePage {
	private var scroll = 0
	private var maxScroll = 0
	private var scrollTop = 0
	private var scrollH = 0

	fun reset() {
		scroll = 0
	}

	fun onEnter() {
		scroll = 0
	}

	// The milestones panel no longer flips to the fossil essence face.
	@Suppress("UNUSED_PARAMETER")
	fun mouseClicked(mx: Double, my: Double): Boolean = false

	fun mouseScrolled(@Suppress("UNUSED_PARAMETER") mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (maxScroll <= 0 || mouseY < scrollTop || mouseY >= scrollTop + scrollH) {
			return false
		}
		val next = maxOf(0, minOf(maxScroll, scroll + if (scrollY > 0) -14 else 14))
		if (next == scroll) {
			return false
		}
		scroll = next
		return true
	}

	fun render(
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, zones: MutableList<HoverZone>,
		x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		val rightW = maxOf(200, w * 55 / 100)
		val leftW = w - rightW - MiningUi.GAP
		PvDraw.innerPanel(g, x, y, leftW, h)

		val lx = x + MiningUi.PAD
		var ly = y + MiningUi.PAD
		val lw = leftW - MiningUi.PAD * 2

		PvDraw.text(g, font, "Glacite", lx, ly, PvDraw.COLOR_MUTED)
		ly += font.lineHeight + 4

		ly = MiningUi.statLine(g, font, "Mineshafts entered", FormatUtil.commas(snapshot.mineshaftsEntered), lx, ly, lw, PvDraw.COLOR_TEXT)
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW)
		ly = MiningUi.statLine(g, font, "Fossil dust", FormatUtil.commas(snapshot.fossilDust), lx, ly, lw, MiningUi.BAR_GLACITE) + 1

		val fossils = snapshot.fossilsDonated
		val fossilTop = ly
		ly = MiningUi.statLine(g, font, "Fossils donated", fossils.size.toString(), lx, ly, lw, PvDraw.COLOR_ACCENT)
		if (fossils.isNotEmpty()) {
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.of("Fossils donated", PvDraw.COLOR_MUTED))
			for (fossil in fossils) {
				tip.add(PvTooltip.Line.of(MiningUi.title(fossil), MiningUi.PLACED))
			}
			zones.add(HoverZone.of(lx, fossilTop, lw, MiningUi.STAT_ROW, tip))
		}
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW)

		val counts = snapshot.corpses
		PvDraw.text(g, font, "Corpses looted", lx, ly, PvDraw.COLOR_MUTED)
		ly += font.lineHeight + 3
		ly = MiningUi.coloredLabelStat(g, font, "Lapis", FormatUtil.commas(counts.lapis), lx, ly, lw, CORPSE_LAPIS, PvDraw.COLOR_TEXT) + 1
		ly = MiningUi.coloredLabelStat(g, font, "Umber", FormatUtil.commas(counts.umber), lx, ly, lw, CORPSE_UMBER, PvDraw.COLOR_TEXT) + 1
		ly = MiningUi.coloredLabelStat(g, font, "Tungsten", FormatUtil.commas(counts.tungsten), lx, ly, lw, CORPSE_TUNGSTEN, PvDraw.COLOR_TEXT) + 1
		ly = MiningUi.coloredLabelStat(g, font, "Vanguard", FormatUtil.commas(counts.vanguard), lx, ly, lw, CORPSE_VANGUARD, PvDraw.COLOR_TEXT) + 1
		ly = MiningUi.statLine(g, font, "Total", FormatUtil.commas(counts.total()), lx, ly, lw, PvDraw.COLOR_ACCENT)
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW)

		val current = snapshot.corpseMilestone
		val tierLabel = if (current <= 0) "None" else "Tier " + current + if (current >= 7) " (max)" else ""
		MiningUi.statLine(g, font, "Milestone", tierLabel, lx, ly, lw, PvDraw.COLOR_ACCENT)

		val rx = x + leftW + MiningUi.GAP
		PvDraw.innerPanel(g, rx, y, rightW, h)
		renderCorpseMilestones(g, font, snapshot, zones, rx, y, rightW, h, mx, my)
	}

	private fun renderCorpseMilestones(
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, zones: MutableList<HoverZone>,
		x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		val rx = x + MiningUi.PAD
		var ry = y + MiningUi.PAD
		val rw = w - MiningUi.PAD * 2

		PvDraw.text(g, font, "Corpse milestones", rx, ry, PvDraw.COLOR_MUTED)
		ry += font.lineHeight + 4

		val counts = snapshot.corpses
		val current = snapshot.corpseMilestone

		val gridTop = ry
		val gridH = y + h - MiningUi.PAD - gridTop
		scrollTop = gridTop
		scrollH = gridH

		val tiers = MiningSnapshot.CORPSE_MILESTONES
		// Two columns side by side, no scrolling.
		val cols = 2
		val colGap = 12
		val colW = (rw - colGap) / cols
		val perCol = maxOf(1, (tiers.size + cols - 1) / cols)
		val rowH = maxOf(MiningUi.barRowH(font), gridH / perCol)
		maxScroll = 0
		scroll = 0

		for ((i, tier) in tiers.withIndex()) {
			val prev = if (i == 0) null else tiers[i - 1]
			val done = current >= tier.tier
			val fill = if (done) 1f else tier.fill(counts, prev)
			val value = if (done) "Done" else requirementShort(tier, counts)
			val col = i / perCol
			val row = i % perCol
			MiningUi.drawBar(
				g, font, "Tier " + tier.tier, value, fill, done, BAR_CORPSE, corpseHover(tier, counts, done),
				rx + col * (colW + colGap), gridTop + row * rowH, colW, mx, my, zones,
			)
		}
	}

	private companion object {
		const val BAR_CORPSE = 0xFF6B9BD1.toInt()
		const val CORPSE_LAPIS = 0xFF5555FF.toInt()
		const val CORPSE_UMBER = 0xFFC4A35A.toInt()
		const val CORPSE_TUNGSTEN = 0xFFB0B0B8.toInt()
		const val CORPSE_VANGUARD = 0xFF55FFFF.toInt()

		fun requirementShort(tier: MiningSnapshot.CorpseMilestone, counts: MiningSnapshot.CorpseCounts): String {
			val parts = ArrayList<String>()
			if (tier.needLapis > 0) parts.add("${counts.lapis}/${tier.needLapis} L")
			if (tier.needUmber > 0) parts.add("${counts.umber}/${tier.needUmber} U")
			if (tier.needTungsten > 0) parts.add("${counts.tungsten}/${tier.needTungsten} T")
			if (tier.needVanguard > 0) parts.add("${counts.vanguard}/${tier.needVanguard} V")
			return if (parts.isEmpty()) "-" else parts.joinToString(" ")
		}

		fun corpseHover(tier: MiningSnapshot.CorpseMilestone, counts: MiningSnapshot.CorpseCounts, done: Boolean): List<PvTooltip.Line> {
			if (done) {
				return listOf(PvTooltip.Line.of("Tier " + tier.tier + " complete", PvDraw.COLOR_ACCENT))
			}
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title("Tier " + tier.tier + " needs", PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.divider())
			addCorpseNeedRow(tip, "Lapis", counts.lapis, tier.needLapis, CORPSE_LAPIS)
			addCorpseNeedRow(tip, "Umber", counts.umber, tier.needUmber, CORPSE_UMBER)
			addCorpseNeedRow(tip, "Tungsten", counts.tungsten, tier.needTungsten, CORPSE_TUNGSTEN)
			addCorpseNeedRow(tip, "Vanguard", counts.vanguard, tier.needVanguard, CORPSE_VANGUARD)
			return tip
		}

		fun addCorpseNeedRow(tip: MutableList<PvTooltip.Line>, label: String, have: Long, need: Long, color: Int) {
			if (need <= 0L) {
				return
			}
			tip.add(PvTooltip.Line.row(label, color, "$have/$need", if (have >= need) 0xFF55FF55.toInt() else PvDraw.COLOR_TEXT))
		}
	}
}
