/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.events.page;

import fishmod.pv.data.EventsSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.events.EventsPage;
import fishmod.pv.gui.events.EventsUi;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static fishmod.pv.gui.events.EventsUi.*;

/** Bingo events subpage. */
public final class BingoPage {
	public void render(
		EventsSnapshot snapshot,
		EventsPage.BingoLoadState bingoState,
		String bingoError,
		boolean bingoHistoryMissing,
		EventsUi ui,
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mx,
		int my
	) {
		EventsSnapshot.Bingo bingo = snapshot.bingo();
		int rightW = Math.max(200, w * 58 / 100);
		int leftW = w - rightW - GAP;
		int lx = x;
		int rx = x + leftW + GAP;
		PvDraw.innerPanel(g, lx, y, leftW, h);
		PvDraw.innerPanel(g, rx, y, rightW, h);

		int bottom = y + h - PAD;
		int cx = lx + PAD;
		int cy = y + PAD;
		int cw = leftW - PAD * 2;

		cy += PvDraw.sectionHeader(g, font, "Bingo profile", cx, cy, cw);
		if (bingo.hasBingoProfile()) {
			cy = ui.tipStat(g, font, "Profile", bingo.bingoProfileName(), PvDraw.COLOR_GOLD, cx, cy, cw, mx, my,
				tipTitle("Bingo profile", PvDraw.COLOR_GOLD,
					PvTooltip.Line.row("Name", PvDraw.COLOR_MUTED, bingo.bingoProfileName(), PvDraw.COLOR_GOLD),
					bingo.bingoFirstJoinMs() > 0L
						? PvTooltip.Line.meta("Joined " + formatAgo(bingo.bingoFirstJoinMs()))
						: null));
			if (bingo.bingoFirstJoinMs() > 0L && cy + STAT_ROW <= bottom) {
				cy = ui.tipStat(g, font, "Joined", formatAgo(bingo.bingoFirstJoinMs()), PvDraw.COLOR_MUTED, cx, cy, cw, mx, my,
					tipTitle("First join", PvDraw.COLOR_TEXT,
						PvTooltip.Line.meta(formatAgo(bingo.bingoFirstJoinMs()))));
			}
		} else {
			cy = EventsUi.statLine(g, font, "Profile", "None", cx, cy, cw, PvDraw.COLOR_MUTED);
		}

		if (bingoState == EventsPage.BingoLoadState.LOADING) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Loading...", cx, cy, PvDraw.COLOR_MUTED);
		} else if (bingoState == EventsPage.BingoLoadState.ERROR) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, trim(font, bingoError, cw), cx, cy, 0xFFFF8888);
		} else if (bingo.eventsPlayed() > 0 && cy + SEP_GAP + STAT_ROW * 2 <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			cy = ui.tipStat(g, font, "Events played", FormatUtil.commas(bingo.eventsPlayed()), PvDraw.COLOR_ACCENT,
				cx, cy, cw, mx, my,
				tipTitle("Bingo history", PvDraw.COLOR_ACCENT,
					PvTooltip.Line.row("Events", PvDraw.COLOR_MUTED, FormatUtil.commas(bingo.eventsPlayed()), PvDraw.COLOR_ACCENT),
					PvTooltip.Line.row("Total points", PvDraw.COLOR_MUTED, FormatUtil.commas(bingo.totalPoints()), PvDraw.COLOR_GOLD)));
			cy = ui.tipStat(g, font, "Total points", FormatUtil.commas(bingo.totalPoints()), PvDraw.COLOR_GOLD,
				cx, cy, cw, mx, my,
				tipTitle("Total points", PvDraw.COLOR_GOLD,
					PvTooltip.Line.meta("All bingo events")));
		} else if (bingoState == EventsPage.BingoLoadState.READY && bingoHistoryMissing && cy + SEP_GAP + font.lineHeight <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "History unavailable", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 2;
			PvDraw.text(g, font, trim(font, "Retrying in background...", cw), cx, cy, PvDraw.COLOR_MUTED);
		}

		List<EventsSnapshot.BingoEvent> history = bingo.history();
		if (bingoState == EventsPage.BingoLoadState.READY && !history.isEmpty() && cy + SEP_GAP + font.lineHeight + STAT_ROW <= bottom) {
			cy = sectionSeparator(g, lx, cy, leftW);
			PvDraw.text(g, font, "Recent events", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 3;
			int histViewH = Math.max(0, bottom - cy);
			int histRows = Math.min(history.size(), Math.max(1, histViewH / STAT_ROW));
			for (int i = 0; i < histRows; i++) {
				EventsSnapshot.BingoEvent ev = history.get(i);
				String label = "Event #" + ev.key();
				String value = FormatUtil.commas(ev.points()) + " pts";
				int rowY = cy + i * STAT_ROW;
				EventsUi.statLine(g, font, label, value, cx, rowY, cw, PvDraw.COLOR_GOLD);
				ui.addClippedHover(cx, rowY, cw, STAT_ROW, lx + PAD, y + PAD, leftW - PAD * 2, h - PAD * 2,
					tipTitle("Bingo event #" + ev.key(), PvDraw.COLOR_GOLD,
						PvTooltip.Line.row("Points", PvDraw.COLOR_MUTED, FormatUtil.commas(ev.points()), PvDraw.COLOR_GOLD),
						PvTooltip.Line.row("Goals done", PvDraw.COLOR_MUTED,
							FormatUtil.commas(ev.completedGoals().size()), PvDraw.COLOR_ACCENT)));
			}
		}

		int rcx = rx + PAD;
		int ry = y + PAD;
		int rcw = rightW - PAD * 2;
		boolean hasEvent = bingo.currentEventId() > 0 || !bingo.currentEventName().isBlank();
		if (bingoState == EventsPage.BingoLoadState.LOADING) {
			PvDraw.textCentered(g, font, "Loading bingo...", rx + rightW / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}
		if (bingoState == EventsPage.BingoLoadState.ERROR) {
			PvDraw.textCentered(g, font, bingoError, rx + rightW / 2, y + h / 2 - font.lineHeight / 2, 0xFFFF8888);
			return;
		}
		if (bingoState != EventsPage.BingoLoadState.READY && bingo.currentGoals().isEmpty()) {
			PvDraw.textCentered(g, font, "Open Events to load bingo", rx + rightW / 2,
				y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		if (hasEvent) {
			String title = bingo.currentEventName().isBlank()
				? ("Event #" + bingo.currentEventId())
				: bingo.currentEventName();
			ry += PvDraw.sectionHeader(g, font, "Current event", rcx, ry, rcw);
			PvDraw.text(g, font, trim(font, title, rcw), rcx, ry, PvDraw.COLOR_TEXT);
			ry += font.lineHeight + 2;
			if (!bingo.currentModifier().isBlank()) {
				PvDraw.text(g, font, trim(font, "Modifier · " + prettyModifier(bingo.currentModifier()), rcw),
					rcx, ry, PvDraw.COLOR_MUTED);
				ry += font.lineHeight + 2;
			}
			if (bingo.currentEndMs() > 0L) {
				long now = System.currentTimeMillis();
				String timing;
				int timingColor;
				if (now < bingo.currentStartMs()) {
					timing = "Starts in " + FormatUtil.prettySpan(bingo.currentStartMs() - now);
					timingColor = PvDraw.COLOR_ACCENT;
				} else if (now < bingo.currentEndMs()) {
					timing = "Ends in " + FormatUtil.prettySpan(bingo.currentEndMs() - now);
					timingColor = PvDraw.COLOR_GOLD;
				} else {
					timing = "Ended " + FormatUtil.prettySpan(now - bingo.currentEndMs()) + " ago";
					timingColor = PvDraw.COLOR_MUTED;
				}
				ry = ui.tipStat(g, font, "Schedule", timing, timingColor, rcx, ry, rcw, mx, my,
					tipTitle(title, PvDraw.COLOR_TEXT,
						PvTooltip.Line.row("Status", PvDraw.COLOR_MUTED, timing, timingColor)));
			}
			ry = sectionSeparator(g, rx, ry, rightW);
		} else {
			ry += PvDraw.sectionHeader(g, font, "Current event", rcx, ry, rcw);
		}

		List<EventsSnapshot.BingoGoal> goals = bingo.currentGoals();
		if (goals.isEmpty()) {
			PvDraw.text(g, font, "No goal data", rcx, ry, PvDraw.COLOR_MUTED);
			return;
		}

		Set<String> completed = completedGoalIds(bingo);
		int gridTop = ry;
		int gridH = Math.max(24, bottom - gridTop);
		renderBingoGrid(ui, g, font, goals, completed, rcx, gridTop, rcw, gridH, mx, my);
	}

	private void renderBingoGrid(
		EventsUi ui,
		GuiGraphicsExtractor g, Font font,
		List<EventsSnapshot.BingoGoal> goals, Set<String> completed,
		int x, int y, int w, int h, int mx, int my
	) {
		int cols = BINGO_COLS;
		int rows = BINGO_ROWS;
		int cellGap = 2;
		int cellByW = Math.max(1, (w - Math.max(0, cols - 1) * cellGap) / cols);
		int cellByH = Math.max(1, (h - Math.max(0, rows - 1) * cellGap) / rows);
		int cell = Math.min(22, Math.min(cellByW, cellByH));
		int gridW = cols * cell + (cols - 1) * cellGap;
		int gridH = rows * cell + (rows - 1) * cellGap;
		int gridX = x + Math.max(0, (w - gridW) / 2);
		int gridY = y + Math.max(0, (h - gridH) / 2);
		int icon = Math.max(1, Math.min(16, cell - 4));

		int count = Math.min(goals.size(), cols * rows);
		for (int i = 0; i < count; i++) {
			EventsSnapshot.BingoGoal goal = goals.get(i);
			int col = i % cols;
			int row = i / cols;
			int cx = gridX + col * (cell + cellGap);
			int cy = gridY + row * (cell + cellGap);
			boolean done = isGoalComplete(goal, completed);
			boolean community = goal.community();

			int bg = done
				? (community ? 0xFF1A2A3A : 0xFF1A2A1A)
				: SLOT_BG;
			int border = done
				? (community ? COLOR_COMMUNITY : COLOR_COMPLETE)
				: (community ? 0xFF4A4A2A : SLOT_BORDER);
			PvDraw.roundedFill(g, cx, cy, cell, cell, 3, bg);
			PvDraw.roundedRing(g, cx, cy, cell, cell, 3, border, PvDraw.C_ALL);

			ItemStack stack = bingoIcon(goal, done);
			int ix = cx + (cell - icon) / 2;
			int iy = cy + (cell - icon) / 2;
			float scale = icon / 16f;
			if (scale != 1f) {
				g.pose().pushMatrix();
				g.pose().translate(ix, iy);
				g.pose().scale(scale, scale);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
			} else {
				g.item(stack, ix, iy);
			}

			if (community && !done && goal.required() > 0L) {
				float fill = (float) Math.min(1.0, (double) goal.progress() / (double) goal.required());
				int barH = 2;
				int barY = cy + cell - barH - 1;
				PvDraw.progressBar(g, cx + 2, barY, cell - 4, barH, fill, COLOR_COMMUNITY, false);
			}

			String name = goal.name().isBlank() ? prettyGoalId(goal.id()) : goal.name();
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title(name, done ? COLOR_COMPLETE : PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			tip.add(PvTooltip.Line.row("Type", PvDraw.COLOR_MUTED,
				community ? "Community" : "Personal",
				community ? COLOR_COMMUNITY : PvDraw.COLOR_ACCENT));
			tip.add(PvTooltip.Line.row("Status", PvDraw.COLOR_MUTED,
				done ? "Complete" : "Incomplete",
				done ? COLOR_COMPLETE : PvDraw.COLOR_MUTED));
			if (community && goal.required() > 0L) {
				tip.add(PvTooltip.Line.row("Progress", PvDraw.COLOR_MUTED,
					FormatUtil.commas(goal.progress()) + " / " + FormatUtil.commas(goal.required()),
					done ? COLOR_COMPLETE : PvDraw.COLOR_GOLD));
			} else if (!community && goal.required() > 0L) {
				tip.add(PvTooltip.Line.row("Required", PvDraw.COLOR_MUTED,
					FormatUtil.commas(goal.required()), PvDraw.COLOR_TEXT));
			}
			if (!goal.lore().isBlank()) {
				tip.add(PvTooltip.Line.blank());
				for (String line : wrapLore(goal.lore(), 42)) {
					tip.add(PvTooltip.Line.meta(line));
				}
			}
			ui.addClippedHover(cx, cy, cell, cell, ui.contentX, ui.contentY, ui.contentW, ui.contentH, tip);
		}
	}

	private static ItemStack bingoIcon(EventsSnapshot.BingoGoal goal, boolean done) {
		if (goal.community()) {
			return new ItemStack(done ? Items.DIAMOND : Items.GOLD_INGOT);
		}
		return new ItemStack(done ? Items.FILLED_MAP : Items.PAPER);
	}

	private static Set<String> completedGoalIds(EventsSnapshot.Bingo bingo) {
		Set<String> out = new HashSet<>();
		if (bingo == null) {
			return out;
		}
		int current = bingo.currentEventId();
		for (EventsSnapshot.BingoEvent ev : bingo.history()) {
			if (current > 0 && ev.key() == current) {
				out.addAll(ev.completedGoals());
			}
		}
		for (EventsSnapshot.BingoGoal goal : bingo.currentGoals()) {
			if (goal.community() && goal.required() > 0L && goal.progress() >= goal.required()) {
				out.add(goal.id());
			}
		}
		return out;
	}

	private static boolean isGoalComplete(EventsSnapshot.BingoGoal goal, Set<String> completed) {
		if (goal == null) {
			return false;
		}
		if (completed.contains(goal.id())) {
			return true;
		}
		return goal.community() && goal.required() > 0L && goal.progress() >= goal.required();
	}
}
