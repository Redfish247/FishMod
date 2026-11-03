/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.mining.page

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.MiningHotmData
import fishmod.pv.data.MiningSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.mining.MiningUi
import fishmod.pv.gui.mining.MiningUi.HoverZone
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** HOTM subtab: powders | perk tree | forge / unlocked flip. Owns HOTM-specific scroll fields. */
class HotmPage {
	private var scroll = 0
	private var maxScroll = 0
	private var scrollTop = 0
	private var scrollH = 0
	private var hotmUnlockedScrollLeft = 0
	private var hotmUnlockedScrollTop = 0
	private var hotmUnlockedScrollW = 0
	private var hotmUnlockedScrollH = 0
	private var hotmUnlockedMaxScroll = 0
	private var treeScroll = 0
	private var treeMaxScroll = 0
	private var treeLeft = 0
	private var treeTop = 0
	private var treeW = 0
	private var treeH = 0

	/** false = forge face, true = unlocked perks face. */
	private var unlockedFace = false
	private var flipTarget = false
	private var flipStartMs = 0L
	private var forgeHitX = 0
	private var forgeHitY = 0
	private var forgeHitW = 0
	private var forgeHitH = 0

	fun reset() {
		scroll = 0
		maxScroll = 0
		treeScroll = 0
		treeMaxScroll = 0
		treeW = 0
		treeH = 0
		clearUnlockedScroll()
		unlockedFace = false
		flipStartMs = 0L
		forgeHitW = 0
		forgeHitH = 0
	}

	fun onEnter() {
		scroll = 0
		treeScroll = 0
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (forgeHitW <= 0 || forgeHitH <= 0) {
			return false
		}
		if (mx < forgeHitX || mx >= forgeHitX + forgeHitW || my < forgeHitY || my >= forgeHitY + forgeHitH) {
			return false
		}
		if (flipStartMs != 0L) {
			return true
		}
		flipTarget = !unlockedFace
		flipStartMs = System.currentTimeMillis()
		return true
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		val step = if (scrollY > 0) -14 else 14
		if (treeMaxScroll > 0 &&
			mouseX >= treeLeft && mouseX < treeLeft + treeW &&
			mouseY >= treeTop && mouseY < treeTop + treeH
		) {
			val next = maxOf(0, minOf(treeMaxScroll, treeScroll + step))
			if (next == treeScroll) {
				return false
			}
			treeScroll = next
			return true
		}
		if (hotmUnlockedMaxScroll <= 0) {
			return false
		}
		if (mouseX < hotmUnlockedScrollLeft || mouseX >= hotmUnlockedScrollLeft + hotmUnlockedScrollW ||
			mouseY < hotmUnlockedScrollTop || mouseY >= hotmUnlockedScrollTop + hotmUnlockedScrollH
		) {
			return false
		}
		val next = maxOf(0, minOf(hotmUnlockedMaxScroll, scroll + step))
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
		var sideW = maxOf(SIDE_MIN, minOf(140, w / 5))
		var centerW = w - sideW * 2 - MiningUi.GAP * 2
		if (centerW < 160) {
			sideW = maxOf(100, (w - MiningUi.GAP * 2 - 160) / 2)
			centerW = w - sideW * 2 - MiningUi.GAP * 2
		}

		val leftX = x
		val centerX = x + sideW + MiningUi.GAP
		val rightX = centerX + centerW + MiningUi.GAP

		PvDraw.innerPanel(g, leftX, y, sideW, h)
		PvDraw.innerPanel(g, centerX, y, centerW, h)

		renderHotmLeft(g, font, snapshot, zones, leftX, y, sideW, mx, my)
		renderHotmTree(g, font, snapshot, zones, centerX, y, centerW, h)
		renderForgeFlipPanel(g, font, snapshot, zones, rightX, y, sideW, h, mx, my)
	}

