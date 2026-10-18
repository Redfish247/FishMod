/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.dungeons;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Compact results popup for dungeon XP calculators. */
public final class DungeonCalcOverlay {
	public record FloorLine(String label, long runsNeeded, long xpPerRun, boolean master) {
	}

	public record ClassBlock(String classId, String name, int currentLevel, long selectedRuns) {
	}

	public record View(
		String titleKey,
		Object titleArg,
		float xpNeeded,
		String modsLabel,
		List<FloorLine> floors,
		List<ClassBlock> classBlocks,
		long totalRuns,
		String floorLabel,
		boolean timedOut,
		String accentClassId
	) {
		public static View single(
			String titleKey,
			Object titleArg,
			float xpNeeded,
			String modsLabel,
			List<FloorLine> floors,
			String accentClassId
		) {
			return new View(titleKey, titleArg, xpNeeded, modsLabel, floors, List.of(), 0L, "", false, accentClassId);
		}

		public static View average(
			String titleKey,
			Object titleArg,
			String modsLabel,
			List<ClassBlock> blocks,
			long totalRuns,
			String floorLabel,
			boolean timedOut
		) {
			return new View(titleKey, titleArg, 0F, modsLabel, List.of(), blocks, totalRuns, floorLabel, timedOut, null);
		}

		public static View error(String titleKey, String message) {
			return new View(titleKey, "", -1F, message, List.of(), List.of(), 0L, "", false, null);
		}

		public boolean isAverage() {
			return this.classBlocks != null && !this.classBlocks.isEmpty();
		}
	}

	private static final int PAD = 8;
	private static final int CLOSE_SIZE = 12;
	private static final int COLOR_XP = PvDraw.COLOR_GOLD;
	private static final int COLOR_RUNS = PvDraw.COLOR_ACCENT;
	private static final int COLOR_DONE = 0xFF6DFF8A;
	private static final int COLOR_WARN = 0xFFFFB86C;
	private static final int COLOR_FLOOR_NORMAL = 0xFF7CB87C;
	private static final int COLOR_FLOOR_MASTER = 0xFFB85A5A;
	private static final int COLOR_PER_RUN = 0xFFE8E8F0;
	private static final int COLOR_MAGE = 0xFF6EB5FF;
	private static final int COLOR_BERSERK = 0xFFFF6B6B;
	private static final int COLOR_ARCHER = 0xFF7CFF9A;
	private static final int COLOR_HEALER = 0xFFFF8EC8;
	private static final int COLOR_TANK = 0xFFC0C0D0;

	private boolean open;
	private View view;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int closeX;
	private int closeY;
	private int scroll;

	public boolean isOpen() {
		return this.open;
	}

	public void open(View view) {
		this.view = view;
		this.open = view != null;
		this.scroll = 0;
	}

	public void close() {
		this.open = false;
		this.view = null;
		this.scroll = 0;
	}

