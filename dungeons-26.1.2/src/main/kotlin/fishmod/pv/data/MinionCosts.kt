/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.price.HypixelCollectionsCache
import fishmod.pv.price.ItemPricer
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/** Minion categories and per-tier upgrade costs from NEU recipes + item prices. */
object MinionCosts {
    enum class Category(private val label: String) {
        FARMING("Farming"),
        MINING("Mining"),
        COMBAT("Combat"),
        FORAGING("Foraging"),
        FISHING("Fishing"),
        RIFT("Rift"),
        OTHER("Other");

        fun label(): String = label
    }

    /**
     * Materials to craft `tier` from the previous tier. The previous-tier minion itself is
     * excluded so summing tiers never prices a minion twice.
     */
    class TierCost(tier: Int, coins: Double, unpriced: List<String>?, noRecipe: Boolean) {
        @get:JvmName("tier") val tier: Int = tier
        @get:JvmName("coins") val coins: Double = coins
        @get:JvmName("unpriced") val unpriced: List<String> = unpriced?.toList() ?: emptyList()
        @get:JvmName("noRecipe") val noRecipe: Boolean = noRecipe

        /** True when every ingredient has a price. */
        fun complete(): Boolean = !noRecipe && unpriced.isEmpty()
    }

    @JvmRecord
    data class MissingTier(val minionId: String, val cost: TierCost)

    /**
     * Minions whose tier I ingredient isn't a Hypixel collection item (or that have no tier I recipe).
     * Categories follow the SkyBlock wiki minion list.
     */
    private val CATEGORY_OVERRIDES = mapOf(
        "COW_GENERATOR" to Category.FARMING,
        "MUSHROOM_GENERATOR" to Category.FARMING,
        "SNOW_GENERATOR" to Category.MINING,
        "FLOWER_GENERATOR" to Category.FORAGING,
        "REVENANT_GENERATOR" to Category.COMBAT,
        "TARANTULA_GENERATOR" to Category.COMBAT,
        "VOIDLING_GENERATOR" to Category.COMBAT,
        "INFERNO_GENERATOR" to Category.COMBAT,
    )

    private val CATEGORY_CACHE = ConcurrentHashMap<String, Category>()

    @JvmStatic
    fun categoryOf(minionId: String?): Category {
        val id = minionId?.uppercase() ?: ""
        CATEGORY_CACHE[id]?.let { return it }
        val byItem = LinkedHashMap<String, String>()
        for (category in HypixelCollectionsCache.categories()) {
            for (item in category.items) {
                byItem[item.id.uppercase()] = category.id
            }
        }
        val resolved = category(id, NeuRepoCache.get(id + "_1")) { byItem[it] }
        // Don't pin OTHER before collections/NEU have loaded.
        if (resolved != Category.OTHER || (byItem.isNotEmpty() && NeuRepoCache.isReady())) {
            CATEGORY_CACHE[id] = resolved
        }
        return resolved
    }

    @JvmStatic
    fun cost(minionId: String?, tier: Int): TierCost {
        val id = minionId?.uppercase() ?: ""
        return tierCost(id, tier, NeuRepoCache.get(id + "_" + tier)) { ItemPricer.knownPrice(it) }
    }

    /** Ingredients for one upgrade step (previous-tier minion excluded), for tooltips. */
    @JvmStatic
    fun ingredientsFor(minionId: String?, tier: Int): Map<String, Int> {
        val id = minionId?.uppercase() ?: ""
        return ingredients(recipe(NeuRepoCache.get(id + "_" + tier)), id)
    }

    /** Missing tiers in order, each priced as a single upgrade step. */
    @JvmStatic
    fun missingTiers(minion: CollectionSnapshot.MinionEntry?): List<TierCost> {
        val out = ArrayList<TierCost>()
        if (minion == null) return out
        for (t in 1..minion.tierCap) {
            if (!minion.crafted(t)) out.add(cost(minion.id, t))
        }
        return out
    }