	private fun renderHotmLeft(
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, zones: MutableList<HoverZone>,
		x: Int, y: Int, w: Int, mx: Int, my: Int,
	) {
		val rx = x + MiningUi.PAD
		var ry = y + MiningUi.PAD
		val rw = w - MiningUi.PAD * 2

		ry = MiningUi.drawBar(
			g, font, "HOTM", snapshot.hotmLevel.toString(), snapshot.hotmFill, snapshot.hotmMaxed, MiningUi.BAR_HOTM,
			snapshot.hotmHover, rx, ry, rw, mx, my, zones,
		) + MiningUi.BAR_AFTER
		ry = MiningUi.drawBar(
			g, font, "Mining", snapshot.miningLevel.toString(), snapshot.miningFill, snapshot.miningMaxed, MiningUi.BAR_MINING,
			snapshot.miningHover, rx, ry, rw, mx, my, zones,
		) + MiningUi.BAR_AFTER + 2

		ry = MiningUi.drawPowdersBlock(g, font, snapshot, rx, ry, rw, zones) + 2
		ry = MiningUi.statLine(g, font, "Tokens spent", snapshot.tokensSpent.toString(), rx, ry, rw, PvDraw.COLOR_TEXT) + 2

		val cotmLevel = snapshot.nodeLevel("core_of_the_mountain")
		val cotmMax = MiningHotmData.perk("core_of_the_mountain")?.maxLevel ?: 10
		PvDraw.text(g, font, "Core of the Mountain", rx, ry, PvDraw.COLOR_MUTED)
		ry += font.lineHeight + 2
		val cotmColor = if (cotmLevel >= cotmMax) MiningUi.PLACED else PvDraw.COLOR_ACCENT
		PvDraw.text(g, font, "$cotmLevel / $cotmMax", rx, ry, cotmColor)
		zones.add(HoverZone.of(
			rx, ry - font.lineHeight - 2, rw, font.lineHeight + MiningUi.STAT_ROW,
			listOf(
				PvTooltip.Line.of("Core of the Mountain", PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("Level $cotmLevel / $cotmMax", PvDraw.COLOR_ACCENT),
			),
		))
		ry += font.lineHeight + 4

		PvDraw.text(g, font, "Ability", rx, ry, PvDraw.COLOR_MUTED)
		ry += font.lineHeight + 2
		MiningUi.wrapText(g, font, snapshot.selectedAbilityName, rx, ry, rw, PvDraw.COLOR_ACCENT)
		ry += font.lineHeight + 4

		val unlockedCount = unlockedNodes(snapshot).size
		PvDraw.text(g, font, "Unlocked", rx, ry, PvDraw.COLOR_MUTED)
		ry += font.lineHeight + 2
		PvDraw.text(g, font, unlockedCount.toString(), rx, ry, PvDraw.COLOR_ACCENT)
		zones.add(HoverZone.of(
			rx, ry - font.lineHeight - 2, rw, font.lineHeight + MiningUi.STAT_ROW,
			listOf(PvTooltip.Line.of("Unlocked perks: $unlockedCount", PvDraw.COLOR_TEXT)),
		))
	}

	private fun clearUnlockedScroll() {
		hotmUnlockedScrollLeft = 0
		hotmUnlockedScrollTop = 0
		hotmUnlockedScrollW = 0
		hotmUnlockedScrollH = 0
		hotmUnlockedMaxScroll = 0
	}

	private fun renderForgeFlipPanel(
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, zones: MutableList<HoverZone>,
		x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		forgeHitX = x
		forgeHitY = y
		forgeHitW = w
		forgeHitH = h

		val hovered = mx >= x && mx < x + w && my >= y && my < y + h
		var flipProgress = 0f
		var animating = flipStartMs != 0L
		if (animating) {
			flipProgress = minOf(1f, (System.currentTimeMillis() - flipStartMs) / FLIP_MS.toFloat())
			if (flipProgress >= 1f) {
				unlockedFace = flipTarget
				flipStartMs = 0L
				animating = false
				flipProgress = 0f
			}
		}
		val angle = (if (animating) easeInOutCubic(flipProgress) else 0f) * Math.PI.toFloat()
		val showUnlocked = if (animating && cos(angle) < 0.0) flipTarget else unlockedFace
		var scaleX = 1f
		var scaleY = 1f
		if (animating) {
			scaleX = maxOf(0.04f, abs(cos(angle)))
			scaleY = 1f - (1f - scaleX) * 0.06f
		}

		val cxFlip = x + w / 2f
		val cyFlip = y + h / 2f
		g.pose().pushMatrix()
		g.pose().translate(cxFlip, cyFlip)
		g.pose().scale(scaleX, scaleY)
		g.pose().translate(-cxFlip, -cyFlip)

		PvDraw.innerPanel(g, x, y, w, h)
		if (hovered && !animating) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER)
		}

