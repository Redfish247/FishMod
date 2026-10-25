/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ColeWeight (ninjune / Vinxey thresholds): each listed amount equals one weight unit.
 * Categories: experience, powder, collection, miscellaneous.
 */
public final class ColeWeight {
	public record Line(String category, String label, double weight, double amount, double perUnit) {
	}

	public record Result(double total, List<Line> lines) {
		public static Result empty() {
			return new Result(0, List.of());
		}

		public Map<String, List<Line>> byCategory() {
			Map<String, List<Line>> out = new LinkedHashMap<>();
			for (Line line : lines) {
				out.computeIfAbsent(line.category(), k -> new ArrayList<>()).add(line);
			}
			return out;
		}
	}

	private record Threshold(String category, String label, double cost, String collectionId) {
	}

	private static final List<Threshold> COLLECTIONS = List.of(
		new Threshold("collection", "Mithril", 350_000, "MITHRIL_ORE"),
		new Threshold("collection", "Gemstone", 1_450_000, "GEMSTONE_COLLECTION"),
		new Threshold("collection", "Gold Ingot", 1_500_000, "GOLD_INGOT"),
		new Threshold("collection", "Netherrack", 1_500_000, "NETHERRACK"),
		new Threshold("collection", "Diamond", 460_000, "DIAMOND"),
		new Threshold("collection", "Ice", 1_000_000, "ICE"),
		new Threshold("collection", "Redstone", 1_000_000, "REDSTONE"),
		new Threshold("collection", "Lapis", 1_000_000, "INK_SACK:4"),
		new Threshold("collection", "Sulphur", 9_999_999_999d, "SULPHUR"),
		new Threshold("collection", "Coal", 650_000, "COAL"),
		new Threshold("collection", "Emerald", 600_000, "EMERALD"),
		new Threshold("collection", "End Stone", 2_000_000, "ENDER_STONE"),
		new Threshold("collection", "Glowstone Dust", 400_000, "GLOWSTONE_DUST"),
		new Threshold("collection", "Gravel", 450_000, "GRAVEL"),
		new Threshold("collection", "Iron Ingot", 500_000, "IRON_INGOT"),
		new Threshold("collection", "Mycelium", 600_000, "MYCEL"),
		new Threshold("collection", "Quartz", 550_000, "QUARTZ"),
		new Threshold("collection", "Obsidian", 400_000, "OBSIDIAN"),
		new Threshold("collection", "Red Sand", 600_000, "SAND:1"),
		new Threshold("collection", "Sand", 800_000, "SAND"),
		new Threshold("collection", "Cobblestone", 2_000_000, "COBBLESTONE"),
		new Threshold("collection", "Hard Stone", 2_000_000, "HARD_STONE"),
		new Threshold("collection", "Living Metal Heart", 40, "METAL_HEART"),
		new Threshold("collection", "Glacite", 80_000, "GLACITE"),
		new Threshold("collection", "Tungsten", 53_000, "TUNGSTEN"),
		new Threshold("collection", "Umber", 48_000, "UMBER")
	);

	private ColeWeight() {
	}

	public static Result calculate(MiningSnapshot mining, CollectionSnapshot collections, JsonObject member) {
		if (mining == null) {
			mining = MiningSnapshot.empty();
		}
		List<Line> lines = new ArrayList<>();

		double miningXp = Leveling.readSkillXp(member, "mining");
		add(lines, "experience", "Mining Experience", miningXp, 2_500_000);

		add(lines, "powder", "Mithril Powder", mining.mithril().total(), 400_000);
		add(lines, "powder", "Gemstone Powder", mining.gemstone().total(), 400_000);
		add(lines, "powder", "Glacite Powder", mining.glacite().total(), 400_000);

		for (Threshold t : COLLECTIONS) {
			long amount = collections == null ? 0L : collections.viewedAmount(t.collectionId());
			add(lines, t.category(), t.label(), amount, t.cost());
		}

		double scatha = killCount(member, "scatha", "scatha_10");
		double worm = killCount(member, "worm", "worm_5");
		add(lines, "miscellaneous", "Scatha Kills", scatha, 8);
		add(lines, "miscellaneous", "Worm Kills", worm, 12);
		add(lines, "miscellaneous", "Nucleus Runs", mining.nucleusRuns(), 2.5);

		double total = 0;
		for (Line line : lines) {
			total += line.weight();
		}
		return new Result(total, List.copyOf(lines));
	}

	private static void add(List<Line> lines, String category, String label, double amount, double cost) {
		if (cost <= 0) {
			return;
		}
		lines.add(new Line(category, label, amount / cost, amount, cost));
	}

	private static double killCount(JsonObject member, String killKey, String bestiaryKey) {
		if (member == null) {
			return 0;
		}
		JsonObject bestiary = Leveling.obj(member.get("bestiary"));
		JsonObject kills = bestiary == null ? null : Leveling.obj(bestiary.get("kills"));
		double fromBestiary = numAt(kills, bestiaryKey);
		if (fromBestiary <= 0) {
			fromBestiary = numAt(kills, killKey);
		}
		if (fromBestiary > 0) {
			return fromBestiary;
		}

		JsonObject playerStats = Leveling.obj(member.get("player_stats"));
		JsonObject psKills = playerStats == null ? null : Leveling.obj(playerStats.get("kills"));
		double fromStats = numAt(psKills, killKey);
		if (fromStats <= 0) {
			fromStats = sumMatching(psKills, killKey);
		}
		return Math.max(0, fromStats);
	}

	private static double numAt(JsonObject obj, String key) {
		if (obj == null || key == null) {
			return 0;
		}
		Float n = Leveling.num(obj.get(key));
		return n == null ? 0 : n.doubleValue();
	}

	private static double sumMatching(JsonObject obj, String needle) {
		if (obj == null || needle == null || needle.isBlank()) {
			return 0;
		}
		String lower = needle.toLowerCase(Locale.ROOT);
		double sum = 0;
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			String key = e.getKey();
			if (key != null && key.toLowerCase(Locale.ROOT).contains(lower)) {
				Float n = Leveling.num(e.getValue());
				if (n != null) {
					sum += n.doubleValue();
				}
			}
		}
		return sum;
	}
}
