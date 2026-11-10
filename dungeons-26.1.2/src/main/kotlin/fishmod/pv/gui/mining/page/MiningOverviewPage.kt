/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.mining.page

import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.MiningSnapshot
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.dungeons.EssencePerkTips
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.essence.EssenceIcons
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.mining.MiningUi
import fishmod.pv.gui.mining.MiningUi.HoverZone
import fishmod.pv.gui.overview.SkillStatRows
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class MiningOverviewPage {
	private var scroll = 0
	private var maxScroll = 0
	private var scrollTop = 0
	private var scrollH = 0

	private var crystalsEssenceFace = false
	private var crystalsFlipStartMs = 0L
	private var crystalsFlipTarget = false
	private var crystalsHitX = 0
	private var crystalsHitY = 0
	private var crystalsHitW = 0
	private var crystalsHitH = 0

	/** Profile apply: HEAD only zeroed shared scroll, not the crystals flip face. */
	fun resetScroll() {
		scroll = 0
	}

	fun onEnter() {
		scroll = 0
	}

	fun leaveOverview() {
		crystalsEssenceFace = false
		crystalsFlipStartMs = 0L
		crystalsHitW = 0
		crystalsHitH = 0
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (crystalsHitW <= 0 || crystalsHitH <= 0) {
			return false
		}
		if (mx < crystalsHitX || mx >= crystalsHitX + crystalsHitW || my < crystalsHitY || my >= crystalsHitY + crystalsHitH) {
			return false
		}
		if (crystalsFlipStartMs != 0L) {
			return true
		}
		crystalsFlipTarget = !crystalsEssenceFace
		crystalsFlipStartMs = System.currentTimeMillis()
		return true
	}

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
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, stats: PlayerStatsSnapshot?, zones: MutableList<HoverZone>,
		x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		val rightW = maxOf(200, w * 55 / 100)
		val leftW = w - rightW - MiningUi.GAP
		PvDraw.innerPanel(g, x, y, leftW, h)

		val lx = x + MiningUi.PAD
		var ly = y + MiningUi.PAD
		val lw = leftW - MiningUi.PAD * 2

		ly = MiningUi.drawBar(
			g, font, "HOTM", snapshot.hotmLevel.toString(), snapshot.hotmFill, snapshot.hotmMaxed, MiningUi.BAR_HOTM,
			snapshot.hotmHover, lx, ly, lw, mx, my, zones,
		) + MiningUi.BAR_AFTER
		ly = MiningUi.drawBar(
			g, font, "Mining", snapshot.miningLevel.toString(), snapshot.miningFill, snapshot.miningMaxed, MiningUi.BAR_MINING,
			snapshot.miningHover, lx, ly, lw, mx, my, zones,
		) + MiningUi.BAR_AFTER + 2

		val cwTop = ly
		ly = MiningUi.statLine(g, font, "ColeWeight", FormatUtil.oneDecimal(snapshot.coleWeight.total), lx, ly, lw, PvDraw.COLOR_GOLD) + 2
		zones.add(HoverZone.coleWeight(lx, cwTop, lw, MiningUi.STAT_ROW))

		ly = MiningUi.drawPowdersBlock(g, font, snapshot, lx, ly, lw, zones)
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW)

		ly = MiningUi.statLine(g, font, "Tokens spent", snapshot.tokensSpent.toString(), lx, ly, lw, PvDraw.COLOR_TEXT) + 1
		ly = MiningUi.statLine(g, font, "Ability", MiningUi.trim(font, snapshot.selectedAbilityName, lw / 2), lx, ly, lw, PvDraw.COLOR_ACCENT) + 1
		ly = MiningUi.statLine(g, font, "Sky Mall", MiningUi.trim(font, snapshot.skyMallEffect, lw / 2), lx, ly, lw, PvDraw.COLOR_GOLD)
		ly = MiningUi.sectionSeparator(g, font, x, ly, leftW)

		if (snapshot.commissionMilestone > 0) {
			ly = MiningUi.statLine(g, font, "Commissions", "Tier " + snapshot.commissionMilestone, lx, ly, lw, PvDraw.COLOR_TEXT) + 1
		}

		if (snapshot.miningFiestaOres > 0L) {
			ly = MiningUi.statLine(g, font, "Fiesta ores", FormatUtil.commas(snapshot.miningFiestaOres), lx, ly, lw, PvDraw.COLOR_GOLD)
		}

		val bottom = y + h - MiningUi.PAD
		if (ly + MiningUi.STAT_ROW * 2 <= bottom) {
			ly = MiningUi.sectionSeparator(g, font, x, ly, leftW)
			for (id in SKILL_STATS) {
				if (ly + MiningUi.STAT_ROW > bottom) {
					break
				}
				val stat = SkillStatRows.row(stats, id, STAT_EXCLUSIONS)
				zones.add(HoverZone.of(lx, ly, lw, MiningUi.STAT_ROW, stat.tip.orEmpty()))
				ly = MiningUi.coloredLabelStat(g, font, stat.label.orEmpty(), stat.value, lx, ly, lw, stat.labelColor, stat.valueColor) + 1
			}
		}

		drawCrystalsPanel(g, font, snapshot, zones, x + leftW + MiningUi.GAP, y, rightW, h, mx, my)
	}

	private fun drawCrystalsPanel(
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, zones: MutableList<HoverZone>,
		x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		crystalsHitX = x
		crystalsHitY = y
		crystalsHitW = w
		crystalsHitH = h

		val hovered = mx >= x && mx < x + w && my >= y && my < y + h
		var flipProgress = 0f
		var animating = crystalsFlipStartMs != 0L
		if (animating) {
			flipProgress = minOf(1f, (System.currentTimeMillis() - crystalsFlipStartMs) / FLIP_MS.toFloat())
			if (flipProgress >= 1f) {
				crystalsEssenceFace = crystalsFlipTarget
				crystalsFlipStartMs = 0L
				animating = false
				flipProgress = 0f
			}
		}
		val angle = (if (animating) easeInOutCubic(flipProgress) else 0f) * Math.PI.toFloat()
		val showEssence = if (animating && cos(angle) < 0.0) crystalsFlipTarget else crystalsEssenceFace
		var slideDx = 0f
		if (animating) {
			slideDx = PvDraw.slideOffset(angle / Math.PI.toFloat(), w)
		}
		PvDraw.innerPanel(g, x, y, w, h)
		g.enableScissor(x, y, x + w, y + h)
		g.pose().pushMatrix()
		g.pose().translate(slideDx, 0f)
		if (hovered && !animating) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER)
		}

		if (showEssence) {
			maxScroll = 0
			drawMiningEssenceFace(g, font, snapshot, zones, x, y, w, h)
		} else {
			drawCrystalsFace(g, font, snapshot, zones, x, y, w, h)
		}

		g.pose().popMatrix()
		g.disableScissor()
	}

	private fun drawCrystalsFace(
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, zones: MutableList<HoverZone>,
		x: Int, y: Int, w: Int, h: Int,
	) {
		val rx = x + MiningUi.PAD
		var ry = y + MiningUi.PAD
		val rw = w - MiningUi.PAD * 2
		val bottom = y + h - MiningUi.PAD
		PvDraw.text(g, font, "Crystals", rx, ry, PvDraw.COLOR_MUTED)
		ry += font.lineHeight + 4
		val crystals = snapshot.crystals
		if (crystals.isEmpty()) {
			PvDraw.text(g, font, "No crystal data", rx, ry, PvDraw.COLOR_MUTED)
			maxScroll = 0
			return
		}

		val cols = 2
		val colGap = 10
		val colW = maxOf(40, (rw - colGap * (cols - 1)) / cols)
		val cellH = maxOf(MiningUi.STAT_ROW, CRYSTAL_ICON) + 4
		val rows = (crystals.size + cols - 1) / cols
		val gridH = rows * cellH

		val hollows = snapshot.goblinKingQuests > 0 || snapshot.jungleTempleOpen || snapshot.precursorTalked
		val hollowsReserve = if (hollows) {
			(if (snapshot.goblinKingQuests > 0) MiningUi.STAT_ROW + 1 else 0) + MiningUi.STAT_ROW + 1 + MiningUi.STAT_ROW + 6
		} else {
			0
		}

		scrollTop = ry
		scrollH = maxOf(0, bottom - ry - MiningUi.STAT_ROW - 8 - hollowsReserve)
		maxScroll = maxOf(0, gridH - scrollH)
		scroll = minOf(scroll, maxScroll)

		g.enableScissor(rx, scrollTop, rx + rw + 3, scrollTop + scrollH)
		for ((i, crystal) in crystals.withIndex()) {
			val cx = rx + (i % cols) * (colW + colGap)
			val cy = ry + (i / cols) * cellH - scroll
			drawCrystalCell(g, font, crystal, cx, cy, colW, cellH)
		}
		g.disableScissor()

		val nucY = if (maxScroll <= 0) ry + gridH + 8 else scrollTop + scrollH + 6
		val derived = snapshot.nucleusRuns()
		val api = snapshot.apiNucleusRuns
		MiningUi.statLine(g, font, "Nucleus runs", FormatUtil.commas(derived), rx, nucY, rw, PvDraw.COLOR_ACCENT)
		if (api > 0L || derived > 0L) {
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title("Nucleus runs", PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.divider())
			tip.add(PvTooltip.Line.row("From crystals", PvDraw.COLOR_MUTED, FormatUtil.commas(derived), PvDraw.COLOR_ACCENT))
			if (api > 0L) {
				tip.add(PvTooltip.Line.row("API", PvDraw.COLOR_MUTED, FormatUtil.commas(api), PvDraw.COLOR_GOLD))
			}
			zones.add(HoverZone.of(rx, nucY, rw, MiningUi.STAT_ROW, tip))
		}

		if (hollows) {
			var hy = nucY + MiningUi.STAT_ROW + 6
			if (snapshot.goblinKingQuests > 0) {
				hy = MiningUi.statLine(g, font, "Goblin King quests", snapshot.goblinKingQuests.toString(), rx, hy, rw, PvDraw.COLOR_TEXT) + 1
			}
			hy = MiningUi.statLine(
				g, font, "Jungle Temple", if (snapshot.jungleTempleOpen) "Open" else "Closed",
				rx, hy, rw, if (snapshot.jungleTempleOpen) MiningUi.PLACED else PvDraw.COLOR_MUTED,
			) + 1
			MiningUi.statLine(
				g, font, "Precursor", if (snapshot.precursorTalked) "Yes" else "No",
				rx, hy, rw, if (snapshot.precursorTalked) MiningUi.PLACED else PvDraw.COLOR_MUTED,
			)
		}
	}

	/** Gold / Diamond essence shops stacked full-width (names fit; rows fill each half). */
	private fun drawMiningEssenceFace(
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, zones: MutableList<HoverZone>, x: Int, y: Int, w: Int, h: Int,
	) {
		val pad = 6
		val cx = x + pad
		val cy = y + pad
		val innerW = w - pad * 2
		val bottom = y + h - pad
		val innerH = maxOf(0, bottom - cy)
		val sectionGap = 8
		val halfH = maxOf(40, (innerH - sectionGap) / 2)

		val headerH = maxOf(ESSENCE_HEADER_ICON, font.lineHeight + 2)
		val goldTop = cy
		val goldBottom = cy + halfH
		val diamondTop = goldBottom + sectionGap

		val goldPerks = maxOf(1, snapshot.goldShop.perks.size)
		val diamondPerks = maxOf(1, snapshot.diamondShop.perks.size)
		val goldBody = maxOf(0, goldBottom - goldTop - headerH - 4)
		val diamondBody = maxOf(0, bottom - diamondTop - headerH - 4)
		val goldRowH = maxOf(font.lineHeight + 2, maxOf(ESSENCE_PERK_ICON + 3, goldBody / goldPerks))
		val diamondRowH = maxOf(font.lineHeight + 2, maxOf(ESSENCE_PERK_ICON + 3, diamondBody / diamondPerks))

		drawMiningEssenceColumn(g, font, snapshot.goldShop, zones, cx, goldTop, innerW, headerH, goldRowH, goldBottom, GOLD_COLOR)
		PvDraw.fill(g, cx, goldBottom + sectionGap / 2, innerW, 1, PvDraw.COLOR_DIVIDER)
		drawMiningEssenceColumn(g, font, snapshot.diamondShop, zones, cx, diamondTop, innerW, headerH, diamondRowH, bottom, DIAMOND_COLOR)
	}

	private fun drawMiningEssenceColumn(
		g: GuiGraphicsExtractor,
		font: Font,
		shop: DungeonSnapshot.EssenceShop,
		zones: MutableList<HoverZone>,
		x: Int,
		y: Int,
		w: Int,
		headerH: Int,
		rowH: Int,
		bottom: Int,
		headerColor: Int,
	) {
		val gap = 3
		val headerLabelX = x + ESSENCE_HEADER_ICON + gap
		val headerAlign = PvDraw.IconTextAlign.of(y, headerH, ESSENCE_HEADER_ICON, font.lineHeight)

		MiningUi.drawItemIcon(g, essenceIcon(shop.iconId), x, headerAlign.iconY, ESSENCE_HEADER_ICON)
		val bal = FormatUtil.commas(shop.balance)
		val balW = PvDraw.widthBold(font, bal)
		val nameMax = maxOf(8, w - (headerLabelX - x) - balW - 4)
		PvDraw.textBold(g, font, MiningUi.trim(font, shop.name, nameMax), headerLabelX, headerAlign.textY, headerColor)
		PvDraw.textBold(g, font, bal, x + w - balW, headerAlign.textY, headerColor)

		val perkLabelX = x + ESSENCE_PERK_ICON + gap
		var ly = y + headerH + 4
		for (perk in shop.perks) {
			if (ly + font.lineHeight > bottom) {
				break
			}
			val rowAlign = PvDraw.IconTextAlign.of(ly, rowH, ESSENCE_PERK_ICON, font.lineHeight)
			MiningUi.drawItemIcon(g, EssenceIcons.perk(perk.id), x, rowAlign.iconY, ESSENCE_PERK_ICON)
			val right = "${perk.level}/${perk.maxLevel}"
			val rightW = font.width(right)
			val left = MiningUi.trim(font, perk.name, maxOf(8, w - (perkLabelX - x) - rightW - 4))
			val valueColor = if (perk.maxed()) COLOR_MAXED else PvDraw.COLOR_TEXT
			PvDraw.text(g, font, left, perkLabelX, rowAlign.textY, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, right, x + w, rowAlign.textY, valueColor)
			zones.add(HoverZone.of(x, ly, w, rowH, EssencePerkTips.tip(perk)))
			ly += rowH
		}
	}

	private fun drawCrystalCell(g: GuiGraphicsExtractor, font: Font, c: MiningSnapshot.Crystal, x: Int, y: Int, w: Int, cellH: Int) {
		val nameColor = if (c.found()) crystalColor(c.id) else mixMuted(crystalColor(c.id))
		val stateColor = if (c.placed()) MiningUi.PLACED else if (c.found()) MiningUi.FOUND else MiningUi.MISSING
		val state = prettyState(c.state)

		drawCrystalIcon(g, c.id, x, y + maxOf(0, (cellH - CRYSTAL_ICON) / 2), CRYSTAL_ICON)

		val textX = x + CRYSTAL_ICON + 4
		val textW = maxOf(8, w - CRYSTAL_ICON - 4)
		val textY = y + maxOf(0, (cellH - font.lineHeight) / 2)
		val name = MiningUi.trim(font, c.name, maxOf(8, textW - font.width(state) - 6))
		PvDraw.text(g, font, name, textX, textY, nameColor)
		PvDraw.textRight(g, font, state, textX + textW, textY, stateColor)
	}

	private companion object {
		const val CRYSTAL_ICON = 14
		const val FLIP_MS = 480
		const val PANEL_HOVER = 0x0AFFFFFF
		const val GOLD_COLOR = 0xFFFFAA00.toInt()
		const val DIAMOND_COLOR = 0xFF55FFFF.toInt()
		const val ESSENCE_HEADER_ICON = 16
		const val ESSENCE_PERK_ICON = 10
		const val COLOR_MAXED = 0xFF7CFF9A.toInt()
		val SKILL_STATS = arrayOf("mining_speed", "mining_fortune", "pristine", "mining_spread")
		val STAT_EXCLUSIONS = listOf(
			"Excludes held tool, set bonuses,",
			"temporary buffs and location bonuses.",
		)

		fun essenceIcon(skyblockId: String?): ItemStack {
			val stack = SkyBlockItemFactory.iconStack(skyblockId ?: "")
			return if (stack.isEmpty) ItemStack(Items.PLAYER_HEAD) else stack
		}

		fun easeInOutCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

		/** Flawless gemstone player-head cubes (classic NBT skulls). */
		fun drawCrystalIcon(g: GuiGraphicsExtractor, crystalApiId: String, x: Int, y: Int, size: Int) {
			val head = SkyBlockItemFactory.gemstoneHead(crystalApiId)
			MiningUi.drawItemIcon(g, if (head.isEmpty) ItemStack(Items.PLAYER_HEAD) else head, x, y, size)
		}

		fun crystalColor(apiId: String?): Int {
			val id = apiId?.lowercase(Locale.ROOT) ?: ""
			return when {
				"jade" in id -> 0xFF55FF55.toInt()
				"amber" in id -> 0xFFFFAA00.toInt()
				"amethyst" in id -> 0xFFCC66FF.toInt()
				"sapphire" in id -> 0xFF55FFFF.toInt()
				"topaz" in id -> 0xFFFFE55A.toInt()
				"jasper" in id -> 0xFFFF55FF.toInt()
				"ruby" in id -> 0xFFFF5555.toInt()
				"aquamarine" in id -> 0xFF5599FF.toInt()
				"citrine" in id -> 0xFFFF5555.toInt()
				"peridot" in id -> 0xFF88FF44.toInt()
				"onyx" in id -> 0xFFB0B0B8.toInt()
				"opal" in id -> 0xFFE8E8F0.toInt()
				else -> PvDraw.COLOR_ACCENT
			}
		}

		fun mixMuted(argb: Int): Int {
			val a = (argb ushr 24) and 0xFF
			val r = (((argb ushr 16) and 0xFF) + 0x9A) / 2
			val g = (((argb ushr 8) and 0xFF) + 0x9A) / 2
			val b = ((argb and 0xFF) + 0xAC) / 2
			return (a shl 24) or (r shl 16) or (g shl 8) or b
		}

		fun prettyState(state: String?): String {
			if (state.isNullOrBlank()) {
				return "-"
			}
			return when (state.uppercase(Locale.ROOT)) {
				"PLACED" -> "Placed"
				"FOUND" -> "Found"
				"NOT_FOUND" -> "Missing"
				else -> MiningUi.title(state)
			}
		}
	}
}
