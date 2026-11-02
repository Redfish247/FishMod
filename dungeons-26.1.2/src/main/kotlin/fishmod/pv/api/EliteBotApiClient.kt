/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.ProfileViewer
import fishmod.pv.data.SoftDataFailure
import fishmod.utils.Http
import java.io.IOException
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Elite Farmers public API (`https://api.elitebot.dev`).
 * Used for Jacob contest history; credit in UI like SkyCofl.
 */
object EliteBotApiClient {
    private const val BASE = "https://api.elitebot.dev"
    private val TIMEOUT: Duration = Duration.ofSeconds(15)
    private const val SPACING_MS = 400L

    private val EXECUTOR: ExecutorService = Executors.newFixedThreadPool(2, PvExecutors.daemon("FishPV-EliteBot"))
    private var nextRequestAtMillis = 0L

    /** Contest participations for a profile (newest-first from API). */
    @JvmStatic
    fun contests(playerUuid: UUID?, profileId: String?): CompletableFuture<Optional<JsonArray>> {
        val url = url("contests", playerUuid, profileId) ?: return CompletableFuture.completedFuture(Optional.empty())
        return CompletableFuture.supplyAsync({ getArray(url) }, EXECUTOR)
    }

    /** Farming weight for a profile. */
    @JvmStatic
    fun weight(playerUuid: UUID?, profileId: String?): CompletableFuture<Optional<JsonObject>> {
        val url = url("weight", playerUuid, profileId) ?: return CompletableFuture.completedFuture(Optional.empty())
        return CompletableFuture.supplyAsync({ getObject(url) }, EXECUTOR)
    }

    private fun url(path: String, playerUuid: UUID?, profileId: String?): String? {
        if (playerUuid == null || profileId.isNullOrBlank()) return null
        val player = HypixelApiClient.undashed(playerUuid)
        val profile = profileId.trim().replace("-", "").lowercase()
        return "$BASE/$path/$player/$profile"
    }

    private fun getArray(url: String): Optional<JsonArray> =
        get(url).filter(JsonElement::isJsonArray).map(JsonElement::getAsJsonArray)

    private fun getObject(url: String): Optional<JsonObject> =
        get(url).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)

    private fun get(url: String): Optional<JsonElement> {
        waitForSlot()
        return try {
            Optional.of(Http.getJson(url, TIMEOUT))
        } catch (exception: IOException) {
            ProfileViewer.LOGGER.warn("Elite GET {} failed", url, exception)
            Optional.empty()
        } catch (exception: InterruptedException) {
            ProfileViewer.LOGGER.warn("Elite GET {} failed", url, exception)
            Thread.currentThread().interrupt()
            Optional.empty()
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            ProfileViewer.LOGGER.warn("Elite GET {} parse failed", url, exception)
            Optional.empty()
        }
    }

    @Synchronized
    private fun waitForSlot() {
        val wait = nextRequestAtMillis - System.currentTimeMillis()
        if (wait > 0L) {
            try {
                Thread.sleep(wait)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        nextRequestAtMillis = System.currentTimeMillis() + SPACING_MS
    }
}
