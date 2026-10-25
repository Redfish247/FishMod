/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.weight;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;

public final class WeightBreakdown {
	private static final int COLOR_WEIGHT = 0xFF55FFFF;
	private static final int COLOR_OVERFLOW = 0xFF7AD7FF;
	private static final int COLOR_SKILLS = 0xFF55FF55;
	private static final int COLOR_SLAYERS = 0xFFFF5555;
	private static final int COLOR_DUNGEONS = 0xFFAA55FF;

	public record Line(String label, double value, double overflow) {
		public double total() {
			return value + overflow;
		}
	}

	public record Category(String name, double value, double overflow, List<Line> lines) {
		public double total() {
			return value + overflow;
		}
	}

	private final WeightSystem system;
	private final double base;
	private final double overflow;
	private final String stageOrRank;
	private final List<Category> categories;

	public WeightBreakdown(WeightSystem system, double base, double overflow, String stageOrRank, List<Category> categories) {
		this.system = system;
		this.base = base;
		this.overflow = overflow;
		this.stageOrRank = stageOrRank;
		this.categories = List.copyOf(categories);
	}

	public static WeightBreakdown empty(WeightSystem system) {
		return new WeightBreakdown(system, 0, 0, "-", List.of());
	}

	public WeightSystem system() {
		return this.system;
	}

	public double base() {
		return this.base;
	}

	public double overflow() {
		return this.overflow;
	}

	public double total() {
		return this.base + this.overflow;
	}

	public String stageOrRank() {
		return this.stageOrRank;
	}

	public List<Category> categories() {
		return this.categories;
	}

	public List<String> tooltipLines() {
		return tooltipStyledLines().stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).reduce("", String::concat))
			.toList();
	}

	public List<PvTooltip.Line> tooltipStyledLines() {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.bold(this.system.display() + " Weight", PvDraw.COLOR_ACCENT));
		lines.add(new PvTooltip.Line(List.of(
			PvTooltip.Span.of("Total: ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.bold(format(total()), COLOR_WEIGHT),
			PvTooltip.Span.of(" (", PvDraw.COLOR_MUTED),
			PvTooltip.Span.of(format(this.base), PvDraw.COLOR_TEXT),
			PvTooltip.Span.of(" without Overflow)", PvDraw.COLOR_MUTED)
		)));
		String kind = this.system == WeightSystem.SENITHER ? "Stage" : "Rank";
		lines.add(new PvTooltip.Line(List.of(
			PvTooltip.Span.of(kind + ": ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.bold(this.stageOrRank, WeightStages.colorFor(this.stageOrRank))
		)));
		for (Category category : this.categories) {
			lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED));
			int catColor = switch (category.name()) {
				case "Skills" -> COLOR_SKILLS;
				case "Slayers" -> COLOR_SLAYERS;
				case "Dungeons" -> COLOR_DUNGEONS;
				default -> PvDraw.COLOR_ACCENT;
			};
			List<PvTooltip.Span> header = new ArrayList<>();
			header.add(PvTooltip.Span.bold(category.name() + ": ", catColor));
			header.add(PvTooltip.Span.bold(format(category.total()), COLOR_WEIGHT));
			if (category.overflow() > 0.05) {
				header.add(PvTooltip.Span.of(" (+" + format(category.overflow()) + ")", COLOR_OVERFLOW));
			}
			lines.add(new PvTooltip.Line(header));
			for (Line line : category.lines()) {
				List<PvTooltip.Span> row = new ArrayList<>();
				row.add(PvTooltip.Span.of("  " + line.label() + ": ", PvDraw.COLOR_MUTED));
				row.add(PvTooltip.Span.of(format(line.total()), PvDraw.COLOR_TEXT));
				if (line.overflow() > 0.05) {
					row.add(PvTooltip.Span.of(" (+" + format(line.overflow()) + ")", COLOR_OVERFLOW));
				}
				lines.add(new PvTooltip.Line(row));
			}
		}
		lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED));
		lines.add(PvTooltip.Line.of(
			"Click to switch to " + this.system.other().display(),
			PvDraw.COLOR_MUTED
		));
		return lines;
	}

	private static String format(double value) {
		return FormatUtil.weight(value);
	}
}
