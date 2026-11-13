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
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.pv.data.SoftDataFailure
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Locale
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException
import javax.net.ssl.SSLParameters

object HypixelApiClient {
    private const val WORKER_BASE = "https://fishmod.dev"

    private const val WORKER_FALLBACK_BASE = "https://fishmod.redfish2471.workers.dev"

    @Volatile
    private var fallbackSticky = false
    private val MOJANG_PROFILE: URI = URI.create("https://api.mojang.com/users/profiles/minecraft/")
    private val MOJANG_SESSION: URI = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/")
    private val TIMEOUT: Duration = Duration.ofSeconds(40)

    @JvmField
    @Volatile
    internal var lastFailure: String? = null
    private val UUID_LOOKUP_TIMEOUT: Duration = Duration.ofSeconds(5)
    private val SERVER_UUID_TIMEOUT: Duration = Duration.ofSeconds(8)
    private val DISK_UUID_RECHECK_MS = TimeUnit.DAYS.toMillis(1)
    private const val SPACING_MS = 80L
    private const val SERVER_UUID_CACHE = false
    private val CORRELATION_ID = Regex("[A-Za-z0-9._:-]{1,80}")

    private val HTTP: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    private val HTTP_TLS12: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .version(HttpClient.Version.HTTP_1_1)
        .sslParameters(SSLParameters().apply { protocols = arrayOf("TLSv1.2") })
        .build()
    private val HTTP_H1: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    private val PLAYER_CACHE = ConcurrentHashMap<String, JsonObject>()
    private val PLAYER_CACHED_AT = ConcurrentHashMap<String, Long>()
    private val PLAYER_IN_FLIGHT = ConcurrentHashMap<String, CompletableFuture<Optional<JsonObject>>>()
    private val PROFILE_IN_FLIGHT = InFlightRequestRegistry<String, Optional<JsonObject>>()

    /** Mojang names do not change during this client session often enough to justify repeat lookups. */
    private val UUID_NAME_CACHE = ConcurrentHashMap<String, UuidName>()

    private val EXECUTOR: ExecutorService = Executors.newFixedThreadPool(4, PvExecutors.daemon("FishPV-HypixelApi"))

    private val PARSE_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(2, PvExecutors.daemon("FishPV-ProfileParse"))

    /** Shared so auth, resources and /pv calls to api.vyriv.dev reuse one warm connection. */
    @JvmStatic
    fun http(): HttpClient = HTTP

    @JvmStatic
    fun parseExecutor(): ExecutorService = PARSE_EXECUTOR

    @JvmStatic
    fun networkExecutor(): ExecutorService = EXECUTOR

    private const val ELECTION_TTL_MS = 10L * 60L * 1000L

    @Volatile
    private var electionFuture: CompletableFuture<Optional<JsonObject>>? = null

    @Volatile
    private var electionFetchedAtMillis = 0L

    private val NEXT_REQUEST_AT_MILLIS = AtomicLong()

    @JvmStatic
    fun canFetch(): Boolean = true

    private enum class LookupStatus { FOUND, MISSING, FAILED }

    private class NameLookup(
        val status: LookupStatus,
        val found: UuidName?,
        val source: String?,
        val ageSeconds: Long,
        val ms: Long,
        val detail: String?
    ) {
        fun foundFor(requested: String): Boolean =
            status == LookupStatus.FOUND && found != null && requested.equals(found.name, ignoreCase = true)

        fun serverStale(): Boolean = source == "stale"

        fun describe(): String = status.name.lowercase(Locale.ROOT) +
            (if (source == null) "" else "/$source") +
            (if (detail == null) "" else "($detail)") +
            " ${ms}ms"

        companion object {
            fun failed(ms: Long, detail: String?) = NameLookup(LookupStatus.FAILED, null, null, 0L, ms, detail)
        }
    }

    @JvmStatic
    fun resolveUuid(name: String?): CompletableFuture<Optional<UuidName>> {
        val cleaned = name?.trim() ?: ""
        if (cleaned.isBlank()) return CompletableFuture.completedFuture(Optional.empty())
        val startedNanos = System.nanoTime()
        UUID_NAME_CACHE[cleaned.lowercase(Locale.ROOT)]?.let { cached ->
            logUuid(cleaned, "memory", startedNanos, "")
            return CompletableFuture.completedFuture(Optional.of(cached))
        }
        return CompletableFuture.supplyAsync({ NameUuidStore.get(cleaned) }, EXECUTOR).thenCompose { stored ->
            if (stored != null) {
                logUuid(cleaned, "disk", startedNanos, " savedAgo=" + (System.currentTimeMillis() - stored.savedAtMs) / 60_000L + "min")
                rememberUuid(stored.value, -1L)
                if (System.currentTimeMillis() - stored.savedAtMs > DISK_UUID_RECHECK_MS) {
                    recheckDiskUuid(cleaned, stored.value)
                }
                CompletableFuture.completedFuture(Optional.of(stored.value))
            } else {
                raceUuidLookups(cleaned, startedNanos)
            }
        }
    }

    // Shared server cache and Mojang race; Mojang is the authority, the server
    // entry wins only when fresh, and an older server entry is used only when
    // Mojang can't answer.
    private fun raceUuidLookups(cleaned: String, startedNanos: Long): CompletableFuture<Optional<UuidName>> {
        val server = CompletableFuture
            .supplyAsync({ lookupServerUuid(cleaned, false) }, EXECUTOR)
            .exceptionally { NameLookup.failed(-1L, "error") }
        val mojang = CompletableFuture
            .supplyAsync({ lookupMojangUuid(cleaned) }, EXECUTOR)
            .exceptionally { NameLookup.failed(-1L, "error") }
        val result = CompletableFuture<Optional<UuidName>>()
        val decide = Runnable { decideUuid(cleaned, startedNanos, server, mojang, result) }
        server.thenRun(decide)
        mojang.thenRun(decide)
        CompletableFuture.allOf(server, mojang).thenRun { reconcileUuid(cleaned, server.join(), mojang.join()) }
        return result
    }

