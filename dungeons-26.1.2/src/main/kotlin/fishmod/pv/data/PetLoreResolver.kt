/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.pv.neu.NeuRepoCache
import fishmod.utils.TextStrip
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import kotlin.math.abs

/**
 * Resolves NEU pet lore placeholders (`{STRENGTH}`, `{0}`, etc.) using
 * `constants/petnums.json`, matching NotEnoughUpdates' interpolation.
 */
object PetLoreResolver {
    private val PLACEHOLDER = Regex("\\{([A-Za-z0-9_]+)\\}|<([A-Za-z0-9_]+)>|\\(([0-9]+)\\)")

    @Volatile
    private var petnumsCache: JsonObject? = null
    private val EMPTY_PETNUMS = JsonObject()
    private var petnumsRetryAt = 0L

    private val PET_TIERS = arrayOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL")

    @JvmRecord
    data class PetType(val type: String, val name: String, val tier: String, val neuId: String)

    @JvmStatic
    fun loreFor(pet: PetSnapshot.Entry?): List<String> {
        if (pet == null) return emptyList()
        val raw = neuLore(pet.neuId)
        val replacements = loreReplacements(pet.type, pet.tier, pet.level)
        val out = raw.mapTo(ArrayList(raw.size)) { apply(it, replacements, pet.level) }
        if (pet.hasHeldItem()) {
            out.add("")
            out.add("§7Held Item: §a" + prettyId(pet.heldItem))
        }
        if (pet.candyUsed > 0) out.add("§7Candy Used: §a" + pet.candyUsed)
        return out
    }

