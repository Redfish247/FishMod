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
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.price.HypixelCollectionsCache;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Minion categories and per-tier upgrade costs from NEU recipes + BetterPV prices. */
public final class MinionCosts {
	public enum Category {
		FARMING("Farming"),
		MINING("Mining"),
		COMBAT("Combat"),
		FORAGING("Foraging"),
		FISHING("Fishing"),
		RIFT("Rift"),
		OTHER("Other");

		private final String label;

		Category(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}

	/**
	 * Materials to craft {@code tier} from the previous tier. The previous-tier minion itself is
	 * excluded so summing tiers never prices a minion twice.
	 */
	public record TierCost(int tier, double coins, List<String> unpriced, boolean noRecipe) {
		public TierCost {
			unpriced = unpriced == null ? List.of() : List.copyOf(unpriced);
		}

		/** True when every ingredient has a price. */
		public boolean complete() {
			return !this.noRecipe && this.unpriced.isEmpty();
		}
	}

	public record MissingTier(String minionId, TierCost cost) {
	}

	/**
	 * Minions whose tier I ingredient isn't a Hypixel collection item (or that have no tier I recipe).
	 * Categories follow the SkyBlock wiki minion list.
	 */
	private static final Map<String, Category> CATEGORY_OVERRIDES = Map.of(
		"COW_GENERATOR", Category.FARMING,
		"MUSHROOM_GENERATOR", Category.FARMING,
		"SNOW_GENERATOR", Category.MINING,
		"FLOWER_GENERATOR", Category.FORAGING,
		"REVENANT_GENERATOR", Category.COMBAT,
		"TARANTULA_GENERATOR", Category.COMBAT,
		"VOIDLING_GENERATOR", Category.COMBAT,
		"INFERNO_GENERATOR", Category.COMBAT
	);

	private static final Map<String, Category> CATEGORY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	private MinionCosts() {
	}

	public static Category categoryOf(String minionId) {
		String id = minionId == null ? "" : minionId.toUpperCase(Locale.ROOT);
		Category cached = CATEGORY_CACHE.get(id);
		if (cached != null) {
			return cached;
		}
		Map<String, String> byItem = new LinkedHashMap<>();
		for (HypixelCollectionsCache.Category category : HypixelCollectionsCache.categories()) {
			for (HypixelCollectionsCache.Item item : category.items()) {
				if (item == null || item.id() == null) {
					continue;
				}
				byItem.put(item.id().toUpperCase(Locale.ROOT), category.id());
			}
		}
		Category resolved = category(id, NeuRepoCache.get(id + "_1"), byItem::get);
		// Don't pin OTHER before collections/NEU have loaded.
		if (resolved != Category.OTHER || (!byItem.isEmpty() && NeuRepoCache.isReady())) {
			CATEGORY_CACHE.put(id, resolved);
		}
		return resolved;
	}

	public static TierCost cost(String minionId, int tier) {
		String id = minionId == null ? "" : minionId.toUpperCase(Locale.ROOT);
		return tierCost(id, tier, NeuRepoCache.get(id + "_" + tier), ItemPricer::knownPrice);
	}

	/** Ingredients for one upgrade step (previous-tier minion excluded), for tooltips. */
	public static Map<String, Integer> ingredientsFor(String minionId, int tier) {
		String id = minionId == null ? "" : minionId.toUpperCase(Locale.ROOT);
		return ingredients(recipe(NeuRepoCache.get(id + "_" + tier)), id);
	}

	/** Missing tiers in order, each priced as a single upgrade step. */
	public static List<TierCost> missingTiers(CollectionSnapshot.MinionEntry minion) {
		List<TierCost> out = new ArrayList<>();
		if (minion == null) {
			return out;
		}
		for (int t = 1; t <= minion.tierCap(); t++) {
			if (!minion.crafted(t)) {
				out.add(cost(minion.id(), t));
			}
		}
		return out;
	}

