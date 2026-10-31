/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.data.MinionCosts;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.SkyBlockStats;
import fishmod.pv.gui.SkyBlockSymbols;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.price.HypixelItemsCache;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps Hypixel's collection unlock text ("Enchanted Bread Recipe") to NEU items. */
final class CollectionRewards {
	enum Kind { ITEM, MINION, XP, STAT, OTHER }

	/**
	 * {@code itemId} is a real item (tooltip, rarity, recipe). {@code iconId} is what gets drawn and can be a
	 * stand-in, like a level I potion for a brewing recipe or a plain enchanted book NEU doesn't have.
	 */
	record Reward(String text, Kind kind, String itemId, String label, String suffix, String iconId) {
		boolean isItem() {
			return this.itemId != null;
		}

		boolean hasIcon() {
			return this.iconId != null;
		}
	}

	/** Recipe cells A1..C3 in reading order; blank cells have a null id. Forge inputs fill cells in order. */
	record Recipe(String[] ids, int[] counts, int outputCount, boolean forge) {
	}

	static final int XP_COLOR = 0xFF55FFFF;
	private static final Pattern MINION = Pattern.compile("^(.+) Minion Recipes?$");
	private static final Pattern RECIPE = Pattern.compile("^(.+) (Recipes?)$");
	private static final Pattern PET = Pattern.compile("^\\[Lvl 1] (.+)$");
	private static final Pattern BOOK = Pattern.compile("^Enchanted Book \\((.+) ([IVXL]+)\\)$");
	private static final Pattern POTION = Pattern.compile("^(.+) Potion$");
	private static final Pattern STAT = Pattern.compile("^\\+[\\d,.]+%?\\S*\\s+(.+)$");
	private static final String FORGE = " Dwarven Forge";
	private static final String[] CELLS = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};
	private static final Map<String, Reward> RESOLVED = new ConcurrentHashMap<>();
	private static final Map<String, Integer> COLORS = new ConcurrentHashMap<>();
	private static volatile Map<String, String> hypixelNames = Map.of();
	private static volatile int hypixelNamesSize = -1;

	private CollectionRewards() {
	}

	static Reward resolve(String unlock) {
		Reward cached = RESOLVED.get(unlock);
		if (cached != null) {
			return cached;
		}
		Reward reward = parse(unlock, CollectionRewards::idForName, id -> NeuRepoCache.get(id) != null);
		if (NeuRepoCache.isReady() && !HypixelItemsCache.allItems().isEmpty()) {
			RESOLVED.put(unlock, reward);
		}
		return reward;
	}

	static Reward parse(String unlock, Function<String, String> idForName) {
		return parse(unlock, idForName, id -> false);
	}

	/** {@code exists} answers whether an NEU item id exists, for rewards whose id follows a naming pattern. */
	static Reward parse(String unlock, Function<String, String> idForName, Predicate<String> exists) {
		String raw = unlock == null ? "" : unlock.trim();
		String text = SkyBlockSymbols.replace(raw);
		if (text.endsWith("SkyBlock XP")) {
			return new Reward(text, Kind.XP, null, text, "", null);
		}
		Matcher minion = MINION.matcher(raw);
		if (minion.matches()) {
			String base = minion.group(1) + " Minion";
			String id = idForName.apply(base + " I");
			return new Reward(text, Kind.MINION, id, base, "Recipes", id);
		}
		Matcher recipe = RECIPE.matcher(raw);
		if (recipe.matches()) {
			String name = recipe.group(1);
			String suffix = recipe.group(2);
			if (name.endsWith(FORGE)) {
				name = name.substring(0, name.length() - FORGE.length());
				suffix = "Forge " + suffix;
			}
			return item(text, name, SkyBlockSymbols.replace(name).trim(), suffix, idForName, exists);
		}
		Matcher stat = STAT.matcher(text);
		if (stat.matches() && SkyBlockStats.byName(stat.group(1)) != null) {
			return new Reward(text, Kind.STAT, null, text, "", null);
		}
		return new Reward(text, Kind.OTHER, null, text, "", null);
	}

	private static Reward item(
		String text, String name, String label, String suffix, Function<String, String> idForName, Predicate<String> exists
	) {
		Matcher pet = PET.matcher(name);
		if (pet.matches()) {
			// Collection pet recipes craft the Common pet, which NEU keeps as TYPE;0.
			String id = idPart(pet.group(1)) + ";0";
			String found = exists.test(id) ? id : null;
			return new Reward(text, Kind.ITEM, found, label, suffix, found);
		}
		Matcher book = BOOK.matcher(name);
		if (book.matches()) {
			int level = roman(book.group(2));
			String base = idPart(book.group(1));
			String enchant = book.group(1) + " " + book.group(2);
			for (String id : new String[] {base + ";" + level, "ULTIMATE_" + base + ";" + level}) {
				if (level > 0 && exists.test(id)) {
					return new Reward(text, Kind.ITEM, id, enchant, suffix, id);
				}
			}
			return new Reward(text, Kind.ITEM, null, enchant, suffix, "ENCHANTED_BOOK");
		}
		// Brewing recipes unlock every level, so only borrow the level I icon, not its rarity or tooltip.
		Matcher potion = POTION.matcher(name);
		if (potion.matches()) {
			String id = "POTION_" + idPart(potion.group(1)) + ";1";
			if (exists.test(id)) {
				return new Reward(text, Kind.ITEM, null, label, suffix, id);
			}
		}
		String id = idForName.apply(name);
		return new Reward(text, Kind.ITEM, id, label, suffix, id);
	}

	/** NEU name match; when several items share the name, the only one with a recipe wins. Hypixel names last. */
	static String idForName(String name) {
		return pickId(NeuRepoCache.idsForDisplayName(name), id -> recipe(id) != null, () -> hypixelIdForName(name));
	}

	static String pickId(List<String> ids, Predicate<String> hasRecipe, Supplier<String> fallback) {
		if (ids.size() == 1) {
			return ids.get(0);
		}
		if (ids.isEmpty()) {
			return fallback.get();
		}
		List<String> crafted = ids.stream().filter(hasRecipe).toList();
		return crafted.size() == 1 ? crafted.get(0) : null;
	}

	// Renamed items keep their old NEU name (Scorched Topaz is still "Hot Stuff" there), so ask Hypixel.
	private static String hypixelIdForName(String name) {
		var items = HypixelItemsCache.allItems();
		if (items.size() != hypixelNamesSize) {
			Map<String, String> index = new HashMap<>();
			for (JsonObject item : items) {
				if (item == null || !(item.get("name") instanceof com.google.gson.JsonPrimitive)
					|| !(item.get("id") instanceof com.google.gson.JsonPrimitive)) {
					continue;
				}
				String key = item.get("name").getAsString().replaceAll("\u00a7.", "").trim().toLowerCase(Locale.ROOT);
				index.merge(key, item.get("id").getAsString(), (a, b) -> "");
			}
			hypixelNames = index;
			hypixelNamesSize = items.size();
		}
		String id = hypixelNames.get(name.trim().toLowerCase(Locale.ROOT));
		return id == null || id.isEmpty() ? null : id;
	}

	static boolean isPet(String itemId) {
		JsonObject item = NeuRepoCache.get(itemId);
		return item != null && item.get("displayname") instanceof com.google.gson.JsonPrimitive name
			&& name.getAsString().contains("{LVL}");
	}

	static int color(Reward reward) {
		if (reward.kind() == Kind.XP) {
			return XP_COLOR;
		}
		if (reward.kind() == Kind.STAT) {
			Matcher stat = STAT.matcher(reward.text());
			SkyBlockStats.StatStyle style = stat.matches() ? SkyBlockStats.byName(stat.group(1)) : null;
			return style == null ? PvDraw.COLOR_TEXT : style.color();
		}
		if (!reward.isItem()) {
			return PvDraw.COLOR_TEXT;
		}
		Integer cached = COLORS.get(reward.itemId());
		if (cached != null) {
			return cached;
		}
		String tier = SkyBlockItemFactory.resolveTier(reward.itemId());
		int color = SkyBlockItemFactory.tierArgb(tier);
		if (!tier.isBlank()) {
			COLORS.put(reward.itemId(), color);
		}
		return color;
	}

	static Recipe recipe(String itemId) {
		if (itemId == null) {
			return null;
		}
		JsonObject item = NeuRepoCache.get(itemId);
		Recipe crafting = recipe(MinionCosts.recipe(item));
		return crafting != null ? crafting : forgeRecipe(item);
	}

	static Recipe recipe(JsonObject recipe) {
		if (recipe == null) {
			return null;
		}
		String[] ids = new String[CELLS.length];
		int[] counts = new int[CELLS.length];
		boolean any = false;
		for (int i = 0; i < CELLS.length; i++) {
			JsonElement cell = recipe.get(CELLS[i]);
			if (cell == null || !cell.isJsonPrimitive()) {
				continue;
			}
			String raw = cell.getAsString().trim();
			if (raw.isEmpty()) {
				continue;
			}
			any |= putCell(ids, counts, i, raw);
		}
		return any ? new Recipe(ids, counts, outputCount(recipe), false) : null;
	}

	/** Dwarven Forge inputs from an NEU item's {@code recipes} list, laid out in reading order. */
	static Recipe forgeRecipe(JsonObject item) {
		if (item == null || !item.has("recipes") || !item.get("recipes").isJsonArray()) {
			return null;
		}
		for (JsonElement el : item.getAsJsonArray("recipes")) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject r = el.getAsJsonObject();
			if (!(r.get("type") instanceof com.google.gson.JsonPrimitive type) || !"forge".equalsIgnoreCase(type.getAsString())
				|| !r.has("inputs") || !r.get("inputs").isJsonArray()) {
				continue;
			}
			String[] ids = new String[CELLS.length];
			int[] counts = new int[CELLS.length];
			int slot = 0;
			for (JsonElement input : r.getAsJsonArray("inputs")) {
				if (slot < CELLS.length && input.isJsonPrimitive() && putCell(ids, counts, slot, input.getAsString().trim())) {
					slot++;
				}
			}
			return slot == 0 ? null : new Recipe(ids, counts, outputCount(r), true);
		}
		return null;
	}

	private static boolean putCell(String[] ids, int[] counts, int i, String raw) {
		if (raw.isEmpty()) {
			return false;
		}
		int colon = raw.lastIndexOf(':');
		String id = raw;
		int count = 1;
		if (colon > 0) {
			try {
				count = Math.max(1, (int) Double.parseDouble(raw.substring(colon + 1).trim()));
				id = raw.substring(0, colon);
			} catch (NumberFormatException ignored) {
				// ids like "PET:3" without a count keep their colon
			}
		}
		ids[i] = id.toUpperCase(Locale.ROOT);
		counts[i] = count;
		return true;
	}

	private static int outputCount(JsonObject recipe) {
		JsonElement count = recipe.get("count");
		if (count != null && count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber()) {
			return Math.max(1, (int) count.getAsDouble());
		}
		return 1;
	}

	private static String idPart(String name) {
		return name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	static int roman(String numeral) {
		int total = 0;
		int prev = 0;
		for (int i = numeral.length() - 1; i >= 0; i--) {
			int v = switch (numeral.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				default -> 0;
			};
			total += v < prev ? -v : v;
			prev = Math.max(prev, v);
		}
		return total;
	}
}