    @JvmStatic
    fun displayNameFor(pet: PetSnapshot.Entry?): String {
        if (pet == null) return ""
        val tierColor = tierColor(pet.tier)
        val neu = NeuRepoCache.get(pet.neuId)
        if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive) {
            val name: String? = neu.get("displayname").asString
            if (!name.isNullOrBlank()) return apply(name, emptyMap(), pet.level)
        }
        return "§7[Lvl " + pet.level + "] " + tierColor + pet.displayName
    }

    /** Every pet type in NEU petnums.json at its highest tier, sorted by name. */
    @JvmStatic
    fun petTypes(): List<PetType> {
        val out = ArrayList<PetType>()
        for ((type, value) in petnums().entrySet()) {
            if (!value.isJsonObject) continue
            val tiers = value.asJsonObject
            val best = PET_TIERS.indices.lastOrNull { tiers.has(PET_TIERS[it]) } ?: continue
            val neuId = "$type;$best"
            var name = prettyId(type)
            val neu = NeuRepoCache.get(neuId)
            if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive) {
                val raw = neu.get("displayname").asString
                val bracket = raw.indexOf("] ")
                val stripped = TextStrip.plain(if (bracket >= 0) raw.substring(bracket + 2) else raw).trim()
                if (stripped.isNotEmpty()) name = stripped
            }
            out.add(PetType(type, name, PET_TIERS[best], neuId))
        }
        out.sortBy { it.name.lowercase() }
        return out
    }

    private fun neuLore(neuId: String?): List<String> {
        val neu = NeuRepoCache.get(neuId)
        if (neu == null || !neu.has("lore") || !neu.get("lore").isJsonArray) return emptyList()
        return neu.getAsJsonArray("lore").filter { it != null && it.isJsonPrimitive }.map { it.asString ?: "" }
    }

    private fun loreReplacements(type: String?, tier: String?, level: Int): Map<String, String> {
        val out = HashMap<String, String>()
        val petnums = petnums()
        if (type.isNullOrBlank()) return out
        if (!petnums.has(type) || !petnums.get(type).isJsonObject) return out
        val byTier = petnums.getAsJsonObject(type)
        val tierKey = if (tier.isNullOrBlank()) "COMMON" else tier.uppercase()
        if (!byTier.has(tierKey) || !byTier.get(tierKey).isJsonObject) return out
        val tierObj = byTier.getAsJsonObject(tierKey)
        val numsLevel = numsLevelFor(tierObj, level)

        val keys = levelKeys(tierObj)
        if (keys.isEmpty()) return out
        var lo = keys.first()
        var hi = keys.last()
        for (key in keys) {
            if (key <= numsLevel) lo = key
            if (key >= numsLevel) {
                hi = key
                break
            }
        }
        val low = tierObj.getAsJsonObject(lo.toString())
        val high = tierObj.getAsJsonObject(hi.toString())
        val t = when {
            lo == hi || numsLevel <= lo -> 0.0
            numsLevel >= hi -> 1.0
            else -> (numsLevel - lo) / (hi - lo).toDouble()
        }

        val lowStats = readStatNums(low)
        val highStats = readStatNums(high)
        for (id in LinkedHashSet(lowStats.keys + highStats.keys)) {
            val a = lowStats[id] ?: 0.0
            val b = highStats[id] ?: 0.0
            out[id.uppercase()] = formatNum(a + (b - a) * t)
        }

        val lowOther = readOtherNums(low)
        val highOther = readOtherNums(high)
        for (i in 0 until maxOf(lowOther.size, highOther.size)) {
            val a = lowOther.getOrElse(i) { 0.0 }
            val b = highOther.getOrElse(i) { a }
            out[i.toString()] = formatNum(a + (b - a) * t)
        }
        return out
    }

    /** NEU `stats_levelling_curve` e.g. `101:200:1` maps pet level 101→1 … 200→100. */
    private fun numsLevelFor(tierObj: JsonObject?, petLevel: Int): Int {
        val level = maxOf(1, petLevel)
        if (tierObj != null && tierObj.has("stats_levelling_curve") && tierObj.get("stats_levelling_curve").isJsonPrimitive) {
            val parts = tierObj.get("stats_levelling_curve").asString.split(":")
            if (parts.size >= 2) {
                try {
                    val start = parts[0].trim().toInt()
                    val end = parts[1].trim().toInt()
                    val offset = if (parts.size >= 3) parts[2].trim().toInt() else 1
                    if (level >= start) {
                        val mapped = level - start + offset
                        val maxMapped = end - start + offset
                        return maxOf(offset, minOf(maxMapped, mapped))
                    }
                } catch (_: NumberFormatException) {
                }
            }
        }
        return minOf(100, level)
    }

    private fun levelKeys(tierObj: JsonObject): List<Int> =
        tierObj.entrySet().filter { it.value.isJsonObject }.mapNotNull { it.key.toIntOrNull() }.sorted()

    private fun readStatNums(node: JsonObject?): Map<String, Double> {
        val out = HashMap<String, Double>()
        if (node == null || !node.has("statNums") || !node.get("statNums").isJsonObject) return out
        for ((key, value) in node.getAsJsonObject("statNums").entrySet()) {
            if (!value.isJsonPrimitive) continue
            try {
                out[key.uppercase()] = value.asDouble
            } catch (_: IllegalStateException) {
            } catch (_: ClassCastException) {
            } catch (_: NumberFormatException) {
            } catch (_: UnsupportedOperationException) {
            }
        }
        return out
    }

    private fun readOtherNums(node: JsonObject?): List<Double> {
        val out = ArrayList<Double>()
        if (node == null || !node.has("otherNums") || !node.get("otherNums").isJsonArray) return out
        for (el in node.getAsJsonArray("otherNums")) {
            if (el == null || !el.isJsonPrimitive) continue
            out.add(
                try {
                    el.asDouble
                } catch (_: IllegalStateException) {
                    0.0
                } catch (_: ClassCastException) {
                    0.0
                } catch (_: NumberFormatException) {
                    0.0
                } catch (_: UnsupportedOperationException) {
                    0.0
                },
            )
        }
        return out
    }

    private fun apply(line: String?, replacements: Map<String, String>?, level: Int): String {
        if (line == null) return ""
        val out = line.replace("{LVL}", level.toString()).replace("{Lvl}", level.toString())
        if (replacements.isNullOrEmpty()) return out
        return PLACEHOLDER.replace(out) { m ->
            val key = m.groups[1]?.value ?: m.groups[2]?.value ?: m.groups[3]?.value
            val value = key?.let { replacements[it.uppercase()] ?: replacements[it] }
            value ?: m.value
        }
    }

    private fun formatNum(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        if (abs(rounded - Math.rint(rounded)) < 1e-9) return Math.rint(rounded).toLong().toString()
        return String.format(Locale.ROOT, "%.1f", rounded).removeSuffix(".0")
    }

    private fun tierColor(tier: String?): String = when (tier?.uppercase()) {
        "MYTHIC" -> "§d"
        "LEGENDARY" -> "§6"
        "EPIC" -> "§5"
        "RARE" -> "§9"
        "UNCOMMON" -> "§a"
        else -> "§f"
    }

    private fun prettyId(id: String?): String {
        if (id.isNullOrBlank()) return ""
        return id.replace('-', '_').split('_').filter { it.isNotBlank() }
            .joinToString(" ") { it.first().uppercaseChar() + it.substring(1).lowercase() }
    }

    private fun petnums(): JsonObject {
        petnumsCache?.let { return it }
        synchronized(this) {
            petnumsCache?.let { return it }
            // Repo may not be downloaded yet: retry later instead of caching empty forever.
            val now = System.currentTimeMillis()
            if (now < petnumsRetryAt) return EMPTY_PETNUMS
            val loaded = loadPetnums()
            if (loaded.size() == 0) {
                petnumsRetryAt = now + 30_000L
                return EMPTY_PETNUMS
            }
            petnumsCache = loaded
            return loaded
        }
    }

    private fun loadPetnums(): JsonObject {
        val candidates = listOf(
            ProfileViewer.DIR.resolve("neu-repo").resolve("repo").resolve("constants").resolve("petnums.json"),
            ProfileViewer.DIR.resolve("neu-repo").resolve("constants").resolve("petnums.json"),
        )
        for (path in candidates) {
            try {
                if (Files.isRegularFile(path)) {
                    Files.newBufferedReader(path).use { reader ->
                        val el = JsonParser.parseReader(reader)
                        if (el != null && el.isJsonObject) return el.asJsonObject
                    }
                }
            } catch (_: IOException) {
            } catch (_: JsonParseException) {
            } catch (_: IllegalStateException) {
            } catch (_: ClassCastException) {
            } catch (_: NumberFormatException) {
            } catch (_: UnsupportedOperationException) {
            }
        }
        return JsonObject()
    }
}
