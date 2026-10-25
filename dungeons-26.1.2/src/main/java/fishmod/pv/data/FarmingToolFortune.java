/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import fishmod.pv.data.FarmingFortuneEstimate.Line;
import fishmod.pv.networth.NbtAttrs;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Farming tool fortune worked out from ExtraAttributes. Toolkit tools have no lore, so this is the only way to value
 * them, and held tools use it too so both compare the same way. Values follow EliteFarmers' FarmingWeight tables.
 */
final class FarmingToolFortune {
	enum Type { HOE, AXE }

	record Info(List<String> crops, Type type, int tier) {
	}

	static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
	private static final Map<String, double[]> REFORGES = Map.of(
		"blessed", new double[] {5, 7, 9, 13, 16, 20},
		"bountiful", new double[] {1, 2, 3, 5, 7, 10},
		"robust", new double[] {2, 3, 4, 6, 8, 10},
		"green_thumb", new double[] {1, 2, 3, 4, 5, 6},
		"earthy", new double[] {2, 4, 6, 8, 10, 12}
	);
	private static final Map<String, Type> REFORGE_TARGET = Map.of(
		"robust", Type.HOE, "green_thumb", Type.HOE, "earthy", Type.AXE
	);
	private static final Map<String, double[]> PERIDOT = Map.of(
		"ROUGH", new double[] {0.5, 1, 1.5, 2, 2.5, 3},
		"FLAWED", new double[] {1, 1.5, 2, 2.5, 3, 4},
		"FINE", new double[] {1.5, 2, 3, 4, 5, 6},
		"FLAWLESS", new double[] {2, 3, 4, 5, 6, 8},
		"PERFECT", new double[] {3, 4, 5, 6, 8, 10}
	);
	private static final double[] DEDICATION = {0.5, 0.75, 1, 2};
	private static final Pattern TIER_SUFFIX = Pattern.compile("_([0-9]+)$");
	private static final Map<String, String> TURBO = Map.ofEntries(
		Map.entry("Wheat", "turbo_wheat"), Map.entry("Carrot", "turbo_carrot"), Map.entry("Potato", "turbo_potato"),
		Map.entry("Pumpkin", "turbo_pumpkin"), Map.entry("Melon", "turbo_melon"), Map.entry("Cactus", "turbo_cactus"),
		Map.entry("Sugar Cane", "turbo_cane"), Map.entry("Cocoa Beans", "turbo_coco"),
		Map.entry("Mushroom", "turbo_mushrooms"), Map.entry("Nether Wart", "turbo_warts"),
		Map.entry("Sunflower", "turbo_sunflower"), Map.entry("Moonflower", "turbo_moonflower"),
		Map.entry("Wild Rose", "turbo_rose")
	);

	private FarmingToolFortune() {
	}

	/** Null when the id isn't a crop-specific farming tool. */
	static Info info(String id) {
		if (id == null) {
			return null;
		}
		String upper = id.toUpperCase(Locale.ROOT);
		if (upper.startsWith("THEORETICAL_HOE_")) {
			String rest = upper.substring("THEORETICAL_HOE_".length());
			int tier = tierSuffix(rest);
			List<String> crops = switch (rest.replaceAll("_[0-9]+$", "")) {
				case "WHEAT" -> List.of("Wheat");
				case "CARROT" -> List.of("Carrot");
				case "POTATO" -> List.of("Potato");
				case "CANE" -> List.of("Sugar Cane");
				case "WARTS" -> List.of("Nether Wart");
				case "SUNFLOWER" -> List.of("Sunflower", "Moonflower");
				case "WILD_ROSE" -> List.of("Wild Rose");
				default -> List.of();
			};
			return crops.isEmpty() ? null : new Info(crops, Type.HOE, tier);
		}
		String base = upper.replaceAll("_[0-9]+$", "");
		int tier = tierSuffix(upper);
		return switch (base) {
			case "MELON_DICER" -> new Info(List.of("Melon"), Type.AXE, tier);
			case "PUMPKIN_DICER" -> new Info(List.of("Pumpkin"), Type.AXE, tier);
			case "COCO_CHOPPER" -> new Info(List.of("Cocoa Beans"), Type.AXE, tier);
			case "CACTUS_KNIFE" -> new Info(List.of("Cactus"), Type.HOE, tier);
			case "FUNGI_CUTTER" -> new Info(List.of("Mushroom"), Type.HOE, tier);
			default -> null;
		};
	}

	private static int tierSuffix(String id) {
		Matcher m = TIER_SUFFIX.matcher(id);
		return m.find() ? Math.max(1, Math.min(3, Integer.parseInt(m.group(1)))) : 1;
	}

	/** Mk. I tools start Uncommon, Mk. II Rare, Mk. III Epic; a recombobulator adds one. */
	static String rarity(Info info, CompoundTag attrs, List<String> lore) {
		String fromLore = rarityFromLore(lore);
		if (fromLore != null) {
			return fromLore;
		}
		int base = info.tier();
		int upgrades = Math.max(0, NbtAttrs.intValue(attrs, "rarity_upgrades", 0));
		return RARITIES.get(Math.min(RARITIES.size() - 1, base + upgrades));
	}

