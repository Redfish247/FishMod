/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.nav.MuseumSort
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.price.HypixelItemsCache
import fishmod.pv.price.ItemPricer
import java.util.EnumMap

/**
 * Museum donation slots from Hypixel `museum_data`.
 * Armor sets are cataloged by set id with piece lists for icons/pricing.
 * Grid order matches Hypixel: `game_stage` progression, then donation XP, then id.
 * Higher-tier / mapped donations credit lower variants via `parent` + `mapped_item_ids`.
 */
object MuseumCatalog {
    private val GAME_STAGE_RANK = mapOf(
        "STARTER" to 0,
        "AMATEUR" to 1,
        "INTERMEDIATE" to 2,
        "SKILLED" to 3,
        "EXPERT" to 4,
        "PROFESSIONAL" to 5,
        "MASTER" to 6,
    )
    private val HYPIXEL_COLOR = Regex("%%[a-zA-Z0-9_]+%%")
    private val UNDERSCORES = Regex("_+")

    class Entry(
        donationId: String?,
        sort: MuseumSort?,
        donationXp: Int,
        @get:JvmName("armorSet") val armorSet: Boolean,
        pieceIds: List<String>?,
        iconId: String?,
        gameStageRank: Int,
    ) {
        @get:JvmName("donationId") val donationId: String = donationId?.uppercase() ?: ""
        @get:JvmName("sort") val sort: MuseumSort = sort ?: MuseumSort.SPECIAL
        @get:JvmName("donationXp") val donationXp: Int = maxOf(0, donationXp)
        @get:JvmName("pieceIds") val pieceIds: List<String> = if (pieceIds.isNullOrEmpty()) listOf(this.donationId) else pieceIds.toList()
        @get:JvmName("iconId") val iconId: String = if (iconId.isNullOrBlank()) this.donationId else iconId.uppercase()
        @get:JvmName("gameStageRank") val gameStageRank: Int = maxOf(0, gameStageRank)
    }

    /**
     * @param stack display/NBT stack from the museum API (may be the higher-tier item)
     * @param coveredBy if non-blank, this slot is filled because `coveredBy` was donated
     */
    class ResolvedDonation(@get:JvmName("stack") val stack: InventoryDecoder.Stack, coveredBy: String?) {
        @get:JvmName("coveredBy") val coveredBy: String? = if (coveredBy.isNullOrBlank()) null else coveredBy.uppercase()

        fun direct(): Boolean = coveredBy == null
    }

    private class Mutable(val id: String, var sort: MuseumSort, val armorSet: Boolean) {
        var donationXp = 0
        var gameStageRank = 0
        val pieces = LinkedHashSet<String>()
    }

    @Volatile
    private var BY_SORT: Map<MuseumSort, List<String>> = emptyMap()

    @Volatile
    private var BY_ID: Map<String, Entry> = emptyMap()

    @Volatile
    private var CHILDREN: Map<String, List<String>> = emptyMap()

    @Volatile
    private var VARIANT_TO_BASE: Map<String, String> = emptyMap()

    @Volatile
    private var PIECE_TO_SET: Map<String, String> = emptyMap()

    @JvmStatic
    fun ensureBuilt() {
        if (BY_ID.isNotEmpty()) return
        rebuild()
    }

    @JvmStatic
    fun invalidate() {
        BY_SORT = emptyMap()
        BY_ID = emptyMap()
        CHILDREN = emptyMap()
        VARIANT_TO_BASE = emptyMap()
        PIECE_TO_SET = emptyMap()
    }

    @JvmStatic
    fun rebuild() {
        rebuild(HypixelItemsCache.allItems())
    }

