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

/**
 * NEU {@code constants/bestiary.json}: island categories, families, and kill brackets.
 */
public final class BestiaryData {
	public record Family(
		String id,
		String name,
		String categoryId,
		int bracket,
		String bracketType,
		int cap,
		List<String> mobIds,
		String textureValue,
		String itemIcon
	) {
		public Family {
			id = id == null ? "" : id;
			name = name == null || name.isBlank() ? id : name;
			categoryId = categoryId == null ? "" : categoryId;
			bracket = Math.max(1, bracket);
			bracketType = bracketType == null ? "" : bracketType;
			cap = Math.max(0, cap);
			mobIds = mobIds == null ? List.of() : List.copyOf(mobIds);
			textureValue = textureValue == null ? "" : textureValue;
			itemIcon = itemIcon == null ? "" : itemIcon;
		}
	}

	public record Category(
		String id,
		String name,
		String textureValue,
		String itemIcon,
		List<Family> families
	) {
		public Category {
			id = id == null ? "" : id;
			name = name == null || name.isBlank() ? id : name;
			textureValue = textureValue == null ? "" : textureValue;
			itemIcon = itemIcon == null ? "" : itemIcon;
			families = families == null ? List.of() : List.copyOf(families);
		}
	}

	private static volatile boolean loaded;
	private static List<Category> categories = List.of();
	private static Map<String, List<Integer>> brackets = Map.of();
	private static Map<String, Map<String, List<Integer>>> bracketSets = Map.of();
	private static Map<String, Family> familiesById = Map.of();

