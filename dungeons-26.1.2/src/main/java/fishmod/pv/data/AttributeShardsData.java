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
import java.io.Reader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** NEU {@code attribute_shards.json} catalog + level costs. */
public final class AttributeShardsData {
	public record Def(
		String stackId, String displayName, String abilityName, String rarity, String internalName, String bazaarName
	) {
		public String iconId() {
			return internalName == null || internalName.isBlank()
				? "ATTRIBUTE_SHARD_" + stackId.toUpperCase(Locale.ROOT) + ";1"
				: internalName;
		}

		/** Prefer bazaar product id (SHARD_*), then stripped internal name. */
		public String priceId() {
			if (bazaarName != null && !bazaarName.isBlank()) {
				return bazaarName;
			}
			String icon = iconId();
			int semi = icon.indexOf(';');
			return semi >= 0 ? icon.substring(0, semi) : icon;
		}
	}

	private static volatile boolean loaded;
	private static volatile List<Def> defs = List.of();
	private static volatile Map<String, Def> byStackId = Map.of();
	private static volatile Map<String, int[]> costsByRarity = Map.of();

	private AttributeShardsData() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (AttributeShardsData.class) {
			if (loaded) {
				return;
			}
			loadFromDisk();
			loaded = true;
		}
	}

	/** Reload after NEU-REPO updates. */
	public static void reload() {
		synchronized (AttributeShardsData.class) {
			loaded = false;
			loadFromDisk();
			loaded = true;
		}
	}

	public static List<Def> all() {
		ensureLoaded();
		return defs;
	}

	public static Def def(String stackId) {
		ensureLoaded();
		if (stackId == null || stackId.isBlank()) {
			return null;
		}
		return byStackId.get(stackId.toLowerCase(Locale.ROOT));
	}

	/** Match by display name or {@code SHARD_<NAME>} bazaar id (safari critters). */
	public static Def byCritterId(String critterId) {
		ensureLoaded();
		if (critterId == null || critterId.isBlank()) {
			return null;
		}
		String key = critterId.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		String bazaar = "shard_" + key;
		String pretty = key.replace('_', ' ');
		for (Def def : defs) {
			if (def.bazaarName() != null && def.bazaarName().equalsIgnoreCase(bazaar)) {
				return def;
			}
			if (def.displayName() != null && def.displayName().equalsIgnoreCase(pretty)) {
				return def;
			}
			if (def.displayName() != null
				&& def.displayName().replace(" ", "").equalsIgnoreCase(key.replace("_", ""))) {
				return def;
			}
		}
		return null;
	}

	private static final List<String> RARITY_ORDER = List.of(
		"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE"
	);

	/** Higher is rarer; unknown rarities sort below COMMON. */
	public static int rarityRank(String rarity) {
		return rarity == null ? -1 : RARITY_ORDER.indexOf(rarity.toUpperCase(Locale.ROOT));
	}

	public static int maxLevel(String rarity) {
		int[] costs = costs(rarity);
		return costs.length;
	}

	public static int shardsForMax(String rarity) {
		int[] costs = costs(rarity);
		int sum = 0;
		for (int c : costs) {
			sum += Math.max(0, c);
		}
		return sum;
	}

	public static int levelFromShards(String rarity, int shards) {
		int[] costs = costs(rarity);
		if (costs.length == 0 || shards <= 0) {
			return 0;
		}
		int level = 0;
		int remaining = shards;
		for (int cost : costs) {
			if (cost <= 0 || remaining < cost) {
				break;
			}
			remaining -= cost;
			level++;
		}
		return level;
	}

	private static int[] costs(String rarity) {
		ensureLoaded();
		if (rarity == null || rarity.isBlank()) {
			return costsByRarity.getOrDefault("COMMON", new int[0]);
		}
		return costsByRarity.getOrDefault(rarity.toUpperCase(Locale.ROOT),
			costsByRarity.getOrDefault("COMMON", new int[0]));
	}

	private static void loadFromDisk() {
		Path path = Path.of(System.getProperty("user.home"),
			".betterpv", "neu-repo", "repo", "constants", "attribute_shards.json");
		if (!Files.isRegularFile(path)) {
			BetterPV.LOGGER.debug("Attribute shards missing at {}", path);
			defs = List.of();
			byStackId = Map.of();
			costsByRarity = Map.of();
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			Map<String, int[]> costs = new LinkedHashMap<>();
			JsonObject levelling = root.has("attribute_levelling") && root.get("attribute_levelling").isJsonObject()
				? root.getAsJsonObject("attribute_levelling") : null;
			if (levelling != null) {
				for (Map.Entry<String, JsonElement> e : levelling.entrySet()) {
					if (e.getValue() == null || !e.getValue().isJsonArray()) {
						continue;
					}
					JsonArray arr = e.getValue().getAsJsonArray();
					int[] row = new int[arr.size()];
					for (int i = 0; i < arr.size(); i++) {
						try {
							row[i] = arr.get(i).getAsInt();
						} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
							row[i] = 0;
						}
					}
					costs.put(e.getKey().toUpperCase(Locale.ROOT), row);
				}
			}
			costsByRarity = Map.copyOf(costs);

			List<Def> list = new ArrayList<>();
			Map<String, Def> map = new LinkedHashMap<>();
			if (root.has("attributes") && root.get("attributes").isJsonArray()) {
				for (JsonElement el : root.getAsJsonArray("attributes")) {
					if (el == null || !el.isJsonObject()) {
						continue;
					}
					JsonObject o = el.getAsJsonObject();
					String internal = str(o, "internalName");
					String stackId = stackIdFromInternal(internal);
					if (stackId.isBlank()) {
						continue;
					}
					String ability = str(o, "abilityName");
					String display = str(o, "displayName");
					if (display.isBlank()) {
						display = ability.isBlank() ? titleCase(stackId) : ability;
					}
					String rarity = str(o, "rarity");
					if (rarity.isBlank()) {
						rarity = "COMMON";
					}
					String bazaar = str(o, "bazaarName");
					Def def = new Def(stackId, display, ability, rarity.toUpperCase(Locale.ROOT), internal, bazaar);
					map.putIfAbsent(stackId, def);
					list.add(def);
				}
			}
			list.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
			defs = List.copyOf(list);
			byStackId = Map.copyOf(map);
		} catch (IOException | RuntimeException ex) {
			if (ex instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			BetterPV.LOGGER.warn("Failed to load attribute shards", ex);
			defs = List.of();
			byStackId = Map.of();
			costsByRarity = Map.of();
		}
	}

	private static String stackIdFromInternal(String internalName) {
		if (internalName == null || internalName.isBlank()) {
			return "";
		}
		String id = internalName;
		if (id.startsWith("ATTRIBUTE_SHARD_")) {
			id = id.substring("ATTRIBUTE_SHARD_".length());
		}
		int semi = id.indexOf(';');
		if (semi >= 0) {
			id = id.substring(0, semi);
		}
		return id.toLowerCase(Locale.ROOT);
	}

	private static String str(JsonObject o, String key) {
		if (o == null || key == null || !o.has(key) || !o.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			return o.get(key).getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static String titleCase(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String[] parts = raw.toLowerCase(Locale.ROOT).replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1));
			}
		}
		return sb.toString();
	}
}