    /**
     * Missing tiers that can be crafted right now (tier I, or the previous tier was crafted),
     * cheapest first. Unpriced / recipe-less tiers sink to the bottom.
     */
    @JvmStatic
    fun cheapestNext(minions: List<CollectionSnapshot.MinionEntry>?): List<MissingTier> {
        val out = ArrayList<MissingTier>()
        if (minions == null) return out
        for (minion in minions) {
            for (t in 1..minion.tierCap) {
                if (!minion.crafted(t) && (t == 1 || minion.crafted(t - 1))) {
                    out.add(MissingTier(minion.id, cost(minion.id, t)))
                }
            }
        }
        out.sortWith(cheapestFirst())
        return out
    }

    @JvmStatic
    fun cheapestFirst(): Comparator<MissingTier> =
        compareBy<MissingTier> { if (it.cost.noRecipe) 2 else if (it.cost.complete()) 0 else 1 }
            .thenBy { it.cost.coins }
            .thenBy { it.minionId }
            .thenBy { it.cost.tier }

    @JvmStatic
    fun category(minionId: String, tier1: JsonObject?, collectionCategory: Function<String, String?>): Category {
        CATEGORY_OVERRIDES[minionId]?.let { return it }
        var main: String? = null
        var best = 0
        for ((key, qty) in ingredients(recipe(tier1), minionId)) {
            if (qty > best) {
                best = qty
                main = key
            }
        }
        val categoryId = main?.let { collectionCategory.apply(it) } ?: return Category.OTHER
        return try {
            Category.valueOf(categoryId.uppercase())
        } catch (_: IllegalArgumentException) {
            Category.OTHER
        }
    }

    @JvmStatic
    fun tierCost(minionId: String, tier: Int, item: JsonObject?, price: Function<String, Double?>): TierCost {
        val recipe = recipe(item) ?: return TierCost(tier, 0.0, emptyList(), true)
        var coins = 0.0
        val unpriced = ArrayList<String>()
        for ((key, qty) in ingredients(recipe, minionId)) {
            val unit = price.apply(key)
            if (unit == null || unit <= 0) unpriced.add(key) else coins += unit * qty
        }
        return TierCost(tier, coins, unpriced, false)
    }

    /** Ingredient id (Hypixel colon form) → total quantity, minus any minion of the same type (none when null). */
    @JvmStatic
    fun ingredients(recipe: JsonObject?, minionId: String?): MutableMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        if (recipe == null) return out
        val minionPrefix = minionId?.let { it.uppercase() + "_" }
        for ((slot, value) in recipe.entrySet()) {
            if (slot.length != 2 || !value.isJsonPrimitive) continue
            val raw: String? = value.asString
            if (raw.isNullOrBlank()) continue
            val colon = raw.lastIndexOf(':')
            var id = if (colon > 0) raw.substring(0, colon) else raw
            var qty = 1
            if (colon > 0) {
                try {
                    qty = maxOf(1, raw.substring(colon + 1).trim().toInt())
                } catch (_: NumberFormatException) {
                    id = raw
                }
            }
            id = id.uppercase().replace('-', ':')
            if (minionPrefix != null && id.startsWith(minionPrefix)) continue
            out.merge(id, qty, Int::plus)
        }
        return out
    }

    /** NEU `recipe`, else the first crafting entry in `recipes`. */
    @JvmStatic
    fun recipe(item: JsonObject?): JsonObject? {
        if (item == null) return null
        if (item.has("recipe") && item.get("recipe").isJsonObject) return item.getAsJsonObject("recipe")
        if (item.has("recipes") && item.get("recipes").isJsonArray) {
            for (el in item.getAsJsonArray("recipes")) {
                if (!el.isJsonObject) continue
                val r = el.asJsonObject
                val type = if (r.has("type") && r.get("type").isJsonPrimitive) r.get("type").asString else "crafting"
                if ("crafting".equals(type, ignoreCase = true)) return r
            }
        }
        return null
    }
}