    @JvmStatic
    fun rebuild(items: Collection<JsonObject?>) {
        val buckets = EnumMap<MuseumSort, LinkedHashSet<String>>(MuseumSort::class.java)
        for (sort in MuseumSort.categories()) buckets[sort] = LinkedHashSet()
        val building = LinkedHashMap<String, Mutable>()
        val upgradeTo = LinkedHashMap<String, String>()
        val variantToBase = LinkedHashMap<String, String>()

        for (item in items) {
            if (item == null || !item.has("museum_data") || !item.get("museum_data").isJsonObject) continue
            val museum = item.getAsJsonObject("museum_data")
            val sort = sortOfCategory(str(museum, "category")) ?: continue
            val itemId = str(item, "id").uppercase()
            if (itemId.isBlank()) continue
            val stageRank = gameStageRank(str(museum, "game_stage"))
            ingestParents(museum, upgradeTo)
            ingestMapped(museum, itemId, variantToBase)

            if (museum.has("armor_set_donation_xp") && museum.get("armor_set_donation_xp").isJsonObject) {
                for ((setId, value) in museum.getAsJsonObject("armor_set_donation_xp").entrySet()) {
                    if (setId.isNullOrBlank()) continue
                    val key = setId.uppercase()
                    val xp = if (value != null && value.isJsonPrimitive) softInt(value) else 0
                    val m = building.getOrPut(key) { Mutable(key, sort, true) }
                    m.sort = sort
                    m.gameStageRank = maxOf(m.gameStageRank, stageRank)
                    if (xp > 0) m.donationXp = xp
                    m.pieces.add(itemId)
                    buckets[sort]?.add(key)
                }
                continue
            }

            val xp = if (museum.has("donation_xp") && museum.get("donation_xp").isJsonPrimitive) softInt(museum.get("donation_xp")) else 0
            val m = building.getOrPut(itemId) { Mutable(itemId, sort, false) }
            m.sort = sort
            m.gameStageRank = maxOf(m.gameStageRank, stageRank)
            m.donationXp = maxOf(m.donationXp, xp)
            m.pieces.add(itemId)
            buckets[sort]?.add(itemId)
        }

        val byId = HashMap<String, Entry>()
        for (m in building.values) {
            val pieces = m.pieces.sorted()
            byId[m.id] = Entry(m.id, m.sort, m.donationXp, m.armorSet, pieces, pickIconId(pieces, m.id), m.gameStageRank)
        }

        val out = EnumMap<MuseumSort, List<String>>(MuseumSort::class.java)
        for (sort in MuseumSort.categories()) {
            out[sort] = (buckets[sort] ?: LinkedHashSet()).sortedWith { a, b -> compareProgression(byId[a], byId[b], a, b) }
        }

        val pieceToSet = LinkedHashMap<String, String>()
        for (m in building.values) {
            if (m.armorSet) {
                for (piece in m.pieces) pieceToSet.putIfAbsent(piece, m.id)
            }
        }

        BY_SORT = out.toMap()
        BY_ID = byId.toMap()
        CHILDREN = childrenOf(upgradeTo, pieceToSet).toMap()
        VARIANT_TO_BASE = variantToBase.toMap()
        PIECE_TO_SET = pieceToSet.toMap()
    }

    private fun softInt(el: JsonElement): Int = try {
        el.asInt
    } catch (_: IllegalStateException) {
        0
    } catch (_: ClassCastException) {
        0
    } catch (_: NumberFormatException) {
        0
    } catch (_: UnsupportedOperationException) {
        0
    }

    @JvmStatic
    fun childrenOf(upgradeTo: Map<String, String>, pieceToSet: Map<String, String>): Map<String, List<String>> {
        val children = LinkedHashMap<String, MutableList<String>>()
        for ((from, to) in upgradeTo) {
            val lower = pieceToSet[from] ?: from
            val higher = pieceToSet[to] ?: to
            if (lower == higher) continue
            children.getOrPut(higher) { ArrayList() }.add(lower)
        }
        return children.mapValuesTo(LinkedHashMap()) { (_, v) -> v.distinct() }
    }

    /**
     * Item id whose price counts toward donated value, or null when the slot is only filled because
     * a higher tier was donated (that donor's price is already counted on its own slot).
     */
    @JvmStatic
    fun valueId(donationId: String?, coveredBy: String?, stack: InventoryDecoder.Stack?): String? {
        if (!coveredBy.isNullOrBlank()) return null
        if (donationId != null && donationId.uppercase().startsWith("SPECIAL:")) {
            return if (stack == null || stack.id.isBlank()) null else stack.id
        }
        return donationId
    }

