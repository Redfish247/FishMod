/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Per-tier essence costs from NEU {@code constants/essenceshops.json}. */
public final class EssenceShopCosts {
	// NEU names these perks differently from the Hypixel API key.
	private static final Map<String, String> NEU_IDS = Map.of("eagles_advantage", "eagle_advantage");
	private static volatile Map<String, int[]> costs;

	private EssenceShopCosts() {
	}

	/** Cost of each tier (index 0 = tier 1), or null when the repo doesn't list the perk. */
	public static int[] costs(String perkId) {
		if (perkId == null) {
			return null;
		}
		Map<String, int[]> map = costs;
		if (map == null) {
			map = load();
		}
		return lookup(map, perkId);
	}

	static int[] lookup(Map<String, int[]> map, String perkId) {
		String key = perkId.toLowerCase(Locale.ROOT);
		int[] tiers = map.get(key);
		return tiers != null ? tiers : map.get(NEU_IDS.getOrDefault(key, key));
	}

	public static long nextTier(int[] costs, int level) {
		return costs == null || level < 0 || level >= costs.length ? 0L : costs[level];
	}

	public static long toMax(int[] costs, int level) {
		if (costs == null) {
			return 0L;
		}
		long sum = 0L;
		for (int i = Math.max(0, level); i < costs.length; i++) {
			sum += costs[i];
		}
		return sum;
	}

	private static synchronized Map<String, int[]> load() {
		if (costs != null) {
			return costs;
		}
		Path path = Path.of(System.getProperty("user.home"),
			".betterpv", "neu-repo", "repo", "constants", "essenceshops.json");
		if (!Files.isRegularFile(path)) {
			// Repo may still be downloading; try again on the next hover.
			return Map.of();
		}
		Map<String, int[]> out = new HashMap<>();
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			out.putAll(parse(JsonParser.parseReader(reader).getAsJsonObject()));
		} catch (Exception ex) {
			BetterPV.LOGGER.warn("Failed to load essence shop costs", ex);
		}
		costs = Map.copyOf(out);
		return costs;
	}

	static Map<String, int[]> parse(JsonObject root) {
		Map<String, int[]> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> shop : root.entrySet()) {
			if (!shop.getValue().isJsonObject()) {
				continue;
			}
			for (Map.Entry<String, JsonElement> perk : shop.getValue().getAsJsonObject().entrySet()) {
				if (!perk.getValue().isJsonObject()) {
					continue;
				}
				JsonElement arr = perk.getValue().getAsJsonObject().get("costs");
				if (arr == null || !arr.isJsonArray()) {
					continue;
				}
				int[] tiers = new int[arr.getAsJsonArray().size()];
				for (int i = 0; i < tiers.length; i++) {
					JsonElement t = arr.getAsJsonArray().get(i); // bad tier = 0, keep the rest
					tiers[i] = t.isJsonPrimitive() && t.getAsJsonPrimitive().isNumber() ? t.getAsInt() : 0;
				}
				out.put(perk.getKey().toLowerCase(Locale.ROOT), tiers);
			}
		}
		return out;
	}
}
