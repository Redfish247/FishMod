/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.dungeons

import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** Compact results popup for dungeon XP calculators. */
class DungeonCalcOverlay {
	@JvmRecord
	data class FloorLine(val label: String, val runsNeeded: Long, val xpPerRun: Long, val master: Boolean)

	@JvmRecord
	data class ClassBlock(val classId: String?, val name: String, val currentLevel: Int, val selectedRuns: Long)

	@JvmRecord
	data class View(
		val titleKey: String,
		val titleArg: Any?,
		val xpNeeded: Float,
		val modsLabel: String?,
		val floors: List<FloorLine>,
		val classBlocks: List<ClassBlock>?,
		val totalRuns: Long,
		val floorLabel: String?,
		val timedOut: Boolean,
		val accentClassId: String?,
	) {
		fun isAverage(): Boolean = !classBlocks.isNullOrEmpty()

		companion object {
			@JvmStatic
			fun single(
				titleKey: String,
				titleArg: Any?,
				xpNeeded: Float,
				modsLabel: String?,
				floors: List<FloorLine>,
				accentClassId: String?,
			): View = View(titleKey, titleArg, xpNeeded, modsLabel, floors, listOf(), 0L, "", false, accentClassId)

			@JvmStatic
			fun average(
				titleKey: String,
				titleArg: Any?,
				modsLabel: String?,
				blocks: List<ClassBlock>,
				totalRuns: Long,
				floorLabel: String?,
				timedOut: Boolean,
			): View = View(titleKey, titleArg, 0f, modsLabel, listOf(), blocks, totalRuns, floorLabel, timedOut, null)

			@JvmStatic
			fun error(titleKey: String, message: String?): View =
				View(titleKey, "", -1f, message, listOf(), listOf(), 0L, "", false, null)
		}
	}

	private var open = false
	private var view: View? = null
	private var panelX = 0
	private var panelY = 0
	private var panelW = 0
	private var panelH = 0
	private var closeX = 0
	private var closeY = 0
	private var scroll = 0
	private var linesFor: View? = null
	private var lines: List<PvTooltip.Line> = listOf()

	fun isOpen(): Boolean = open

	fun open(view: View?) {
		this.view = view
		open = view != null
		scroll = 0
	}

	fun close() {
		open = false
		view = null
		scroll = 0
	}

