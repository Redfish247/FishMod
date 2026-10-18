/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import fishmod.pv.price.ItemPricer;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Garden upgrade cost tables from {@code garden_costs.json} (NEU {@code constants/garden.json} plot, crop upgrade and
 * composter tables, plus the per-level chip Sowdust costs from the wiki). All costs are per level, never cumulative.
 */
public final class GardenCosts {
	public record Cost(String itemId, long amount) {
	}

	/** {@code x}/{@code y} are the 5x5 map column/row, row 0 at the top. */
	public record Plot(String id, String tier, int number, int x, int y) {
	}

	public record Progress(long spent, long total) {
		public double fraction() {
			return total <= 0L ? 0D : Math.min(1D, spent / (double) total);
		}

		public boolean maxed() {
			return total > 0L && spent >= total;
		}
	}

	/** Resources still needed to max one or more composter upgrades. */
	public record ComposterRemaining(long copper, Map<String, Long> crops, Map<String, Long> rare) {
		public boolean none() {
			return copper <= 0L && crops.isEmpty() && rare.isEmpty();
		}

		public boolean needsItems() {
			return !crops.isEmpty() || !rare.isEmpty();
		}

		/** Bazaar coin value of the remaining crops; copper has no coin price. */
		public double coins() {
			return coins(ItemPricer::materialPrice);
		}

		public double coins(ToDoubleFunction<String> price) {
			double total = 0D;
			for (var e : crops.entrySet()) {
				total += Math.max(0D, price.applyAsDouble(e.getKey())) * Math.max(0L, e.getValue());
			}
			for (var e : rare.entrySet()) {
				total += Math.max(0D, price.applyAsDouble(e.getKey())) * Math.max(0L, e.getValue());
			}
			return total;
		}

		public boolean fullyPriced() {
			return unpriced(ItemPricer::materialPrice) == 0;
		}

		public int unpriced(ToDoubleFunction<String> price) {
			int missing = 0;
			for (String id : crops.keySet()) {
				if (price.applyAsDouble(id) <= 0D) {
					missing++;
				}
			}
			for (String id : rare.keySet()) {
				if (price.applyAsDouble(id) <= 0D) {
					missing++;
				}
			}
			return missing;
		}
	}

	public static double itemCoins(String itemId, long amount) {
		return ItemPricer.materialPrice(itemId) * Math.max(0L, amount);
	}

	private static volatile boolean loaded;
	private static long[] chipSowdust = new long[0];
	private static long[] cropUpgradeCopper = new long[0];
	private static Set<String> rareItems = Set.of();
	private static List<Plot> plots = List.of();
	private static Map<String, List<Cost>> plotCosts = Map.of();
	private static Map<String, List<JsonObject>> composterLevels = Map.of();

	private GardenCosts() {
	}

	/** Sowdust spent getting a chip to {@code level} (level 1 is free) vs. the level 20 total. */
	public static Progress chipSowdust(int level) {
		ensureLoaded();
		return new Progress(sum(chipSowdust, Math.max(0, level - 1)), sum(chipSowdust, chipSowdust.length));
	}

	public static int cropUpgradeMax() {
		ensureLoaded();
		return cropUpgradeCopper.length;
	}

	public static Progress cropUpgradeCopper(int level) {
		ensureLoaded();
		return new Progress(sum(cropUpgradeCopper, Math.max(0, level)), sum(cropUpgradeCopper, cropUpgradeCopper.length));
	}

	public static List<Plot> plots() {
		ensureLoaded();
		return plots;
	}

	/**
	 * Price of the next plot bought in {@code tier}: Hypixel prices by how many plots of that tier are
	 * already unlocked, not by which specific plot. Null when the tier is fully unlocked or unknown.
	 */
	public static Cost nextPlotCost(String tier, int unlockedInTier) {
		ensureLoaded();
		List<Cost> costs = plotCosts.get(tier == null ? "" : tier.toLowerCase(Locale.ROOT));
		if (costs == null || unlockedInTier < 0 || unlockedInTier >= costs.size()) {
			return null;
		}
		return costs.get(unlockedInTier);
	}

	public static boolean hasComposterCosts(String upgradeId) {
		ensureLoaded();
		return upgradeId != null && composterLevels.containsKey(upgradeId.toLowerCase(Locale.ROOT));
	}

	public static ComposterRemaining composterRemaining(String upgradeId, int level) {
		return composterRemaining(Map.of(upgradeId == null ? "" : upgradeId, level));
	}

