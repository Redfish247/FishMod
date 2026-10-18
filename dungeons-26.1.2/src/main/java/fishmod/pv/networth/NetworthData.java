/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
public final class NetworthData {
	private static JsonObject applicationWorth;
	private static JsonObject pets;
	private static JsonObject misc;
	private static Map<String, String> reforges = Map.of();

	private NetworthData() {
	}

	public static void ensureLoaded() {
		if (applicationWorth != null) {
			return;
		}
		JsonObject awRoot = load("data/networth/application_worth.json");
		applicationWorth = awRoot != null && awRoot.has("APPLICATION_WORTH")
			? awRoot.getAsJsonObject("APPLICATION_WORTH")
			: new JsonObject();
		pets = load("data/networth/pets.json");
		if (pets == null) {
			pets = new JsonObject();
		}
		misc = load("data/networth/misc.json");
		if (misc == null) {
			misc = new JsonObject();
		}
		JsonObject reforgeRoot = load("data/networth/reforges.json");
		Map<String, String> map = new HashMap<>();
		if (reforgeRoot != null) {
			for (var entry : reforgeRoot.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					map.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
		}
		reforges = Map.copyOf(map);
	}

	public static double worth(String key, double fallback) {
		ensureLoaded();
		JsonElement el = applicationWorth.get(key);
		return el != null && el.isJsonPrimitive() ? el.getAsDouble() : fallback;
	}

	public static List<String> masterStars() {
		ensureLoaded();
		return stringList(misc.get("MASTER_STARS"));
	}

	public static List<String> enrichments() {
		ensureLoaded();
		return stringList(misc.get("ENRICHMENTS"));
	}

	public static List<String> gemstoneSlots() {
		ensureLoaded();
		return stringList(misc.get("GEMSTONE_SLOTS"));
	}

	public static List<String> stackingEnchants() {
		ensureLoaded();
		return stringList(misc.get("STACKING_ENCHANTMENTS"));
	}

	public static List<String> ignoreSilex() {
		ensureLoaded();
		return stringList(misc.get("IGNORE_SILEX"));
	}

	public static List<String> allowedRecombCategories() {
		ensureLoaded();
		return stringList(misc.get("ALLOWED_RECOMBOBULATED_CATEGORIES"));
	}

	public static List<String> allowedRecombIds() {
		ensureLoaded();
		return stringList(misc.get("ALLOWED_RECOMBOBULATED_IDS"));
	}

	public static Set<String> blockedCandyPets() {
		ensureLoaded();
		return new HashSet<>(stringList(pets.get("BLOCKED_CANDY_REDUCE_PETS")));
	}

	public static Set<String> soulboundPets() {
		ensureLoaded();
		return new HashSet<>(stringList(pets.get("SOULBOUND_PETS")));
	}

	public static Map<String, Integer> specialLevels() {
		ensureLoaded();
		Map<String, Integer> out = new HashMap<>();
		JsonObject obj = pets.has("SPECIAL_LEVELS") ? pets.getAsJsonObject("SPECIAL_LEVELS") : null;
		if (obj != null) {
			for (var e : obj.entrySet()) {
				if (e.getValue().isJsonPrimitive()) {
					out.put(e.getKey(), e.getValue().getAsInt());
				}
			}
		}
		return out;
	}

	public static Map<String, Integer> rarityOffset() {
		ensureLoaded();
		Map<String, Integer> out = new HashMap<>();
		JsonObject obj = pets.has("RARITY_OFFSET") ? pets.getAsJsonObject("RARITY_OFFSET") : null;
		if (obj != null) {
			for (var e : obj.entrySet()) {
				if (e.getValue().isJsonPrimitive()) {
					out.put(e.getKey(), e.getValue().getAsInt());
				}
			}
		}
		return out;
	}

	public static List<Integer> petLevels() {
		ensureLoaded();
		List<Integer> out = new ArrayList<>();
		JsonArray arr = pets.has("LEVELS") ? pets.getAsJsonArray("LEVELS") : null;
		if (arr != null) {
			for (JsonElement el : arr) {
				out.add(el.getAsInt());
			}
		}
		return out;
	}

	public static List<String> tiers() {
		ensureLoaded();
		return stringList(pets.get("TIERS"));
	}

	public static String reforgeItem(String modifier) {
		ensureLoaded();
		return reforges.get(modifier);
	}

	public static Map<String, Integer> ignoredEnchants() {
		ensureLoaded();
		Map<String, Integer> out = new HashMap<>();
		JsonObject obj = misc.has("IGNORED_ENCHANTMENTS") ? misc.getAsJsonObject("IGNORED_ENCHANTMENTS") : null;
		if (obj != null) {
			for (var e : obj.entrySet()) {
				if (e.getValue().isJsonPrimitive()) {
					out.put(e.getKey(), e.getValue().getAsInt());
				}
			}
		}
		return out;
	}

	public static Map<String, List<String>> blockedEnchants() {
		ensureLoaded();
		Map<String, List<String>> out = new HashMap<>();
		JsonObject obj = misc.has("BLOCKED_ENCHANTMENTS") ? misc.getAsJsonObject("BLOCKED_ENCHANTMENTS") : null;
		if (obj != null) {
			for (var e : obj.entrySet()) {
				out.put(e.getKey(), stringList(e.getValue()));
			}
		}
		return out;
	}

	private static List<String> stringList(JsonElement element) {
		List<String> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) {
			return out;
		}
		for (JsonElement el : element.getAsJsonArray()) {
			if (el.isJsonPrimitive()) {
				out.add(el.getAsString());
			}
		}
		return out;
	}

	private static JsonObject load(String path) {
		try {
			InputStream stream = NetworthData.class.getClassLoader()
				.getResourceAsStream("assets/betterpv/" + path);
			if (stream == null) {
				Minecraft client = Minecraft.getInstance();
				if (client != null) {
					Identifier id = Identifier.fromNamespaceAndPath(BetterPV.MOD_ID, path);
					var opt = client.getResourceManager().getResource(id);
					if (opt.isPresent()) {
						stream = opt.get().open();
					}
				}
			}
			if (stream == null) {
				return null;
			}
			try (InputStream in = stream; InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to load {}", path, exception);
			return null;
		}
	}
}
