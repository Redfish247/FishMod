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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Chocolate Factory constants from {@code chocolate_factory.json} (prestige thresholds, Hitman slot costs and rabbit
 * factions, sourced from the meowdding repo with rabbit ids checked against NEU {@code hoppity.json}).
 */
public final class ChocolateFactoryData {
	/** {@code threshold} is chocolate earned this prestige needed for {@code level + 1}; 0 when maxed. */
	public record Prestige(int level, int maxLevel, long threshold, long earned) {
		public boolean maxed() {
			return level >= maxLevel || threshold <= 0L;
		}

		public long needed() {
			return maxed() ? 0L : Math.max(0L, threshold - earned);
		}

		public boolean ready() {
			return !maxed() && earned >= threshold;
		}
	}

	/** Coin costs are per slot, so {@code paid} is the sum of the first {@code slots} entries. */
	public record Hitmen(int slots, int maxSlots, long paid, long total, long nextSlotCost) {
		public double fraction() {
			return total <= 0L ? 0D : Math.min(1D, paid / (double) total);
		}

		public boolean maxed() {
			return maxSlots > 0 && slots >= maxSlots;
		}
	}

	public record Faction(String id, String name, Map<String, List<String>> rabbitsByRarity) {
		public int size() {
			int n = 0;
			for (List<String> ids : rabbitsByRarity.values()) {
				n += ids.size();
			}
			return n;
		}

		public int found(Collection<String> ownedRabbits) {
			int n = 0;
			for (List<String> ids : rabbitsByRarity.values()) {
				for (String id : ids) {
					if (ownedRabbits.contains(id)) {
						n++;
					}
				}
			}
			return n;
		}
	}

	// SkyBlock year 1 began 2019-06-11 17:55 UTC; one year is 124 real hours.
	private static final long SKYBLOCK_EPOCH_MS = 1_560_275_700_000L;
	private static final long SKYBLOCK_YEAR_MS = 124L * 60L * 60L * 1000L;

	private static volatile boolean loaded;
	private static TreeMap<Integer, Long> prestigeChocolate = new TreeMap<>();
	private static long[] hitmanSlotCost = new long[0];
	private static List<Faction> factions = List.of();

	private ChocolateFactoryData() {
	}

	public static Prestige prestige(int level, long earnedThisPrestige) {
		ensureLoaded();
		int lvl = Math.max(1, level);
		int max = prestigeChocolate.isEmpty() ? lvl : Math.max(1, prestigeChocolate.lastKey());
		Long next = prestigeChocolate.get(lvl + 1);
		return new Prestige(lvl, max, next == null ? 0L : next, Math.max(0L, earnedThisPrestige));
	}

	public static Hitmen hitmen(int slots) {
		ensureLoaded();
		int max = hitmanSlotCost.length;
		int owned = Math.max(0, Math.min(slots, max));
		long paid = 0L;
		long total = 0L;
		for (int i = 0; i < max; i++) {
			total += hitmanSlotCost[i];
			if (i < owned) {
				paid += hitmanSlotCost[i];
			}
		}
		long next = owned < max ? hitmanSlotCost[owned] : 0L;
		return new Hitmen(Math.max(0, slots), max, paid, total, next);
	}

	public static List<Faction> factions() {
		ensureLoaded();
		return factions;
	}

	public static Faction faction(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		String key = id.toLowerCase(Locale.ROOT);
		for (Faction faction : factions()) {
			if (faction.id().equals(key)) {
				return faction;
			}
		}
		return null;
	}

	public static Faction factionOf(String rabbitId) {
		if (rabbitId == null || rabbitId.isBlank()) {
			return null;
		}
		String key = rabbitId.toLowerCase(Locale.ROOT);
		for (Faction faction : factions()) {
			for (List<String> ids : faction.rabbitsByRarity().values()) {
				if (ids.contains(key)) {
					return faction;
				}
			}
		}
		return null;
	}

	/** Employee level tier colours (white, green, blue, purple, gold, pink, aqua), matching Skyblock-PV. */
	public static int employeeLevelColor(int level) {
		if (level < 10) {
			return 0xFFFFFFFF;
		}
		if (level < 75) {
			return 0xFF55FF55;
		}
		if (level < 125) {
			return 0xFF5555FF;
		}
		if (level < 175) {
			return 0xFFAA00AA;
		}
		if (level < 200) {
			return 0xFFFFAA00;
		}
		if (level < 220) {
			return 0xFFFF55FF;
		}
		return 0xFF55FFFF;
	}

	public static int currentSkyBlockYear(long nowMs) {
		return (int) ((nowMs - SKYBLOCK_EPOCH_MS) / SKYBLOCK_YEAR_MS) + 1;
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (ChocolateFactoryData.class) {
			if (loaded) {
				return;
			}
			try (InputStream in = ChocolateFactoryData.class.getClassLoader()
				.getResourceAsStream("assets/betterpv/data/chocolate_factory.json")) {
				if (in == null) {
					BetterPV.LOGGER.warn("Missing chocolate_factory.json");
				} else {
					parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
				}
			} catch (IOException | RuntimeException exception) {
				if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
					throw runtime;
				}
				BetterPV.LOGGER.warn("Failed to load chocolate_factory.json", exception);
			}
			loaded = true;
		}
	}

	private static void parse(JsonObject root) {
		TreeMap<Integer, Long> prestige = new TreeMap<>();
		JsonObject prestigeObj = obj(root.get("prestige_chocolate"));
		if (prestigeObj != null) {
			for (Map.Entry<String, JsonElement> e : prestigeObj.entrySet()) {
				try {
					prestige.put(Integer.parseInt(e.getKey()), Math.max(0L, e.getValue().getAsLong()));
				} catch (RuntimeException ignored) {
				}
			}
		}
		prestigeChocolate = prestige;

		JsonElement costs = root.get("hitman_slot_cost");
		if (costs != null && costs.isJsonArray()) {
			JsonArray array = costs.getAsJsonArray();
			long[] out = new long[array.size()];
			for (int i = 0; i < array.size(); i++) {
				try {
					out[i] = Math.max(0L, array.get(i).getAsLong());
				} catch (RuntimeException ignored) {
					out[i] = 0L;
				}
			}
			hitmanSlotCost = out;
		}

		List<Faction> list = new ArrayList<>();
		JsonObject factionObj = obj(root.get("factions"));
		if (factionObj != null) {
			for (Map.Entry<String, JsonElement> e : factionObj.entrySet()) {
				JsonObject rarities = obj(e.getValue());
				if (rarities == null) {
					continue;
				}
				Map<String, List<String>> byRarity = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> r : rarities.entrySet()) {
					List<String> ids = new ArrayList<>();
					if (r.getValue().isJsonArray()) {
						for (JsonElement el : r.getValue().getAsJsonArray()) {
							if (el.isJsonPrimitive()) {
								ids.add(el.getAsString().toLowerCase(Locale.ROOT));
							}
						}
					} else if (r.getValue().isJsonPrimitive()) {
						ids.add(r.getValue().getAsString().toLowerCase(Locale.ROOT));
					}
					byRarity.put(r.getKey().toUpperCase(Locale.ROOT), List.copyOf(ids));
				}
				String id = e.getKey().toLowerCase(Locale.ROOT);
				if (id.isEmpty()) {
					continue;
				}
				String name = Character.toUpperCase(id.charAt(0)) + id.substring(1);
				list.add(new Faction(id, name, Collections.unmodifiableMap(byRarity)));
			}
		}
		factions = List.copyOf(list);
	}

	private static JsonObject obj(JsonElement el) {
		return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
	}
}