	private BestiaryData() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (BestiaryData.class) {
			if (loaded) {
				return;
			}
			loadFromDisk();
			loaded = true;
		}
	}

	/** Reload after NEU-REPO updates. */
	public static void reload() {
		synchronized (BestiaryData.class) {
			loaded = false;
			loadFromDisk();
			loaded = true;
		}
	}

	public static List<Category> categories() {
		ensureLoaded();
		return categories;
	}

	public static Category category(String id) {
		ensureLoaded();
		if (id == null) {
			return null;
		}
		for (Category cat : categories) {
			if (cat.id().equalsIgnoreCase(id)) {
				return cat;
			}
		}
		return null;
	}

	public static List<Integer> bracket(int bracketId) {
		return bracket("", bracketId);
	}

	public static List<Integer> bracket(String bracketType, int bracketId) {
		ensureLoaded();
		String id = String.valueOf(bracketId);
		if (bracketType != null && !bracketType.isBlank()) {
			Map<String, List<Integer>> set = bracketSets.get(bracketType);
			if (set != null) {
				List<Integer> ladder = set.get(id);
				if (ladder != null && !ladder.isEmpty()) {
					return ladder;
				}
			}
		}
		return brackets.getOrDefault(id, List.of());
	}

	public static Family family(String id) {
		ensureLoaded();
		return id == null ? null : familiesById.get(id);
	}

	private static void loadFromDisk() {
		Path path = Path.of(
			System.getProperty("user.home"),
			".betterpv",
			"neu-repo",
			"repo",
			"constants",
			"bestiary.json"
		);
		if (!Files.isRegularFile(path)) {
			BetterPV.LOGGER.warn("Missing NEU bestiary.json at {}", path);
			categories = List.of();
			brackets = Map.of();
			bracketSets = Map.of();
			familiesById = Map.of();
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement rootEl = JsonParser.parseReader(reader);
			if (rootEl == null || !rootEl.isJsonObject()) {
				return;
			}
			JsonObject root = rootEl.getAsJsonObject();
			Map<String, List<Integer>> br = parseBracketLadders(obj(root.get("brackets")));
			Map<String, Map<String, List<Integer>>> sets = new LinkedHashMap<>();
			JsonObject setsObj = obj(root.get("bracketSets"));
			if (setsObj != null) {
				for (var setEntry : setsObj.entrySet()) {
					if (setEntry.getKey() == null || !setEntry.getValue().isJsonObject()) {
						continue;
					}
					sets.put(setEntry.getKey(), parseBracketLadders(setEntry.getValue().getAsJsonObject()));
				}
			}

			List<Category> cats = new ArrayList<>();
			Map<String, Family> byId = new LinkedHashMap<>();
			for (var entry : root.entrySet()) {
				String key = entry.getKey();
				if (isCatalogMetaKey(key) || !entry.getValue().isJsonObject()) {
					continue;
				}
				parseCategory(key, entry.getValue().getAsJsonObject(), cats, byId);
			}
			brackets = Map.copyOf(br);
			bracketSets = copyBracketSets(sets);
			categories = List.copyOf(cats);
			familiesById = Map.copyOf(byId);
			BetterPV.LOGGER.info("Loaded bestiary catalog ({} categories, {} families)", cats.size(), byId.size());
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			BetterPV.LOGGER.warn("Failed to load bestiary.json", exception);
			categories = List.of();
			brackets = Map.of();
			bracketSets = Map.of();
			familiesById = Map.of();
		}
	}

	private static void parseCategory(
		String id,
		JsonObject obj,
		List<Category> cats,
		Map<String, Family> byId
	) {
		String name = strip(str(obj, "name"));
		if (name.isBlank()) {
			name = pretty(id);
		}
		Icon icon = parseIcon(obj.get("icon"));
		List<Family> families = new ArrayList<>();
		if (obj.has("hasSubcategories") && obj.get("hasSubcategories").getAsBoolean()) {
			for (var sub : obj.entrySet()) {
				String subKey = sub.getKey();
				if (subKey == null
					|| subKey.equals("name")
					|| subKey.equals("icon")
					|| subKey.equals("hasSubcategories")
					|| !sub.getValue().isJsonObject()) {
					continue;
				}
				JsonObject subObj = sub.getValue().getAsJsonObject();
				families.addAll(parseFamilies(id, subObj.get("mobs"), byId));
			}
		} else {
			families.addAll(parseFamilies(id, obj.get("mobs"), byId));
		}
		if (families.isEmpty()) {
			return;
		}
		cats.add(new Category(id, name, icon.texture(), icon.item(), families));
	}

	private static List<Family> parseFamilies(String categoryId, JsonElement mobsEl, Map<String, Family> byId) {
		List<Family> out = new ArrayList<>();
		if (mobsEl == null || !mobsEl.isJsonArray()) {
			return out;
		}
		JsonArray arr = mobsEl.getAsJsonArray();
		int index = 0;
		for (JsonElement el : arr) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject mob = el.getAsJsonObject();
			String name = strip(str(mob, "name"));
			int bracket = Math.max(1, intOr(mob, "bracket", 1));
			String bracketType = str(mob, "bracketType");
			int cap = Math.max(0, intOr(mob, "cap", 0));
			List<String> mobIds = new ArrayList<>();
			if (mob.has("mobs") && mob.get("mobs").isJsonArray()) {
				for (JsonElement mid : mob.getAsJsonArray("mobs")) {
					if (mid != null && mid.isJsonPrimitive()) {
						String id = mid.getAsString();
						if (id != null && !id.isBlank()) {
							mobIds.add(id.toLowerCase(Locale.ROOT));
						}
					}
				}
			}
			Icon icon = parseIcon(mob);
			String famId = categoryId + ":" + index + ":" + slug(name.isBlank() ? "mob" : name);
			Family family = new Family(famId, name.isBlank() ? pretty(famId) : name, categoryId, bracket, bracketType, cap, mobIds,
				icon.texture(), icon.item());
			out.add(family);
			byId.put(famId, family);
			index++;
		}
		return out;
	}

	private record Icon(String texture, String item) {
	}

	private static Icon parseIcon(JsonElement el) {
		if (el == null) {
			return new Icon("", "");
		}
		if (el.isJsonObject()) {
			JsonObject obj = el.getAsJsonObject();
			String texture = str(obj, "texture");
			// A few NEU mobs (e.g. Cow) list several textures; the first is the mob's own.
			if (texture.isBlank() && obj.has("texture") && obj.get("texture").isJsonArray()) {
				for (JsonElement t : obj.getAsJsonArray("texture")) {
					if (t != null && t.isJsonPrimitive() && !t.getAsString().isBlank()) {
						texture = t.getAsString();
						break;
					}
				}
			}
			String item = str(obj, "item");
			if (item.isBlank()) {
				item = str(obj, "vanilla");
			}
			return new Icon(texture, item);
		}
		return new Icon("", "");
	}

	private static boolean isCatalogMetaKey(String key) {
		return key == null
			|| "brackets".equalsIgnoreCase(key)
			|| "bracketSets".equalsIgnoreCase(key);
	}

	private static Map<String, List<Integer>> parseBracketLadders(JsonObject obj) {
		Map<String, List<Integer>> out = new LinkedHashMap<>();
		if (obj == null) {
			return out;
		}
		for (var entry : obj.entrySet()) {
			if (entry.getKey() == null || !entry.getValue().isJsonArray()) {
				continue;
			}
			List<Integer> ladder = new ArrayList<>();
			for (JsonElement el : entry.getValue().getAsJsonArray()) {
				if (el != null && el.isJsonPrimitive()) {
					try {
						ladder.add(Math.max(0, el.getAsInt()));
					} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					}
				}
			}
			out.put(entry.getKey(), List.copyOf(ladder));
		}
		return out;
	}

	private static Map<String, Map<String, List<Integer>>> copyBracketSets(
		Map<String, Map<String, List<Integer>>> sets
	) {
		Map<String, Map<String, List<Integer>>> out = new LinkedHashMap<>();
		for (var entry : sets.entrySet()) {
			out.put(entry.getKey(), Map.copyOf(entry.getValue()));
		}
		return Map.copyOf(out);
	}

	private static JsonObject obj(JsonElement el) {
		return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
	}

	private static String str(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			return obj.get(key).getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static int intOr(JsonObject obj, String key, int fallback) {
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return fallback;
		}
		try {
			return obj.get(key).getAsInt();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return fallback;
		}
	}

	private static String strip(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String cleaned = raw.replaceAll("§[0-9a-fk-or]", "");
		return cleaned.trim();
	}

	private static String slug(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	public static String prettyId(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String[] parts = id.toLowerCase(Locale.ROOT).split("[:_]+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1));
			}
		}
		return out.toString();
	}

	private static String pretty(String id) {
		return prettyId(id);
	}
}
