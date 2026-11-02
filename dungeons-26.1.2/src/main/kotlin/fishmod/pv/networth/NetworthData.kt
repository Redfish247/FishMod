/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import net.minecraft.client.Minecraft

object NetworthData {
    // applicationWorth is published last so a non-null read implies the rest are loaded.
    @Volatile
    private var applicationWorth: JsonObject? = null
    @Volatile
    private var pets: JsonObject = JsonObject()
    @Volatile
    private var misc: JsonObject = JsonObject()
    @Volatile
    private var reforges: Map<String, String> = emptyMap()

    @JvmStatic
    fun ensureLoaded() {
        if (applicationWorth != null) return
        synchronized(this) {
            if (applicationWorth == null) loadAll()
        }
    }

    private fun loaded(): JsonObject {
        ensureLoaded()
        return applicationWorth!!
    }

    private fun loadAll() {
        val awRoot = load("data/networth/application_worth.json")
        val aw = awRoot?.get("APPLICATION_WORTH")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        pets = load("data/networth/pets.json") ?: JsonObject()
        misc = load("data/networth/misc.json") ?: JsonObject()
        val map = HashMap<String, String>()
        load("data/networth/reforges.json")?.entrySet()?.forEach { (key, value) ->
            if (value.isJsonPrimitive) map[key] = value.asString
        }
        reforges = map.toMap()
        applicationWorth = aw
    }

    @JvmStatic
    fun worth(key: String, fallback: Double): Double {
        val el = loaded().get(key)
        return if (el != null && el.isJsonPrimitive) el.asDouble else fallback
    }

    private fun miscList(key: String): MutableList<String> {
        ensureLoaded()
        return stringList(misc.get(key))
    }

    @JvmStatic
    fun masterStars(): List<String> = miscList("MASTER_STARS")

    @JvmStatic
    fun enrichments(): List<String> = miscList("ENRICHMENTS")

    @JvmStatic
    fun gemstoneSlots(): List<String> = miscList("GEMSTONE_SLOTS")

    @JvmStatic
    fun stackingEnchants(): List<String> = miscList("STACKING_ENCHANTMENTS")

    @JvmStatic
    fun ignoreSilex(): List<String> = miscList("IGNORE_SILEX")

    @JvmStatic
    fun allowedRecombCategories(): List<String> = miscList("ALLOWED_RECOMBOBULATED_CATEGORIES")

    @JvmStatic
    fun allowedRecombIds(): List<String> = miscList("ALLOWED_RECOMBOBULATED_IDS")

    @JvmStatic
    fun blockedCandyPets(): Set<String> {
        ensureLoaded()
        return HashSet(stringList(pets.get("BLOCKED_CANDY_REDUCE_PETS")))
    }

    @JvmStatic
    fun soulboundPets(): Set<String> {
        ensureLoaded()
        return HashSet(stringList(pets.get("SOULBOUND_PETS")))
    }

    @JvmStatic
    fun specialLevels(): Map<String, Int> {
        ensureLoaded()
        return intMap(pets, "SPECIAL_LEVELS")
    }

    @JvmStatic
    fun rarityOffset(): Map<String, Int> {
        ensureLoaded()
        return intMap(pets, "RARITY_OFFSET")
    }

    @JvmStatic
    fun petLevels(): List<Int> {
        ensureLoaded()
        val arr = if (pets.has("LEVELS")) pets.getAsJsonArray("LEVELS") else null
        return arr?.mapTo(ArrayList()) { it.asInt } ?: ArrayList()
    }

    @JvmStatic
    fun tiers(): List<String> {
        ensureLoaded()
        return stringList(pets.get("TIERS"))
    }

    @JvmStatic
    fun reforgeItem(modifier: String?): String? {
        ensureLoaded()
        return reforges[modifier]
    }

    @JvmStatic
    fun ignoredEnchants(): Map<String, Int> {
        ensureLoaded()
        return intMap(misc, "IGNORED_ENCHANTMENTS")
    }

    @JvmStatic
    fun blockedEnchants(): Map<String, List<String>> {
        ensureLoaded()
        val out = HashMap<String, List<String>>()
        val obj = if (misc.has("BLOCKED_ENCHANTMENTS")) misc.getAsJsonObject("BLOCKED_ENCHANTMENTS") else null
        obj?.entrySet()?.forEach { (key, value) -> out[key] = stringList(value) }
        return out
    }

    private fun intMap(root: JsonObject, key: String): MutableMap<String, Int> {
        val out = HashMap<String, Int>()
        val obj = if (root.has(key)) root.getAsJsonObject(key) else null
        obj?.entrySet()?.forEach { (k, value) ->
            if (value.isJsonPrimitive) out[k] = value.asInt
        }
        return out
    }

    private fun stringList(element: JsonElement?): MutableList<String> {
        val out = ArrayList<String>()
        if (element == null || !element.isJsonArray) return out
        for (el in element.asJsonArray) {
            if (el.isJsonPrimitive) out.add(el.asString)
        }
        return out
    }

    private fun load(path: String): JsonObject? = try {
        var stream: InputStream? = NetworthData::class.java.classLoader.getResourceAsStream("assets/fishmod/pv/$path")
        if (stream == null) {
            val client: Minecraft? = Minecraft.getInstance()
            if (client != null) {
                val opt = client.resourceManager.getResource(ProfileViewer.id(path))
                if (opt.isPresent) stream = opt.get().open()
            }
        }
        stream?.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        }
    } catch (exception: Exception) {
        ProfileViewer.LOGGER.warn("Failed to load {}", path, exception)
        null
    }
}