    private fun decideUuid(
        cleaned: String,
        startedNanos: Long,
        serverFut: CompletableFuture<NameLookup>,
        mojangFut: CompletableFuture<NameLookup>,
        result: CompletableFuture<Optional<UuidName>>
    ) {
        synchronized(result) {
            if (result.isDone) return
            val server = serverFut.getNow(null)
            val mojang = mojangFut.getNow(null)
            val detail = " server=" + (server?.describe() ?: "pending") + " mojang=" + (mojang?.describe() ?: "pending")
            if (server != null && server.foundFor(cleaned) && !server.serverStale()) {
                val found = server.found!!
                rememberUuid(found, System.currentTimeMillis() - server.ageSeconds * 1000L)
                logUuid(cleaned, if (server.source == "mojang") "mojang via=server" else "server-cache", startedNanos, detail)
                result.complete(Optional.of(found))
                return
            }
            if (mojang == null) return
            if (mojang.status == LookupStatus.FOUND) {
                val found = mojang.found!!
                rememberUuid(found, System.currentTimeMillis())
                logUuid(cleaned, "mojang", startedNanos, detail)
                result.complete(Optional.of(found))
                return
            }
            if (mojang.status == LookupStatus.MISSING) {
                logUuid(cleaned, "not-found", startedNanos, detail)
                result.complete(Optional.empty())
                return
            }
            if (server == null) return
            if (server.foundFor(cleaned)) {
                // Not remembered: a fallback must not be extended into the caches.
                logUuid(cleaned, "stale-fallback", startedNanos, detail + " age=" + server.ageSeconds + "s")
                result.complete(Optional.of(server.found!!))
                return
            }
            logUuid(cleaned, "unavailable", startedNanos, detail)
            result.complete(Optional.empty())
        }
    }

    // Runs once both sides answered. A Mojang answer that contradicts the server
    // makes the server re-check Mojang itself; clients never write mappings.
    private fun reconcileUuid(cleaned: String, server: NameLookup, mojang: NameLookup) {
        if (mojang.status == LookupStatus.FOUND) {
            val found = mojang.found!!
            rememberUuid(found, System.currentTimeMillis())
            val serverCurrent = server.foundFor(cleaned) && !server.serverStale() && server.found!!.uuid == found.uuid
            if (!serverCurrent && server.status != LookupStatus.FAILED) {
                ProfileViewer.LOGGER.info("[PV timing] {} uuid server out of date ({}), asking it to re-check", cleaned, server.describe())
                refreshServerUuid(cleaned)
            }
        } else if (mojang.status == LookupStatus.MISSING) {
            forgetUuid(cleaned)
            if (server.status == LookupStatus.FOUND) {
                ProfileViewer.LOGGER.info("[PV timing] {} uuid gone on Mojang but server had it, asking it to re-check", cleaned)
                refreshServerUuid(cleaned)
            }
        }
    }

    private fun recheckDiskUuid(cleaned: String, stored: UuidName) {
        CompletableFuture.supplyAsync({ lookupMojangUuid(cleaned) }, EXECUTOR).thenAccept { mojang ->
            if (mojang.status == LookupStatus.FOUND) {
                val found = mojang.found!!
                if (found.uuid != stored.uuid) {
                    ProfileViewer.LOGGER.warn("[PV timing] {} uuid disk entry was out of date, replaced", cleaned)
                    refreshServerUuid(cleaned)
                }
                rememberUuid(found, System.currentTimeMillis())
            } else if (mojang.status == LookupStatus.MISSING) {
                ProfileViewer.LOGGER.warn("[PV timing] {} uuid disk entry no longer exists on Mojang, dropped", cleaned)
                forgetUuid(cleaned)
                refreshServerUuid(cleaned)
            }
        }
    }

    private fun refreshServerUuid(cleaned: String) {
        CompletableFuture.runAsync({ lookupServerUuid(cleaned, true) }, EXECUTOR)
    }

    private fun rememberUuid(uuidName: UuidName, savedAtMs: Long) {
        val key = uuidName.name.lowercase(Locale.ROOT)
        UUID_NAME_CACHE[key] = uuidName
        UUID_NAME_CACHE.entries.removeIf { it.value.uuid == uuidName.uuid && it.key != key }
        if (savedAtMs >= 0L) NameUuidStore.put(uuidName.name, uuidName.uuid, uuidName.name, savedAtMs)
    }

    private fun forgetUuid(cleaned: String) {
        UUID_NAME_CACHE.remove(cleaned.lowercase(Locale.ROOT))
        NameUuidStore.forget(cleaned)
    }

    private fun logUuid(name: String, source: String, startedNanos: Long, detail: String) {
        ProfileViewer.LOGGER.info("[PV timing] {} uuid {} {}ms{}", name, source, (System.nanoTime() - startedNanos) / 1_000_000L, detail)
    }

    private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000L

    private fun lookupMojangUuid(cleaned: String): NameLookup {
        val startedNanos = System.nanoTime()
        try {
            val request = HttpRequest.newBuilder(MOJANG_PROFILE.resolve(URLEncoder.encode(cleaned, StandardCharsets.UTF_8)))
                .timeout(UUID_LOOKUP_TIMEOUT).GET().build()
            val response = HTTP.send(request, HttpResponse.BodyHandlers.ofString())
            val ms = elapsedMs(startedNanos)
            if (response.statusCode() == 404 || response.statusCode() == 204) {
                return NameLookup(LookupStatus.MISSING, null, null, 0L, ms, null)
            }
            val body: String? = response.body()
            if (response.statusCode() != 200 || body.isNullOrBlank()) {
                return NameLookup.failed(ms, "status " + response.statusCode())
            }
            val root = JsonParser.parseString(body).asJsonObject
            val uuid = parseUndashedUuid(if (root.has("id")) root.get("id").asString else null)
            val resolved = if (root.has("name")) root.get("name").asString else null
            if (uuid == null || resolved == null || !cleaned.equals(resolved, ignoreCase = true)) {
                return NameLookup.failed(ms, "malformed")
            }
            return NameLookup(LookupStatus.FOUND, UuidName(uuid, resolved), null, 0L, ms, null)
        } catch (exception: IOException) {
            ProfileViewer.LOGGER.warn("Mojang UUID lookup failed for {}: {}", cleaned, exception.toString())
            return NameLookup.failed(elapsedMs(startedNanos), exception.javaClass.simpleName)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            ProfileViewer.LOGGER.warn("Mojang UUID lookup failed for {}: {}", cleaned, exception.toString())
            return NameLookup.failed(elapsedMs(startedNanos), exception.javaClass.simpleName)
        } catch (exception: RuntimeException) {
            ProfileViewer.LOGGER.warn("Mojang UUID lookup parse failed for {}", cleaned, exception)
            return NameLookup.failed(elapsedMs(startedNanos), "parse")
        }
    }