	/**
	 * Missing tiers that can be crafted right now (tier I, or the previous tier was crafted),
	 * cheapest first. Unpriced / recipe-less tiers sink to the bottom.
	 */
	public static List<MissingTier> cheapestNext(List<CollectionSnapshot.MinionEntry> minions) {
		List<MissingTier> out = new ArrayList<>();
		if (minions == null) {
			return out;
		}
		for (CollectionSnapshot.MinionEntry minion : minions) {
			for (int t = 1; t <= minion.tierCap(); t++) {
				if (!minion.crafted(t) && (t == 1 || minion.crafted(t - 1))) {
					out.add(new MissingTier(minion.id(), cost(minion.id(), t)));
				}
			}
		}
		out.sort(cheapestFirst());
		return out;
	}

	static Comparator<MissingTier> cheapestFirst() {
		return Comparator
			.comparingInt((MissingTier m) -> m.cost().noRecipe() ? 2 : m.cost().complete() ? 0 : 1)
			.thenComparingDouble(m -> m.cost().coins())
			.thenComparing(MissingTier::minionId)
			.thenComparingInt(m -> m.cost().tier());
	}

	static Category category(String minionId, JsonObject tier1, Function<String, String> collectionCategory) {
		Category override = CATEGORY_OVERRIDES.get(minionId);
		if (override != null) {
			return override;
		}
		Map<String, Integer> ingredients = ingredients(recipe(tier1), minionId);
		String main = null;
		int best = 0;
		for (var e : ingredients.entrySet()) {
			if (e.getValue() > best) {
				best = e.getValue();
				main = e.getKey();
			}
		}
		String categoryId = main == null ? null : collectionCategory.apply(main);
		if (categoryId == null) {
			return Category.OTHER;
		}
		try {
			return Category.valueOf(categoryId.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return Category.OTHER;
		}
	}

	static TierCost tierCost(String minionId, int tier, JsonObject item, Function<String, Double> price) {
		JsonObject recipe = recipe(item);
		if (recipe == null) {
			return new TierCost(tier, 0, List.of(), true);
		}
		double coins = 0;
		List<String> unpriced = new ArrayList<>();
		for (var e : ingredients(recipe, minionId).entrySet()) {
			Double unit = price.apply(e.getKey());
			if (unit == null || unit <= 0) {
				unpriced.add(e.getKey());
			} else {
				coins += unit * e.getValue();
			}
		}
		return new TierCost(tier, coins, unpriced, false);
	}

	/** Ingredient id (Hypixel colon form) → total quantity, minus any minion of the same type (none when null). */
	public static Map<String, Integer> ingredients(JsonObject recipe, String minionId) {
		Map<String, Integer> out = new LinkedHashMap<>();
		if (recipe == null) {
			return out;
		}
		String minionPrefix = minionId == null ? null : minionId.toUpperCase(Locale.ROOT) + "_";
		for (var e : recipe.entrySet()) {
			String slot = e.getKey();
			if (slot.length() != 2 || !e.getValue().isJsonPrimitive()) {
				continue;
			}
			String raw = e.getValue().getAsString();
			if (raw == null || raw.isBlank()) {
				continue;
			}
			int colon = raw.lastIndexOf(':');
			String id = colon > 0 ? raw.substring(0, colon) : raw;
			int qty = 1;
			if (colon > 0) {
				try {
					qty = Math.max(1, Integer.parseInt(raw.substring(colon + 1).trim()));
				} catch (NumberFormatException ignored) {
					id = raw;
				}
			}
			id = id.toUpperCase(Locale.ROOT).replace('-', ':');
			if (minionPrefix != null && id.startsWith(minionPrefix)) {
				continue;
			}
			out.merge(id, qty, Integer::sum);
		}
		return out;
	}

	/** NEU {@code recipe}, else the first crafting entry in {@code recipes}. */
	public static JsonObject recipe(JsonObject item) {
		if (item == null) {
			return null;
		}
		if (item.has("recipe") && item.get("recipe").isJsonObject()) {
			return item.getAsJsonObject("recipe");
		}
		if (item.has("recipes") && item.get("recipes").isJsonArray()) {
			for (JsonElement el : item.getAsJsonArray("recipes")) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject r = el.getAsJsonObject();
				String type = r.has("type") && r.get("type").isJsonPrimitive() ? r.get("type").getAsString() : "crafting";
				if ("crafting".equalsIgnoreCase(type)) {
					return r;
				}
			}
		}
		return null;
	}
}
