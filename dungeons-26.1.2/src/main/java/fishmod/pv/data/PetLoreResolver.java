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
import fishmod.pv.neu.NeuRepoCache;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves NEU pet lore placeholders ({@code {STRENGTH}}, {@code {0}}, etc.) using
 * {@code constants/petnums.json}, matching NotEnoughUpdates' interpolation.
 */
public final class PetLoreResolver {
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)\\}|<([A-Za-z0-9_]+)>|\\(([0-9]+)\\)");
	private static volatile JsonObject petnumsCache;

	private PetLoreResolver() {
	}

	public static List<String> loreFor(PetSnapshot.Entry pet) {
		if (pet == null) {
			return List.of();
		}
		List<String> raw = neuLore(pet.neuId());
		Map<String, String> replacements = loreReplacements(pet.type(), pet.tier(), pet.level());
		List<String> out = new ArrayList<>(raw.size());
		for (String line : raw) {
			out.add(apply(line, replacements, pet.level()));
		}
		if (pet.hasHeldItem()) {
			out.add("");
			out.add("§7Held Item: §a" + prettyId(pet.heldItem()));
		}
		if (pet.candyUsed() > 0) {
			out.add("§7Candy Used: §a" + pet.candyUsed());
		}
		return out;
	}

	public static String displayNameFor(PetSnapshot.Entry pet) {
		if (pet == null) {
			return "";
		}
		String tierColor = tierColor(pet.tier());
		JsonObject neu = NeuRepoCache.get(pet.neuId());
		if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive()) {
			String name = neu.get("displayname").getAsString();
			if (name != null && !name.isBlank()) {
				return apply(name, Map.of(), pet.level());
			}
		}
		return "§7[Lvl " + pet.level() + "] " + tierColor + pet.displayName();
	}

	public record PetType(String type, String name, String tier, String neuId) {
	}

	private static final String[] PET_TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL"};

	/** Every pet type in NEU petnums.json at its highest tier, sorted by name. */
	public static List<PetType> petTypes() {
		List<PetType> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : petnums().entrySet()) {
			if (!e.getValue().isJsonObject()) continue;
			JsonObject tiers = e.getValue().getAsJsonObject();
			int best = -1;
			for (int i = PET_TIERS.length - 1; i >= 0; i--) {
				if (tiers.has(PET_TIERS[i])) {
					best = i;
					break;
				}
			}
			if (best < 0) continue;
			String type = e.getKey();
			String neuId = type + ";" + best;
			String name = prettyId(type);
			JsonObject neu = NeuRepoCache.get(neuId);
			if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive()) {
				String raw = neu.get("displayname").getAsString();
				int bracket = raw.indexOf("] ");
				String stripped = (bracket >= 0 ? raw.substring(bracket + 2) : raw).replaceAll("§.", "").trim();
				if (!stripped.isEmpty()) name = stripped;
			}
			out.add(new PetType(type, name, PET_TIERS[best], neuId));
		}
		out.sort(java.util.Comparator.comparing((PetType p) -> p.name().toLowerCase(Locale.ROOT)));
		return out;
	}

	private static List<String> neuLore(String neuId) {
		JsonObject neu = NeuRepoCache.get(neuId);
		if (neu == null || !neu.has("lore") || !neu.get("lore").isJsonArray()) {
			return List.of();
		}
		List<String> lore = new ArrayList<>();
		for (JsonElement el : neu.getAsJsonArray("lore")) {
			if (el != null && el.isJsonPrimitive()) {
				String line = el.getAsString();
				lore.add(line == null ? "" : line);
			}
		}
		return lore;
	}

	private static Map<String, String> loreReplacements(String type, String tier, int level) {
		Map<String, String> out = new HashMap<>();
		JsonObject petnums = petnums();
		if (petnums == null || type == null || type.isBlank()) {
			return out;
		}
		if (!petnums.has(type) || !petnums.get(type).isJsonObject()) {
			return out;
		}
		JsonObject byTier = petnums.getAsJsonObject(type);
		String tierKey = tier == null || tier.isBlank() ? "COMMON" : tier.toUpperCase(Locale.ROOT);
		if (!byTier.has(tierKey) || !byTier.get(tierKey).isJsonObject()) {
			return out;
		}
		JsonObject tierObj = byTier.getAsJsonObject(tierKey);
		int numsLevel = numsLevelFor(tierObj, level);

		List<Integer> keys = levelKeys(tierObj);
		if (keys.isEmpty()) {
			return out;
		}
		int lo = keys.get(0);
		int hi = keys.get(keys.size() - 1);
		for (int key : keys) {
			if (key <= numsLevel) {
				lo = key;
			}
			if (key >= numsLevel) {
				hi = key;
				break;
			}
		}
		JsonObject low = tierObj.getAsJsonObject(String.valueOf(lo));
		JsonObject high = tierObj.getAsJsonObject(String.valueOf(hi));
		double t = (lo == hi || numsLevel <= lo) ? 0.0
			: (numsLevel >= hi) ? 1.0
			: (numsLevel - lo) / (double) (hi - lo);

		Map<String, Double> lowStats = readStatNums(low);
		Map<String, Double> highStats = readStatNums(high);
		Set<String> statIds = new LinkedHashSet<>();
		statIds.addAll(lowStats.keySet());
		statIds.addAll(highStats.keySet());
		for (String id : statIds) {
			double a = lowStats.getOrDefault(id, 0.0);
			double b = highStats.getOrDefault(id, 0.0);
			out.put(id.toUpperCase(Locale.ROOT), formatNum(a + (b - a) * t));
		}

		List<Double> lowOther = readOtherNums(low);
		List<Double> highOther = readOtherNums(high);
		int n = Math.max(lowOther.size(), highOther.size());
		for (int i = 0; i < n; i++) {
			double a = i < lowOther.size() ? lowOther.get(i) : 0.0;
			double b = i < highOther.size() ? highOther.get(i) : a;
			out.put(String.valueOf(i), formatNum(a + (b - a) * t));
		}
		return out;
	}

	/**
	 * NEU {@code stats_levelling_curve} e.g. {@code 101:200:1} maps pet level 101→1 … 200→100.
	 */
	private static int numsLevelFor(JsonObject tierObj, int petLevel) {
		int level = Math.max(1, petLevel);
		if (tierObj != null && tierObj.has("stats_levelling_curve")
			&& tierObj.get("stats_levelling_curve").isJsonPrimitive()) {
			String curve = tierObj.get("stats_levelling_curve").getAsString();
			String[] parts = curve.split(":");
			if (parts.length >= 2) {
				try {
					int start = Integer.parseInt(parts[0].trim());
					int end = Integer.parseInt(parts[1].trim());
					int offset = parts.length >= 3 ? Integer.parseInt(parts[2].trim()) : 1;
					if (level >= start) {
						int mapped = level - start + offset;
						int maxMapped = end - start + offset;
						return Math.max(offset, Math.min(maxMapped, mapped));
					}
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return Math.min(100, level);
	}

	private static List<Integer> levelKeys(JsonObject tierObj) {
		List<Integer> keys = new ArrayList<>();
		for (var entry : tierObj.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			try {
				keys.add(Integer.parseInt(entry.getKey()));
			} catch (NumberFormatException ignored) {
			}
		}
		keys.sort(Integer::compareTo);
		return keys;
	}

	private static Map<String, Double> readStatNums(JsonObject node) {
		Map<String, Double> out = new HashMap<>();
		if (node == null || !node.has("statNums") || !node.get("statNums").isJsonObject()) {
			return out;
		}
		for (var entry : node.getAsJsonObject("statNums").entrySet()) {
			if (entry.getValue().isJsonPrimitive()) {
				try {
					out.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue().getAsDouble());
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				}
			}
		}
		return out;
	}

	private static List<Double> readOtherNums(JsonObject node) {
		List<Double> out = new ArrayList<>();
		if (node == null || !node.has("otherNums") || !node.get("otherNums").isJsonArray()) {
			return out;
		}
		JsonArray arr = node.getAsJsonArray("otherNums");
		for (JsonElement el : arr) {
			if (el != null && el.isJsonPrimitive()) {
				try {
					out.add(el.getAsDouble());
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					out.add(0.0);
				}
			}
		}
		return out;
	}

	private static String apply(String line, Map<String, String> replacements, int level) {
		if (line == null) {
			return "";
		}
		String out = line
			.replace("{LVL}", String.valueOf(level))
			.replace("{Lvl}", String.valueOf(level));
		if (replacements == null || replacements.isEmpty()) {
			return out;
		}
		Matcher matcher = PLACEHOLDER.matcher(out);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(1);
			if (key == null) key = matcher.group(2);
			if (key == null) key = matcher.group(3);
			String value = key == null ? null : replacements.get(key.toUpperCase(Locale.ROOT));
			if (value == null && key != null) {
				value = replacements.get(key);
			}
			matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? matcher.group() : value));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private static String formatNum(double value) {
		double rounded = Math.round(value * 10.0) / 10.0;
		if (Math.abs(rounded - Math.rint(rounded)) < 1e-9) {
			return String.valueOf((long) Math.rint(rounded));
		}
		String s = String.format(Locale.ROOT, "%.1f", rounded);
		if (s.endsWith(".0")) {
			return s.substring(0, s.length() - 2);
		}
		return s;
	}

	private static String tierColor(String tier) {
		if (tier == null) return "§f";
		return switch (tier.toUpperCase(Locale.ROOT)) {
			case "MYTHIC" -> "§d";
			case "LEGENDARY" -> "§6";
			case "EPIC" -> "§5";
			case "RARE" -> "§9";
			case "UNCOMMON" -> "§a";
			default -> "§f";
		};
	}

	private static String prettyId(String id) {
		if (id == null || id.isBlank()) return "";
		String[] parts = id.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) sb.append(part.substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}

	private static JsonObject petnums() {
		JsonObject cached = petnumsCache;
		if (cached != null) {
			return cached;
		}
		synchronized (PetLoreResolver.class) {
			if (petnumsCache != null) {
				return petnumsCache;
			}
			petnumsCache = loadPetnums();
			return petnumsCache;
		}
	}

	private static JsonObject loadPetnums() {
		Path[] candidates = {
			Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo", "repo", "constants", "petnums.json"),
			Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo", "constants", "petnums.json")
		};
		for (Path path : candidates) {
			try {
				if (Files.isRegularFile(path)) {
					try (var reader = Files.newBufferedReader(path)) {
						JsonElement el = JsonParser.parseReader(reader);
						if (el != null && el.isJsonObject()) {
							return el.getAsJsonObject();
						}
					}
				}
			} catch (IOException | IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			}
		}
		return new JsonObject();
	}
}