    @JvmStatic
    fun resolveDonations(raw: Map<String, InventoryDecoder.Stack?>?): Map<String, ResolvedDonation> {
        ensureBuilt()
        val out = LinkedHashMap<String, ResolvedDonation>()
        if (raw.isNullOrEmpty()) return out
        val direct = LinkedHashMap<String, InventoryDecoder.Stack>()
        for ((rawKey, value) in raw) {
            if (rawKey.isNullOrBlank() || value == null) continue
            val key = rawKey.uppercase()
            if (key.startsWith("SPECIAL:")) {
                out[key] = ResolvedDonation(value, null)
                continue
            }
            val slot = canonicalSlotId(key)
            if (!BY_ID.containsKey(slot) && PIECE_TO_SET.containsKey(slot)) {
                // Pieces donated before Hypixel grouped them into a set keep their own key (e.g. PESTHUNTERS_BELT
                // next to PESTHUNTER). They aren't a museum slot and don't donate the set, so they're not Special either.
                continue
            }
            direct.putIfAbsent(slot, value)
        }
        for ((slot, stack) in direct) out[slot] = ResolvedDonation(stack, null)
        for ((donor, stack) in direct) {
            for (child in descendants(donor)) out.putIfAbsent(child, ResolvedDonation(stack, donor))
        }
        return out
    }

    @JvmStatic
    fun canonicalSlotId(rawId: String?): String {
        ensureBuilt()
        if (rawId.isNullOrBlank()) return ""
        val key = rawId.uppercase()
        val base = VARIANT_TO_BASE[key]
        return if (base.isNullOrBlank()) key else base
    }