    private fun lookupServerUuid(cleaned: String, refresh: Boolean): NameLookup {
        val startedNanos = System.nanoTime()
        if (!SERVER_UUID_CACHE) return NameLookup.failed(0L, "no server uuid cache")
        try {
            val url = WORKER_BASE + "/hypixel/uuid/" + URLEncoder.encode(cleaned, StandardCharsets.UTF_8) + (if (refresh) "?refresh=1" else "")
            val uri = URI.create(url)
            val builder = HttpRequest.newBuilder(uri).timeout(SERVER_UUID_TIMEOUT).GET()
            if (!FishProxyAuth.applyAuthHeaders(builder, uri)) {
                return NameLookup.failed(elapsedMs(startedNanos), "auth")
            }
            val response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            val ms = elapsedMs(startedNanos)
            if (response.statusCode() == 404) return NameLookup(LookupStatus.MISSING, null, null, 0L, ms, null)
            val body: String? = response.body()
            if (response.statusCode() != 200 || body == null) return NameLookup.failed(ms, "status " + response.statusCode())
            val root = JsonParser.parseString(body).asJsonObject
            val uuid = parseUndashedUuid(if (root.has("uuid")) root.get("uuid").asString else null)
            val resolved = if (root.has("name")) root.get("name").asString else null
            val source = if (root.has("source")) root.get("source").asString else "cache"
            val age = if (root.has("age") && root.get("age").isJsonPrimitive) root.get("age").asLong else 0L
            if (uuid == null || resolved == null) return NameLookup.failed(ms, "malformed")
            return NameLookup(LookupStatus.FOUND, UuidName(uuid, resolved), source, maxOf(0L, age), ms, null)
        } catch (exception: IOException) {
            return NameLookup.failed(elapsedMs(startedNanos), exception.javaClass.simpleName)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            return NameLookup.failed(elapsedMs(startedNanos), exception.javaClass.simpleName)
        } catch (exception: RuntimeException) {
            ProfileViewer.LOGGER.warn("Server UUID lookup parse failed for {}", cleaned, exception)
            return NameLookup.failed(elapsedMs(startedNanos), "parse")
        }
    }

    @JvmStatic
    fun resolveName(uuid: UUID?): CompletableFuture<Optional<UuidName>> {
        if (uuid == null) return CompletableFuture.completedFuture(Optional.empty())
        val id = undashed(uuid)
        return CompletableFuture.supplyAsync({
            try {
                val request = HttpRequest.newBuilder(MOJANG_SESSION.resolve(id)).timeout(TIMEOUT).GET().build()
                val response = HTTP.send(request, HttpResponse.BodyHandlers.ofString())
                val body: String? = response.body()
                if (response.statusCode() !in 200..299 || body.isNullOrBlank()) return@supplyAsync Optional.empty()
                val root = JsonParser.parseString(body).asJsonObject
                val resolved = if (root.has("name")) root.get("name").asString else null
                if (resolved.isNullOrBlank()) return@supplyAsync Optional.empty()
                val uuidName = UuidName(uuid, resolved)
                rememberUuid(uuidName, -1L)
                NameUuidStore.forgetOtherNames(uuid, resolved)
                Optional.of(uuidName)
            } catch (exception: IOException) {
                ProfileViewer.LOGGER.debug("Mojang name lookup failed for {}", id, exception)
                Optional.empty()
            } catch (exception: InterruptedException) {
                ProfileViewer.LOGGER.debug("Mojang name lookup failed for {}", id, exception)
                Thread.currentThread().interrupt()
                Optional.empty()
            } catch (exception: RuntimeException) {
                if (!SoftDataFailure.isSoft(exception)) throw exception
                ProfileViewer.LOGGER.debug("Mojang name lookup parse failed for {}", id, exception)
                Optional.empty()
            }
        }, EXECUTOR)
    }

    @JvmStatic
    @JvmOverloads
    fun skyblockProfiles(uuid: UUID?, correlationId: String? = UUID.randomUUID().toString()): CompletableFuture<Optional<JsonObject>> {
        val id = undashed(uuid)
        val correlation = validCorrelationId(correlationId)
        val queuedNanos = System.nanoTime()
        val shared = PROFILE_IN_FLIGHT.share(id, correlation) {
            CompletableFuture.supplyAsync({
                val taskStartedNanos = System.nanoTime()
                fetchVyrivApi(
                    "$WORKER_BASE/hypixel/skyblock/profiles/$id",
                    "skyblock/profiles",
                    RequestTiming(correlation, queuedNanos, taskStartedNanos)
                )
            }, EXECUTOR)
        }
        if (shared.reused) {
            ProfileViewer.LOGGER.info(
                "[PV timing] event=profile_request_deduplicated correlation_id={} active_correlation_id={} player_uuid={}",
                correlation, shared.correlationId, id
            )
        }
        return shared.future
    }

    @JvmStatic
    @JvmOverloads
    fun skyblockMuseum(uuid: UUID?, profileId: String? = null): CompletableFuture<Optional<JsonObject>> {
        val id = undashed(uuid)
        var workerUrl = "$WORKER_BASE/hypixel/skyblock/museum/$id"
        if (!profileId.isNullOrBlank()) {
            val profile = profileId.trim().replace("-", "").lowercase(Locale.ROOT)
            workerUrl += "?profile=" + URLEncoder.encode(profile, StandardCharsets.UTF_8)
        }
        val finalWorkerUrl = workerUrl
        return CompletableFuture.supplyAsync({ fetchVyrivApi(finalWorkerUrl, "skyblock/museum") }, EXECUTOR)
    }

