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
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** NEU {@code hoppity.json} rabbit id → rarity (COMMON … DIVINE). */
public final class HoppityRabbitsData {
	private static volatile boolean loaded;
	private static volatile Map<String, String> rarityById = Map.of();

	private HoppityRabbitsData() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (HoppityRabbitsData.class) {
			if (loaded) {
				return;
			}
			loadFromDisk();
			loaded = true;
		}
	}

	/** Reload after NEU-REPO updates. */
	public static void reload() {
		synchronized (HoppityRabbitsData.class) {
			loaded = false;
			loadFromDisk();
			loaded = true;
		}
	}

	/** Uppercase rarity, or {@code COMMON} when unknown. */
	public static String rarityOf(String rabbitId) {
		ensureLoaded();
		if (rabbitId == null || rabbitId.isBlank()) {
			return "COMMON";
		}
		String hit = rarityById.get(rabbitId.toLowerCase(Locale.ROOT));
		return hit == null ? "COMMON" : hit;
	}

	private static void loadFromDisk() {
		Path path = Path.of(System.getProperty("user.home"),
			".betterpv", "neu-repo", "repo", "constants", "hoppity.json");
		if (!Files.isRegularFile(path)) {
			BetterPV.LOGGER.warn("hoppity.json missing at {}", path);
			rarityById = Map.of();
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject hoppity = root.has("hoppity") && root.get("hoppity").isJsonObject()
				? root.getAsJsonObject("hoppity")
				: root;
			Map<String, String> map = new HashMap<>();
			JsonObject rarities = hoppity.has("rarities") && hoppity.get("rarities").isJsonObject()
				? hoppity.getAsJsonObject("rarities")
				: null;
			if (rarities != null) {
				for (var entry : rarities.entrySet()) {
					String rarityKey = entry.getKey();
					if (rarityKey == null || !entry.getValue().isJsonObject()) {
						continue;
					}
					String tier = rarityKey.toUpperCase(Locale.ROOT);
					JsonArray rabbits = entry.getValue().getAsJsonObject().has("rabbits")
						&& entry.getValue().getAsJsonObject().get("rabbits").isJsonArray()
						? entry.getValue().getAsJsonObject().getAsJsonArray("rabbits")
						: null;
					if (rabbits == null) {
						continue;
					}
					for (JsonElement el : rabbits) {
						if (el != null && el.isJsonPrimitive()) {
							map.put(el.getAsString().toLowerCase(Locale.ROOT), tier);
						}
					}
				}
			}
			// Specials are mythic/divine named rabbits; keep existing rarity if listed, else MYTHIC.
			JsonObject special = hoppity.has("special") && hoppity.get("special").isJsonObject()
				? hoppity.getAsJsonObject("special")
				: null;
			if (special != null) {
				for (var entry : special.entrySet()) {
					String id = entry.getKey();
					if (id == null || id.isBlank()) {
						continue;
					}
					map.putIfAbsent(id.toLowerCase(Locale.ROOT), "MYTHIC");
				}
			}
			rarityById = Collections.unmodifiableMap(map);
			BetterPV.LOGGER.info("Loaded {} chocolate rabbits from hoppity.json", rarityById.size());
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			BetterPV.LOGGER.warn("Failed loading hoppity.json", exception);
			rarityById = Map.of();
		}
	}
}
