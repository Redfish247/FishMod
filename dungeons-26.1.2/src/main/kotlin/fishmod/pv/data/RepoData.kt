/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import net.minecraft.client.Minecraft
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object RepoData {
    @Volatile
    private var weight: JsonObject? = null

    @Volatile
    private var leveling: JsonObject? = null

    @Volatile
    private var dungeonsXp: JsonObject? = null

    @JvmStatic
    fun ensureLoaded() {
        if (weight != null && leveling != null && dungeonsXp != null) return
        synchronized(this) {
            if (weight == null || leveling == null) {
                weight = loadJson("data/weight.json")
                leveling = loadJson("data/leveling.json")
            }
            if (dungeonsXp == null) dungeonsXp = loadJson("data/dungeons_xp.json")
        }
    }

    @JvmStatic
    fun weight(): JsonObject? {
        ensureLoaded()
        return weight
    }

    @JvmStatic
    fun leveling(): JsonObject? {
        ensureLoaded()
        return leveling
    }

    @JvmStatic
    fun dungeonsXp(): JsonObject? {
        ensureLoaded()
        return dungeonsXp
    }

    @JvmStatic
    fun levelingXp(): JsonArray? = leveling()?.get("leveling_xp") as? JsonArray

    @JvmStatic
    fun catacombsXp(): JsonArray? {
        val root = leveling()
        if (root != null && root.has("catacombs") && root.get("catacombs").isJsonArray) return root.getAsJsonArray("catacombs")
        return levelingXp()
    }

    @JvmStatic
    fun slayerXp(slayer: String?): JsonArray? {
        val root = leveling()
        if (root == null || !root.has("slayer_xp") || !root.get("slayer_xp").isJsonObject) return null
        val map = root.getAsJsonObject("slayer_xp")
        return if (map.has(slayer) && map.get(slayer).isJsonArray) map.getAsJsonArray(slayer) else null
    }

    @JvmStatic
    fun slayerBossXp(slayer: String?): JsonArray? {
        val root = leveling() ?: return null
        if (root.has("slayer_boss_xp_type") && root.get("slayer_boss_xp_type").isJsonObject) {
            val byType = root.getAsJsonObject("slayer_boss_xp_type")
            if (slayer != null && byType.has(slayer) && byType.get(slayer).isJsonArray) return byType.getAsJsonArray(slayer)
        }
        return if (root.has("slayer_boss_xp") && root.get("slayer_boss_xp").isJsonArray) root.getAsJsonArray("slayer_boss_xp") else null
    }

    @JvmStatic
    fun slayerHighestTier(slayer: String?): Int {
        val root = leveling()
        if (root != null && root.has("slayer_to_highest_tier") && root.get("slayer_to_highest_tier").isJsonObject) {
            val map = root.getAsJsonObject("slayer_to_highest_tier")
            if (slayer != null && map.has(slayer) && map.get(slayer).isJsonPrimitive) {
                try {
                    return maxOf(1, map.get(slayer).asInt)
                } catch (_: IllegalStateException) {
                } catch (_: ClassCastException) {
                } catch (_: NumberFormatException) {
                } catch (_: UnsupportedOperationException) {
                }
            }
        }
        return 5
    }

    @JvmStatic
    fun skillCap(skill: String?): Int {
        val root = leveling()
        if (root != null && root.has("leveling_caps") && root.get("leveling_caps").isJsonObject) {
            val caps = root.getAsJsonObject("leveling_caps")
            val cap: Double? = if (skill == null) null else Leveling.numDouble(caps.get(skill))
            if (cap != null) return cap.toInt()
        }
        return 60
    }

    @JvmStatic
    fun path(root: JsonObject?, dotted: String?): JsonElement? {
        if (root == null || dotted.isNullOrBlank()) return null
        var current: JsonElement? = root
        for (part in dotted.split('.')) {
            if (current == null || !current.isJsonObject || !current.asJsonObject.has(part)) return null
            current = current.asJsonObject.get(part)
        }
        return current
    }

    private fun loadJson(path: String): JsonObject? = loadFromResourceManager(path) ?: loadFromClasspath(path)

    private fun loadFromResourceManager(path: String): JsonObject? {
        try {
            val client: Minecraft? = Minecraft.getInstance()
            if (client?.resourceManager == null) return null
            val resource = client.resourceManager.getResource(ProfileViewer.id(path))
            if (resource.isEmpty) return null
            InputStreamReader(resource.get().open(), StandardCharsets.UTF_8).use { reader ->
                val element = JsonParser.parseReader(reader)
                return if (element.isJsonObject) element.asJsonObject else null
            }
        } catch (_: IOException) {
            return null
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            return null
        }
    }

    private fun loadFromClasspath(path: String): JsonObject? {
        val classpath = ProfileViewer.ASSETS + path
        try {
            val stream = RepoData::class.java.getResourceAsStream(classpath)
            if (stream == null) {
                ProfileViewer.LOGGER.warn("Missing classpath resource {}", classpath)
                return null
            }
            InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                val element = JsonParser.parseReader(reader)
                return if (element.isJsonObject) element.asJsonObject else null
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
}