	static String rarityFromLore(List<String> lore) {
		if (lore == null) {
			return null;
		}
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = PlayerStatsCalculator.stripFormatting(lore.get(i)).trim();
			if (line.isEmpty()) {
				continue;
			}
			for (String word : line.toUpperCase(Locale.ROOT).split("\\s+")) {
				if (RARITIES.contains(word)) {
					return word;
				}
			}
			return null;
		}
		return null;
	}

	/**
	 * Breakdown for one crop. {@code milestones} is null until the garden loads, which leaves Dedication unknown
	 * instead of zero.
	 */
	static List<Line> parts(
		Info info, CompoundTag attrs, String rarity, String crop, Map<String, Integer> milestones, boolean axedPerk
	) {
		List<Line> out = new ArrayList<>();
		int r = Math.max(0, RARITIES.indexOf(rarity));
		int level = NbtAttrs.intValue(attrs, "levelable_lvl", 1);
		out.add(Line.counted("Tool level", 4D * Math.max(1, level), "Level " + Math.max(1, level)));

		String reforge = NbtAttrs.string(attrs, "modifier");
		if (reforge != null) {
			String key = reforge.toLowerCase(Locale.ROOT);
			double[] table = REFORGES.get(key);
			Type target = REFORGE_TARGET.get(key);
			if (table != null && (target == null || target == info.type())) {
				out.add(Line.counted(pretty(key) + " reforge", table[r], titleCase(rarity)));
			}
		}
		int ffd = Math.max(0, Math.min(5, NbtAttrs.intValue(attrs, "farming_for_dummies_count", 0)));
		if (ffd > 0) {
			out.add(Line.counted("Farming for Dummies", ffd, ffd + " / 5"));
		}
		int[] gemCount = new int[1];
		double gems = peridot(attrs, r, gemCount);
		if (gems > 0D) {
			out.add(Line.counted("Peridot gems", gems, gemCount[0] == 1 ? "1 gem" : gemCount[0] + " gems"));
		}

		Map<String, Integer> ench = NbtAttrs.intMap(attrs, "enchantments");
		int harvesting = ench.getOrDefault("harvesting", 0);
		if (info.type() == Type.HOE && harvesting > 0) {
			out.add(Line.counted("Harvesting " + roman(harvesting), 12.5D * Math.min(6, harvesting), ""));
		}
		int sunder = ench.getOrDefault("sunder", 0);
		if (info.type() == Type.AXE && sunder > 0) {
			out.add(Line.counted("Sunder " + roman(sunder), 12.5D * Math.min(6, sunder), ""));
		}
		int cultivating = ench.getOrDefault("cultivating", 0);
		if (cultivating > 0) {
			out.add(Line.counted("Cultivating " + roman(cultivating), 2D * Math.min(10, cultivating), ""));
		}
		int dedication = ench.getOrDefault("dedication", 0);
		if (dedication > 0) {
			String label = "Dedication " + roman(dedication);
			if (milestones == null) {
				out.add(Line.unavailable(label, "Needs crop milestone, garden not loaded"));
			} else {
				int milestone = milestones.getOrDefault(crop, 0);
				double per = DEDICATION[Math.min(DEDICATION.length, dedication) - 1];
				out.add(Line.counted(label, per * milestone, crop + " milestone " + milestone));
			}
		}
		String turboKey = TURBO.get(crop);
		int turbo = turboKey == null ? 0 : ench.getOrDefault(turboKey, 0);
		if (turbo > 0) {
			out.add(Line.counted(turboName(crop) + " " + roman(turbo), 5D * turbo, ""));
		}
		if (info.type() == Type.AXE && axedPerk) {
			double before = 0D;
			for (Line line : out) {
				if (line.kind() != FarmingFortuneEstimate.Kind.UNAVAILABLE) {
					before += line.value();
				}
			}
			out.add(Line.counted("Axed perk", before * 0.02D, "+2% on axes"));
		}
		return out;
	}

	static double peridot(CompoundTag attrs, int rarity, int[] count) {
		CompoundTag gems = NbtAttrs.compound(attrs, "gems");
		if (gems == null) {
			return 0D;
		}
		double total = 0D;
		for (String key : gems.keySet()) {
			if (!key.startsWith("PERIDOT_") || key.endsWith("_gem")) {
				continue;
			}
			Tag value = gems.get(key);
			String quality = value instanceof CompoundTag c ? NbtAttrs.string(c, "quality") : NbtAttrs.string(gems, key);
			double[] table = quality == null ? null : PERIDOT.get(quality.toUpperCase(Locale.ROOT));
			if (table != null) {
				total += table[rarity];
				if (count != null) {
					count[0]++;
				}
			}
		}
		return total;
	}

	private static String turboName(String crop) {
		return switch (crop) {
			case "Cactus" -> "Turbo-Cacti";
			case "Sugar Cane" -> "Turbo-Cane";
			case "Cocoa Beans" -> "Turbo-Cocoa";
			case "Mushroom" -> "Turbo-Mushrooms";
			case "Nether Wart" -> "Turbo-Warts";
			case "Wild Rose" -> "Turbo-Rose";
			default -> "Turbo-" + crop;
		};
	}

	static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> String.valueOf(n);
		};
	}

	static String pretty(String key) {
		StringBuilder out = new StringBuilder();
		for (String part : key.split("_")) {
			if (!part.isEmpty()) {
				if (out.length() > 0) {
					out.append(' ');
				}
				out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return out.toString();
	}

	private static String titleCase(String rarity) {
		return rarity == null || rarity.isEmpty() ? "" : pretty(rarity);
	}
}