		if (showUnlocked) {
			drawUnlockedFace(g, font, snapshot, zones, x, y, w, h)
		} else {
			clearUnlockedScroll()
			maxScroll = 0
			drawForgeFace(g, font, snapshot, x, y, w, h)
		}

		g.pose().popMatrix()
	}

	private fun drawForgeFace(g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, x: Int, y: Int, w: Int, h: Int) {
		val rx = x + MiningUi.PAD
		val ry = y + MiningUi.PAD
		val rw = w - MiningUi.PAD * 2
		PvDraw.text(g, font, "Forge", rx, ry, PvDraw.COLOR_MUTED)
		drawForgeList(g, font, snapshot, rx, ry + font.lineHeight + 3, rw, y + h - MiningUi.PAD)
	}

	private fun drawUnlockedFace(
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, zones: MutableList<HoverZone>,
		x: Int, y: Int, w: Int, h: Int,
	) {
		val rx = x + MiningUi.PAD
		var ry = y + MiningUi.PAD
		val rw = w - MiningUi.PAD * 2
		val bottom = y + h - MiningUi.PAD
		val unlocked = unlockedNodes(snapshot)
		PvDraw.text(g, font, "Unlocked (" + unlocked.size + ")", rx, ry, PvDraw.COLOR_MUTED)
		ry += font.lineHeight + 3

		if (unlocked.isEmpty()) {
			clearUnlockedScroll()
			maxScroll = 0
			scroll = 0
			PvDraw.text(g, font, "None", rx, ry, PvDraw.COLOR_MUTED)
			return
		}

		val availH = maxOf(font.lineHeight, bottom - ry)
		val rowH = MiningUi.STAT_ROW + 1
		val contentH = unlocked.size * rowH
		hotmUnlockedScrollLeft = rx
		hotmUnlockedScrollTop = ry
		hotmUnlockedScrollW = rw
		hotmUnlockedScrollH = availH
		hotmUnlockedMaxScroll = maxOf(0, contentH - availH)
		maxScroll = hotmUnlockedMaxScroll
		scrollTop = ry
		scrollH = availH
		scroll = maxOf(0, minOf(scroll, hotmUnlockedMaxScroll))

		g.enableScissor(rx, ry, rx + rw + 3, ry + availH)
		var yy = ry - scroll
		for (node in unlocked) {
			if (yy + rowH >= ry && yy <= ry + availH) {
				val valueColor = if (node.ability) PvDraw.COLOR_GOLD else if (node.maxed) MiningUi.PLACED else PvDraw.COLOR_TEXT
				val right = "${node.level}/${node.maxLevel}"
				val rightW = maxOf(1, font.width(right))
				PvDraw.text(g, font, MiningUi.trim(font, node.name, maxOf(8, rw - rightW - 6)), rx, yy, valueColor)
				PvDraw.textRight(g, font, right, rx + rw, yy, PvDraw.COLOR_MUTED)
				val tip = ArrayList<PvTooltip.Line>()
				tip.add(PvTooltip.Line.of(node.name, PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.of("Level " + node.level + " / " + node.maxLevel, PvDraw.COLOR_ACCENT))
				if (node.ability) {
					tip.add(PvTooltip.Line.of("Pickaxe Ability", PvDraw.COLOR_GOLD))
				}
				zones.add(HoverZone.of(rx, maxOf(ry, yy), rw, minOf(rowH, ry + availH - maxOf(ry, yy)), tip))
			}
			yy += rowH
		}
		g.disableScissor()
	}

	private fun renderHotmTree(
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, zones: MutableList<HoverZone>,
		x: Int, y: Int, w: Int, h: Int,
	) {
		val perks = MiningHotmData.perks()
		if (perks.isEmpty()) {
			PvDraw.textCentered(g, font, "HOTM layout unavailable", x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			return
		}

		val cols = MiningHotmData.maxX() + 1
		val rows = MiningHotmData.maxY() + 1
		val innerW = w - MiningUi.PAD * 2
		val innerH = h - MiningUi.PAD * 2
		val layout = treeLayout(cols, rows, innerW, innerH)
		val cell = layout.cell
		val cellGap = layout.gap
		treeMaxScroll = layout.maxScroll
		treeScroll = maxOf(0, minOf(treeScroll, treeMaxScroll))
		treeLeft = x
		treeTop = y
		treeW = w
		treeH = h
		val gridX = x + MiningUi.PAD + maxOf(0, (innerW - layout.gridW) / 2)
		val gridY = y + MiningUi.PAD + maxOf(0, (innerH - layout.gridH) / 2) - treeScroll
		val clipTop = y + 1
		val clipBottom = y + h - 1

		scrollTop = y + MiningUi.PAD
		scrollH = innerH

		g.enableScissor(x + 1, clipTop, x + w - 1, clipBottom)
		for (perk in perks) {
			val cx = gridX + perk.x * (cell + cellGap)
			val cy = gridY + perk.y * (cell + cellGap)
			if (cy + cell <= clipTop || cy >= clipBottom) {
				continue
			}
			val level = snapshot.nodeLevel(perk.id)

			PvDraw.roundedFill(g, cx, cy, cell, cell, 3, MiningUi.ITEM_SLOT_BG)
			PvDraw.roundedRing(g, cx, cy, cell, cell, 3, MiningUi.ITEM_SLOT_BORDER, PvDraw.C_ALL)

			val stack = perkIcon(perk, level)
			val iconScale = layout.iconScale
			val iconDraw = (16 * iconScale).roundToInt()
			val ix = cx + maxOf(0, (cell - iconDraw) / 2)
			val iy = cy + maxOf(0, (cell - iconDraw) / 2)
			if (iconScale == 1f) {
				g.item(stack, ix, iy)
			} else {
				g.pose().pushMatrix()
				g.pose().translate(ix.toFloat(), iy.toFloat())
				g.pose().scale(iconScale, iconScale)
				g.item(stack, 0, 0)
				g.pose().popMatrix()
			}

			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.of(perk.name, PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.of("Level " + level + " / " + perk.maxLevel, PvDraw.COLOR_ACCENT))
			if (perk.ability) {
				tip.add(PvTooltip.Line.of("Pickaxe Ability", PvDraw.COLOR_GOLD))
			}
			if (perk.powder.isNotBlank() && !perk.powder.startsWith("(")) {
				tip.add(PvTooltip.Line.of(MiningUi.title(perk.powder) + " powder", powderColor(perk.powder)))
			}
			val zoneTop = maxOf(cy, clipTop)
			val zoneBottom = minOf(cy + cell, clipBottom)
			zones.add(HoverZone.of(cx, zoneTop, cell, zoneBottom - zoneTop, tip))
		}
		g.disableScissor()
		if (treeMaxScroll > 0) {
			val trackH = h - 4
			val thumbH = maxOf(12, trackH * innerH / maxOf(1, layout.gridH))
			val thumbY = y + 2 + (trackH - thumbH) * treeScroll / treeMaxScroll
			PvDraw.fill(g, x + w - 4, thumbY, 2, thumbH, PvDraw.COLOR_DIVIDER)
		}
	}

	@JvmRecord
	data class TreeLayout(val cell: Int, val gap: Int, val iconScale: Float, val gridW: Int, val gridH: Int, val maxScroll: Int)

	private class UnlockedNode(val name: String, val level: Int, val maxLevel: Int, val ability: Boolean, val maxed: Boolean)

	companion object {
		private const val SIDE_MIN = 118
		private const val FLIP_MS = 480
		private const val PANEL_HOVER = 0x0AFFFFFF
		private const val MIN_TREE_SCALE = 0.7f

		/**
		 * Integer zoom when the tree fits (fractional scale softens item textures). Otherwise tighter cells, then
		 * a scaled-down grid, then vertical scrolling, so every node stays reachable at any GUI scale.
		 */
		@JvmStatic
		fun treeLayout(cols: Int, rows: Int, innerW: Int, innerH: Int): TreeLayout {
			val baseCell = 20
			val baseGap = 2
			val baseW = gridSize(cols, baseCell, baseGap)
			val baseH = gridSize(rows, baseCell, baseGap)
			if (baseW <= innerW && baseH <= innerH) {
				val zoom = maxOf(1, minOf(3, minOf(innerW / baseW, innerH / baseH)))
				return TreeLayout(
					baseCell * zoom, baseGap * zoom, zoom.toFloat(),
					gridSize(cols, baseCell * zoom, baseGap * zoom), gridSize(rows, baseCell * zoom, baseGap * zoom), 0,
				)
			}
			val tightCell = 18
			val tightGap = 1
			val tightW = gridSize(cols, tightCell, tightGap)
			val tightH = gridSize(rows, tightCell, tightGap)
			if (tightW <= innerW && tightH <= innerH) {
				return TreeLayout(tightCell, tightGap, 1f, tightW, tightH, 0)
			}
			val fit = minOf(innerW.toFloat() / tightW, innerH.toFloat() / tightH)
			val scale = maxOf(MIN_TREE_SCALE, minOf(1f, fit))
			val cell = maxOf(8, floor(tightCell * scale).toInt())
			val iconScale = minOf(1f, (cell - 1) / 16f)
			val gridW = gridSize(cols, cell, tightGap)
			val gridH = gridSize(rows, cell, tightGap)
			return TreeLayout(cell, tightGap, iconScale, gridW, gridH, maxOf(0, gridH - innerH))
		}

		private fun gridSize(count: Int, cell: Int, gap: Int): Int = count * cell + maxOf(0, count - 1) * gap

		private fun unlockedNodes(snapshot: MiningSnapshot): List<UnlockedNode> =
			MiningHotmData.perks()
				.filter { it.id != "core_of_the_mountain" }
				.mapNotNull { perk ->
					val level = snapshot.nodeLevel(perk.id)
					if (level <= 0) null else UnlockedNode(perk.name, level, perk.maxLevel, perk.ability, level >= perk.maxLevel)
				}
				.sortedWith(compareByDescending<UnlockedNode> { it.ability }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

		private fun drawForgeList(g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, x: Int, y: Int, w: Int, bottom: Int) {
			val forge = snapshot.forge
			if (forge.isEmpty()) {
				PvDraw.text(g, font, "No items forging", x, y, PvDraw.COLOR_MUTED)
				return
			}
			var ly = y
			for (p in forge) {
				if (ly + MiningUi.STAT_ROW > bottom) {
					break
				}
				val left = "Slot " + p.slot
				val right = MiningUi.trim(font, p.name, maxOf(24, w - font.width(left) - 8))
				ly = MiningUi.statLine(g, font, left, right, x, ly, w, PvDraw.COLOR_TEXT) + 1
				val ago = forgeAgo(p.startTimeMs)
				val endMs = snapshot.forgeEndMs(p)
				var eta = ""
				var etaColor = PvDraw.COLOR_MUTED
				if (endMs > 0L) {
					val remainingMs = endMs - System.currentTimeMillis()
					eta = if (remainingMs <= 0L) "Ready" else "Remaining: " + FormatUtil.prettySpan(remainingMs)
					etaColor = if (remainingMs <= 0L) MiningUi.PLACED else PvDraw.COLOR_GOLD
				}
				if ((ago.isNotBlank() || eta.isNotBlank()) && ly + MiningUi.STAT_ROW <= bottom) {
					ly = MiningUi.coloredLabelStat(g, font, eta, ago, x, ly, w, etaColor, PvDraw.COLOR_MUTED) + 1
				}
			}
		}

		private fun easeInOutCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

		/**
		 * Abilities: locked coal block, unlocked diamond block.
		 * CotM: diamond block only when maxed, otherwise coal block.
		 * Other perks: locked coal, unlocked emerald, maxed diamond.
		 */
		private fun perkIcon(perk: MiningHotmData.PerkDef, level: Int): ItemStack = ItemStack(
			when {
				perk.id == "core_of_the_mountain" -> if (level >= perk.maxLevel) Items.DIAMOND_BLOCK else Items.COAL_BLOCK
				perk.ability -> if (level > 0) Items.DIAMOND_BLOCK else Items.COAL_BLOCK
				level <= 0 -> Items.COAL
				level >= perk.maxLevel -> Items.DIAMOND
				else -> Items.EMERALD
			},
		)

		private fun powderColor(powder: String?): Int = when (powder?.uppercase(Locale.ROOT)) {
			"MITHRIL" -> MiningUi.BAR_MITHRIL
			"GEMSTONE" -> MiningUi.BAR_GEM
			"GLACITE" -> MiningUi.BAR_GLACITE
			else -> PvDraw.COLOR_MUTED
		}

		private fun forgeAgo(startMs: Long): String {
			if (startMs <= 0L) {
				return ""
			}
			val mins = maxOf(0L, System.currentTimeMillis() - startMs) / 60_000L
			if (mins < 60L) {
				return "${mins}m ago"
			}
			val hours = mins / 60L
			if (hours < 48L) {
				return "${hours}h ago"
			}
			return "${hours / 24L}d ago"
		}
	}
}
