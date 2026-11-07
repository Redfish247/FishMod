/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.dungeons

import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.dungeons.CataXpCalculator
import fishmod.pv.dungeons.CataXpMath
import fishmod.pv.dungeons.ClassLevelQuery
import fishmod.pv.dungeons.ClassXpCalculator
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.essence.EssenceShopColumn
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class DungeonPage {
	private enum class FieldFocus { NONE, CATA, CLASS }

	private var data: DungeonSnapshot = DungeonSnapshot.empty()
	private val zones = ArrayList<HoverZone>()
	private val overlay = DungeonCalcOverlay()

	// Calculator rows are never laid out since the panel was removed; commands still run the calcs.
	private val cataRow = CalcRow()
	private val classRow = CalcRow()

	private var cataLevelText = ""
	private var classLevelText = ""
	private var focus = FieldFocus.NONE
	private var replaceOnType = false

	private var runsEssenceFace = false
	private var runsFlipStartMs = 0L
	private var runsFlipTarget = false
	private var runsHitX = 0
	private var runsHitY = 0
	private var runsHitW = 0
	private var runsHitH = 0

	private var classEssenceFace = false
	private var classFlipStartMs = 0L
	private var classFlipTarget = false
	private var classHitX = 0
	private var classHitY = 0
	private var classHitW = 0
	private var classHitH = 0

	fun apply(data: DungeonSnapshot?) {
		this.data = data ?: DungeonSnapshot.empty()
	}

	fun overlay(): DungeonCalcOverlay = overlay

	fun blurField() {
		focus = FieldFocus.NONE
		replaceOnType = false
	}

	fun runCataCalc() {
		blurField()
		val target = parseTarget(cataLevelText, data.cataLevel + 1)
		overlay.open(toView(CataXpCalculator.calculate(data, target)))
	}

	fun runClassCalc() {
		blurField()
		val query = ClassLevelQuery.parse(classLevelText)
		if (query == null) {
			overlay.open(DungeonCalcOverlay.View.error(
				"fishmod.pv.dungeons.calc.class",
				Component.translatable("fishmod.pv.dungeons.calc.class_invalid").string,
			))
			return
		}
		if (query.classAverage) {
			overlay.open(toView(ClassXpCalculator.calculateAverage(data, query.targetLevel)))
			return
		}
		val result = ClassXpCalculator.calculate(data, query) ?: return
		overlay.open(toView(result))
	}

	fun mouseScrolled(delta: Double): Boolean = overlay.mouseScrolled(delta)

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (overlay.isOpen()) {
			return overlay.mouseClicked(mx, my)
		}
		if (hitButton(cataRow, mx, my)) {
			runCataCalc()
			return true
		}
		if (hitButton(classRow, mx, my)) {
			runClassCalc()
			return true
		}
		if (hitField(cataRow, mx, my)) {
			focus = FieldFocus.CATA
			replaceOnType = cataLevelText.isNotEmpty()
			return true
		}
		if (hitField(classRow, mx, my)) {
			focus = FieldFocus.CLASS
			replaceOnType = classLevelText.isNotEmpty()
			return true
		}
		if (focus != FieldFocus.NONE) {
			blurField()
			return true
		}
		return clickRunsPanel(mx, my) || clickClassPanel(mx, my)
	}

	private fun clickRunsPanel(mouseX: Double, mouseY: Double): Boolean {
		if (!FLIP_ENABLED || runsHitW <= 0 || runsHitH <= 0) {
			return false
		}
		if (mouseX < runsHitX || mouseX >= runsHitX + runsHitW || mouseY < runsHitY || mouseY >= runsHitY + runsHitH) {
			return false
		}
		if (runsFlipStartMs != 0L) {
			return true
		}
		runsFlipTarget = !runsEssenceFace
		runsFlipStartMs = System.currentTimeMillis()
		return true
	}

	private fun clickClassPanel(mouseX: Double, mouseY: Double): Boolean {
		if (!FLIP_ENABLED || classHitW <= 0 || classHitH <= 0) {
			return false
		}
		if (mouseX < classHitX || mouseX >= classHitX + classHitW || mouseY < classHitY || mouseY >= classHitY + classHitH) {
			return false
		}
		if (classFlipStartMs != 0L) {
			return true
		}
		classFlipTarget = !classEssenceFace
		classFlipStartMs = System.currentTimeMillis()
		return true
	}

	fun isCalcButton(mx: Double, my: Double): Boolean =
		!overlay.isOpen() && (hitButton(cataRow, mx, my) || hitButton(classRow, mx, my))

	fun charTyped(codepoint: Char): Boolean {
		if (focus == FieldFocus.NONE || overlay.isOpen()) {
			return false
		}
		if (focus == FieldFocus.CATA) {
			if (!isCataChar(codepoint)) {
				return false
			}
			if (replaceOnType) {
				cataLevelText = ""
				replaceOnType = false
			}
			if (cataLevelText.length < CATA_MAX_LEN) {
				cataLevelText += codepoint
			}
			return true
		}
		if (!isClassChar(codepoint)) {
			return false
		}
		if (replaceOnType) {
			classLevelText = ""
			replaceOnType = false
		}
		if (classLevelText.length < CLASS_MAX_LEN) {
			classLevelText += codepoint
		}
		return true
	}

	fun keyPressed(key: Int): Boolean {
		if (overlay.isOpen()) {
			if (key == fishmod.compat.GLFW.GLFW_KEY_ESCAPE) {
				overlay.close()
				return true
			}
			return false
		}
		if (focus == FieldFocus.NONE) {
			return false
		}
		when (key) {
			256 -> blurField() // Esc
			fishmod.compat.GLFW.GLFW_KEY_ENTER, fishmod.compat.GLFW.GLFW_KEY_KP_ENTER -> if (focus == FieldFocus.CLASS) runClassCalc() else runCataCalc() // Enter
			259 -> { // Backspace
				if (replaceOnType) {
					if (focus == FieldFocus.CLASS) classLevelText = "" else cataLevelText = ""
					replaceOnType = false
				} else if (focus == FieldFocus.CLASS) {
					classLevelText = classLevelText.dropLast(1)
				} else {
					cataLevelText = cataLevelText.dropLast(1)
				}
			}
			else -> return false
		}
		return true
	}

	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
		@Suppress("UNUSED_PARAMETER") screenW: Int,
		@Suppress("UNUSED_PARAMETER") screenH: Int,
	) {
		zones.clear()

		// Catacombs / class column on the left, calculators and floor runs on the right.
		val rightW = maxOf(120, w * 38 / 100)
		val leftW = w - rightW - GAP
		val leftX = x + rightW + GAP
		val rightX = x

		// Calculators removed (commands cover them); floors take the whole panel.
		val headerH = font.lineHeight + 4
		val lineH = font.lineHeight
		val runsY = y
		val runsH = h

		val firstFloorY = runsY + PAD + headerH
		var totalY = runsY + runsH - PAD - lineH
		val floorBlock = 7 * (lineH + PB_H)
		val stretch = totalY - firstFloorY - floorBlock
		val gap: Int
		if (stretch < 14) {
			gap = 2
			val totalGap = 2
			totalY = firstFloorY + floorBlock + 6 * gap + totalGap
		} else {
			gap = maxOf(2, minOf(FLOOR_GAP_MAX, stretch / 7))
			val usedGaps = gap * 6
			val totalGap = maxOf(gap, stretch - usedGaps)
			totalY = firstFloorY + floorBlock + usedGaps + totalGap
		}
		val floors = FloorLayout(firstFloorY, lineH, gap, totalY, PB_H)
		drawRunsPanel(g, font, leftX, runsY, leftW, runsH, floors)
		drawRightColumn(g, font, rightX, y, rightW, h, floors.totalY)
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		if (runsFlipStartMs != 0L || classFlipStartMs != 0L) {
			return
		}
		val zone = zones.firstOrNull { mouseX >= it.x && mouseX < it.x + it.w && mouseY >= it.y && mouseY < it.y + it.h } ?: return
		if (zone.lines.isNotEmpty()) {
			PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH)
		}
	}

	fun renderOverlay(g: GuiGraphicsExtractor, font: Font, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
		overlay.render(g, font, screenW, screenH, mouseX, mouseY)
	}

	/** Flip progress 0..1, or -1 when no flip is running. */
	private fun flipState(startMs: Long): Float =
		if (startMs == 0L) -1f else minOf(1f, (System.currentTimeMillis() - startMs) / FLIP_MS.toFloat())

	private fun drawRightColumn(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, totalY: Int) {
		classHitX = x
		classHitY = y
		classHitW = w
		classHitH = h

		var progress = flipState(classFlipStartMs)
		if (progress >= 1f) {
			classEssenceFace = classFlipTarget
			classFlipStartMs = 0L
			progress = -1f
		}
		val animating = progress >= 0f
		val angle = (if (animating) easeInOutCubic(progress) else 0f) * Math.PI.toFloat()
		val showEssence = if (animating && cos(angle) < 0.0) classFlipTarget else classEssenceFace

		pushFlip(g, x, y, w, h, animating, angle)
		PvDraw.innerPanel(g, x, y, w, h)
		if (showEssence) {
			drawWitherUndeadEssenceFace(g, font, x + PAD, y + PAD, w - PAD * 2, h - PAD * 2, !animating)
		} else {
			drawClassStatsFace(g, font, x, y, w, totalY)
		}
		g.pose().popMatrix()
	}

	private fun pushFlip(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, animating: Boolean, angle: Float) {
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
	}

	private fun drawClassStatsFace(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, totalY: Int) {
		val cx = x + PAD
		val barW = w - PAD * 2
		val labeledH = font.lineHeight + 2 + PvDraw.BAR_HEIGHT
		val defaultRowH = labeledH + BAR_AFTER

		val cataY = y + PAD + 4
		PvDraw.labeledBar(
			g, font,
			"Catacombs", data.cataLevel.toString(),
			data.cataProgress,
			cx, cataY, barW,
			PvDraw.COLOR_BAR_FILL,
			data.cataLevel >= 50 || data.cataMaxed,
		)
		zones.add(HoverZone(cx, cataY, barW, labeledH, cataTip()))

		val secretsY = cataY + labeledH + 6
		PvDraw.text(g, font, "Secrets", cx, secretsY, PvDraw.COLOR_MUTED)
		val secretsValue = FormatUtil.commas(data.secrets) + " (avg " + FormatUtil.oneDecimal(data.secretsPerRun) + "/run)"
		val secretsMax = maxOf(12, barW - font.width("Secrets") - 8)
		PvDraw.textRight(g, font, trimToWidth(font, secretsValue, secretsMax), cx + barW, secretsY, PvDraw.COLOR_TEXT)

		var extraY = secretsY + font.lineHeight + 4
		if (data.dailyRuns > 0) {
			PvDraw.text(g, font, "Daily runs", cx, extraY, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, FormatUtil.commas(data.dailyRuns.toLong()), cx + barW, extraY, PvDraw.COLOR_ACCENT)
			extraY += font.lineHeight + 2
		}
		if (data.journalsUnlocked > 0) {
			PvDraw.text(g, font, "Unique Journals", cx, extraY, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, FormatUtil.commas(data.journalsUnlocked.toLong()), cx + barW, extraY, PvDraw.COLOR_GOLD)
			extraY += font.lineHeight + 2
		}

		val classes = data.classes
		if (classes.isEmpty()) {
			return
		}
		val n = classes.size

		val avgGap = 8
		var tankY = totalY + font.lineHeight - labeledH
		var rowH = defaultRowH
		var firstY = if (n <= 1) tankY else tankY - (n - 1) * rowH
		var avgY = firstY - avgGap - labeledH
		val minAvgY = extraY + 8
		if (avgY < minAvgY) {
			avgY = minAvgY
			val afterAvg = avgY + labeledH + avgGap
			if (n <= 1) {
				if (tankY < afterAvg) {
					tankY = afterAvg
					firstY = tankY
				}
			} else {
				val span = tankY - afterAvg
				if (span >= (n - 1) * (labeledH + 2)) {
					rowH = maxOf(labeledH + 2, span / (n - 1))
					firstY = tankY - (n - 1) * rowH
				} else {
					firstY = afterAvg
					rowH = labeledH + 2
					tankY = firstY + (n - 1) * rowH
				}
			}
		}

		PvDraw.labeledBar(
			g, font,
			"Class Average", FormatUtil.oneDecimal(data.classAverage.toDouble()),
			data.classAverageProgress,
			cx, avgY, barW,
			PvDraw.COLOR_BAR_FILL,
			data.classAverage >= 50 || data.classAverageMaxed,
		)
		zones.add(HoverZone(cx, avgY, barW, labeledH, plainTip(data.classAverageHover)))

		for ((i, clazz) in classes.withIndex()) {
			val cy = if (i == n - 1) tankY else firstY + i * rowH
			val label = if (clazz.selected) "> " + clazz.name else clazz.name
			val labelColor = if (clazz.selected) 0xFF6DFF8A.toInt() else PvDraw.COLOR_TEXT
			PvDraw.text(g, font, label, cx, cy, labelColor)
			PvDraw.textRight(g, font, clazz.level.toString(), cx + barW, cy, PvDraw.COLOR_MUTED)
			val barY = cy + font.lineHeight + 2
			PvDraw.progressBar(g, cx, barY, barW, PvDraw.BAR_HEIGHT, clazz.progress, PvDraw.COLOR_BAR_FILL, clazz.level >= 50 || clazz.maxed)
			zones.add(HoverZone(cx, cy, barW, labeledH, classTip(clazz)))
		}
	}

	private fun drawWitherUndeadEssenceFace(
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, registerHovers: Boolean,
	) {
		val gap = 20
		val colW = maxOf(40, (w - gap) / 2)
		val sepX = x + colW + gap / 2
		drawEssenceColumnFit(g, font, data.witherShop, x, y, colW, h, WITHER_COLOR, 3, registerHovers)
		PvDraw.fill(g, sepX, y, 1, h, PvDraw.COLOR_DIVIDER)
		drawEssenceColumnFit(g, font, data.undeadShop, x + colW + gap, y, colW, h, UNDEAD_COLOR, 3, registerHovers)
	}

	private fun drawIceSpiderDragonEssenceFace(
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, registerHovers: Boolean,
	) {
		val gap = 8
		val colW = maxOf(40, (w - gap * 2) / 3)
		val c0 = x
		val c1 = x + colW + gap
		val c2 = c1 + colW + gap
		drawEssenceColumnFit(g, font, data.iceShop, c0, y, colW, h, ICE_COLOR, 0, registerHovers)
		PvDraw.fill(g, c0 + colW + gap / 2, y, 1, h, PvDraw.COLOR_DIVIDER)
		drawEssenceColumnFit(g, font, data.spiderShop, c1, y, colW, h, SPIDER_COLOR, 0, registerHovers)
		PvDraw.fill(g, c1 + colW + gap / 2, y, 1, h, PvDraw.COLOR_DIVIDER)
		drawEssenceColumnFit(g, font, data.dragonShop, c2, y, colW, h, DRAGON_COLOR, 0, registerHovers)
	}

	private fun drawEssenceColumnFit(
		g: GuiGraphicsExtractor,
		font: Font,
		shop: DungeonSnapshot.EssenceShop,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		headerColor: Int,
		rowGap: Int,
		registerHovers: Boolean,
	) {
		EssenceShopColumn.draw(
			g, font, shop, x, y, w, h, headerColor, rowGap,
			if (registerHovers) EssenceShopColumn.HoverSink { zx, zy, zw, zh, lines -> zones.add(HoverZone(zx, zy, zw, zh, lines)) } else null,
		)
	}

	private fun drawRunsPanel(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, floors: FloorLayout) {
		runsHitX = x
		runsHitY = y
		runsHitW = w
		runsHitH = h

		var progress = flipState(runsFlipStartMs)
		if (progress >= 1f) {
			runsEssenceFace = runsFlipTarget
			runsFlipStartMs = 0L
			progress = -1f
		}
		val animating = progress >= 0f
		val angle = (if (animating) easeInOutCubic(progress) else 0f) * Math.PI.toFloat()
		val showEssence = if (animating && cos(angle) < 0.0) runsFlipTarget else runsEssenceFace

		pushFlip(g, x, y, w, h, animating, angle)
		PvDraw.innerPanel(g, x, y, w, h)
		if (showEssence) {
			drawIceSpiderDragonEssenceFace(g, font, x + PAD, y + PAD, w - PAD * 2, h - PAD * 2, !animating)
		} else {
			drawRunsFace(g, font, x, y, w, floors)
		}
		g.pose().popMatrix()
	}

	private fun drawRunsFace(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, floors: FloorLayout) {
		val cx = x + PAD
		val innerW = w - PAD * 2
		val colGap = 20
		val colW = (innerW - colGap) / 2
		val leftCol = cx
		val rightCol = cx + colW + colGap

		PvDraw.text(g, font, Component.translatable("fishmod.pv.dungeons.normal").string, leftCol, y + PAD, PvDraw.COLOR_MUTED)
		PvDraw.text(g, font, Component.translatable("fishmod.pv.dungeons.master").string, rightCol, y + PAD, PvDraw.COLOR_MUTED)

		var cy = floors.firstFloorY
		var normalTotal = 0L
		var masterTotal = 0L
		for (floor in 1..7) {
			val normal = floorById(data.normal, floor)
			val master = floorById(data.master, floor)
			normalTotal += normal.completions
			masterTotal += master.completions

			val floorLabel = Component.translatable("fishmod.pv.dungeons.floor", floor).string
			val masterLabel = Component.translatable("fishmod.pv.dungeons.master_floor", floor).string

			val cellH = floors.lineH + floors.pbH
			drawFloorCell(g, font, leftCol, cy, colW, cellH, floors.gap, floorLabel, normal, NORMAL_FLOOR_COLOR)
			drawFloorCell(g, font, rightCol, cy, colW, cellH, floors.gap, masterLabel, master, MASTER_FLOOR_COLOR)
			cy += cellH + floors.gap
		}

		drawTotalLine(g, font, leftCol, floors.totalY, colW, normalTotal)
		drawTotalLine(g, font, rightCol, floors.totalY, colW, masterTotal)
	}

	private fun drawTotalLine(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, total: Long) {
		PvDraw.text(g, font, Component.translatable("fishmod.pv.dungeons.total").string, x, y, PvDraw.COLOR_MUTED)
		PvDraw.textRight(g, font, FormatUtil.commas(total), x + w, y, PvDraw.COLOR_TEXT)
	}

	private fun drawFloorCell(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		lineH: Int,
		gap: Int,
		label: String,
		floor: DungeonSnapshot.FloorEntry,
		nameColor: Int,
	) {
		val right = FormatUtil.commas(floor.completions)
		val rightW = font.width(right)
		val shown = trimToWidth(font, label, maxOf(8, w - rightW - 4))
		PvDraw.text(g, font, shown, x, y, nameColor)
		PvDraw.textRight(g, font, right, x + w, y, PvDraw.COLOR_TEXT)
		drawPbLine(g, font, x, y + font.lineHeight, floor)
		zones.add(HoverZone(x, y - 1, w, lineH + maxOf(2, gap / 2), floorStatsTip(label, floor)))
	}

	private fun cataTip(): List<PvTooltip.Line> {
		val lines = ArrayList(plainTip(data.cataHover))
		val race = data.hubRace
		if (race.present()) {
			lines.add(PvTooltip.Line.divider())
			lines.add(PvTooltip.Line.title("Dungeon Hub Race", PvDraw.COLOR_ACCENT))
			if (race.selectedRace.isNotBlank()) {
				lines.add(PvTooltip.Line.row("Race", PvDraw.COLOR_MUTED, race.selectedRace, PvDraw.COLOR_TEXT))
			}
			if (race.selectedSetting.isNotBlank()) {
				lines.add(PvTooltip.Line.row("Setting", PvDraw.COLOR_MUTED, race.selectedSetting, PvDraw.COLOR_TEXT))
			}
			race.runback?.let {
				lines.add(PvTooltip.Line.row("Runback", PvDraw.COLOR_MUTED, if (it) "On" else "Off", PvDraw.COLOR_TEXT))
			}
		}
		return lines
	}

	private fun classTip(clazz: DungeonSnapshot.ClassEntry): List<PvTooltip.Line> {
		val lines = ArrayList<PvTooltip.Line>()
		lines.add(PvTooltip.Line.of(clazz.name + " Lvl " + clazz.level, classColor(clazz.id)))
		for (part in clazz.xpHover.orEmpty().split("\n")) {
			if (part.isNotBlank()) {
				lines.add(PvTooltip.Line.plain(part))
			}
		}
		return lines
	}

	private class FloorLayout(val firstFloorY: Int, val lineH: Int, val gap: Int, val totalY: Int, val pbH: Int)

	private class HoverZone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>)

	private class CalcRow {
		var fieldX = 0
		var fieldY = 0
		var fieldW = 0
		var fieldH = 0
		var btnX = 0
		var btnY = 0
		var btnW = 0
		var btnH = 0
	}

	companion object {
		private const val PAD = 6
		private const val BAR_AFTER = 4
		private const val GAP = 6
		private const val FLOOR_GAP_MAX = 6

		/** Height of the personal-best line under each floor name. */
		private const val PB_H = 8
		private const val PB_SCALE = 0.75f

		/** Enough for `cata99` / `C55`. */
		private const val CATA_MAX_LEN = 6
		private const val CLASS_MAX_LEN = 32
		private const val FLIP_MS = 480

		// Click-to-flip to the essence faces is switched off.
		private const val FLIP_ENABLED = false

		/** Wither essence: light gray so the header reads at the same weight as Ice/Spider/Dragon. */
		private const val WITHER_COLOR = 0xFFAAAAAA.toInt()

		/** Undead essence - pink. */
		private const val UNDEAD_COLOR = 0xFFFF8EC8.toInt()
		private const val ICE_COLOR = 0xFF55FFFF.toInt()
		private const val SPIDER_COLOR = 0xFF55FF55.toInt()
		private const val DRAGON_COLOR = 0xFFFF5555.toInt()
		private const val NORMAL_FLOOR_COLOR = 0xFF7CB87C.toInt()
		private const val MASTER_FLOOR_COLOR = 0xFFB85A5A.toInt()

		private const val COLOR_STAT = 0xFFE8E8F0.toInt()
		private const val COLOR_COMPLETIONS = 0xFF7CFF9A.toInt()
		private const val COLOR_MOBS = 0xFFFFB86C.toInt()
		private const val COLOR_SCORE_S_PLUS = 0xFF6DFF8A.toInt()
		private const val COLOR_SCORE = 0xFFFFD36A.toInt()
		private const val COLOR_TIME = 0xFF7EC8FF.toInt()
		private const val COLOR_TIME_S = 0xFFFFCC66.toInt()
		private const val COLOR_TIME_S_PLUS = 0xFF6DFF8A.toInt()
		private const val COLOR_HEALING = 0xFFFF8EC8.toInt()
		private const val COLOR_MAGE = 0xFF6EB5FF.toInt()
		private const val COLOR_BERSERK = 0xFFFF6B6B.toInt()
		private const val COLOR_ARCHER = 0xFF7CFF9A.toInt()
		private const val COLOR_HEALER = 0xFFFF8EC8.toInt()
		private const val COLOR_TANK = 0xFFC0C0D0.toInt()

		private fun isClassChar(c: Char): Boolean = c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == ' '

		/** Digits plus optional `C`/`cata` prefix for overflow targets like `C55`. */
		private fun isCataChar(c: Char): Boolean = c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z'

		private fun hitButton(row: CalcRow, mx: Double, my: Double): Boolean =
			mx >= row.btnX && mx < row.btnX + row.btnW && my >= row.btnY && my < row.btnY + row.btnH

		private fun hitField(row: CalcRow, mx: Double, my: Double): Boolean =
			mx >= row.fieldX && mx < row.fieldX + row.fieldW && my >= row.fieldY && my < row.fieldY + row.fieldH

		private fun toView(result: CataXpCalculator.Result): DungeonCalcOverlay.View =
			DungeonCalcOverlay.View.single(
				"fishmod.pv.dungeons.calc.result_title",
				result.targetLevel,
				result.xpNeeded,
				if (result.mayorLabel.isBlank()) "" else "Mayor: " + result.mayorLabel,
				result.floors.map { DungeonCalcOverlay.FloorLine(it.label, it.runsNeeded, it.xpPerRun, it.master) },
				null,
			)

		private fun toView(result: ClassXpCalculator.Result): DungeonCalcOverlay.View =
			DungeonCalcOverlay.View.single(
				"fishmod.pv.dungeons.calc.class_result_title",
				result.className + " " + result.targetLevel,
				result.xpNeeded,
				result.modsLabel,
				result.floors.map { DungeonCalcOverlay.FloorLine(it.label, it.runsNeeded, it.xpPerRun, it.master) },
				result.classId,
			)

		private fun toView(result: ClassXpCalculator.AverageResult): DungeonCalcOverlay.View =
			DungeonCalcOverlay.View.average(
				"fishmod.pv.dungeons.calc.ca_result_title",
				result.targetLevel.toString() + " · " + result.floorLabel,
				result.modsLabel,
				result.classes.map { DungeonCalcOverlay.ClassBlock(it.classId, it.className, it.currentLevel, it.selectedRuns) },
				result.totalRuns,
				result.floorLabel,
				result.timedOut,
			)

		private fun trimToWidth(font: Font, text: String, maxW: Int): String {
			if (font.width(text) <= maxW) {
				return text
			}
			val ellipsis = "..."
			val budget = maxW - font.width(ellipsis)
			if (budget <= 0) {
				return ellipsis
			}
			val out = StringBuilder()
			for (c in text) {
				if (font.width(out.toString() + c) > budget) {
					break
				}
				out.append(c)
			}
			return "$out$ellipsis"
		}

		private fun parseTarget(text: String?, fallback: Int): Int {
			val max = CataXpMath.maxLevel()
			if (text.isNullOrBlank()) {
				return maxOf(1, minOf(max, fallback))
			}
			var cleaned = text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), "")
			if (cleaned.startsWith("cata")) {
				cleaned = cleaned.substring(4)
			} else if (cleaned.startsWith("c") && cleaned.length > 1 && cleaned[1].isDigit()) {
				cleaned = cleaned.substring(1)
			}
			return maxOf(1, minOf(max, cleaned.toIntOrNull() ?: fallback))
		}

		private fun easeInOutCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

		/** Small personal-best line under a floor name: S+, S and any-rank clear times. */
		private fun drawPbLine(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, floor: DungeonSnapshot.FloorEntry) {
			val times = longArrayOf(floor.fastestSPlusMs, floor.fastestSMs, floor.fastestMs)
			val tags = arrayOf("S+", "S", "Any")
			val colors = intArrayOf(COLOR_TIME_S_PLUS, COLOR_TIME_S, COLOR_TIME)
			var cx = x
			var any = false
			for (i in times.indices) {
				if (times[i] <= 0L) {
					continue
				}
				any = true
				val tag = tags[i] + " "
				val time = FormatUtil.prettyTime(times[i])
				PvDraw.textScaled(g, font, tag, cx, y + 1, PvDraw.COLOR_MUTED, PB_SCALE)
				cx += Math.round(font.width(tag) * PB_SCALE)
				PvDraw.textScaled(g, font, time, cx, y + 1, colors[i], PB_SCALE)
				cx += Math.round(font.width("$time  ") * PB_SCALE)
			}
			if (!any) {
				PvDraw.textScaled(g, font, "no clears", x, y + 1, 0xFF6A6A7A.toInt(), PB_SCALE)
			}
		}

		private fun plainTip(text: String?): List<PvTooltip.Line> = listOf(PvTooltip.Line.plain(text))

		@JvmStatic
		fun floorStatsTip(title: String, floor: DungeonSnapshot.FloorEntry): List<PvTooltip.Line> {
			val lines = ArrayList<PvTooltip.Line>()
			lines.add(PvTooltip.Line.title(title, PvDraw.COLOR_ACCENT))
			lines.add(PvTooltip.Line.row("Clears", PvDraw.COLOR_MUTED, FormatUtil.commas(floor.completions), COLOR_COMPLETIONS))
			if (floor.milestoneCompletions > 0L) {
				lines.add(PvTooltip.Line.row("Milestone", PvDraw.COLOR_MUTED, FormatUtil.commas(floor.milestoneCompletions), COLOR_COMPLETIONS))
			}
			lines.add(PvTooltip.Line.divider())
			lines.add(PvTooltip.Line.row("S+ record", PvDraw.COLOR_MUTED, timeText(floor.fastestSPlusMs), COLOR_TIME_S_PLUS))
			lines.add(PvTooltip.Line.row("S record", PvDraw.COLOR_MUTED, timeText(floor.fastestSMs), COLOR_TIME_S))
			lines.add(PvTooltip.Line.row("Fastest clear", PvDraw.COLOR_MUTED, timeText(floor.fastestMs), COLOR_TIME))
			lines.add(PvTooltip.Line.divider())
			lines.add(PvTooltip.Line.row(
				"Top score",
				PvDraw.COLOR_MUTED,
				FormatUtil.commas(floor.bestScore),
				if (floor.bestScore >= 300L) COLOR_SCORE_S_PLUS else COLOR_SCORE,
			))
			lines.add(PvTooltip.Line.row("Kills (total)", PvDraw.COLOR_MUTED, FormatUtil.shortXp(floor.mobsKilled.toDouble()), COLOR_MOBS))
			lines.add(PvTooltip.Line.row("Kills (best run)", PvDraw.COLOR_MUTED, FormatUtil.commas(floor.mostMobsKilled), COLOR_MOBS))
			if (floor.mostHealing > 0.0) {
				lines.add(PvTooltip.Line.row("Healing (best run)", PvDraw.COLOR_MUTED, FormatUtil.shortXp(floor.mostHealing), COLOR_HEALING))
			}
			val damageClass = floor.mostDamageClass
			if (floor.mostDamage > 0.0 && damageClass != null) {
				lines.add(PvTooltip.Line.row(
					"Damage (best run)",
					PvDraw.COLOR_MUTED,
					FormatUtil.shortXp(floor.mostDamage) + " " + ClassLevelQuery.displayName(damageClass),
					classColor(damageClass),
				))
			}
			return lines
		}

		private fun timeText(ms: Long): String = if (ms <= 0L) "none" else FormatUtil.prettyTime(ms)

		private fun classColor(classId: String?): Int {
			if (classId == null) {
				return COLOR_STAT
			}
			return when (classId.lowercase(Locale.ROOT)) {
				"mage" -> COLOR_MAGE
				"berserk" -> COLOR_BERSERK
				"archer" -> COLOR_ARCHER
				"healer" -> COLOR_HEALER
				"tank" -> COLOR_TANK
				else -> COLOR_STAT
			}
		}

		private fun floorById(mode: DungeonSnapshot.ModeStats, floor: Int): DungeonSnapshot.FloorEntry {
			val key = floor.toString()
			return mode.floors.firstOrNull { it.id == key } ?: DungeonSnapshot.FloorEntry.empty(key, "F$floor")
		}
	}
}
