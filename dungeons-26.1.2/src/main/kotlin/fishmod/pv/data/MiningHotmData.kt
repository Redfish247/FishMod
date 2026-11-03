/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Collections

/** NEU `hotmlayout.json` perk grid + Hypixel API id aliases. */
object MiningHotmData {
    @JvmRecord
    data class PerkDef(
        val id: String,
        val name: String,
        val x: Int,
        val y: Int,
        val maxLevel: Int,
        val powder: String,
        val ability: Boolean,
    )

    private val API_TO_LAYOUT = mapOf(
        "special_0" to "core_of_the_mountain",
        "forge_time" to "quick_forge",
        "pickaxe_toss" to "pickobulus",
        "mining_speed_2" to "speedy_mineman",
        "mining_fortune_2" to "fortunate_mineman",
        "fortunate" to "gem_lover",
        "mining_experience" to "seasoned_mineman",
        "daily_effect" to "sky_mall",
        "random_event" to "luck_of_the_cave",
        "hungry_for_more" to "dead_mans_chest",
        "warm_hearted" to "warm_heart",
    )

    @Volatile
    private var loaded = false
    private var perks: List<PerkDef> = emptyList()
    private var byId: Map<String, PerkDef> = emptyMap()
    private var maxX = 0
    private var maxY = 0

    @JvmStatic
    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loadFromDisk()
            loaded = true
        }
    }

    /** Reload after NEU-REPO updates. */
    @JvmStatic
    fun reload() {
        synchronized(this) {
            loaded = false
            loadFromDisk()
            loaded = true
        }
    }

    @JvmStatic
    fun perks(): List<PerkDef> {
        ensureLoaded()
        return perks
    }

    @JvmStatic
    fun perk(id: String?): PerkDef? {
        ensureLoaded()
        if (id.isNullOrBlank()) return null
        return byId[id]
    }

    @JvmStatic
    fun maxX(): Int {
        ensureLoaded()
        return maxX
    }

    @JvmStatic
    fun maxY(): Int {
        ensureLoaded()
        return maxY
    }

    /** Map Hypixel `mining_core.nodes` key → NEU layout id. */
    @JvmStatic
    fun layoutId(apiId: String?): String {
        if (apiId.isNullOrBlank()) return ""
        return API_TO_LAYOUT[apiId] ?: apiId
    }

    @JvmStatic
    fun displayName(apiOrLayoutId: String?): String =
        perk(layoutId(apiOrLayoutId))?.name ?: titleCase(apiOrLayoutId ?: "")

    private fun loadFromDisk() {
        val path = ProfileViewer.DIR.resolve("neu-repo").resolve("repo").resolve("constants").resolve("hotmlayout.json")
        if (!Files.isRegularFile(path)) {
            ProfileViewer.LOGGER.debug("HOTM layout missing at {}", path)
            perks = emptyList()
            byId = emptyMap()
            return
        }
        try {
            Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                val root = JsonParser.parseReader(reader).asJsonObject
                val hotm = if (root.has("hotm") && root.get("hotm").isJsonObject) root.getAsJsonObject("hotm") else root
                val perkObj = (if (hotm.has("perks") && hotm.get("perks").isJsonObject) hotm.getAsJsonObject("perks") else null)
                    ?: return
                val map = LinkedHashMap<String, PerkDef>()
                val list = ArrayList<PerkDef>()
                var mx = 0
                var my = 0
                for ((id, value) in perkObj.entrySet()) {
                    if (!value.isJsonObject) continue
                    val o = value.asJsonObject
                    val name = stringOf(o, "name").ifBlank { titleCase(id) }
                    val x = intOf(o, "x")
                    val y = intOf(o, "y")
                    val maxLevel = maxOf(1, intOf(o, "maxLevel"))
                    var powder = stringOf(o, "powder").uppercase()
                    if (powder.contains(" ")) powder = ""
                    val ability = stringOf(o, "item").contains("(api")
                    val def = PerkDef(id, name, x, y, maxLevel, powder, ability)
                    map[id] = def
                    list.add(def)
                    mx = maxOf(mx, x)
                    my = maxOf(my, y)
                }
                list.sortWith(compareBy<PerkDef> { it.y }.thenBy { it.x })
                perks = Collections.unmodifiableList(list)
                byId = Collections.unmodifiableMap(map)
                maxX = mx
                maxY = my
                ProfileViewer.LOGGER.info("Loaded HOTM layout ({} perks)", list.size)
            }
        } catch (exception: IOException) {
            failLoad(exception)
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            failLoad(exception)
        }
    }

    private fun failLoad(exception: Exception) {
        ProfileViewer.LOGGER.warn("Failed loading hotmlayout.json", exception)
        perks = emptyList()
        byId = emptyMap()
    }

    private fun intOf(obj: JsonObject, key: String): Int = Leveling.num(obj.get(key))?.let { Math.round(it) } ?: 0

    private fun stringOf(obj: JsonObject, key: String): String {
        val el = obj.get(key)
        if (el == null || !el.isJsonPrimitive) return ""
        return try {
            el.asString
        } catch (_: IllegalStateException) {
            ""
        } catch (_: ClassCastException) {
            ""
        } catch (_: NumberFormatException) {
            ""
        } catch (_: UnsupportedOperationException) {
            ""
        }
    }

    private fun titleCase(id: String?): String {
        if (id.isNullOrBlank()) return ""
        return id.replace('-', '_').split('_').filter { it.isNotEmpty() }
            .joinToString(" ") { it.first().uppercaseChar() + it.substring(1).lowercase() }
    }
}
