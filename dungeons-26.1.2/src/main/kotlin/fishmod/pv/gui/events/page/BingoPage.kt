/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.events.page

import fishmod.pv.data.EventsSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.events.EventsPage
import fishmod.pv.gui.events.EventsUi
import fishmod.pv.gui.events.EventsUi.Companion.BINGO_COLS
import fishmod.pv.gui.events.EventsUi.Companion.BINGO_ROWS
import fishmod.pv.gui.events.EventsUi.Companion.COLOR_COMMUNITY
import fishmod.pv.gui.events.EventsUi.Companion.COLOR_COMPLETE
import fishmod.pv.gui.events.EventsUi.Companion.GAP
import fishmod.pv.gui.events.EventsUi.Companion.PAD
import fishmod.pv.gui.events.EventsUi.Companion.SEP_GAP
import fishmod.pv.gui.events.EventsUi.Companion.SLOT_BG
import fishmod.pv.gui.events.EventsUi.Companion.SLOT_BORDER
import fishmod.pv.gui.events.EventsUi.Companion.STAT_ROW
import fishmod.pv.gui.events.EventsUi.Companion.formatAgo
import fishmod.pv.gui.events.EventsUi.Companion.prettyGoalId
import fishmod.pv.gui.events.EventsUi.Companion.prettyModifier
import fishmod.pv.gui.events.EventsUi.Companion.sectionSeparator
import fishmod.pv.gui.events.EventsUi.Companion.tipTitle
import fishmod.pv.gui.events.EventsUi.Companion.trim
import fishmod.pv.gui.events.EventsUi.Companion.wrapLore
import java.util.IdentityHashMap
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Bingo events subpage. */
class BingoPage {
	// Goal tooltips per goals list; done-state is fixed for a given snapshot.
	private var tipGoals: List<EventsSnapshot.BingoGoal>? = null
	private var tipCompleted: Set<String> = setOf()
	private val tipCache = IdentityHashMap<EventsSnapshot.BingoGoal, List<PvTooltip.Line>>()