    @JvmStatic
    fun skyblockGarden(profileId: String?): CompletableFuture<Optional<JsonObject>> {
        if (profileId.isNullOrBlank()) return CompletableFuture.completedFuture(Optional.empty())
        val id = profileId.trim().replace("-", "").lowercase(Locale.ROOT)
        val encoded = URLEncoder.encode(id, StandardCharsets.UTF_8)
        return CompletableFuture.supplyAsync({
            fetchVyrivApi("$WORKER_BASE/hypixel/skyblock/garden/$encoded", "skyblock/garden").map { body ->
                if (body.has("garden") && body.get("garden").isJsonObject) body.getAsJsonObject("garden") else body
            }
        }, EXECUTOR)
    }

    /** Refetches when the cached player is older than `maxAgeMs` (lastLogin/lastLogout go stale). */
    @JvmStatic
    @JvmOverloads
    fun player(uuid: UUID?, maxAgeMs: Long = Long.MAX_VALUE): CompletableFuture<Optional<JsonObject>> {
        val id = undashed(uuid)
        val cached = PLAYER_CACHE[id]
        if (cached != null && System.currentTimeMillis() - PLAYER_CACHED_AT.getOrDefault(id, 0L) <= maxAgeMs) {
            return CompletableFuture.completedFuture(Optional.of(cached))
        }
        val created = CompletableFuture<Optional<JsonObject>>()
        PLAYER_IN_FLIGHT.putIfAbsent(id, created)?.let { return it }
        CompletableFuture.supplyAsync({
            fetchVyrivApi("$WORKER_BASE/hypixel/player/$id", "player").map { body ->
                val player = if (body.has("player") && body.get("player").isJsonObject) body.getAsJsonObject("player") else body
                val now = System.currentTimeMillis()
                PLAYER_CACHED_AT.entries.removeIf { e ->
                    if (now - e.value <= 3_600_000L) return@removeIf false
                    PLAYER_CACHE.remove(e.key)
                    true
                }
                PLAYER_CACHE[id] = player
                PLAYER_CACHED_AT[id] = now
                player
            }
        }, EXECUTOR).whenComplete { value, error ->
            PLAYER_IN_FLIGHT.remove(id, created)
            if (error != null) created.completeExceptionally(error) else created.complete(value)
        }
        return created
    }

    @JvmStatic
    fun guild(uuid: UUID?): CompletableFuture<Optional<JsonObject>> {
        val id = undashed(uuid)
        return CompletableFuture.supplyAsync({ fetchVyrivApi("$WORKER_BASE/hypixel/guild/$id", "guild") }, EXECUTOR)
    }

    @JvmStatic
    fun skyblockElection(): CompletableFuture<Optional<JsonObject>> {
        val now = System.currentTimeMillis()
        val cached = electionFuture
        if (cached != null && now - electionFetchedAtMillis < ELECTION_TTL_MS && !failed(cached)) return cached
        val next = CompletableFuture.supplyAsync({
            waitForSlot()
            getJson("$WORKER_BASE/hypixel/resources/skyblock/election")
        }, EXECUTOR)
        electionFuture = next
        electionFetchedAtMillis = now
        return next
    }

    @JvmStatic
    fun failed(future: CompletableFuture<out Optional<*>>?): Boolean {
        if (future == null || !future.isDone) return false
        if (future.isCompletedExceptionally) return true
        val value: Optional<*>? = future.join()
        return value == null || value.isEmpty
    }

    @JvmStatic
    fun skyblockBingoResources(): CompletableFuture<Optional<JsonObject>> = CompletableFuture.supplyAsync({
        waitForSlot()
        getJson("$WORKER_BASE/hypixel/resources/skyblock/bingo")
    }, EXECUTOR)

    @JvmStatic
    fun skyblockBingo(uuid: UUID?): CompletableFuture<Optional<JsonObject>> {
        val id = undashed(uuid)
        return CompletableFuture.supplyAsync({ fetchVyrivApi("$WORKER_BASE/hypixel/skyblock/bingo/$id", "skyblock/bingo") }, EXECUTOR)
    }

    private fun fetchVyrivApi(workerUrl: String, routeName: String): Optional<JsonObject> {
        val now = System.nanoTime()
        return fetchVyrivApi(workerUrl, routeName, RequestTiming(UUID.randomUUID().toString(), now, now))
    }

    private fun fetchVyrivApi(workerUrl: String, routeName: String, timing: RequestTiming): Optional<JsonObject> {
        waitForSlot()
        val attemptStarted = System.nanoTime()
        var viaWorker = getJson(workerUrl, true, true, timing, 0)
        if (viaWorker.isEmpty && System.nanoTime() - attemptStarted > TIMEOUT.toNanos() - 2_000_000_000L) {
            ProfileViewer.LOGGER.info("[PV] {} timed out, retrying once", routeName)
            viaWorker = getJson(workerUrl, true, true, timing, 0)
        }
        if (viaWorker.isPresent) return viaWorker
        ProfileViewer.LOGGER.warn("Vyriv Hypixel API unavailable for {}", routeName)
        return Optional.empty()
    }

    @JvmStatic
    internal fun rewriteUrl(url: String?): String? {
        val prefix = "$WORKER_BASE/hypixel/"
        if (url == null || !url.startsWith(prefix)) return url
        val rest = url.substring(prefix.length)
        val slash = rest.indexOf('/')
        if (rest.startsWith("resources/")) return "https://api.hypixel.net/v2/$rest"
        if (rest.startsWith("skyblock/")) {
            val sub = rest.substring("skyblock/".length)
            val s2 = sub.indexOf('/')
            val kind = if (s2 < 0) sub else sub.substring(0, s2)
            val id = if (s2 < 0) "" else sub.substring(s2 + 1)
            return when (kind) {
                "profiles" -> "$WORKER_BASE/skyblock/profiles?uuid=$id"
                "museum" -> {
                    val q = id.indexOf("?profile=")
                    "$WORKER_BASE/skyblock/museum?profile=" + (if (q >= 0) id.substring(q + "?profile=".length) else id)
                }
                "garden" -> "$WORKER_BASE/skyblock/garden?profile=$id"
                else -> "$WORKER_BASE/skyblock/$sub"
            }
        }
        val kind = if (slash < 0) rest else rest.substring(0, slash)
        val id = if (slash < 0) "" else rest.substring(slash + 1)
        return when (kind) {
            "player" -> "$WORKER_BASE/player?uuid=$id"
            "guild" -> "$WORKER_BASE/guild?player=$id"
            "status" -> "$WORKER_BASE/status?uuid=$id"
            else -> url
        }
    }

