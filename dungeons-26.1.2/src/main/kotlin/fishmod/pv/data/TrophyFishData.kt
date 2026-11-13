/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.utils.TextStrip
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

object TrophyFishData {
    enum class Tier {
        DIAMOND,
        GOLD,
        SILVER,
        BRONZE;

        fun key(): String = name.lowercase()

        fun suffix(): String = name

        companion object {
            @JvmStatic
            fun topToBottom(): Array<Tier> = arrayOf(DIAMOND, GOLD, SILVER, BRONZE)
        }
    }

    @JvmRecord
    data class Def(val id: String, val name: String) {
        fun iconId(tier: Tier): String = id + "_" + tier.suffix()
    }

    private val FALLBACK_ORDER = listOf(
        "SULPHUR_SKITTER", "OBFUSCATED_FISH_1", "OBFUSCATED_FISH_2", "OBFUSCATED_FISH_3",
        "GUSHER", "BLOBFISH", "STEAMING_HOT_FLOUNDER", "SLUGFISH", "FLYFISH", "LAVA_HORSE",
        "MANA_RAY", "VOLCANIC_STONEFISH", "VANILLE", "SKELETON_FISH", "MOLDFIN", "SOUL_FISH",
        "KARATE_FISH", "GOLDEN_FISH",
    )

    @Volatile
    private var loaded = false

    @Volatile
    private var defs: List<Def> = emptyList()

    @JvmStatic
    fun howtoFromLore(lore: List<String?>?): String? {
        if (lore == null) return null
        var first: String? = null
        for (raw in lore) {
            val plain = if (raw == null) "" else TextStrip.plain(raw).trim()
            if (plain.isBlank()) continue
            if (first == null) first = plain
            val lower = plain.lowercase()
            if (lower.startsWith("caught") || lower.contains("near ") || lower.contains("found ") ||
                lower.contains("obtain") || lower.startsWith("can be") || lower.contains("fished")
            ) {
                return plain
            }
        }
        return first
    }

    @JvmStatic
    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loadFromDisk()
            loaded = true
        }
    }

    @JvmStatic
    fun reload() {
        synchronized(this) {
            loaded = false
            loadFromDisk()
            loaded = true
        }
    }

    @JvmStatic
    fun all(): List<Def> {
        ensureLoaded()
        return defs
    }

    private fun loadFromDisk() {
        val path = ProfileViewer.DIR.resolve("neu-repo").resolve("repo").resolve("constants").resolve("trophyfish.json")
        val map = LinkedHashMap<String, Def>()
        if (Files.isRegularFile(path)) {
            try {
                Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                    val root = JsonParser.parseReader(reader)
                    if (root != null && root.isJsonObject) {
                        for (id in root.asJsonObject.keySet()) {
                            if (id.isNullOrBlank()) continue
                            val key = id.uppercase()
                            map[key] = Def(key, pretty(key))
                        }
                    }
                }
            } catch (ex: IOException) {
                ProfileViewer.LOGGER.warn("Failed to load trophyfish.json", ex)
            } catch (ex: RuntimeException) {
                if (!SoftDataFailure.isSoft(ex)) throw ex
                ProfileViewer.LOGGER.warn("Failed to load trophyfish.json", ex)
            }
        }
        defs = if (map.isNotEmpty()) map.values.toList() else FALLBACK_ORDER.map { Def(it, pretty(it)) }
    }

    private fun pretty(id: String?): String {
        if (id.isNullOrBlank()) return ""
        if (id.equals("LAVA_HORSE", ignoreCase = true)) return "Lavahorse"
        return id.replace('-', '_').split('_').filter { it.isNotEmpty() }
            .joinToString(" ") { it.first().uppercaseChar() + it.substring(1).lowercase() }
    }
}