	/** Sums every unpurchased level above the current one; ids without cost data are ignored. */
	public static ComposterRemaining composterRemaining(Map<String, Integer> levels) {
		ensureLoaded();
		long copper = 0L;
		Map<String, Long> crops = new LinkedHashMap<>();
		Map<String, Long> rare = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : levels.entrySet()) {
			List<JsonObject> table = composterLevels.get(entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT));
			if (table == null) {
				continue;
			}
			int owned = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
			for (int i = owned; i < table.size(); i++) {
				JsonObject level = table.get(i);
				copper += longOf(level, "copper");
				JsonObject items = level.has("items") && level.get("items").isJsonObject() ? level.getAsJsonObject("items") : null;
				if (items == null) {
					continue;
				}
				for (Map.Entry<String, JsonElement> item : items.entrySet()) {
					long amount = longOf(items, item.getKey());
					if (amount <= 0L) {
						continue;
					}
					String id = item.getKey().toUpperCase(Locale.ROOT);
					(rareItems.contains(id) ? rare : crops).merge(id, amount, Long::sum);
				}
			}
		}
		return new ComposterRemaining(copper, sortedByAmount(crops), sortedByAmount(rare));
	}

	private static Map<String, Long> sortedByAmount(Map<String, Long> in) {
		List<Map.Entry<String, Long>> entries = new ArrayList<>(in.entrySet());
		entries.sort(Map.Entry.<String, Long>comparingByValue().reversed());
		Map<String, Long> out = new LinkedHashMap<>();
		for (Map.Entry<String, Long> e : entries) {
			out.put(e.getKey(), e.getValue());
		}
		return out;
	}

	private static long sum(long[] costs, int count) {
		long total = 0L;
		for (int i = 0; i < Math.min(count, costs.length); i++) {
			total += costs[i];
		}
		return total;
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (GardenCosts.class) {
			if (loaded) {
				return;
			}
			try (InputStream in = GardenCosts.class.getClassLoader().getResourceAsStream("assets/betterpv/data/garden_costs.json")) {
				if (in == null) {
					BetterPV.LOGGER.warn("Missing garden_costs.json");
				} else {
					parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
				}
			} catch (IOException | RuntimeException exception) {
				if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
					throw runtime;
				}
				BetterPV.LOGGER.warn("Failed to load garden_costs.json", exception);
			}
			loaded = true;
		}
	}

	private static void parse(JsonObject root) {
		chipSowdust = longArray(root.get("chip_sowdust"));
		cropUpgradeCopper = longArray(root.get("crop_upgrade_copper"));

		List<String> rare = new ArrayList<>();
		if (root.has("composter_rare_items") && root.get("composter_rare_items").isJsonArray()) {
			for (JsonElement el : root.getAsJsonArray("composter_rare_items")) {
				if (el.isJsonPrimitive()) {
					rare.add(el.getAsString().toUpperCase(Locale.ROOT));
				}
			}
		}
		rareItems = Set.copyOf(rare);

		List<Plot> plotList = new ArrayList<>();
		JsonObject plotObj = obj(root.get("plots"));
		if (plotObj != null) {
			for (Map.Entry<String, JsonElement> e : plotObj.entrySet()) {
				JsonObject p = obj(e.getValue());
				if (p == null) {
					continue;
				}
				String id = e.getKey().toLowerCase(Locale.ROOT);
				int cut = id.lastIndexOf('_');
				String tier = cut > 0 ? id.substring(0, cut) : id;
				plotList.add(new Plot(id, tier, (int) longOf(p, "number"), (int) longOf(p, "x"), (int) longOf(p, "y")));
			}
		}
		plotList.sort(Comparator.comparingInt(Plot::number));
		plots = List.copyOf(plotList);

		Map<String, List<Cost>> costs = new LinkedHashMap<>();
		JsonObject costObj = obj(root.get("plot_costs"));
		if (costObj != null) {
			for (Map.Entry<String, JsonElement> e : costObj.entrySet()) {
				if (!e.getValue().isJsonArray()) {
					continue;
				}
				List<Cost> tier = new ArrayList<>();
				for (JsonElement el : e.getValue().getAsJsonArray()) {
					JsonObject c = obj(el);
					if (c != null && c.has("item")) {
						tier.add(new Cost(c.get("item").getAsString(), longOf(c, "amount")));
					}
				}
				costs.put(e.getKey().toLowerCase(Locale.ROOT), List.copyOf(tier));
			}
		}
		plotCosts = Map.copyOf(costs);

		Map<String, List<JsonObject>> composter = new LinkedHashMap<>();
		JsonObject compObj = obj(root.get("composter_upgrades"));
		if (compObj != null) {
			for (Map.Entry<String, JsonElement> e : compObj.entrySet()) {
				if (!e.getValue().isJsonArray()) {
					continue;
				}
				List<JsonObject> levels = new ArrayList<>();
				for (JsonElement el : e.getValue().getAsJsonArray()) {
					JsonObject level = obj(el);
					levels.add(level == null ? new JsonObject() : level);
				}
				composter.put(e.getKey().toLowerCase(Locale.ROOT), List.copyOf(levels));
			}
		}
		composterLevels = Map.copyOf(composter);
	}

	private static JsonObject obj(JsonElement el) {
		return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
	}

	private static long[] longArray(JsonElement el) {
		if (el == null || !el.isJsonArray()) {
			return new long[0];
		}
		JsonArray array = el.getAsJsonArray();
		long[] out = new long[array.size()];
		for (int i = 0; i < array.size(); i++) {
			try {
				out[i] = Math.max(0L, array.get(i).getAsLong());
			} catch (RuntimeException ignored) {
				out[i] = 0L;
			}
		}
		return out;
	}

	private static long longOf(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return 0L;
		}
		try {
			return Math.max(0L, obj.get(key).getAsLong());
		} catch (RuntimeException ignored) {
			return 0L;
		}
	}
}
