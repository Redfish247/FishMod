/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.fishing

import fishmod.pv.data.BestiarySnapshot
import fishmod.pv.data.FishingSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.Leveling
import fishmod.pv.data.TrophyFishData
import fishmod.pv.data.TrophySkulls
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.neu.NeuRepoCache
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class FishingPage {
	private var snapshot: FishingSnapshot = FishingSnapshot.empty()
	private var bestiary: BestiarySnapshot = BestiarySnapshot.empty()
	private val zones = ArrayList<HoverZone>()
	private var seaRowsFor: BestiarySnapshot.CategoryProgress? = null
	private var seaRows: List<BestiarySnapshot.FamilyProgress>? = null
	private var listScroll = 0
	private var listMaxScroll = 0
	private var listX = 0
	private var listY = 0
	private var listW = 0
	private var listH = 0

	fun apply(snapshot: FishingSnapshot?, bestiary: BestiarySnapshot?) {
		this.snapshot = snapshot ?: FishingSnapshot.empty()
		this.bestiary = bestiary ?: BestiarySnapshot.empty()
		zones.clear()
		listScroll = 0
		TrophyFishData.ensureLoaded()
		TrophySkulls.ensureLoaded()
	}

	fun snapshot(): FishingSnapshot = snapshot

	fun mouseScrolled(mx: Double, my: Double, scrollY: Double): Boolean {
		if (listMaxScroll <= 0 || mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) {
			return false
		}
		val next = maxOf(0, minOf(listMaxScroll, listScroll + (if (scrollY > 0) -STAT_ROW else STAT_ROW)))
		if (next == listScroll) {
			return false
		}
		listScroll = next
		return true
	}

	fun render(
		g: GuiGraphicsExtractor, font: Font,
		x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int
	) {
		zones.clear()
		val leftW = maxOf(150, w * 42 / 100)
		val rightW = w - leftW - GAP
		PvDraw.innerPanel(g, x, y, leftW, h)
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h)
		drawOverview(g, font, x, y, leftW)
		drawSeaCreatureKills(g, font, x + leftW + GAP, y, rightW, h)
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		val zone = zones.firstOrNull { mouseX >= it.x && mouseX < it.x + it.w && mouseY >= it.y && mouseY < it.y + it.h } ?: return
		PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH)
	}

	private fun drawOverview(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int) {
		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2

		ly = drawBar(
			g, font, "Fishing", snapshot.fishingLevel.toString(),
			snapshot.fishingFill, snapshot.fishingMaxed, BAR_FISH,
			snapshot.fishingHover, lx, ly, lw
		) + BAR_AFTER + 2

		ly = sectionSeparator(g, font, x, ly, w)
		PvDraw.text(g, font, "Items fished", lx, ly, PvDraw.COLOR_MUTED)
		ly += font.lineHeight + 3
		ly = statLine(g, font, "Total", FormatUtil.commas(snapshot.itemsFishedTotal), lx, ly, lw, PvDraw.COLOR_ACCENT)
		ly = statLine(g, font, "Normal", FormatUtil.commas(snapshot.itemsFishedNormal), lx, ly, lw, PvDraw.COLOR_TEXT)
		ly = statLine(g, font, "Treasure", FormatUtil.commas(snapshot.itemsFishedTreasure), lx, ly, lw, PvDraw.COLOR_TEXT)
		ly = statLine(g, font, "Large treasure", FormatUtil.commas(snapshot.itemsFishedLargeTreasure), lx, ly, lw, PvDraw.COLOR_TEXT)
		ly = statLine(g, font, "Outstanding", FormatUtil.commas(snapshot.itemsFishedOutstanding), lx, ly, lw, PvDraw.COLOR_MUTED)

		ly = sectionSeparator(g, font, x, ly, w)
		PvDraw.text(g, font, "Sea creatures", lx, ly, PvDraw.COLOR_MUTED)
		ly += font.lineHeight + 3
		val killsY = ly
		ly = statLine(g, font, "Kills", FormatUtil.commas(snapshot.seaCreatureKills), lx, ly, lw, PvDraw.COLOR_ACCENT)
		zones.add(
			HoverZone(
				lx, killsY, lw, STAT_ROW, listOf(
					PvTooltip.Line.title("Sea creature kills", PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.of("Hypixel's sea creature kills stat.", PvDraw.COLOR_MUTED)
				)
			)
		)
		if (snapshot.dolphinMilestone >= 0L) {
			val rowY = ly
			ly = statLine(g, font, "Dolphin milestone", FormatUtil.commas(snapshot.dolphinMilestone), lx, ly, lw, PvDraw.COLOR_TEXT)
			zones.add(
				HoverZone(
					lx, rowY, lw, STAT_ROW, listOf(
						PvTooltip.Line.title("Dolphin milestone", PvDraw.COLOR_TEXT),
						PvTooltip.Line.divider(),
						PvTooltip.Line.of("Sea creatures killed toward the", PvDraw.COLOR_MUTED),
						PvTooltip.Line.of("Dolphin pet's milestone rewards.", PvDraw.COLOR_MUTED),
						PvTooltip.Line.of("Hypixel keeps this as a separate", PvDraw.COLOR_MUTED),
						PvTooltip.Line.of("counter from Kills, so the two", PvDraw.COLOR_MUTED),
						PvTooltip.Line.of("totals don't match.", PvDraw.COLOR_MUTED)
					)
				)
			)
		}
		statLine(g, font, "Festival sharks", FormatUtil.commas(snapshot.festivalSharksKilled), lx, ly, lw, TIER_GOLD)
	}

	private fun drawSeaCreatureKills(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int) {
		val rx = x + PAD
		var ry = y + PAD
		val rw = w - PAD * 2
		val bottom = y + h - PAD

		val category = bestiary.category(SEA_CREATURE_CATEGORY)
		val cachedRows = seaRows
		val rows = if (category !== seaRowsFor || cachedRows == null) {
			seaCreatureRows(category).also {
				seaRows = it
				seaRowsFor = category
			}
		} else {
			cachedRows
		}
		val families = category?.families?.size ?: 0
		PvDraw.text(g, font, "Sea creature kills", rx, ry, PvDraw.COLOR_MUTED)
		if (families > 0) {
			PvDraw.textRight(g, font, "${rows.size} / $families killed", rx + rw, ry, PvDraw.COLOR_ACCENT)
		}
		zones.add(
			HoverZone(
				rx, ry, rw, font.lineHeight, listOf(
					PvTooltip.Line.title("Sea creature kills", PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.of("Kills per sea creature from the bestiary.", PvDraw.COLOR_MUTED),
					PvTooltip.Line.of("The total on the left comes from player", PvDraw.COLOR_MUTED),
					PvTooltip.Line.of("stats, so the two can differ.", PvDraw.COLOR_MUTED)
				)
			)
		)
		ry += font.lineHeight + 4

		listX = rx
		listY = ry
		listW = rw
		listH = maxOf(0, bottom - ry)
		if (rows.isEmpty()) {
			listMaxScroll = 0
			val empty = if (category == null) "No bestiary data" else "No sea creature kills"
			PvDraw.textCentered(g, font, empty, rx + rw / 2, ry + listH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			return
		}
		listMaxScroll = maxOf(0, rows.size * STAT_ROW - listH)
		listScroll = minOf(listScroll, listMaxScroll)
		val textW = if (listMaxScroll > 0) rw - 5 else rw

		g.enableScissor(rx, ry, rx + rw + 3, ry + listH)
		for ((i, fam) in rows.withIndex()) {
			val rowY = ry + i * STAT_ROW - listScroll
			if (rowY + STAT_ROW <= ry || rowY >= ry + listH) {
				continue
			}
			val kills = FormatUtil.commas(fam.kills)
			val nameMax = maxOf(8, textW - font.width(kills) - 6)
			PvDraw.text(g, font, trim(font, fam.family().name, nameMax), rx, rowY, if (fam.maxed) COLOR_MAXED else PvDraw.COLOR_TEXT)
			PvDraw.textRight(g, font, kills, rx + textW, rowY, PvDraw.COLOR_ACCENT)
			val top = maxOf(rowY, ry)
			val hz = minOf(rowY + STAT_ROW, ry + listH) - top
			if (hz > 0) {
				zones.add(HoverZone(rx, top, textW, hz, seaCreatureTip(fam)))
			}
		}
		g.disableScissor()

		if (listMaxScroll > 0) {
			val contentH = rows.size * STAT_ROW
			val thumbH = maxOf(12, listH * listH / maxOf(1, contentH))
			val thumbY = ry + (listH - thumbH) * listScroll / listMaxScroll
			PvDraw.fill(g, rx + rw - 2, thumbY, 2, thumbH, PvDraw.COLOR_DIVIDER)
		}
	}

	private fun drawBar(
		g: GuiGraphicsExtractor, font: Font, label: String, value: String?, fill: Float, maxed: Boolean,
		color: Int, hover: String?, x: Int, y: Int, w: Int
	): Int {
		val shown = fitValue(font, label, value ?: "", w)
		PvDraw.labeledBar(g, font, trim(font, label, maxOf(24, w - font.width(shown) - 8)), shown, fill, x, y, w, color, maxed)
		val bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT
		if (!hover.isNullOrBlank()) {
			zones.add(HoverZone(x, y, w, bottom - y, Leveling.hoverTextLines(hover)))
		}
		return bottom
	}

	private data class HoverZone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>)

	companion object {
		private const val PAD = 6
		private const val GAP = 6
		private const val BAR_LABEL_GAP = 2
		private const val BAR_AFTER = 4
		private const val STAT_ROW = 12
		private const val SEP_GAP = 10
		private const val BAR_FISH = 0xFF55AAFF.toInt()
		private const val SLOT = 16
		private const val TIER_BORDER = 1
		private const val COLOR_MAXED = 0xFF7CFF9A.toInt()
		private const val SEA_CREATURE_CATEGORY = "fishing"
		private const val NAME_COLOR = 0xFFC9A6FF.toInt()
		private const val TIER_DIAMOND = 0xFF55FFFF.toInt()
		private const val TIER_GOLD = 0xFFFFE566.toInt()
		private const val TIER_SILVER = 0xFFD0D0D8.toInt()
		private const val TIER_BRONZE = 0xFFA05A2C.toInt()
		private const val TIP_BG = 0xF0111116.toInt()
		private const val TIP_BORDER = 0x28FFFFFF
		private const val TIP_TOTAL_LABEL = 0xFF9A9AA5.toInt()
		private const val TIP_TOTAL_VALUE = 0xFFE8E8EE.toInt()
		private const val TIP_COUNT = 0xFFC8C8D0.toInt()
		private const val TIP_PAD_X = 12
		private const val TIP_PAD_Y = 10
		private const val TIP_ROW_GAP = 4
		private const val TIP_MIN_W = 160

		private fun seaCreatureTip(fam: BestiarySnapshot.FamilyProgress): List<PvTooltip.Line> {
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title(fam.family().name, PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.divider())
			tip.add(PvTooltip.Line.row("Kills", PvDraw.COLOR_MUTED, FormatUtil.commas(fam.kills), PvDraw.COLOR_ACCENT))
			tip.add(PvTooltip.Line.row("Deaths", PvDraw.COLOR_MUTED, FormatUtil.commas(fam.deaths), PvDraw.COLOR_TEXT))
			if (fam.tiersMax > 0) {
				val tier = if (fam.maxed) "Maxed" else "${fam.tier} / ${fam.tiersMax}"
				tip.add(PvTooltip.Line.row("Bestiary tier", PvDraw.COLOR_MUTED, tier, if (fam.maxed) COLOR_MAXED else PvDraw.COLOR_TEXT))
			}
			return tip
		}

		@JvmStatic
		fun seaCreatureRows(category: BestiarySnapshot.CategoryProgress?): List<BestiarySnapshot.FamilyProgress> {
			if (category == null) {
				return listOf()
			}
			return category.families.filter { it.kills > 0L }.sortedWith(
				compareByDescending<BestiarySnapshot.FamilyProgress> { it.kills }
					.thenBy(String.CASE_INSENSITIVE_ORDER) { it.family().name }
			)
		}

		@JvmStatic
		fun drawTierBorder(g: GuiGraphicsExtractor, bx: Int, by: Int, color: Int) {
			val t = TIER_BORDER
			PvDraw.fill(g, bx, by, SLOT, t, color)
			PvDraw.fill(g, bx, by + SLOT - t, SLOT, t, color)
			PvDraw.fill(g, bx, by + t, t, SLOT - t * 2, color)
			PvDraw.fill(g, bx + SLOT - t, by + t, t, SLOT - t * 2, color)
		}

		@JvmStatic
		fun sortedRows(rows: List<FishingSnapshot.TrophyRow>?): List<FishingSnapshot.TrophyRow> {
			if (rows.isNullOrEmpty()) {
				return listOf()
			}
			return rows.sortedWith(
				compareBy<FishingSnapshot.TrophyRow> { -(it.counts?.bestRank() ?: 0) }
					.thenBy { -(it.counts?.discoveredTier() ?: 0) }
					.thenBy { -(if (it.countsExact) it.totalCaught() else 0) }
					.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
			)
		}

		@JvmStatic
		fun drawTrophyStatTooltip(
			g: GuiGraphicsExtractor, font: Font, row: FishingSnapshot.TrophyRow,
			mouseX: Int, mouseY: Int, screenW: Int, screenH: Int
		) {
			val title = row.name
			val totalLabel = "Total caught"
			val totalValue = when {
				row.countsExact -> FormatUtil.commas(row.totalCaught().toLong())
				row.anyDiscovered() -> "Discovered"
				else -> "Undiscovered"
			}

			val tiers = TrophyFishData.Tier.topToBottom()
			val tierLabels = tiers.map { tierTitle(it) }
			val tierValues = tiers.map {
				val count = row.counts?.of(it) ?: 0
				when {
					count <= 0 -> "Undiscovered"
					row.countsExact -> FormatUtil.commas(count.toLong())
					else -> "Caught"
				}
			}

			val howto = trophyHowtoFromNeu(row)

			var contentW = maxOf(
				font.width(PvDraw.styled(title, NAME_COLOR, true)),
				font.width(totalLabel) + 10 + font.width(PvDraw.styled(totalValue, TIP_TOTAL_VALUE, true))
			)
			for (i in tiers.indices) {
				contentW = maxOf(contentW, font.width(tierLabels[i]) + 10 + font.width(tierValues[i]))
			}
			if (!howto.isNullOrBlank()) {
				contentW = maxOf(contentW, font.width(howto))
			}
			contentW = maxOf(TIP_MIN_W, contentW)
			val boxW = contentW + TIP_PAD_X * 2

			val titleH = font.lineHeight
			val dividerGap = 3
			val totalH = font.lineHeight + 1
			val tierH = font.lineHeight
			val howtoH = if (howto.isNullOrBlank()) 0 else font.lineHeight + TIP_ROW_GAP
			val boxH = TIP_PAD_Y * 2 +
				titleH +
				dividerGap + 1 + dividerGap +
				totalH +
				TIP_ROW_GAP +
				tiers.size * tierH +
				maxOf(0, tiers.size - 1) * TIP_ROW_GAP +
				howtoH

			var x = mouseX + 12
			var y = mouseY - 12
			if (x + boxW > screenW - 4) {
				x = mouseX - boxW - 8
			}
			if (y + boxH > screenH - 4) {
				y = screenH - boxH - 4
			}
			if (x < 4) {
				x = 4
			}
			if (y < 4) {
				y = 4
			}

			PvDraw.roundedFill(g, x, y, boxW, boxH, 3, TIP_BG)
			PvDraw.roundedRing(g, x, y, boxW, boxH, 3, TIP_BORDER, PvDraw.C_ALL)

			val tx = x + TIP_PAD_X
			var ty = y + TIP_PAD_Y
			val right = x + boxW - TIP_PAD_X

			PvDraw.text(g, font, PvDraw.styled(title, NAME_COLOR, true), tx, ty)
			ty += titleH + dividerGap
			PvDraw.fill(g, tx, ty, contentW, 1, TIP_BORDER)
			ty += 1 + dividerGap

			PvDraw.text(g, font, totalLabel, tx, ty, TIP_TOTAL_LABEL)
			val totalComp = PvDraw.styled(totalValue, TIP_TOTAL_VALUE, true)
			PvDraw.text(g, font, totalComp, right - font.width(totalComp), ty)
			ty += totalH + TIP_ROW_GAP

			for (i in tiers.indices) {
				PvDraw.text(g, font, tierLabels[i], tx, ty, tierColor(tiers[i]))
				PvDraw.textRight(g, font, tierValues[i], right, ty, TIP_COUNT)
				ty += tierH + TIP_ROW_GAP
			}
			if (!howto.isNullOrBlank()) {
				PvDraw.text(g, font, howto, tx, ty, PvDraw.COLOR_MUTED)
			}
		}

		private fun trophyHowtoFromNeu(row: FishingSnapshot.TrophyRow?): String? {
			if (row == null || row.id.isBlank()) {
				return null
			}
			val tier = row.counts?.bestDiscovered() ?: TrophyFishData.Tier.BRONZE
			val neu = NeuRepoCache.get(row.id + "_" + tier.suffix())
			if (neu == null || !neu.has("lore") || !neu.get("lore").isJsonArray) {
				return null
			}
			val lore = neu.getAsJsonArray("lore").filter { it != null && it.isJsonPrimitive }.map { it.asString }
			return TrophyFishData.howtoFromLore(lore)
		}

		@JvmStatic
		fun tierTitle(tier: TrophyFishData.Tier): String = when (tier) {
			TrophyFishData.Tier.DIAMOND -> "Diamond"
			TrophyFishData.Tier.GOLD -> "Gold"
			TrophyFishData.Tier.SILVER -> "Silver"
			TrophyFishData.Tier.BRONZE -> "Bronze"
		}

		@JvmStatic
		fun tierColor(tier: TrophyFishData.Tier): Int = when (tier) {
			TrophyFishData.Tier.DIAMOND -> TIER_DIAMOND
			TrophyFishData.Tier.GOLD -> TIER_GOLD
			TrophyFishData.Tier.SILVER -> TIER_SILVER
			TrophyFishData.Tier.BRONZE -> TIER_BRONZE
		}

		@JvmStatic
		fun prettyLast(raw: String?): String {
			if (raw.isNullOrBlank()) {
				return ""
			}
			val parts = raw.split("/").dropLastWhile { it.isEmpty() }
			if (parts.isEmpty()) {
				return ""
			}
			val sb = StringBuilder()
			for (p in parts[0].replace('_', ' ').split(" ")) {
				if (p.isEmpty()) {
					continue
				}
				if (sb.isNotEmpty()) {
					sb.append(' ')
				}
				sb.append(p[0].uppercaseChar())
				if (p.length > 1) {
					sb.append(p.substring(1).lowercase(Locale.ROOT))
				}
			}
			if (parts.size > 1 && parts[1].isNotEmpty()) {
				sb.append(" · ").append(parts[1].substring(0, 1).uppercase(Locale.ROOT))
					.append(parts[1].substring(1).lowercase(Locale.ROOT))
			}
			return sb.toString()
		}

		private fun sectionSeparator(g: GuiGraphicsExtractor, font: Font, panelX: Int, y: Int, panelW: Int): Int {
			val visualBottom = y - maxOf(0, STAT_ROW - font.lineHeight)
			val pad = SEP_GAP / 2
			val lineY = visualBottom + pad
			val lineInset = PAD + 4
			val lineW = maxOf(0, panelW - lineInset * 2)
			if (lineW > 0) {
				PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF)
			}
			return lineY + 1 + pad
		}

		private fun statLine(g: GuiGraphicsExtractor, font: Font, label: String, value: String?, x: Int, y: Int, w: Int, valueColor: Int): Int {
			val r = value ?: ""
			val leftMax = maxOf(8, w - font.width(r) - 6)
			PvDraw.text(g, font, trim(font, label, leftMax), x, y, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, r, x + w, y, valueColor)
			return y + STAT_ROW
		}

		private fun fitValue(font: Font, label: String, value: String, w: Int): String {
			val max = maxOf(8, w - font.width(label) - 10)
			return trim(font, value, max)
		}

		@JvmStatic
		fun trim(font: Font, text: String?, maxW: Int): String {
			if (text == null) {
				return ""
			}
			if (font.width(text) <= maxW) {
				return text
			}
			val ellipsis = "..."
			val ellipsisW = font.width(ellipsis)
			if (maxW <= ellipsisW) {
				return ellipsis
			}
			val sb = StringBuilder()
			for (c in text) {
				if (font.width(sb.toString() + c) + ellipsisW > maxW) {
					break
				}
				sb.append(c)
			}
			return sb.toString() + ellipsis
		}
	}
}