	public boolean mouseScrolled(double delta) {
		if (!this.open || this.view == null) {
			return false;
		}
		// Scroll whenever the panel is open and content may overflow (not only average views).
		this.scroll = Math.max(0, this.scroll - (int) Math.round(delta * 12));
		return true;
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		int screenW,
		int screenH,
		int mouseX,
		int mouseY
	) {
		if (!this.open || this.view == null) {
			return;
		}

		PvDraw.fill(g, 0, 0, screenW, screenH, 0x88000000);

		int lineH = font.lineHeight + 2;
		List<PvTooltip.Line> lines = buildLines(this.view);
		boolean average = this.view.isAverage();
		int contentH = lines.size() * lineH + PAD * 2 + font.lineHeight + 6;
		this.panelW = average
			? Math.min(360, Math.max(260, screenW * 2 / 5))
			: Math.min(280, Math.max(200, screenW / 3));
		this.panelH = Math.min(contentH, screenH - 40);
		this.panelX = (screenW - this.panelW) / 2;
		this.panelY = (screenH - this.panelH) / 2;

		int maxScroll = Math.max(0, contentH - this.panelH);
		this.scroll = Math.min(this.scroll, maxScroll);

		PvDraw.overlayPanel(g, this.panelX, this.panelY, this.panelW, this.panelH);

		String title = Component.translatable(this.view.titleKey(), this.view.titleArg()).getString();
		PvDraw.textBold(g, font, title, this.panelX + PAD, this.panelY + PAD, titleColor(this.view));

		this.closeX = this.panelX + this.panelW - PAD - CLOSE_SIZE;
		this.closeY = this.panelY + PAD;
		boolean closeHover = mouseX >= this.closeX && mouseX < this.closeX + CLOSE_SIZE
			&& mouseY >= this.closeY && mouseY < this.closeY + CLOSE_SIZE;
		PvDraw.roundedFill(g, this.closeX, this.closeY, CLOSE_SIZE, CLOSE_SIZE, 3, closeHover ? 0xFF3A3A4A : 0x24FFFFFF);
		PvDraw.roundedRing(g, this.closeX, this.closeY, CLOSE_SIZE, CLOSE_SIZE, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		PvDraw.textCentered(g, font, "x", this.closeX + CLOSE_SIZE / 2, this.closeY + 1, PvDraw.COLOR_MUTED);

		int y = this.panelY + PAD + font.lineHeight + 6 - this.scroll;
		int maxY = this.panelY + this.panelH - PAD;
		int minY = this.panelY + PAD + font.lineHeight + 4;
		for (PvTooltip.Line line : lines) {
			if (y + font.lineHeight > maxY) {
				break;
			}
			if (y >= minY && line.kind() != PvTooltip.Kind.BLANK) {
				PvDraw.text(g, font, toComponent(line), this.panelX + PAD, y);
			}
			y += lineH;
		}
	}

	/** @return true if the click was consumed (close or absorb inside panel). */
	public boolean mouseClicked(double mx, double my) {
		if (!this.open) {
			return false;
		}
		if (mx >= this.closeX && mx < this.closeX + CLOSE_SIZE
			&& my >= this.closeY && my < this.closeY + CLOSE_SIZE) {
			close();
			return true;
		}
		if (mx >= this.panelX && mx < this.panelX + this.panelW
			&& my >= this.panelY && my < this.panelY + this.panelH) {
			return true;
		}
		close();
		return true;
	}

	private static int titleColor(View view) {
		if (view.accentClassId() != null && !view.accentClassId().isBlank()) {
			return classColor(view.accentClassId());
		}
		if (view.isAverage()) {
			return PvDraw.COLOR_GOLD;
		}
		if (view.xpNeeded() < 0F) {
			return PvDraw.COLOR_TEXT;
		}
		return PvDraw.COLOR_GOLD;
	}

	private static List<PvTooltip.Line> buildLines(View view) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		if (view.xpNeeded() < 0F) {
			if (view.modsLabel() != null && !view.modsLabel().isBlank()) {
				lines.add(PvTooltip.Line.of(view.modsLabel(), COLOR_WARN));
			}
			return lines;
		}
		if (view.isAverage()) {
			if (view.modsLabel() != null && !view.modsLabel().isBlank()) {
				lines.add(PvTooltip.Line.of(view.modsLabel(), PvDraw.COLOR_MUTED));
			}
			String floor = view.floorLabel() == null || view.floorLabel().isBlank() ? "-" : view.floorLabel();
			lines.add(PvTooltip.Line.text(List.of(
				PvTooltip.Span.of("Total: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.bold(FormatUtil.commas(view.totalRuns()), COLOR_RUNS),
				PvTooltip.Span.of(" ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.of(floor, floorColor(floor)),
				PvTooltip.Span.of(" runs (S+ est.)", PvDraw.COLOR_MUTED)
			)));
			if (view.timedOut()) {
				lines.add(PvTooltip.Line.of("Hit the run cap", COLOR_WARN));
			}
			lines.add(PvTooltip.Line.of("Runs per class:", PvDraw.COLOR_MUTED));
			lines.add(PvTooltip.Line.blank());
			for (ClassBlock block : view.classBlocks()) {
				int nameColor = classColor(block.classId());
				if (block.selectedRuns() <= 0L) {
					lines.add(PvTooltip.Line.text(List.of(
						PvTooltip.Span.bold(block.name(), nameColor),
						PvTooltip.Span.of(" - ", PvDraw.COLOR_MUTED),
						PvTooltip.Span.of("0 (already covered)", COLOR_DONE)
					)));
				} else {
					lines.add(PvTooltip.Line.text(List.of(
						PvTooltip.Span.bold(block.name(), nameColor),
						PvTooltip.Span.of(" (", PvDraw.COLOR_MUTED),
						PvTooltip.Span.of(String.valueOf(block.currentLevel()), PvDraw.COLOR_TEXT),
						PvTooltip.Span.of(")  ", PvDraw.COLOR_MUTED),
						PvTooltip.Span.bold(FormatUtil.commas(block.selectedRuns()), COLOR_RUNS),
						PvTooltip.Span.of(" runs", PvDraw.COLOR_MUTED)
					)));
				}
			}
			return lines;
		}

		lines.add(PvTooltip.Line.text(List.of(
			PvTooltip.Span.of("XP needed: ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.bold(FormatUtil.commas(Math.round(view.xpNeeded())), COLOR_XP)
		)));
		if (view.modsLabel() != null && !view.modsLabel().isBlank()) {
			lines.add(PvTooltip.Line.of(view.modsLabel(), PvDraw.COLOR_MUTED));
		}
		lines.add(PvTooltip.Line.blank());
		if (view.floors().isEmpty()) {
			if (view.xpNeeded() <= 0F) {
				lines.add(PvTooltip.Line.of("Already at target", COLOR_DONE));
			} else {
				lines.add(PvTooltip.Line.of("No floors available", COLOR_WARN));
			}
			return lines;
		}
		lines.add(PvTooltip.Line.of("Best floors (S+ est.):", PvDraw.COLOR_MUTED));
		for (FloorLine floor : view.floors()) {
			int labelColor = floor.master() ? COLOR_FLOOR_MASTER : COLOR_FLOOR_NORMAL;
			lines.add(PvTooltip.Line.text(List.of(
				PvTooltip.Span.bold(floor.label(), labelColor),
				PvTooltip.Span.of("  ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.bold(FormatUtil.commas(floor.runsNeeded()), COLOR_RUNS),
				PvTooltip.Span.of(" runs  (", PvDraw.COLOR_MUTED),
				PvTooltip.Span.of(FormatUtil.shortXp(floor.xpPerRun()), COLOR_PER_RUN),
				PvTooltip.Span.of("/run)", PvDraw.COLOR_MUTED)
			)));
		}
		return lines;
	}

	private static Component toComponent(PvTooltip.Line line) {
		MutableComponent out = Component.empty();
		for (PvTooltip.Span span : line.spans()) {
			out.append(span.toComponent());
		}
		return out;
	}

	/** Matches dungeon page MM red / normal green; labels are {@code F7}/{@code M7}. */
	private static int floorColor(String label) {
		if (label == null || label.isBlank()) {
			return COLOR_FLOOR_NORMAL;
		}
		String trimmed = label.trim();
		char first = trimmed.charAt(0);
		if ((first == 'M' || first == 'm')
			&& trimmed.length() > 1
			&& Character.isDigit(trimmed.charAt(1))) {
			return COLOR_FLOOR_MASTER;
		}
		String lower = trimmed.toLowerCase();
		if (lower.contains("master") || lower.startsWith("mm")) {
			return COLOR_FLOOR_MASTER;
		}
		return COLOR_FLOOR_NORMAL;
	}

	private static int classColor(String classId) {
		if (classId == null || classId.isBlank()) {
			return PvDraw.COLOR_TEXT;
		}
		return switch (classId.toLowerCase()) {
			case "mage" -> COLOR_MAGE;
			case "berserk" -> COLOR_BERSERK;
			case "archer" -> COLOR_ARCHER;
			case "healer" -> COLOR_HEALER;
			case "tank" -> COLOR_TANK;
			default -> PvDraw.COLOR_TEXT;
		};
	}
}