    private fun getJson(url: String): Optional<JsonObject> {
        val now = System.nanoTime()
        return getJson(url, true, true, RequestTiming(UUID.randomUUID().toString(), now, now), 0)
    }

    private fun isTransientNetwork(exception: IOException): Boolean =
        exception is SSLException ||
            exception is ConnectException ||
            exception is HttpConnectTimeoutException ||
            exception is ClosedChannelException

    // A streamed MISS is cut mid-body when the upstream read fails, so a broken transfer is retried once.
    private fun getJson(
        url: String,
        allowReauth: Boolean,
        allowStreamRetry: Boolean,
        timing: RequestTiming,
        netAttempt: Int
    ): Optional<JsonObject> {
        var cacheKey: String? = null
        try {
            val target = rewriteUrl(url)!!
            if (target.startsWith(WORKER_BASE) && !target.contains("/status")) {
                cacheKey = target
                val cached = ResponseCache.get(cacheKey)
                if (cached.isPresent) {
                    ProfileViewer.LOGGER.info("[PV cache] hit {}", target.substring(WORKER_BASE.length))
                    return cached
                }
            }
            val fallback = PvConfig.fallbackHost().ifBlank { WORKER_FALLBACK_BASE }
            val useFallback = (fallbackSticky || netAttempt >= 1) && fallback.isNotBlank() && target.startsWith(WORKER_BASE)
            val uri = URI.create(if (useFallback) fallback.replace(TRAILING_SLASHES, "") + target.substring(WORKER_BASE.length) else target)
            val builder = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET()
            val needsProxyAuth = target.startsWith(WORKER_BASE)
            if (needsProxyAuth && !FishProxyAuth.applyAuthHeaders(builder, uri)) {
                ProfileViewer.LOGGER.warn(
                    "Hypixel GET {} skipped: {}",
                    url,
                    FishProxyAuth.userFacingFailure().orElse("missing FishMod credentials")
                )
                FishProxyAuth.notifyPlayerIfNeeded()
                return Optional.empty()
            }
            if (needsProxyAuth) {
                builder.header("Accept-Encoding", "gzip")
                builder.setHeader("X-FishPV-Correlation-ID", timing.correlationId)
            }
            val startedNanos = System.nanoTime()
            val client = when (netAttempt) {
                0 -> HTTP
                1 -> HTTP_TLS12
                else -> HTTP_H1
            }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
            val headersNanos = System.nanoTime()
            val streamed = response.headers().firstValue("x-hypixel-stream").orElse("") == "1"
            val bodyRead = BodyReadTiming(response.body(), headersNanos)
            val bytes = try {
                bodyRead.use { it.readAllBytes() }
            } catch (exception: IOException) {
                logTiming(url, response, bodyRead.bytesRead(), timing, startedNanos, headersNanos, System.nanoTime(), 0L, 0L, bodyRead)
                if (streamed && allowStreamRetry) {
                    ProfileViewer.LOGGER.warn("Hypixel GET {} stream broke ({}), retrying once", url, exception.toString())
                    return getJson(url, allowReauth, false, RequestTiming.retry(timing.correlationId), 0)
                }
                throw exception
            }
            val bodyNanos = System.nanoTime()
            val status = response.statusCode()
            if (status == 401 && needsProxyAuth) {
                logTiming(url, response, bytes.size, timing, startedNanos, headersNanos, bodyNanos, 0L, 0L, bodyRead)
                FishProxyAuth.handleUnauthorized(
                    response.request(),
                    String(bytes, StandardCharsets.UTF_8),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
                )
                if (allowReauth) return getJson(url, false, allowStreamRetry, RequestTiming.retry(timing.correlationId), 0)
                lastFailure = "HTTP 401"
                ProfileViewer.LOGGER.warn("Hypixel GET {} unauthorized after re-auth", url)
                return Optional.empty()
            }
            if (status == 503 && needsProxyAuth) {
                logTiming(url, response, bytes.size, timing, startedNanos, headersNanos, bodyNanos, 0L, 0L, bodyRead)
                lastFailure = "HTTP 503"
                ProfileViewer.LOGGER.warn("Hypixel GET {} session auth unavailable (503)", url)
                return Optional.empty()
            }
            if (status !in 200..299 || bytes.isEmpty()) {
                logTiming(url, response, bytes.size, timing, startedNanos, headersNanos, bodyNanos, 0L, 0L, bodyRead)
                lastFailure = if (status in 200..299) "empty response" else "HTTP $status"
                ProfileViewer.LOGGER.warn("Hypixel GET {} failed status={}", url, status)
                return Optional.empty()
            }
            val element: JsonElement
            val parseStartedNanos = System.nanoTime()
            var gzipNanos = 0L
            try {
                var body: InputStream = ByteArrayInputStream(bytes)
                if (response.headers().firstValue("Content-Encoding").orElse("").equals("gzip", ignoreCase = true)) {
                    val gzipStartedNanos = System.nanoTime()
                    body = GZIPInputStream(body, 64 * 1024)
                    gzipNanos = System.nanoTime() - gzipStartedNanos
                }
                val timedBody = TimedInputStream(body)
                element = InputStreamReader(timedBody, StandardCharsets.UTF_8).use { JsonParser.parseReader(it) }
                if (body is GZIPInputStream) gzipNanos += timedBody.readNanos()
            } catch (exception: Exception) {
                if (exception !is IOException && exception !is JsonParseException) throw exception
                val parseNanos = System.nanoTime() - parseStartedNanos
                logTiming(url, response, bytes.size, timing, startedNanos, headersNanos, bodyNanos, gzipNanos, maxOf(0L, parseNanos - gzipNanos), bodyRead)
                if (streamed && allowStreamRetry) {
                    ProfileViewer.LOGGER.warn("Hypixel GET {} stream incomplete ({}), retrying once", url, exception.toString())
                    return getJson(url, allowReauth, false, RequestTiming.retry(timing.correlationId), 0)
                }
                throw exception
            }
            val parseNanos = System.nanoTime() - parseStartedNanos
            logTiming(url, response, bytes.size, timing, startedNanos, headersNanos, bodyNanos, gzipNanos, maxOf(0L, parseNanos - gzipNanos), bodyRead)
            if (!element.isJsonObject) return Optional.empty()
            val root = element.asJsonObject
            if (root.has("success") && root.get("success").isJsonPrimitive && !root.get("success").asBoolean) {
                lastFailure = if (root.has("cause")) root.get("cause").asString else "success=false"
                return Optional.empty()
            }
            if (cacheKey != null) ResponseCache.put(cacheKey, root)
            return Optional.of(root)
        } catch (exception: IOException) {
            if (netAttempt < 3 && isTransientNetwork(exception)) {
                if (exception is SSLException) fallbackSticky = true
                ProfileViewer.LOGGER.warn("Hypixel GET {} network error ({}), retry {}", url, exception.toString(), netAttempt + 1)
                try {
                    Thread.sleep(300L * (netAttempt + 1))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return Optional.empty()
                }
                return getJson(url, allowReauth, allowStreamRetry, timing, netAttempt + 1)
            }
            return requestFailed(url, exception)
        } catch (exception: InterruptedException) {
            val result = requestFailed(url, exception)
            Thread.currentThread().interrupt()
            return result
        }
    }