	fun mouseScrolled(delta: Double): Boolean {
		if (!open || view == null) {
			return false
		}
		// Scroll whenever the panel is open and content may overflow (not only average views).
		scroll = maxOf(0, scroll - Math.round(delta * 12).toInt())
		return true
	}

	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		screenW: Int,
		screenH: Int,
		mouseX: Int,
		mouseY: Int,
	) {
		val view = view
		if (!open || view == null) {
			return
		}

		PvDraw.fill(g, 0, 0, screenW, screenH, 0x88000000.toInt())

		val lineH = font.lineHeight + 2
		if (linesFor !== view) {
			linesFor = view
			lines = buildLines(view)
		}
		val lines = lines
		val average = view.isAverage()
		val contentH = lines.size * lineH + PAD * 2 + font.lineHeight + 6
		panelW = if (average) minOf(360, maxOf(260, screenW * 2 / 5)) else minOf(280, maxOf(200, screenW / 3))
		panelH = minOf(contentH, screenH - 40)
		panelX = (screenW - panelW) / 2
		panelY = (screenH - panelH) / 2

		val maxScroll = maxOf(0, contentH - panelH)
		scroll = minOf(scroll, maxScroll)

		PvDraw.overlayPanel(g, panelX, panelY, panelW, panelH)

		val title = Component.translatable(view.titleKey, view.titleArg).string
		PvDraw.textBold(g, font, title, panelX + PAD, panelY + PAD, titleColor(view))

		closeX = panelX + panelW - PAD - CLOSE_SIZE
		closeY = panelY + PAD
		val closeHover = mouseX >= closeX && mouseX < closeX + CLOSE_SIZE && mouseY >= closeY && mouseY < closeY + CLOSE_SIZE
		PvDraw.roundedFill(g, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, 3, if (closeHover) 0xFF3A3A4A.toInt() else 0x24FFFFFF)
		PvDraw.roundedRing(g, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL)
		PvDraw.textCentered(g, font, "x", closeX + CLOSE_SIZE / 2, closeY + 1, PvDraw.COLOR_MUTED)

		var y = panelY + PAD + font.lineHeight + 6 - scroll
		val maxY = panelY + panelH - PAD
		val minY = panelY + PAD + font.lineHeight + 4
		for (line in lines) {
			if (y + font.lineHeight > maxY) {
				break
			}
			if (y >= minY && line.kind != PvTooltip.Kind.BLANK) {
				PvDraw.text(g, font, toComponent(line), panelX + PAD, y)
			}
			y += lineH
		}
	}

	/** @return true if the click was consumed (close or absorb inside panel). */
	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (!open) {
			return false
		}
		if (mx >= closeX && mx < closeX + CLOSE_SIZE && my >= closeY && my < closeY + CLOSE_SIZE) {
			close()
			return true
		}
		if (mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH) {
			return true
		}
		close()
		return true
	}

	private companion object {
		const val PAD = 8
		const val CLOSE_SIZE = 12
		const val COLOR_XP = PvDraw.COLOR_GOLD
		const val COLOR_DONE = 0xFF6DFF8A.toInt()
		const val COLOR_WARN = 0xFFFFB86C.toInt()
		const val COLOR_FLOOR_NORMAL = 0xFF7CB87C.toInt()
		const val COLOR_FLOOR_MASTER = 0xFFB85A5A.toInt()
		const val COLOR_PER_RUN = 0xFFE8E8F0.toInt()
		const val COLOR_MAGE = 0xFF6EB5FF.toInt()
		const val COLOR_BERSERK = 0xFFFF6B6B.toInt()
		const val COLOR_ARCHER = 0xFF7CFF9A.toInt()
		const val COLOR_HEALER = 0xFFFF8EC8.toInt()
		const val COLOR_TANK = 0xFFC0C0D0.toInt()

		// Theme accent captured once at class load, as before.
		val COLOR_RUNS = PvDraw.COLOR_ACCENT

		fun titleColor(view: View): Int = when {
			!view.accentClassId.isNullOrBlank() -> classColor(view.accentClassId)
			view.isAverage() -> PvDraw.COLOR_GOLD
			view.xpNeeded < 0f -> PvDraw.COLOR_TEXT
			else -> PvDraw.COLOR_GOLD
		}

		fun buildLines(view: View): List<PvTooltip.Line> {
			val lines = ArrayList<PvTooltip.Line>()
			if (view.xpNeeded < 0f) {
				if (!view.modsLabel.isNullOrBlank()) {
					lines.add(PvTooltip.Line.of(view.modsLabel, COLOR_WARN))
				}
				return lines
			}
			if (view.isAverage()) {
				if (!view.modsLabel.isNullOrBlank()) {
					lines.add(PvTooltip.Line.of(view.modsLabel, PvDraw.COLOR_MUTED))
				}
				val floor = if (view.floorLabel.isNullOrBlank()) "-" else view.floorLabel
				lines.add(PvTooltip.Line.text(listOf(
					PvTooltip.Span.of("Total: ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.bold(FormatUtil.commas(view.totalRuns), COLOR_RUNS),
					PvTooltip.Span.of(" ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.of(floor, floorColor(floor)),
					PvTooltip.Span.of(" runs (S+ est.)", PvDraw.COLOR_MUTED),
				)))
				if (view.timedOut) {
					lines.add(PvTooltip.Line.of("Hit the run cap", COLOR_WARN))
				}
				lines.add(PvTooltip.Line.of("Runs per class:", PvDraw.COLOR_MUTED))
				lines.add(PvTooltip.Line.blank())
				for (block in view.classBlocks.orEmpty()) {
					val nameColor = classColor(block.classId)
					if (block.selectedRuns <= 0L) {
						lines.add(PvTooltip.Line.text(listOf(
							PvTooltip.Span.bold(block.name, nameColor),
							PvTooltip.Span.of(" - ", PvDraw.COLOR_MUTED),
							PvTooltip.Span.of("0 (already covered)", COLOR_DONE),
						)))
					} else {
						lines.add(PvTooltip.Line.text(listOf(
							PvTooltip.Span.bold(block.name, nameColor),
							PvTooltip.Span.of(" (", PvDraw.COLOR_MUTED),
							PvTooltip.Span.of(block.currentLevel.toString(), PvDraw.COLOR_TEXT),
							PvTooltip.Span.of(")  ", PvDraw.COLOR_MUTED),
							PvTooltip.Span.bold(FormatUtil.commas(block.selectedRuns), COLOR_RUNS),
							PvTooltip.Span.of(" runs", PvDraw.COLOR_MUTED),
						)))
					}
				}
				return lines
			}

			lines.add(PvTooltip.Line.text(listOf(
				PvTooltip.Span.of("XP needed: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.bold(FormatUtil.commas(Math.round(view.xpNeeded).toLong()), COLOR_XP),
			)))
			if (!view.modsLabel.isNullOrBlank()) {
				lines.add(PvTooltip.Line.of(view.modsLabel, PvDraw.COLOR_MUTED))
			}
			lines.add(PvTooltip.Line.blank())
			if (view.floors.isEmpty()) {
				if (view.xpNeeded <= 0f) {
					lines.add(PvTooltip.Line.of("Already at target", COLOR_DONE))
				} else {
					lines.add(PvTooltip.Line.of("No floors available", COLOR_WARN))
				}
				return lines
			}
			lines.add(PvTooltip.Line.of("Best floors (S+ est.):", PvDraw.COLOR_MUTED))
			for (floor in view.floors) {
				val labelColor = if (floor.master) COLOR_FLOOR_MASTER else COLOR_FLOOR_NORMAL
				lines.add(PvTooltip.Line.text(listOf(
					PvTooltip.Span.bold(floor.label, labelColor),
					PvTooltip.Span.of("  ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.bold(FormatUtil.commas(floor.runsNeeded), COLOR_RUNS),
					PvTooltip.Span.of(" runs  (", PvDraw.COLOR_MUTED),
					PvTooltip.Span.of(FormatUtil.shortXp(floor.xpPerRun.toDouble()), COLOR_PER_RUN),
					PvTooltip.Span.of("/run)", PvDraw.COLOR_MUTED),
				)))
			}
			return lines
		}

		fun toComponent(line: PvTooltip.Line): Component {
			val out = Component.empty()
			for (span in line.spans()) {
				out.append(span.toComponent())
			}
			return out
		}

		/** Matches dungeon page MM red / normal green; labels are `F7`/`M7`. */
		fun floorColor(label: String?): Int {
			if (label.isNullOrBlank()) {
				return COLOR_FLOOR_NORMAL
			}
			val trimmed = label.trim()
			val first = trimmed[0]
			if ((first == 'M' || first == 'm') && trimmed.length > 1 && trimmed[1].isDigit()) {
				return COLOR_FLOOR_MASTER
			}
			val lower = trimmed.lowercase(Locale.ROOT)
			if (lower.contains("master") || lower.startsWith("mm")) {
				return COLOR_FLOOR_MASTER
			}
			return COLOR_FLOOR_NORMAL
		}

		fun classColor(classId: String?): Int {
			if (classId.isNullOrBlank()) {
				return PvDraw.COLOR_TEXT
			}
			return when (classId.lowercase(Locale.ROOT)) {
				"mage" -> COLOR_MAGE
				"berserk" -> COLOR_BERSERK
				"archer" -> COLOR_ARCHER
				"healer" -> COLOR_HEALER
				"tank" -> COLOR_TANK
				else -> PvDraw.COLOR_TEXT
			}
		}
	}
}
