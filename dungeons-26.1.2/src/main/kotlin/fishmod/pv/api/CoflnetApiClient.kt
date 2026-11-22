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
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.pv.data.SoftDataFailure
import fishmod.utils.Http
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * SkyCofl public auction history (`https://sky.coflnet.com/api`).
 * No API key for player auctions/bids pages. Respect ~1 req/s pacing.
 */
object CoflnetApiClient {
    private const val BASE = "https://sky.coflnet.com/api"
    private val TIMEOUT: Duration = Duration.ofSeconds(12)
    private const val SPACING_MS = 350L

    private val HTTP: HttpClient = Http.CLIENT
    private val EXECUTOR: ExecutorService = Executors.newFixedThreadPool(2, PvExecutors.daemon("FishPV-Coflnet"))
    private var nextRequestAtMillis = 0L

    @JvmStatic
    fun auction(auctionId: String?): CompletableFuture<Optional<JsonObject>> {
        if (auctionId.isNullOrBlank()) return CompletableFuture.completedFuture(Optional.empty())
        val id = auctionId.replace("-", "").lowercase()
        val queuedNanos = System.nanoTime()
        return CompletableFuture.supplyAsync({ getObject("$BASE/auction/$id", queuedNanos) }, EXECUTOR)
    }

    /** Bazaar daily history for an item tag, newest first. Empty when unknown. */
    @JvmStatic
    fun bazaarHistory(tag: String): CompletableFuture<Optional<JsonArray>> {
        val queuedNanos = System.nanoTime()
        return CompletableFuture.supplyAsync({ getArray("$BASE/bazaar/${enc(tag)}/history", queuedNanos) }, EXECUTOR)
    }

    /** AH price history (min/avg/max/volume per hour or day). range = day | week | month | full. */
    @JvmStatic
    fun auctionHistory(tag: String, range: String, rarity: String? = null): CompletableFuture<Optional<JsonArray>> {
        val queuedNanos = System.nanoTime()
        return CompletableFuture.supplyAsync({ getArray("$BASE/item/price/${enc(tag)}/history/$range${rarityQuery(rarity)}", queuedNanos) }, EXECUTOR)
    }

    /** Active BIN auctions for an item tag, cheapest first. */
    @JvmStatic
    fun activeBins(tag: String, rarity: String? = null): CompletableFuture<Optional<JsonArray>> {
        val queuedNanos = System.nanoTime()
        return CompletableFuture.supplyAsync({ getArray("$BASE/auctions/tag/${enc(tag)}/active/bin${rarityQuery(rarity)}", queuedNanos) }, EXECUTOR)
    }

    private fun rarityQuery(rarity: String?): String = if (rarity.isNullOrEmpty()) "" else "?Rarity=${enc(rarity)}"

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")

    private fun getArray(url: String, queuedNanos: Long): Optional<JsonArray> =
        getJson(url, queuedNanos).filter(JsonElement::isJsonArray).map(JsonElement::getAsJsonArray)

    private fun getObject(url: String, queuedNanos: Long): Optional<JsonObject> =
        getJson(url, queuedNanos).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)

    private fun getJson(url: String, queuedNanos: Long): Optional<JsonElement> {
        val dequeuedNanos = System.nanoTime()
        waitForSlot()
        val sentNanos = System.nanoTime()
        try {
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build()
            val response = HTTP.send(request, HttpResponse.BodyHandlers.ofString())
            val body: String? = response.body()
            ProfileViewer.LOGGER.info(
                "[PV timing] Coflnet {} {}ms queue={}ms spacing={}ms http={}ms status={} chars={}",
                url.substring(BASE.length),
                (System.nanoTime() - queuedNanos) / 1_000_000L,
                (dequeuedNanos - queuedNanos) / 1_000_000L,
                (sentNanos - dequeuedNanos) / 1_000_000L,
                (System.nanoTime() - sentNanos) / 1_000_000L,
                response.statusCode(),
                body?.length ?: 0
            )
            if (response.statusCode() !in 200..299 || body.isNullOrBlank()) {
                ProfileViewer.LOGGER.warn("Coflnet {} → HTTP {}", url, response.statusCode())
                return Optional.empty()
            }
            return Optional.of(JsonParser.parseString(body))
        } catch (exception: IOException) {
            logFailure(url, sentNanos, exception)
            return Optional.empty()
        } catch (exception: InterruptedException) {
            logFailure(url, sentNanos, exception)
            Thread.currentThread().interrupt()
            return Optional.empty()
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            ProfileViewer.LOGGER.warn("Coflnet parse failed: {}", url, exception)
            return Optional.empty()
        }
    }

    private fun logFailure(url: String, sentNanos: Long, exception: Exception) {
        ProfileViewer.LOGGER.warn("Coflnet request failed after {}ms: {}", (System.nanoTime() - sentNanos) / 1_000_000L, url, exception)
    }

    @Synchronized
    private fun waitForSlot() {
        var now = System.currentTimeMillis()
        val wait = nextRequestAtMillis - now
        if (wait > 0L) {
            try {
                Thread.sleep(wait)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            now = System.currentTimeMillis()
        }
        nextRequestAtMillis = now + SPACING_MS
    }

    @JvmStatic
    fun objects(array: JsonArray?): MutableList<JsonObject> {
        val out = ArrayList<JsonObject>()
        array?.forEach { el -> if (el != null && el.isJsonObject) out.add(el.asJsonObject) }
        return out
    }
}