    private val TRAILING_SLASHES = Regex("/+$")

    private fun requestFailed(url: String, exception: Exception): Optional<JsonObject> {
        lastFailure = exception.javaClass.simpleName + (exception.message?.let { ": $it" } ?: "")
        ProfileViewer.LOGGER.warn("Hypixel GET {} failed", url, exception)
        return Optional.empty()
    }

    private fun logTiming(
        url: String,
        response: HttpResponse<*>,
        bytes: Int,
        timing: RequestTiming,
        startedNanos: Long,
        headersNanos: Long,
        bodyNanos: Long,
        gzipNanos: Long,
        jsonNanos: Long,
        bodyRead: BodyReadTiming
    ) {
        val completedNanos = System.nanoTime()
        val path = if (url.startsWith(WORKER_BASE)) url.substring(WORKER_BASE.length) else url
        val headers = response.headers()
        fun header(name: String, fallback: String) = headers.firstValue(name).orElse(fallback)
        ProfileViewer.LOGGER.info(
            "[PV timing] event=http_complete correlation_id={} path={} queue_ms={} admission_ms={} headers_ms={} body_ms={} first_body_byte_ms={} max_read_wait_ms={} read_calls={} http_version={} content_length={} transfer_encoding={} gzip_ms={} json_ms={} total_ms={} stream={} status={} bytes={} encoding={} cache={} server=[{}] upstream=[bytes={} enc={} prevAge={} {}] arrival=[{}]",
            timing.correlationId,
            path,
            millis(timing.taskStartedNanos - timing.queuedNanos),
            millis(startedNanos - timing.taskStartedNanos),
            millis(headersNanos - startedNanos),
            millis(bodyNanos - headersNanos),
            bodyRead.firstByteMillis(),
            bodyRead.maxReadWaitMillis(),
            bodyRead.readCalls(),
            response.version(),
            header("Content-Length", "-"),
            header("Transfer-Encoding", "-"),
            millis(gzipNanos),
            millis(jsonNanos),
            millis(completedNanos - timing.queuedNanos),
            header("x-hypixel-stream", "0"),
            response.statusCode(),
            bytes,
            header("Content-Encoding", "none"),
            header("x-hypixel-cache", "-"),
            header("server-timing", ""),
            header("x-hypixel-upstream-bytes", "-"),
            header("x-hypixel-upstream-encoding", "-"),
            header("x-hypixel-prev-age", "-"),
            header("x-hypixel-shape", ""),
            header("x-hypixel-arrival", "")
        )
    }

    private fun millis(nanos: Long): Long = maxOf(0L, TimeUnit.NANOSECONDS.toMillis(nanos))

    private fun validCorrelationId(value: String?): String =
        if (value != null && CORRELATION_ID.matches(value)) value else UUID.randomUUID().toString()

    private class RequestTiming(val correlationId: String, val queuedNanos: Long, val taskStartedNanos: Long) {
        companion object {
            fun retry(correlationId: String): RequestTiming {
                val now = System.nanoTime()
                return RequestTiming(correlationId, now, now)
            }
        }
    }

    internal class BodyReadTiming(private val delegate: InputStream, private val headersNanos: Long) : InputStream() {
        private var firstByteNanos = 0L
        private var maxReadWaitNanos = 0L
        private var readCalls = 0
        private var bytesRead = 0

        override fun read(): Int {
            val started = System.nanoTime()
            try {
                val value = delegate.read()
                recordRead(started, if (value < 0) 0 else 1)
                return value
            } catch (exception: IOException) {
                recordRead(started, 0)
                throw exception
            }
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val started = System.nanoTime()
            try {
                val count = delegate.read(bytes, offset, length)
                recordRead(started, maxOf(0, count))
                return count
            } catch (exception: IOException) {
                recordRead(started, 0)
                throw exception
            }
        }

        private fun recordRead(started: Long, count: Int) {
            val finished = System.nanoTime()
            readCalls++
            maxReadWaitNanos = maxOf(maxReadWaitNanos, finished - started)
            if (count > 0) {
                if (firstByteNanos == 0L) firstByteNanos = finished
                bytesRead += count
            }
        }

        override fun close() {
            delegate.close()
        }

        fun bytesRead(): Int = bytesRead

        fun readCalls(): Int = readCalls

        fun firstByteMillis(): Long = if (firstByteNanos == 0L) -1L else millis(firstByteNanos - headersNanos)

        fun maxReadWaitMillis(): Long = millis(maxReadWaitNanos)
    }

    private class TimedInputStream(private val delegate: InputStream) : InputStream() {
        private var readNanos = 0L

        override fun read(): Int {
            val started = System.nanoTime()
            try {
                return delegate.read()
            } finally {
                readNanos += System.nanoTime() - started
            }
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val started = System.nanoTime()
            try {
                return delegate.read(bytes, offset, length)
            } finally {
                readNanos += System.nanoTime() - started
            }
        }

        override fun close() {
            delegate.close()
        }

        fun readNanos(): Long = readNanos
    }