    private fun descendants(donorId: String?): List<String> {
        if (donorId.isNullOrBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        val queue = arrayListOf(donorId.uppercase())
        var i = 0
        while (i < queue.size) {
            for (kid in CHILDREN[queue[i]] ?: emptyList()) {
                if (out.add(kid)) queue.add(kid)
            }
            i++
        }
        return out.toList()
    }

    private fun ingestParents(museum: JsonObject?, upgradeTo: MutableMap<String, String>) {
        if (museum == null || !museum.has("parent") || !museum.get("parent").isJsonObject) return
        for ((from, value) in museum.getAsJsonObject("parent").entrySet()) {
            if (from.isNullOrBlank() || value == null || !value.isJsonPrimitive) continue
            val to = softString(value)
            if (to.isNullOrBlank()) continue
            upgradeTo[from.uppercase()] = to.uppercase()
        }
    }

    private fun ingestMapped(museum: JsonObject?, baseId: String?, variantToBase: MutableMap<String, String>) {
        if (museum == null || baseId == null || !museum.has("mapped_item_ids") || !museum.get("mapped_item_ids").isJsonArray) return
        for (el in museum.getAsJsonArray("mapped_item_ids")) {
            if (el == null || !el.isJsonPrimitive) continue
            val variant = softString(el)
            if (variant.isNullOrBlank()) continue
            variantToBase[variant.uppercase()] = baseId
        }
    }

    private fun softString(el: JsonElement): String? = try {
        el.asString
    } catch (_: IllegalStateException) {
        null
    } catch (_: ClassCastException) {
        null
    } catch (_: NumberFormatException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }

    @JvmStatic
    fun donationIds(sort: MuseumSort?): List<String> {
        ensureBuilt()
        if (sort == null) return emptyList()
        if (sort.isAll()) return MuseumSort.categories().flatMap { BY_SORT[it] ?: emptyList() }
        return BY_SORT[sort] ?: emptyList()
    }

    @JvmStatic
    fun entry(donationId: String?): Entry? {
        ensureBuilt()
        if (donationId.isNullOrBlank()) return null
        return BY_ID[donationId.uppercase()]
    }

    @JvmStatic
    fun sortOfDonationId(id: String?): MuseumSort = entry(id)?.sort ?: MuseumSort.SPECIAL

    @JvmStatic
    fun isCataloged(id: String?): Boolean = entry(id) != null

    @JvmStatic
    fun iconId(donationId: String?): String = entry(donationId)?.iconId ?: donationId?.uppercase() ?: ""

    @JvmStatic
    fun donationXp(donationId: String?): Int = entry(donationId)?.donationXp ?: 0

    @JvmStatic
    fun marketPrice(donationId: String?): Double {
        val e = entry(donationId) ?: return ItemPricer.price(donationId)
        if (!e.armorSet) return ItemPricer.price(e.donationId)
        return e.pieceIds.sumOf { ItemPricer.price(it) }
    }

    @JvmStatic
    fun displayName(donationId: String?): String {
        val e = entry(donationId)
        val look = e?.iconId ?: donationId ?: ""
        val def = HypixelItemsCache.get(look)
        if (def != null && def.has("name") && def.get("name").isJsonPrimitive) {
            val stripped = stripFormatting(def.get("name").asString)
            if (stripped.isNotBlank()) return stripped
        }
        val plain: String? = SkyBlockItemFactory.plainDisplayName(look)
        if (!plain.isNullOrBlank()) return plain
        if (e != null && e.armorSet) return prettyId(e.donationId) + " Armor"
        return prettyId(look)
    }

    private fun pickIconId(pieces: List<String>?, fallback: String): String {
        if (pieces.isNullOrEmpty()) return fallback
        var best = pieces[0]
        var bestScore = scorePiece(best)
        for (i in 1 until pieces.size) {
            val score = scorePiece(pieces[i])
            if (score > bestScore) {
                best = pieces[i]
                bestScore = score
            }
        }
        return best
    }

    private fun scorePiece(id: String?): Int {
        if (id == null) return 0
        val u = id.uppercase()
        return when {
            u.endsWith("_HELMET") || u.endsWith("_HAT") -> 5
            u.endsWith("_CHESTPLATE") || u.endsWith("_CLOAK") -> 4
            u.endsWith("_LEGGINGS") || u.endsWith("_BELT") -> 3
            u.endsWith("_BOOTS") || u.endsWith("_NECKLACE") || u.endsWith("_GLOVES") -> 2
            else -> 1
        }
    }

    private fun sortOfCategory(category: String?): MuseumSort? {
        if (category.isNullOrBlank()) return null
        return when (category.trim().uppercase()) {
            "COMBAT" -> MuseumSort.COMBAT
            "MINING" -> MuseumSort.MINING
            "FORAGING" -> MuseumSort.FORAGING
            "FARMING" -> MuseumSort.FARMING
            "FISHING" -> MuseumSort.FISHING
            "DUNGEONEERING" -> MuseumSort.DUNGEONEERING
            "HUNTING" -> MuseumSort.HUNTING
            "SPECIAL" -> MuseumSort.SPECIAL
            else -> null
        }
    }

    private fun gameStageRank(stage: String?): Int {
        if (stage.isNullOrBlank()) return 99
        return GAME_STAGE_RANK[stage.trim().uppercase()] ?: 99
    }

    private fun compareProgression(a: Entry?, b: Entry?, idA: String?, idB: String?): Int {
        val rankA = a?.gameStageRank ?: 99
        val rankB = b?.gameStageRank ?: 99
        if (rankA != rankB) return rankA.compareTo(rankB)
        val xpA = a?.donationXp ?: 0
        val xpB = b?.donationXp ?: 0
        if (xpA != xpB) return xpA.compareTo(xpB)
        return String.CASE_INSENSITIVE_ORDER.compare(idA ?: "", idB ?: "")
    }

    private fun str(obj: JsonObject?, key: String): String {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) return ""
        return softString(obj.get(key)) ?: ""
    }

    private fun prettyId(id: String?): String {
        if (id.isNullOrBlank()) return ""
        return id.lowercase().split(UNDERSCORES).filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
    }

    private fun stripFormatting(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = raw.replace(HYPIXEL_COLOR, "")
        val out = StringBuilder(cleaned.length)
        var i = 0
        while (i < cleaned.length) {
            val c = cleaned[i]
            if ((c == '§' || c == '&') && i + 1 < cleaned.length) {
                i += 2
                continue
            }
            out.append(c)
            i++
        }
        return out.toString().trim()
    }
}