	fun render(
		snapshot: EventsSnapshot,
		bingoState: EventsPage.BingoLoadState,
		bingoError: String,
		bingoHistoryMissing: Boolean,
		ui: EventsUi,
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mx: Int,
		my: Int
	) {
		val bingo = snapshot.bingo
		val rightW = maxOf(200, w * 58 / 100)
		val leftW = w - rightW - GAP
		val lx = x
		val rx = x + leftW + GAP
		PvDraw.innerPanel(g, lx, y, leftW, h)
		PvDraw.innerPanel(g, rx, y, rightW, h)

		val bottom = y + h - PAD
		val cx = lx + PAD
		var cy = y + PAD
		val cw = leftW - PAD * 2

		cy += PvDraw.sectionHeader(g, font, "Bingo profile", cx, cy, cw)
		if (bingo.hasBingoProfile) {
			cy = ui.tipStat(
				g, font, "Profile", bingo.bingoProfileName, PvDraw.COLOR_GOLD, cx, cy, cw, mx, my,
				tipTitle(
					"Bingo profile", PvDraw.COLOR_GOLD,
					PvTooltip.Line.row("Name", PvDraw.COLOR_MUTED, bingo.bingoProfileName, PvDraw.COLOR_GOLD),
					if (bingo.bingoFirstJoinMs > 0L) PvTooltip.Line.meta("Joined " + formatAgo(bingo.bingoFirstJoinMs)) else null
				)
			)
			if (bingo.bingoFirstJoinMs > 0L && cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(
					g, font, "Joined", formatAgo(bingo.bingoFirstJoinMs), PvDraw.COLOR_MUTED, cx, cy, cw, mx, my,
					tipTitle("First join", PvDraw.COLOR_TEXT, PvTooltip.Line.meta(formatAgo(bingo.bingoFirstJoinMs)))
				)
			}
		} else {
			cy = EventsUi.statLine(g, font, "Profile", "None", cx, cy, cw, PvDraw.COLOR_MUTED)
		}

		if (bingoState == EventsPage.BingoLoadState.LOADING) {
			cy = sectionSeparator(g, lx, cy, leftW)
			PvDraw.text(g, font, "Loading...", cx, cy, PvDraw.COLOR_MUTED)
		} else if (bingoState == EventsPage.BingoLoadState.ERROR) {
			cy = sectionSeparator(g, lx, cy, leftW)
			PvDraw.text(g, font, trim(font, bingoError, cw), cx, cy, 0xFFFF8888.toInt())
		} else if (bingo.eventsPlayed > 0 && cy + SEP_GAP + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW)
			val played = FormatUtil.commas(bingo.eventsPlayed.toLong())
			val points = FormatUtil.commas(bingo.totalPoints)
			cy = ui.tipStat(
				g, font, "Events played", played, PvDraw.COLOR_ACCENT, cx, cy, cw, mx, my,
				tipTitle(
					"Bingo history", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Events", PvDraw.COLOR_MUTED, played, PvDraw.COLOR_ACCENT),
					PvTooltip.Line.row("Total points", PvDraw.COLOR_MUTED, points, PvDraw.COLOR_GOLD)
				)
			)
			cy = ui.tipStat(
				g, font, "Total points", points, PvDraw.COLOR_GOLD, cx, cy, cw, mx, my,
				tipTitle("Total points", PvDraw.COLOR_GOLD, PvTooltip.Line.meta("All bingo events"))
			)
		} else if (bingoState == EventsPage.BingoLoadState.READY && bingoHistoryMissing && cy + SEP_GAP + font.lineHeight <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW)
			PvDraw.text(g, font, "History unavailable", cx, cy, PvDraw.COLOR_MUTED)
			cy += font.lineHeight + 2
			PvDraw.text(g, font, trim(font, "Retrying in background...", cw), cx, cy, PvDraw.COLOR_MUTED)
		}

		val history = bingo.history
		if (bingoState == EventsPage.BingoLoadState.READY && history.isNotEmpty() && cy + SEP_GAP + font.lineHeight + STAT_ROW <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW)
			PvDraw.text(g, font, "Recent events", cx, cy, PvDraw.COLOR_MUTED)
			cy += font.lineHeight + 3
			val histViewH = maxOf(0, bottom - cy)
			val histRows = minOf(history.size, maxOf(1, histViewH / STAT_ROW))
			for (i in 0 until histRows) {
				val ev = history[i]
				val rowY = cy + i * STAT_ROW
				EventsUi.statLine(g, font, "Event #" + ev.key, FormatUtil.commas(ev.points) + " pts", cx, rowY, cw, PvDraw.COLOR_GOLD)
				ui.addClippedHover(
					cx, rowY, cw, STAT_ROW, lx + PAD, y + PAD, leftW - PAD * 2, h - PAD * 2,
					tipTitle(
						"Bingo event #" + ev.key, PvDraw.COLOR_GOLD,
						PvTooltip.Line.row("Points", PvDraw.COLOR_MUTED, FormatUtil.commas(ev.points), PvDraw.COLOR_GOLD),
						PvTooltip.Line.row(
							"Goals done", PvDraw.COLOR_MUTED,
							FormatUtil.commas(ev.completedGoals.size.toLong()), PvDraw.COLOR_ACCENT
						)
					)
				)
			}
		}

		val rcx = rx + PAD
		var ry = y + PAD
		val rcw = rightW - PAD * 2
		val hasEvent = bingo.currentEventId > 0 || bingo.currentEventName.isNotBlank()
		if (bingoState == EventsPage.BingoLoadState.LOADING) {
			PvDraw.textCentered(g, font, "Loading bingo...", rx + rightW / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			return
		}
		if (bingoState == EventsPage.BingoLoadState.ERROR) {
			PvDraw.textCentered(g, font, bingoError, rx + rightW / 2, y + h / 2 - font.lineHeight / 2, 0xFFFF8888.toInt())
			return
		}
		if (bingoState != EventsPage.BingoLoadState.READY && bingo.currentGoals.isEmpty()) {
			PvDraw.textCentered(
				g, font, "Open Events to load bingo", rx + rightW / 2,
				y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED
			)
			return
		}

		if (hasEvent) {
			val title = bingo.currentEventName.ifBlank { "Event #" + bingo.currentEventId }
			ry += PvDraw.sectionHeader(g, font, "Current event", rcx, ry, rcw)
			PvDraw.text(g, font, trim(font, title, rcw), rcx, ry, PvDraw.COLOR_TEXT)
			ry += font.lineHeight + 2
			if (bingo.currentModifier.isNotBlank()) {
				PvDraw.text(
					g, font, trim(font, "Modifier · " + prettyModifier(bingo.currentModifier), rcw),
					rcx, ry, PvDraw.COLOR_MUTED
				)
				ry += font.lineHeight + 2
			}
			if (bingo.currentEndMs > 0L) {
				val now = System.currentTimeMillis()
				val timing: String
				val timingColor: Int
				if (now < bingo.currentStartMs) {
					timing = "Starts in " + FormatUtil.prettySpan(bingo.currentStartMs - now)
					timingColor = PvDraw.COLOR_ACCENT
				} else if (now < bingo.currentEndMs) {
					timing = "Ends in " + FormatUtil.prettySpan(bingo.currentEndMs - now)
					timingColor = PvDraw.COLOR_GOLD
				} else {
					timing = "Ended " + FormatUtil.prettySpan(now - bingo.currentEndMs) + " ago"
					timingColor = PvDraw.COLOR_MUTED
				}
				ry = ui.tipStat(
					g, font, "Schedule", timing, timingColor, rcx, ry, rcw, mx, my,
					tipTitle(title, PvDraw.COLOR_TEXT, PvTooltip.Line.row("Status", PvDraw.COLOR_MUTED, timing, timingColor))
				)
			}
			ry = sectionSeparator(g, rx, ry, rightW)
		} else {
			ry += PvDraw.sectionHeader(g, font, "Current event", rcx, ry, rcw)
		}

		val goals = bingo.currentGoals
		if (goals.isEmpty()) {
			PvDraw.text(g, font, "No goal data", rcx, ry, PvDraw.COLOR_MUTED)
			return
		}

		val completed = completedGoalIds(bingo)
		val gridTop = ry
		val gridH = maxOf(24, bottom - gridTop)
		renderBingoGrid(ui, g, font, goals, completed, rcx, gridTop, rcw, gridH)
	}

	private fun renderBingoGrid(
		ui: EventsUi,
		g: GuiGraphicsExtractor,
		font: Font,
		goals: List<EventsSnapshot.BingoGoal>,
		completed: Set<String>,
		x: Int,
		y: Int,
		w: Int,
		h: Int
	) {
		val cols = BINGO_COLS
		val rows = BINGO_ROWS
		val cellGap = 2
		val cellByW = maxOf(1, (w - maxOf(0, cols - 1) * cellGap) / cols)
		val cellByH = maxOf(1, (h - maxOf(0, rows - 1) * cellGap) / rows)
		val cell = minOf(22, minOf(cellByW, cellByH))
		val gridW = cols * cell + (cols - 1) * cellGap
		val gridH = rows * cell + (rows - 1) * cellGap
		val gridX = x + maxOf(0, (w - gridW) / 2)
		val gridY = y + maxOf(0, (h - gridH) / 2)
		val icon = maxOf(1, minOf(16, cell - 4))

		if (goals !== tipGoals || completed != tipCompleted) {
			tipGoals = goals
			tipCompleted = completed
			tipCache.clear()
		}
		val count = minOf(goals.size, cols * rows)
		for (i in 0 until count) {
			val goal = goals[i]
			val col = i % cols
			val row = i / cols
			val cx = gridX + col * (cell + cellGap)
			val cy = gridY + row * (cell + cellGap)
			val done = isGoalComplete(goal, completed)
			val community = goal.community

			val bg = if (done) (if (community) 0xFF1A2A3A.toInt() else 0xFF1A2A1A.toInt()) else SLOT_BG
			val border = if (done) {
				if (community) COLOR_COMMUNITY else COLOR_COMPLETE
			} else {
				if (community) 0xFF4A4A2A.toInt() else SLOT_BORDER
			}
			PvDraw.roundedFill(g, cx, cy, cell, cell, 3, bg)
			PvDraw.roundedRing(g, cx, cy, cell, cell, 3, border, PvDraw.C_ALL)

			val stack = bingoIcon(goal, done)
			val ix = cx + (cell - icon) / 2
			val iy = cy + (cell - icon) / 2
			val scale = icon / 16f
			if (scale != 1f) {
				g.pose().pushMatrix()
				g.pose().translate(ix.toFloat(), iy.toFloat())
				g.pose().scale(scale, scale)
				g.item(stack, 0, 0)
				g.pose().popMatrix()
			} else {
				g.item(stack, ix, iy)
			}

			if (community && !done && goal.required > 0L) {
				val fill = minOf(1.0, goal.progress.toDouble() / goal.required.toDouble()).toFloat()
				val barH = 2
				val barY = cy + cell - barH - 1
				PvDraw.progressBar(g, cx + 2, barY, cell - 4, barH, fill, COLOR_COMMUNITY, false)
			}

			val tip = tipCache.getOrPut(goal) { goalTip(goal, done, community) }
			ui.addClippedHover(cx, cy, cell, cell, ui.contentX, ui.contentY, ui.contentW, ui.contentH, tip)
		}
	}

	companion object {
		private val ICON_COMMUNITY_DONE = ItemStack(Items.DIAMOND)
		private val ICON_COMMUNITY = ItemStack(Items.GOLD_INGOT)
		private val ICON_PERSONAL_DONE = ItemStack(Items.FILLED_MAP)
		private val ICON_PERSONAL = ItemStack(Items.PAPER)

		private fun goalTip(goal: EventsSnapshot.BingoGoal, done: Boolean, community: Boolean): List<PvTooltip.Line> {
			val name = goal.name.ifBlank { prettyGoalId(goal.id) }
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title(name, if (done) COLOR_COMPLETE else PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.divider())
			tip.add(
				PvTooltip.Line.row(
					"Type", PvDraw.COLOR_MUTED,
					if (community) "Community" else "Personal",
					if (community) COLOR_COMMUNITY else PvDraw.COLOR_ACCENT
				)
			)
			tip.add(
				PvTooltip.Line.row(
					"Status", PvDraw.COLOR_MUTED,
					if (done) "Complete" else "Incomplete",
					if (done) COLOR_COMPLETE else PvDraw.COLOR_MUTED
				)
			)
			if (community && goal.required > 0L) {
				tip.add(
					PvTooltip.Line.row(
						"Progress", PvDraw.COLOR_MUTED,
						FormatUtil.commas(goal.progress) + " / " + FormatUtil.commas(goal.required),
						if (done) COLOR_COMPLETE else PvDraw.COLOR_GOLD
					)
				)
			} else if (!community && goal.required > 0L) {
				tip.add(PvTooltip.Line.row("Required", PvDraw.COLOR_MUTED, FormatUtil.commas(goal.required), PvDraw.COLOR_TEXT))
			}
			if (goal.lore.isNotBlank()) {
				tip.add(PvTooltip.Line.blank())
				for (line in wrapLore(goal.lore, 42)) {
					tip.add(PvTooltip.Line.meta(line))
				}
			}
			return tip
		}

		private fun bingoIcon(goal: EventsSnapshot.BingoGoal, done: Boolean): ItemStack = if (goal.community) {
			if (done) ICON_COMMUNITY_DONE else ICON_COMMUNITY
		} else {
			if (done) ICON_PERSONAL_DONE else ICON_PERSONAL
		}

		private fun completedGoalIds(bingo: EventsSnapshot.Bingo?): Set<String> {
			val out = HashSet<String>()
			if (bingo == null) {
				return out
			}
			val current = bingo.currentEventId
			for (ev in bingo.history) {
				if (current > 0 && ev.key == current) {
					out.addAll(ev.completedGoals)
				}
			}
			for (goal in bingo.currentGoals) {
				if (goal.community && goal.required > 0L && goal.progress >= goal.required) {
					out.add(goal.id)
				}
			}
			return out
		}

		private fun isGoalComplete(goal: EventsSnapshot.BingoGoal?, completed: Set<String>): Boolean {
			if (goal == null) {
				return false
			}
			if (completed.contains(goal.id)) {
				return true
			}
			return goal.community && goal.required > 0L && goal.progress >= goal.required
		}
	}
}
