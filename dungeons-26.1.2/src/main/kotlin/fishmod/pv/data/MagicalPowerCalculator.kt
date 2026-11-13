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
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.NbtAttrs
import fishmod.utils.TextStrip
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object MagicalPowerCalculator {
    private val MP_BY_TIER = mapOf(
        "COMMON" to 3,
        "UNCOMMON" to 5,
        "RARE" to 8,
        "EPIC" to 12,
        "LEGENDARY" to 16,
        "MYTHIC" to 22,
        "DIVINE" to 26,
        "SPECIAL" to 3,
        "VERY_SPECIAL" to 5,
    )
    private val RARITY_ORDER = listOf(
        "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL",
    )
    private val RECOMB_LADDER = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")

    private val LORE_RARITY = Regex("\\b(VERY SPECIAL|COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL)\\b")

    private class FamilyPos(val root: String, val rank: Int)

    private class Candidate(val id: String, val tier: String, val rank: Int, val rarityIndex: Int)

    @Volatile
    private var loaded = false
    private var familyById: Map<String, FamilyPos> = emptyMap()
    private var aliasToCanonical: Map<String, String> = emptyMap()

    @JvmStatic
    fun fromMember(member: JsonObject?): Int {
        if (member == null) return 0
        val stacks = InventoryDecoder.accessoryCandidates(member)
        return fromStacks(stacks, abiphoneContacts(member), riftPrismConsumed(member))
    }

    @JvmStatic
    fun fromStacks(stacks: List<InventoryDecoder.Stack?>?, abiphoneContacts: Int, riftPrismConsumed: Boolean): Int {
        ensureFamilies()
        if (stacks.isNullOrEmpty()) return if (riftPrismConsumed) 11 else 0

        val bestByFamily = LinkedHashMap<String, Candidate>()
        var hasAbicase = false
        var hasRiftPrismItem = false
        for (stack in stacks) {
            if (stack == null || stack.id.isBlank()) continue
            var id = stack.id.trim().uppercase()
            var tier = effectiveTier(id, stack.extraAttributes, stack.lore)
            if (isAbicase(id)) {
                hasAbicase = true
                id = "ABICASE"
                if (tier.isBlank()) tier = "RARE"
            }
            if (id == "RIFT_PRISM") {
                hasRiftPrismItem = true
                if (tier.isBlank()) tier = "RARE"
            }
            if (tier.isBlank()) tier = "COMMON"

            val family = familyKey(id)
            val next = Candidate(id, tier, familyRank(id), rarityIndex(tier))
            val prev = bestByFamily[family]
            if (prev == null || better(next, prev)) bestByFamily[family] = next
        }

        var total = bestByFamily.values.sumOf { magicalPowerFor(it.id, it.tier) }
        if (riftPrismConsumed && !hasRiftPrismItem) total += 11
        if (hasAbicase && abiphoneContacts > 0) total += abiphoneContacts / 2
        return maxOf(0, total)
    }

    @JvmStatic
    fun abiphoneContacts(member: JsonObject?): Int {
        if (member == null) return 0
        val nether = obj(member.get("nether_island_player_data"))
        val abiphone = nether?.let { obj(it.get("abiphone")) } ?: return 0
        if (abiphone.has("active_contacts") && abiphone.get("active_contacts").isJsonArray) {
            return abiphone.getAsJsonArray("active_contacts").size()
        }
        return obj(abiphone.get("contact_data"))?.size() ?: 0
    }

    @JvmStatic
    fun riftPrismConsumed(member: JsonObject?): Boolean {
        if (member == null) return false
        val rift = obj(member.get("rift"))
        val access = rift?.let { obj(it.get("access")) }
        if (access == null || !access.has("consumed_prism")) return false
        val el = access.get("consumed_prism")
        return el != null && el.isJsonPrimitive && el.asBoolean
    }

    @JvmStatic
    fun accessoryFamily(id: String?): String {
        ensureFamilies()
        return familyKey(id?.trim()?.uppercase() ?: "")
    }

    @JvmStatic
    fun accessoryFamilyRank(id: String?): Int {
        ensureFamilies()
        return familyRank(id?.trim()?.uppercase() ?: "")
    }

    @JvmStatic
    fun magicalPower(id: String?, tier: String?): Int {
        val normalized = SkyBlockItemFactory.normalizeTier(tier)
        return magicalPowerFor(id, normalized.ifBlank { "COMMON" })
    }

    private fun magicalPowerFor(id: String?, tier: String): Int {
        if (id == "RIFT_PRISM") return 11
        val base = MP_BY_TIER[tier] ?: 0
        return if (id == "HEGEMONY_ARTIFACT") base * 2 else base
    }

    private fun effectiveTier(id: String, ea: CompoundTag?, lore: List<String?>?): String {
        val loreTier = SkyBlockItemFactory.normalizeTier(tierFromLore(lore))
        if (loreTier.isNotBlank()) return loreTier
        var tier = SkyBlockItemFactory.normalizeTier(SkyBlockItemFactory.resolveTier(id))
        if (tier.isBlank()) return ""
        if (NbtAttrs.intValue(ea, "rarity_upgrades", 0) > 0) tier = bumpTier(tier)
        return tier
    }

    private fun bumpTier(tier: String): String {
        val idx = RECOMB_LADDER.indexOf(tier)
        if (idx >= 0 && idx + 1 < RECOMB_LADDER.size) return RECOMB_LADDER[idx + 1]
        return if (tier == "SPECIAL") "VERY_SPECIAL" else tier
    }

    private fun tierFromLore(lore: List<String?>?): String {
        if (lore.isNullOrEmpty()) return ""
        for (i in lore.indices.reversed()) {
            val line = lore[i]
            if (line.isNullOrBlank()) continue
            // Rarity line looks like "§d§l§ka§r §d§lMYTHIC ACCESSORY §d§l§ka". Match on the spaced text:
            // normalizeTier underscores it first, and "MYTHIC_ACCESSORY" never matches a word boundary.
            val plain = TextStrip.plain(line).uppercase()
            if (!plain.contains("ACCESSORY") && !plain.contains("HATCESSORY")) continue
            return LORE_RARITY.find(plain)?.groupValues?.get(1)?.replace(' ', '_') ?: ""
        }
        return ""
    }

    private fun isAbicase(id: String?): Boolean = id != null && id.uppercase().contains("ABICASE")

    private fun familyKey(id: String): String {
        val canonical = aliasToCanonical[id] ?: id
        familyById[canonical]?.let { return it.root }
        return if (isAbicase(canonical)) "ABICASE" else canonical
    }

    private fun familyRank(id: String): Int = familyById[aliasToCanonical[id] ?: id]?.rank ?: 0

    private fun rarityIndex(tier: String): Int = RARITY_ORDER.indexOf(tier)

    private fun better(a: Candidate, b: Candidate): Boolean =
        if (a.rank != b.rank) a.rank > b.rank else a.rarityIndex > b.rarityIndex

    private fun ensureFamilies() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loadFamilies()
            loaded = true
        }
    }

    private fun loadFamilies() {
        val root = loadJson("data/accessory_families.json")
        val byId = HashMap<String, FamilyPos>()
        val aliases = HashMap<String, String>()
        if (root != null && root.has("families") && root.get("families").isJsonArray) {
            for (familyEl in root.getAsJsonArray("families")) {
                if (familyEl == null || !familyEl.isJsonArray) continue
                val family = familyEl.asJsonArray
                var rootId: String? = null
                for (i in 0 until family.size()) {
                    val idEl = family.get(i)
                    if (idEl == null || !idEl.isJsonPrimitive) continue
                    val id = idEl.asString.trim().uppercase()
                    if (id.isBlank()) continue
                    if (rootId == null) rootId = id
                    byId[id] = FamilyPos(rootId, i)
                }
            }
        }
        if (root != null && root.has("aliases") && root.get("aliases").isJsonObject) {
            for ((key, value) in root.getAsJsonObject("aliases").entrySet()) {
                val canonical = key?.trim()?.uppercase() ?: ""
                if (canonical.isBlank() || value == null || !value.isJsonArray) continue
                val canonicalPos = byId[canonical]
                for (aliasEl in value.asJsonArray) {
                    if (aliasEl == null || !aliasEl.isJsonPrimitive) continue
                    val alias = aliasEl.asString.trim().uppercase()
                    if (alias.isBlank()) continue
                    aliases[alias] = canonical
                    if (canonicalPos != null && !byId.containsKey(alias)) byId[alias] = canonicalPos
                }
            }
        }
        familyById = byId.toMap()
        aliasToCanonical = aliases.toMap()
    }

    private fun loadJson(path: String): JsonObject? {
        try {
            val client: Minecraft? = Minecraft.getInstance()
            if (client?.resourceManager != null) {
                val optional = client.resourceManager.getResource(ProfileViewer.id(path))
                if (optional.isPresent) {
                    optional.get().open().use { stream ->
                        InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                            return JsonParser.parseReader(reader).asJsonObject
                        }
                    }
                }
            }
        } catch (exception: IOException) {
            ProfileViewer.LOGGER.warn("Failed loading {} from resource manager", path, exception)
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            ProfileViewer.LOGGER.warn("Failed loading {} from resource manager", path, exception)
        }
        try {
            val stream = MagicalPowerCalculator::class.java.getResourceAsStream("/assets/fishmod/pv/$path")
            if (stream == null) {
                ProfileViewer.LOGGER.warn("Missing classpath resource assets/fishmod/pv/{}", path)
                return null
            }
            stream.use {
                InputStreamReader(it, StandardCharsets.UTF_8).use { reader ->
                    return JsonParser.parseReader(reader).asJsonObject
                }
            }
        } catch (exception: IOException) {
            ProfileViewer.LOGGER.warn("Failed loading {}", path, exception)
            return null
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            ProfileViewer.LOGGER.warn("Failed loading {}", path, exception)
            return null
        }
    }

    private fun obj(element: JsonElement?): JsonObject? =
        if (element != null && element.isJsonObject) element.asJsonObject else null
}