    private fun waitForSlot() {
        var sleepMs: Long
        while (true) {
            val now = System.currentTimeMillis()
            val current = NEXT_REQUEST_AT_MILLIS.get()
            val scheduled = maxOf(now, current)
            if (NEXT_REQUEST_AT_MILLIS.compareAndSet(current, scheduled + SPACING_MS)) {
                sleepMs = scheduled - now
                break
            }
        }
        if (sleepMs <= 0L) return
        try {
            Thread.sleep(sleepMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    @JvmStatic
    fun undashed(uuid: UUID?): String = uuid?.toString()?.replace("-", "")?.lowercase(Locale.ROOT) ?: ""

    @JvmStatic
    fun parseUndashedUuid(raw: String?): UUID? {
        if (raw.isNullOrBlank()) return null
        val hex = raw.trim().replace("-", "").lowercase(Locale.ROOT)
        if (hex.length != 32) return null
        return try {
            UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @JvmStatic
    fun status(uuid: UUID?): CompletableFuture<Optional<JsonObject>> {
        val id = undashed(uuid)
        return CompletableFuture.supplyAsync({
            waitForSlot()
            getJson("$WORKER_BASE/hypixel/status/$id")
        }, EXECUTOR)
    }

    private val NAME_HISTORY_TIMEOUT: Duration = Duration.ofSeconds(8)
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    @JvmStatic
    fun usernameHistory(uuid: UUID?): CompletableFuture<Optional<JsonArray>> {
        val id = undashed(uuid)
        val dashed = uuid?.toString() ?: ""
        val labyId = dashed.ifBlank { id }
        fun source(label: String, fetch: (String) -> Optional<JsonArray>): CompletableFuture<Optional<JsonArray>> =
            CompletableFuture.supplyAsync({ fetch(labyId) }, EXECUTOR).exceptionally { error ->
                ProfileViewer.LOGGER.warn("$label name history future failed for {}", labyId, error)
                Optional.empty()
            }
        val laby = source("Laby", ::fetchLabyNameHistory)
        val crafty = source("Crafty", ::fetchCraftyNameHistory)
        val namemc = source("NameMC", ::fetchNameMcNameHistory)
        return CompletableFuture.allOf(laby, crafty, namemc).thenApply {
            val merged = JsonArray()
            mergeNameHistory(merged, laby.join())
            mergeNameHistory(merged, crafty.join())
            mergeNameHistory(merged, namemc.join())
            if (merged.size() == 0) mergeNameHistory(merged, fetchAshconNameHistory(id))
            if (merged.size() == 0) return@thenApply Optional.empty()
            val cleaned = collapseConsecutiveNameDupes(sortNameHistoryOldestFirst(merged))
            ProfileViewer.LOGGER.info("Username history for {}: {} name(s)", id, cleaned.size())
            Optional.of(cleaned)
        }
    }

    private inline fun nameHistoryRequest(
        label: String,
        key: String,
        uri: URI,
        accept: String,
        extraHeaders: Map<String, String> = emptyMap(),
        parse: (String) -> Optional<JsonArray>
    ): Optional<JsonArray> {
        try {
            val builder = HttpRequest.newBuilder(uri)
                .timeout(NAME_HISTORY_TIMEOUT)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", accept)
            extraHeaders.forEach { (k, v) -> builder.header(k, v) }
            val response = HTTP.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
            val body: String? = response.body()
            if (response.statusCode() !in 200..299 || body.isNullOrBlank()) return Optional.empty()
            return parse(body)
        } catch (exception: IOException) {
            ProfileViewer.LOGGER.warn("$label name history failed for {}", key, exception)
            return Optional.empty()
        } catch (exception: InterruptedException) {
            ProfileViewer.LOGGER.warn("$label name history failed for {}", key, exception)
            Thread.currentThread().interrupt()
            return Optional.empty()
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            ProfileViewer.LOGGER.warn("$label name history parse failed for {}", key, exception)
            return Optional.empty()
        }
    }

    private fun fetchLabyNameHistory(uuidPath: String?): Optional<JsonArray> {
        if (uuidPath.isNullOrBlank()) return Optional.empty()
        return nameHistoryRequest("Laby", uuidPath, URI.create("https://laby.net/api/v3/user/$uuidPath/names"), "application/json") { body ->
            val element = JsonParser.parseString(body)
            when {
                element.isJsonArray -> Optional.of(normalizeNameHistoryArray(element.asJsonArray))
                element.isJsonObject -> {
                    val root = element.asJsonObject
                    when {
                        root.has("username_history") && root.get("username_history").isJsonArray ->
                            Optional.of(normalizeNameHistoryArray(root.getAsJsonArray("username_history")))
                        root.has("name_history") && root.get("name_history").isJsonArray ->
                            Optional.of(normalizeNameHistoryArray(root.getAsJsonArray("name_history")))
                        else -> Optional.empty()
                    }
                }
                else -> Optional.empty()
            }
        }
    }

    private fun fetchCraftyNameHistory(uuidPath: String?): Optional<JsonArray> {
        if (uuidPath.isNullOrBlank()) return Optional.empty()
        return nameHistoryRequest("Crafty", uuidPath, URI.create("https://api.crafty.gg/api/v2/players/$uuidPath"), "application/json") { body ->
            if (body.trim().startsWith("<")) return@nameHistoryRequest Optional.empty()
            val root = JsonParser.parseString(body).asJsonObject
            val data = if (root.has("data") && root.get("data").isJsonObject) root.getAsJsonObject("data") else root
            if (!data.has("usernames") || !data.get("usernames").isJsonArray) return@nameHistoryRequest Optional.empty()
            val out = JsonArray()
            for (el in data.getAsJsonArray("usernames")) {
                if (el == null || !el.isJsonObject) continue
                val obj = el.asJsonObject
                if (obj.has("hidden") && obj.get("hidden").isJsonPrimitive && obj.get("hidden").asBoolean) continue
                val name: String? = if (obj.has("username") && obj.get("username").isJsonPrimitive) obj.get("username").asString else ""
                if (name.isNullOrBlank()) continue
                val normalized = JsonObject()
                normalized.addProperty("username", name)
                if (obj.has("changed_at") && !obj.get("changed_at").isJsonNull && obj.get("changed_at").isJsonPrimitive) {
                    normalized.addProperty("changed_at", obj.get("changed_at").asString)
                }
                out.add(normalized)
            }
            if (out.size() == 0) Optional.empty() else Optional.of(out)
        }
    }

    private fun fetchNameMcNameHistory(uuidPath: String?): Optional<JsonArray> {
        if (uuidPath.isNullOrBlank()) return Optional.empty()
        return nameHistoryRequest(
            "NameMC",
            uuidPath,
            URI.create("https://namemc.com/profile/$uuidPath"),
            "text/html,application/xhtml+xml",
            mapOf("Accept-Language" to "en-US,en;q=0.9")
        ) { parseNameMcHistoryHtml(it) }
    }

    private val NAMEMC_ROW = Regex("<tr>(?!\\s*class=\"d-lg-none)[\\s\\S]*?</tr>", RegexOption.IGNORE_CASE)
    private val NAMEMC_NAME_LINK = Regex("href=\"/search\\?q=([^\"]+)\"[^>]*>([^<]+)</a>")
    private val NAMEMC_TIME = Regex("datetime=\"([^\"]+)\"")

    private fun parseNameMcHistoryHtml(html: String?): Optional<JsonArray> {
        if (html.isNullOrBlank()) return Optional.empty()
        val start = html.indexOf("<strong>Name History</strong>")
        if (start < 0) return Optional.empty()
        var end = html.indexOf("</table>", start)
        if (end < 0) end = minOf(html.length, start + 80_000)
        val chunk = html.substring(start, end)
        val out = JsonArray()
        for (row in NAMEMC_ROW.findAll(chunk)) {
            val tr = row.value
            if (!tr.contains("fw-bold")) continue
            val nm = NAMEMC_NAME_LINK.find(tr) ?: continue
            val name = nm.groupValues[2].trim()
            if (name.isBlank() || name == "-" || name.equals("&mdash;", ignoreCase = true)) continue
            val normalized = JsonObject()
            normalized.addProperty("username", name)
            NAMEMC_TIME.find(tr)?.let { normalized.addProperty("changed_at", it.groupValues[1]) }
            out.add(normalized)
        }
        return if (out.size() == 0) Optional.empty() else Optional.of(out)
    }

    private fun mergeNameHistory(into: JsonArray?, source: Optional<JsonArray>?) {
        if (into == null || source == null || source.isEmpty) return
        val seen = LinkedHashSet<String>()
        for (el in into) {
            if (el != null && el.isJsonObject) {
                val key = nameHistoryKey(el.asJsonObject)
                if (key.isNotBlank()) seen.add(key)
            }
        }
        for (el in source.get()) {
            if (el == null || !el.isJsonObject) continue
            val obj = el.asJsonObject
            val key = nameHistoryKey(obj)
            if (key.isBlank() || !seen.add(key)) continue
            into.add(obj)
        }
    }

    private fun nameHistoryKey(obj: JsonObject?): String {
        if (obj == null) return ""
        val name: String? = if (obj.has("username") && obj.get("username").isJsonPrimitive) obj.get("username").asString else ""
        if (name.isNullOrBlank()) return ""
        val changed: String? = if (obj.has("changed_at") && obj.get("changed_at").isJsonPrimitive) obj.get("changed_at").asString else ""
        val day = if (changed != null && changed.length >= 10) changed.substring(0, 10) else ""
        return name.lowercase(Locale.ROOT) + "|" + day
    }

    private fun changedAt(obj: JsonObject): String =
        if (obj.has("changed_at") && obj.get("changed_at").isJsonPrimitive) obj.get("changed_at").asString else ""

    private fun sortNameHistoryOldestFirst(raw: JsonArray): JsonArray {
        val list = raw.filter { it != null && it.isJsonObject }.map { it.asJsonObject }.toMutableList()
        list.sortWith { a, b ->
            val ca = changedAt(a)
            val cb = changedAt(b)
            when {
                ca.isBlank() && cb.isBlank() -> 0
                ca.isBlank() -> -1
                cb.isBlank() -> 1
                else -> ca.compareTo(cb)
            }
        }
        val out = JsonArray()
        list.forEach { out.add(it) }
        return out
    }

    private fun collapseConsecutiveNameDupes(sorted: JsonArray): JsonArray {
        val out = JsonArray()
        var prev: String? = null
        for (el in sorted) {
            if (el == null || !el.isJsonObject) continue
            val obj = el.asJsonObject
            val name: String? = if (obj.has("username") && obj.get("username").isJsonPrimitive) obj.get("username").asString else ""
            if (name.isNullOrBlank()) continue
            val key = name.lowercase(Locale.ROOT)
            if (prev != null && prev == key) continue
            prev = key
            out.add(obj)
        }
        return out
    }

    private fun fetchAshconNameHistory(undashedUuid: String): Optional<JsonArray> =
        nameHistoryRequest("Ashcon", undashedUuid, URI.create("https://api.ashcon.app/mojang/v2/user/$undashedUuid"), "application/json") { body ->
            val root = JsonParser.parseString(body).asJsonObject
            if (root.has("username_history") && root.get("username_history").isJsonArray) {
                Optional.of(normalizeNameHistoryArray(root.getAsJsonArray("username_history")))
            } else {
                Optional.empty()
            }
        }

    private fun normalizeNameHistoryArray(raw: JsonArray?): JsonArray {
        val out = JsonArray()
        if (raw == null) return out
        for (el in raw) {
            if (el == null || !el.isJsonObject) continue
            val obj = el.asJsonObject
            val name: String? = when {
                obj.has("username") && obj.get("username").isJsonPrimitive -> obj.get("username").asString
                obj.has("name") && obj.get("name").isJsonPrimitive -> obj.get("name").asString
                else -> ""
            }
            if (name.isNullOrBlank()) continue
            var changed: String? = ""
            if (obj.has("changed_at") && obj.get("changed_at").isJsonPrimitive && !obj.get("changed_at").isJsonNull) {
                changed = ProfileParseSupport.lenient("") { obj.get("changed_at").asString }
            } else if (obj.has("changedAt") && obj.get("changedAt").isJsonPrimitive) {
                changed = obj.get("changedAt").asString
            }
            val normalized = JsonObject()
            normalized.addProperty("username", name)
            if (!changed.isNullOrBlank()) normalized.addProperty("changed_at", changed)
            out.add(normalized)
        }
        return out
    }

    @JvmRecord
    data class UuidName(val uuid: UUID, val name: String)
}
