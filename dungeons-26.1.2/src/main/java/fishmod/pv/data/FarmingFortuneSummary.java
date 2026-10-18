/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import fishmod.pv.data.FarmingFortuneEstimate.Garden;
import fishmod.pv.data.FarmingFortuneEstimate.Kind;
import fishmod.pv.data.FarmingFortuneEstimate.Line;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.NbtAttrs;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Groups the estimate into short page labels; the full sources stay available for tooltips. Never changes totals. */
public final class FarmingFortuneSummary {
	public enum Category {
		SKILLS("Skills"), GARDEN("Garden"), SHARDS("Shards"), ACCESSORIES("Accessories");

		private final String label;

		Category(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** {@code lines} holds counted and unavailable sources; temporary ones are listed separately. */
	public record Group(Category category, double total, List<Line> lines) {
	}

	private static final Map<String, Category> CATEGORIES = Map.ofEntries(
		Map.entry("Farming level", Category.SKILLS),
		Map.entry("Anita bonus", Category.SKILLS),
		Map.entry("Unlocked plots", Category.GARDEN),
		Map.entry("Garden bestiary", Category.GARDEN),
		Map.entry("DNA Analysis milestone", Category.GARDEN),
		Map.entry("Cropshot chip", Category.GARDEN),
		Map.entry("Fly shard", Category.SHARDS),
		Map.entry("Galaxy Fish shard", Category.SHARDS),
		Map.entry("Firefly and Lunar Moth shards", Category.SHARDS),
		Map.entry("Accessories", Category.ACCESSORIES),
		Map.entry("Helianthus Relic", Category.ACCESSORIES)
	);
	private static final Map<String, String> SHORT = Map.ofEntries(
		Map.entry("Farming level", "Farming"),
		Map.entry("Anita bonus", "Anita"),
		Map.entry("Unlocked plots", "Plots"),
		Map.entry("Garden bestiary", "Bestiary"),
		Map.entry("DNA Analysis milestone", "DNA"),
		Map.entry("Cropshot chip", "Cropshot"),
		Map.entry("Fly shard", "Fly"),
		Map.entry("Galaxy Fish shard", "Galaxy Fish"),
		Map.entry("Firefly and Lunar Moth shards", "Firefly, Lunar Moth"),
		Map.entry("Helianthus Relic", "Relic")
	);
	private static final Map<String, String> SHARD_KEYS = Map.of(
		"Fly shard", "fortunate_farmer",
		"Galaxy Fish shard", "ultimate_dna",
		"Firefly and Lunar Moth shards", "solar_power"
	);

	private FarmingFortuneSummary() {
	}

	/** Permanent bonuses in fixed category order, skipping empty categories. Totals add up to {@code permanentTotal}. */
	public static List<Group> permanentGroups(FarmingFortuneEstimate est, Garden garden) {
		Map<Category, List<Line>> byCategory = new EnumMap<>(Category.class);
		for (Line line : est.permanentLines(garden)) {
			if (line.kind() != Kind.CONDITIONAL) {
				byCategory.computeIfAbsent(category(line.label()), c -> new ArrayList<>()).add(line);
			}
		}
		List<Group> out = new ArrayList<>();
		for (Map.Entry<Category, List<Line>> e : byCategory.entrySet()) {
			double total = 0D;
			for (Line line : e.getValue()) {
				if (line.kind() == Kind.COUNTED) {
					total += line.value();
				}
			}
			out.add(new Group(e.getKey(), total, List.copyOf(e.getValue())));
		}
		return out;
	}

	/** Unrecognised sources land in Garden so a new bonus is never dropped from the page. */
	public static Category category(String label) {
		return CATEGORIES.getOrDefault(label, Category.GARDEN);
	}

	public static String shortLabel(Line line) {
		if ("Accessories".equals(line.label())) {
			return line.note();
		}
		return SHORT.getOrDefault(line.label(), line.label());
	}

	/** Comma-joined short source names, for the second line of a category tile. */
	public static String detail(Group group) {
		StringBuilder out = new StringBuilder();
		for (Line line : group.lines()) {
			if (line.kind() == Kind.COUNTED && line.value() <= 0D) {
				continue;
			}
			if (out.length() > 0) {
				out.append(", ");
			}
			out.append(shortLabel(line));
		}
		return out.toString();
	}

	public static String shardKey(String label) {
		return SHARD_KEYS.get(label);
	}

	/** Tile detail for the tool line when there is no tool to show. */
	public static String missingToolDetail(Line toolLine) {
		return switch (toolLine.note()) {
			case "Inventory API off" -> "Inventory API off";
			case "Toolkit slot unreadable", "Toolkit not in API" -> "Toolkit not readable";
			default -> "No tool";
		};
	}

	public static String missingToolExplanation(String crop, Line toolLine) {
		return switch (toolLine.note()) {
			case "Inventory API off" -> "The inventory API is off, so tools can't be checked.";
			case "Toolkit slot unreadable" -> "The " + crop + " toolkit slot couldn't be read.";
			case "Toolkit not in API" -> "The Farming Toolkit isn't in the API for this profile.";
			case "None in inventories, toolkit locked" -> "No " + crop + " tool in inventories, and the toolkit is locked.";
			default -> "No " + crop + " tool in inventories or the toolkit.";
		};
	}

	/** Display name without the reforge or "Mk." so the page can show it in one short line. */
	public static String shortToolName(InventoryDecoder.Stack stack) {
		String raw = stack.displayName() == null ? "" : stack.displayName().replaceAll("\u00a7.", "").trim();
		String name = raw.isEmpty() ? stack.id() : raw;
		String reforge = NbtAttrs.string(stack.extraAttributes(), "modifier");
		if (reforge != null && !reforge.isBlank()) {
			String prefix = FarmingToolFortune.pretty(reforge) + " ";
			if (name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
				name = name.substring(prefix.length());
			}
		}
		return name.replace(" Mk. ", " ").trim();
	}
}
