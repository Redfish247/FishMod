/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.utils.config.values.FishSettings
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.roundToInt

object PvConfig {
    private const val FILE_NAME = "config.json"
    private const val PROFILE_SCALE_KEY = "profileViewerScalePercent"
    @JvmField
    internal val REMOVED_TRIAL_KEYS = arrayOf("trialEssenceTab", "trialSkillOverviews", "trialFishingTrophies")
    private const val SEARCH_HISTORY_KEY = "searchHistory"

    @Volatile
    private var profileViewerScalePercent = 100

    @Volatile
    private var searchHistory = SearchHistory.empty()
    private var document = JsonObject()

    @JvmStatic
    @Synchronized
    fun load() {
        val path = configPath()
        if (Files.isRegularFile(path)) {
            try {
                val loaded = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).asJsonObject
                document = loaded
                if (loaded.has(PROFILE_SCALE_KEY) && loaded.get(PROFILE_SCALE_KEY).isJsonPrimitive) {
                    profileViewerScalePercent = normalizeProfileScale(loaded.get(PROFILE_SCALE_KEY).asInt)
                }
                searchHistory = readSearchHistory(loaded)
            } catch (exception: Exception) {
                ProfileViewer.LOGGER.warn("Could not load PV config", exception)
                document = JsonObject()
                profileViewerScalePercent = 100
                searchHistory = SearchHistory.empty()
            }
        }
        if (dropRemovedTrialKeys(document)) save()
        ProfileViewer.LOGGER.info("Hypixel: fishmod.dev proxy")
    }

    @JvmStatic
    fun profileViewerScalePercent(): Int = normalizeProfileScale(FishSettings.guiScaleProfileViewer)

    @JvmStatic
    fun fallbackHost(): String {
        val doc = document
        return if (doc.has("fallbackHost") && doc.get("fallbackHost").isJsonPrimitive) doc.get("fallbackHost").asString.trim() else ""
    }

    @JvmStatic
    @Synchronized
    fun setProfileViewerScalePercent(percent: Int) {
        val normalized = normalizeProfileScale(percent)
        if (normalized == profileViewerScalePercent) return
        profileViewerScalePercent = normalized
        document.addProperty(PROFILE_SCALE_KEY, normalized)
        save()
    }

    @JvmStatic
    fun searchHistory(): SearchHistory = searchHistory

    @JvmStatic
    @Synchronized
    fun recordSearch(uuid: UUID?, name: String?) {
        val next = searchHistory.record(uuid, name)
        if (next.entries() == searchHistory.entries()) return
        searchHistory = next
        writeSearchHistory(document, next)
        save()
    }

    @JvmStatic
    fun readSearchHistory(doc: JsonObject): SearchHistory = SearchHistory.fromJson(doc.get(SEARCH_HISTORY_KEY))

    @JvmStatic
    fun writeSearchHistory(doc: JsonObject, history: SearchHistory) {
        doc.add(SEARCH_HISTORY_KEY, history.toJson())
    }

    @JvmStatic
    fun dropRemovedTrialKeys(doc: JsonObject): Boolean {
        var removed = false
        for (key in REMOVED_TRIAL_KEYS) {
            removed = removed or (doc.remove(key) != null)
        }
        return removed
    }

    @JvmStatic
    fun normalizeProfileScale(percent: Int): Int {
        val clamped = percent.coerceIn(75, 150)
        return (clamped / 5.0f).roundToInt() * 5
    }

    private fun configPath(): Path = ProfileViewer.DIR.resolve(FILE_NAME)

    private fun save() {
        val path = configPath()
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        try {
            Files.createDirectories(path.parent)
            Files.writeString(temporary, document.toString(), StandardCharsets.UTF_8)
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: IOException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: IOException) {
            ProfileViewer.LOGGER.warn("Could not save PV config", exception)
            try {
                Files.deleteIfExists(temporary)
            } catch (_: IOException) {
            }
        }
    }
}
