/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import fishmod.pv.data.SoftDataFailure;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

public final class HypixelApiClient {
	private static final String WORKER_BASE = "https://fishmod.dev";
	private static final URI MOJANG_PROFILE = URI.create("https://api.mojang.com/users/profiles/minecraft/");
	private static final URI MOJANG_SESSION = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/");
	private static final Duration TIMEOUT = Duration.ofSeconds(40);
	private static final Duration UUID_LOOKUP_TIMEOUT = Duration.ofSeconds(5);
	// The server may try two Mojang endpoints before answering from an older entry.
	private static final Duration SERVER_UUID_TIMEOUT = Duration.ofSeconds(8);
	private static final long DISK_UUID_RECHECK_MS = TimeUnit.DAYS.toMillis(1);
	private static final long SPACING_MS = 80L;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ConcurrentHashMap<String, JsonObject> PLAYER_CACHE = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, Long> PLAYER_CACHED_AT = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, CompletableFuture<Optional<JsonObject>>> PLAYER_IN_FLIGHT =
		new ConcurrentHashMap<>();
	private static final InFlightRequestRegistry<String, Optional<JsonObject>> PROFILE_IN_FLIGHT =
		new InFlightRequestRegistry<>();
	/** Mojang names do not change during this client session often enough to justify repeat lookups. */
	private static final ConcurrentHashMap<String, UuidName> UUID_NAME_CACHE = new ConcurrentHashMap<>();
	/** Small pool so museum + election (and other GETs) can overlap while spacing starts. */
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "BetterPV-HypixelApi");
		t.setDaemon(true);
		return t;
	});
	/** Heavy profile parse (NBT / networth) stays off the HTTP pool. */
	private static final ExecutorService PARSE_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "BetterPV-ProfileParse");
		t.setDaemon(true);
		return t;
	});

	/** Shared so auth, resources and /pv calls to api.vyriv.dev reuse one warm connection. */
	public static HttpClient http() {
		return HTTP;
	}

	public static ExecutorService parseExecutor() {
		return PARSE_EXECUTOR;
	}

	public static ExecutorService networkExecutor() {
		return EXECUTOR;
	}

	private static final long ELECTION_TTL_MS = 10L * 60L * 1000L;
	private static volatile CompletableFuture<Optional<JsonObject>> electionFuture;
	private static volatile long electionFetchedAtMillis;

	/** Next allowed request start time. Claimed with CAS so sleep happens off the shared path. */
	private static final AtomicLong NEXT_REQUEST_AT_MILLIS = new AtomicLong();

	private HypixelApiClient() {
	}

	public static boolean canFetch() {
		return true;
	}

	private enum LookupStatus { FOUND, MISSING, FAILED }

	private record NameLookup(LookupStatus status, UuidName found, String source, long ageSeconds, long ms, String detail) {
		static NameLookup failed(long ms, String detail) {
			return new NameLookup(LookupStatus.FAILED, null, null, 0L, ms, detail);
		}

		boolean foundFor(String requested) {
			return this.status == LookupStatus.FOUND && this.found != null && requested.equalsIgnoreCase(this.found.name());
		}

		boolean serverStale() {
			return "stale".equals(this.source);
		}

		String describe() {
			return this.status.name().toLowerCase(Locale.ROOT)
				+ (this.source == null ? "" : "/" + this.source)
				+ (this.detail == null ? "" : "(" + this.detail + ")")
				+ " " + this.ms + "ms";
		}
	}

	public static CompletableFuture<Optional<UuidName>> resolveUuid(String name) {
		String cleaned = name == null ? "" : name.trim();
		if (cleaned.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		long startedNanos = System.nanoTime();
		UuidName cached = UUID_NAME_CACHE.get(cleaned.toLowerCase(Locale.ROOT));
		if (cached != null) {
			logUuid(cleaned, "memory", startedNanos, "");
			return CompletableFuture.completedFuture(Optional.of(cached));
		}
		return CompletableFuture.supplyAsync(() -> NameUuidStore.get(cleaned), EXECUTOR).thenCompose(stored -> {
			if (stored != null) {
				logUuid(cleaned, "disk", startedNanos, " savedAgo=" + (System.currentTimeMillis() - stored.savedAtMs()) / 60_000L + "min");
				rememberUuid(stored.value(), -1L);
				if (System.currentTimeMillis() - stored.savedAtMs() > DISK_UUID_RECHECK_MS) {
					recheckDiskUuid(cleaned, stored.value());
				}
				return CompletableFuture.completedFuture(Optional.of(stored.value()));
			}
			return raceUuidLookups(cleaned, startedNanos);
		});
	}

	// Shared server cache and Mojang race; Mojang is the authority, the server
	// entry wins only when fresh, and an older server entry is used only when
	// Mojang can't answer.
	private static CompletableFuture<Optional<UuidName>> raceUuidLookups(String cleaned, long startedNanos) {
		CompletableFuture<NameLookup> server = CompletableFuture
			.supplyAsync(() -> lookupServerUuid(cleaned, false), EXECUTOR)
			.exceptionally(error -> NameLookup.failed(-1L, "error"));
		CompletableFuture<NameLookup> mojang = CompletableFuture
			.supplyAsync(() -> lookupMojangUuid(cleaned), EXECUTOR)
			.exceptionally(error -> NameLookup.failed(-1L, "error"));
		CompletableFuture<Optional<UuidName>> result = new CompletableFuture<>();
		Runnable decide = () -> decideUuid(cleaned, startedNanos, server, mojang, result);
		server.thenRun(decide);
		mojang.thenRun(decide);
		CompletableFuture.allOf(server, mojang).thenRun(() -> reconcileUuid(cleaned, server.join(), mojang.join()));
		return result;
	}

	private static void decideUuid(
		String cleaned,
		long startedNanos,
		CompletableFuture<NameLookup> serverFut,
		CompletableFuture<NameLookup> mojangFut,
		CompletableFuture<Optional<UuidName>> result
	) {
		synchronized (result) {
			if (result.isDone()) {
				return;
			}
			NameLookup server = serverFut.getNow(null);
			NameLookup mojang = mojangFut.getNow(null);
			String detail = " server=" + (server == null ? "pending" : server.describe())
				+ " mojang=" + (mojang == null ? "pending" : mojang.describe());
			if (server != null && server.foundFor(cleaned) && !server.serverStale()) {
				rememberUuid(server.found(), System.currentTimeMillis() - server.ageSeconds() * 1000L);
				logUuid(cleaned, "mojang".equals(server.source()) ? "mojang via=server" : "server-cache", startedNanos, detail);
				result.complete(Optional.of(server.found()));
				return;
			}
			if (mojang == null) {
				return;
			}
			if (mojang.status() == LookupStatus.FOUND) {
				rememberUuid(mojang.found(), System.currentTimeMillis());
				logUuid(cleaned, "mojang", startedNanos, detail);
				result.complete(Optional.of(mojang.found()));
				return;
			}
			if (mojang.status() == LookupStatus.MISSING) {
				logUuid(cleaned, "not-found", startedNanos, detail);
				result.complete(Optional.empty());
				return;
			}
			if (server == null) {
				return;
			}
			if (server.foundFor(cleaned)) {
				// Not remembered: a fallback must not be extended into the caches.
				logUuid(cleaned, "stale-fallback", startedNanos, detail + " age=" + server.ageSeconds() + "s");
				result.complete(Optional.of(server.found()));
				return;
			}
			logUuid(cleaned, "unavailable", startedNanos, detail);
			result.complete(Optional.empty());
		}
	}

	// Runs once both sides answered. A Mojang answer that contradicts the server
	// makes the server re-check Mojang itself; clients never write mappings.
	private static void reconcileUuid(String cleaned, NameLookup server, NameLookup mojang) {
		if (mojang.status() == LookupStatus.FOUND) {
			rememberUuid(mojang.found(), System.currentTimeMillis());
			boolean serverCurrent = server.foundFor(cleaned) && !server.serverStale()
				&& server.found().uuid().equals(mojang.found().uuid());
			if (!serverCurrent && server.status() != LookupStatus.FAILED) {
				BetterPV.LOGGER.info("[PV timing] {} uuid server out of date ({}), asking it to re-check", cleaned, server.describe());
				refreshServerUuid(cleaned);
			}
		} else if (mojang.status() == LookupStatus.MISSING) {
			forgetUuid(cleaned);
			if (server.status() == LookupStatus.FOUND) {
				BetterPV.LOGGER.info("[PV timing] {} uuid gone on Mojang but server had it, asking it to re-check", cleaned);
				refreshServerUuid(cleaned);
			}
		}
	}

	// Disk entries are served straight away; older ones are confirmed in the
	// background so a rename is dropped by the next /pv.
	private static void recheckDiskUuid(String cleaned, UuidName stored) {
		CompletableFuture.supplyAsync(() -> lookupMojangUuid(cleaned), EXECUTOR).thenAccept(mojang -> {
			if (mojang.status() == LookupStatus.FOUND) {
				if (!mojang.found().uuid().equals(stored.uuid())) {
					BetterPV.LOGGER.warn("[PV timing] {} uuid disk entry was out of date, replaced", cleaned);
					refreshServerUuid(cleaned);
				}
				rememberUuid(mojang.found(), System.currentTimeMillis());
			} else if (mojang.status() == LookupStatus.MISSING) {
				BetterPV.LOGGER.warn("[PV timing] {} uuid disk entry no longer exists on Mojang, dropped", cleaned);
				forgetUuid(cleaned);
				refreshServerUuid(cleaned);
			}
		});
	}

	private static void refreshServerUuid(String cleaned) {
		CompletableFuture.runAsync(() -> lookupServerUuid(cleaned, true), EXECUTOR);
	}

	// savedAtMs < 0 keeps the existing disk entry untouched.
	private static void rememberUuid(UuidName uuidName, long savedAtMs) {
		String key = uuidName.name().toLowerCase(Locale.ROOT);
		UUID_NAME_CACHE.put(key, uuidName);
		UUID_NAME_CACHE.entrySet().removeIf(e -> e.getValue().uuid().equals(uuidName.uuid()) && !e.getKey().equals(key));
		if (savedAtMs >= 0L) {
			NameUuidStore.put(uuidName.name(), uuidName.uuid(), uuidName.name(), savedAtMs);
		}
	}

	private static void forgetUuid(String cleaned) {
		UUID_NAME_CACHE.remove(cleaned.toLowerCase(Locale.ROOT));
		NameUuidStore.forget(cleaned);
	}

	private static void logUuid(String name, String source, long startedNanos, String detail) {
		BetterPV.LOGGER.info("[PV timing] {} uuid {} {}ms{}", name, source, (System.nanoTime() - startedNanos) / 1_000_000L, detail);
	}

	private static NameLookup lookupMojangUuid(String cleaned) {
		long startedNanos = System.nanoTime();
		try {
			HttpRequest request = HttpRequest.newBuilder(
				MOJANG_PROFILE.resolve(URLEncoder.encode(cleaned, StandardCharsets.UTF_8))
			).timeout(UUID_LOOKUP_TIMEOUT).GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			long ms = (System.nanoTime() - startedNanos) / 1_000_000L;
			if (response.statusCode() == 404 || response.statusCode() == 204) {
				return new NameLookup(LookupStatus.MISSING, null, null, 0L, ms, null);
			}
			if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
				return NameLookup.failed(ms, "status " + response.statusCode());
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			UUID uuid = parseUndashedUuid(root.has("id") ? root.get("id").getAsString() : null);
			String resolved = root.has("name") ? root.get("name").getAsString() : null;
			if (uuid == null || resolved == null || !cleaned.equalsIgnoreCase(resolved)) {
				return NameLookup.failed(ms, "malformed");
			}
			return new NameLookup(LookupStatus.FOUND, new UuidName(uuid, resolved), null, 0L, ms, null);
		} catch (IOException | InterruptedException exception) {
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			BetterPV.LOGGER.warn("Mojang UUID lookup failed for {}: {}", cleaned, exception.toString());
			return NameLookup.failed((System.nanoTime() - startedNanos) / 1_000_000L, exception.getClass().getSimpleName());
		} catch (RuntimeException exception) {
			BetterPV.LOGGER.warn("Mojang UUID lookup parse failed for {}", cleaned, exception);
			return NameLookup.failed((System.nanoTime() - startedNanos) / 1_000_000L, "parse");
		}
	}

	private static NameLookup lookupServerUuid(String cleaned, boolean refresh) {
		long startedNanos = System.nanoTime();
		if (true) {
			return NameLookup.failed(0L, "no server uuid cache");
		}
		try {
			String url = WORKER_BASE + "/hypixel/uuid/" + URLEncoder.encode(cleaned, StandardCharsets.UTF_8)
				+ (refresh ? "?refresh=1" : "");
			URI uri = URI.create(url);
			HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(SERVER_UUID_TIMEOUT).GET();
			if (!FishProxyAuth.applyAuthHeaders(builder, uri)) {
				return NameLookup.failed((System.nanoTime() - startedNanos) / 1_000_000L, "auth");
			}
			HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			long ms = (System.nanoTime() - startedNanos) / 1_000_000L;
			if (response.statusCode() == 404) {
				return new NameLookup(LookupStatus.MISSING, null, null, 0L, ms, null);
			}
			if (response.statusCode() != 200 || response.body() == null) {
				return NameLookup.failed(ms, "status " + response.statusCode());
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			UUID uuid = parseUndashedUuid(root.has("uuid") ? root.get("uuid").getAsString() : null);
			String resolved = root.has("name") ? root.get("name").getAsString() : null;
			String source = root.has("source") ? root.get("source").getAsString() : "cache";
			long age = root.has("age") && root.get("age").isJsonPrimitive() ? root.get("age").getAsLong() : 0L;
			if (uuid == null || resolved == null) {
				return NameLookup.failed(ms, "malformed");
			}
			return new NameLookup(LookupStatus.FOUND, new UuidName(uuid, resolved), source, Math.max(0L, age), ms, null);
		} catch (IOException | InterruptedException exception) {
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return NameLookup.failed((System.nanoTime() - startedNanos) / 1_000_000L, exception.getClass().getSimpleName());
		} catch (RuntimeException exception) {
			BetterPV.LOGGER.warn("Server UUID lookup parse failed for {}", cleaned, exception);
			return NameLookup.failed((System.nanoTime() - startedNanos) / 1_000_000L, "parse");
		}
	}

	public static CompletableFuture<Optional<UuidName>> resolveName(UUID uuid) {
		if (uuid == null) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(MOJANG_SESSION.resolve(id))
					.timeout(TIMEOUT)
					.GET()
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
					return Optional.<UuidName>empty();
				}
				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
				String resolved = root.has("name") ? root.get("name").getAsString() : null;
				if (resolved == null || resolved.isBlank()) {
					return Optional.empty();
				}
				UuidName uuidName = new UuidName(uuid, resolved);
				rememberUuid(uuidName, -1L);
				NameUuidStore.forgetOtherNames(uuid, resolved);
				return Optional.of(uuidName);
			} catch (IOException | InterruptedException exception) {
				BetterPV.LOGGER.debug("Mojang name lookup failed for {}", id, exception);
				if (exception instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				return Optional.empty();
			} catch (RuntimeException exception) {
				if (!SoftDataFailure.isSoft(exception)) {
					throw exception;
				}
				BetterPV.LOGGER.debug("Mojang name lookup parse failed for {}", id, exception);
				return Optional.empty();
			}
		}, EXECUTOR);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockProfiles(UUID uuid) {
		return skyblockProfiles(uuid, UUID.randomUUID().toString());
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockProfiles(UUID uuid, String correlationId) {
		String id = undashed(uuid);
		String correlation = validCorrelationId(correlationId);
		long queuedNanos = System.nanoTime();
		InFlightRequestRegistry.Shared<Optional<JsonObject>> shared = PROFILE_IN_FLIGHT.share(
			id,
			correlation,
			() -> CompletableFuture.supplyAsync(() -> {
				long taskStartedNanos = System.nanoTime();
				return fetchVyrivApi(
				WORKER_BASE + "/hypixel/skyblock/profiles/" + id,
				"skyblock/profiles",
				new RequestTiming(correlation, queuedNanos, taskStartedNanos)
				);
			}, EXECUTOR)
		);
		if (shared.reused()) {
			BetterPV.LOGGER.info(
				"[PV timing] event=profile_request_deduplicated correlation_id={} active_correlation_id={} player_uuid={}",
				correlation,
				shared.correlationId(),
				id
			);
		}
		return shared.future();
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockMuseum(UUID uuid) {
		return skyblockMuseum(uuid, null);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockMuseum(UUID uuid, String profileId) {
		String id = undashed(uuid);
		String workerUrl = WORKER_BASE + "/hypixel/skyblock/museum/" + id;
		if (profileId != null && !profileId.isBlank()) {
			String profile = profileId.trim().replace("-", "").toLowerCase(Locale.ROOT);
			String encoded = URLEncoder.encode(profile, StandardCharsets.UTF_8);
			workerUrl += "?profile=" + encoded;
		}
		String finalWorkerUrl = workerUrl;
		return CompletableFuture.supplyAsync(
			() -> fetchVyrivApi(finalWorkerUrl, "skyblock/museum"),
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockAuction(UUID uuid) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(
			() -> fetchVyrivApi(
				WORKER_BASE + "/hypixel/skyblock/auction/" + id,
				"skyblock/auction"
			),
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockGarden(String profileId) {
		if (profileId == null || profileId.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String id = profileId.trim().replace("-", "").toLowerCase(Locale.ROOT);
		String encoded = URLEncoder.encode(id, StandardCharsets.UTF_8);
		return CompletableFuture.supplyAsync(
			() -> {
				Optional<JsonObject> root = fetchVyrivApi(
					WORKER_BASE + "/hypixel/skyblock/garden/" + encoded,
					"skyblock/garden"
				);
				if (root.isEmpty()) {
					return Optional.empty();
				}
				JsonObject body = root.get();
				if (body.has("garden") && body.get("garden").isJsonObject()) {
					return Optional.of(body.getAsJsonObject("garden"));
				}
				return Optional.of(body);
			},
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> player(UUID uuid) {
		return player(uuid, Long.MAX_VALUE);
	}

	/** Refetches when the cached player is older than {@code maxAgeMs} (lastLogin/lastLogout go stale). */
	public static CompletableFuture<Optional<JsonObject>> player(UUID uuid, long maxAgeMs) {
		String id = undashed(uuid);
		JsonObject cached = PLAYER_CACHE.get(id);
		if (cached != null && System.currentTimeMillis() - PLAYER_CACHED_AT.getOrDefault(id, 0L) <= maxAgeMs) {
			return CompletableFuture.completedFuture(Optional.of(cached));
		}
		// Home and Collections both ask for the viewed player at once; share one request.
		CompletableFuture<Optional<JsonObject>> created = new CompletableFuture<>();
		CompletableFuture<Optional<JsonObject>> existing = PLAYER_IN_FLIGHT.putIfAbsent(id, created);
		if (existing != null) {
			return existing;
		}
		CompletableFuture.supplyAsync(
			() -> {
				Optional<JsonObject> root = fetchVyrivApi(
					WORKER_BASE + "/hypixel/player/" + id,
					"player"
				);
				if (root.isEmpty()) {
					return Optional.<JsonObject>empty();
				}
				JsonObject body = root.get();
				JsonObject player = body.has("player") && body.get("player").isJsonObject()
					? body.getAsJsonObject("player")
					: body;
				long now = System.currentTimeMillis();
				// evict player JSON older than an hour
				PLAYER_CACHED_AT.entrySet().removeIf(e -> {
					if (now - e.getValue() <= 3_600_000L) return false;
					PLAYER_CACHE.remove(e.getKey());
					return true;
				});
				PLAYER_CACHE.put(id, player);
				PLAYER_CACHED_AT.put(id, now);
				return Optional.of(player);
			},
			EXECUTOR
		).whenComplete((value, error) -> {
			PLAYER_IN_FLIGHT.remove(id, created);
			if (error != null) {
				created.completeExceptionally(error);
			} else {
				created.complete(value);
			}
		});
		return created;
	}

	public static CompletableFuture<Optional<JsonObject>> guild(UUID uuid) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(
			() -> fetchVyrivApi(
				WORKER_BASE + "/hypixel/guild/" + id,
				"guild"
			),
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockElection() {
		// Global data; reuse it across /pv opens and profile switches unless the last fetch failed.
		long now = System.currentTimeMillis();
		CompletableFuture<Optional<JsonObject>> cached = electionFuture;
		if (cached != null && now - electionFetchedAtMillis < ELECTION_TTL_MS && !failed(cached)) {
			return cached;
		}
		CompletableFuture<Optional<JsonObject>> next = CompletableFuture.supplyAsync(() -> {
			waitForSlot();
			return getJson(WORKER_BASE + "/hypixel/resources/skyblock/election");
		}, EXECUTOR);
		electionFuture = next;
		electionFetchedAtMillis = now;
		return next;
	}

	/** True when a finished lookup failed or came back empty, so callers should retry. */
	public static boolean failed(CompletableFuture<? extends Optional<?>> future) {
		if (future == null || !future.isDone()) {
			return false;
		}
		if (future.isCompletedExceptionally()) {
			return true;
		}
		Optional<?> value = future.join();
		return value == null || value.isEmpty();
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockBingoResources() {
		return CompletableFuture.supplyAsync(() -> {
			waitForSlot();
			return getJson(WORKER_BASE + "/hypixel/resources/skyblock/bingo");
		}, EXECUTOR);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockBingo(UUID uuid) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(
			() -> fetchVyrivApi(
				WORKER_BASE + "/hypixel/skyblock/bingo/" + id,
				"skyblock/bingo"
			),
			EXECUTOR
		);
	}

	private static Optional<JsonObject> fetchVyrivApi(String workerUrl, String routeName) {
		long now = System.nanoTime();
		return fetchVyrivApi(workerUrl, routeName, new RequestTiming(UUID.randomUUID().toString(), now, now));
	}

	private static Optional<JsonObject> fetchVyrivApi(String workerUrl, String routeName, RequestTiming timing) {
		waitForSlot();
		long attemptStarted = System.nanoTime();
		Optional<JsonObject> viaWorker = getJson(workerUrl, true, true, timing);
		if (viaWorker.isEmpty() && System.nanoTime() - attemptStarted > TIMEOUT.toNanos() - 2_000_000_000L) {
			// The proxy was still fetching from Hypixel when we gave up; it caches the result, so ask again.
			BetterPV.LOGGER.info("[PV] {} timed out, retrying once", routeName);
			viaWorker = getJson(workerUrl, true, true, timing);
		}
		if (viaWorker.isPresent()) {
			return viaWorker;
		}
		BetterPV.LOGGER.warn("Vyriv Hypixel API unavailable for {}", routeName);
		return Optional.empty();
	}

	/** Maps BetterPV's /hypixel/... routes onto the fishmod.dev proxy, or public Hypixel resources. */
	static String rewriteUrl(String url) {
		String prefix = WORKER_BASE + "/hypixel/";
		if (url == null || !url.startsWith(prefix)) {
			return url;
		}
		String rest = url.substring(prefix.length());
		int slash = rest.indexOf('/');
		if (rest.startsWith("resources/")) {
			return "https://api.hypixel.net/v2/" + rest;
		}
		if (rest.startsWith("skyblock/")) {
			String sub = rest.substring("skyblock/".length());
			int s2 = sub.indexOf('/');
			String kind = s2 < 0 ? sub : sub.substring(0, s2);
			String id = s2 < 0 ? "" : sub.substring(s2 + 1);
			return switch (kind) {
				case "profiles" -> WORKER_BASE + "/skyblock/profiles?uuid=" + id;
				case "museum" -> {
					int q = id.indexOf("?profile=");
					yield WORKER_BASE + "/skyblock/museum?profile=" + (q >= 0 ? id.substring(q + "?profile=".length()) : id);
				}
				case "garden" -> WORKER_BASE + "/skyblock/garden?profile=" + id;
				default -> WORKER_BASE + "/skyblock/" + sub;
			};
		}
		String kind = slash < 0 ? rest : rest.substring(0, slash);
		String id = slash < 0 ? "" : rest.substring(slash + 1);
		return switch (kind) {
			case "player" -> WORKER_BASE + "/player?uuid=" + id;
			case "guild" -> WORKER_BASE + "/guild?player=" + id;
			case "status" -> WORKER_BASE + "/status?uuid=" + id;
			default -> url;
		};
	}

	private static Optional<JsonObject> getJson(String url) {
		long now = System.nanoTime();
		return getJson(url, true, true, new RequestTiming(UUID.randomUUID().toString(), now, now));
	}

	// A streamed MISS is cut mid-body when the upstream read fails, so a broken
	// transfer is retried once (usually a cache HIT by then).
	private static Optional<JsonObject> getJson(String url, boolean allowReauth, boolean allowStreamRetry) {
		long now = System.nanoTime();
		return getJson(url, allowReauth, allowStreamRetry, new RequestTiming(UUID.randomUUID().toString(), now, now));
	}

	private static Optional<JsonObject> getJson(
		String url,
		boolean allowReauth,
		boolean allowStreamRetry,
		RequestTiming timing
	) {
		String cacheKey = null;
		try {
			String target = rewriteUrl(url);
			if (target.startsWith(WORKER_BASE) && !target.contains("/status")) {
				cacheKey = target;
				Optional<JsonObject> cached = ResponseCache.get(cacheKey);
				if (cached.isPresent()) {
					BetterPV.LOGGER.info("[PV cache] hit {}", target.substring(WORKER_BASE.length()));
					return cached;
				}
			}
			URI uri = URI.create(target);
			HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET();
			boolean needsProxyAuth = target.startsWith(WORKER_BASE);
			if (needsProxyAuth && !FishProxyAuth.applyAuthHeaders(builder, uri)) {
				BetterPV.LOGGER.warn(
					"Hypixel GET {} skipped: {}",
					url,
					FishProxyAuth.userFacingFailure().orElse("missing BetterPV credentials")
				);
				FishProxyAuth.notifyPlayerIfNeeded();
				return Optional.empty();
			}
			if (needsProxyAuth) {
				builder.header("Accept-Encoding", "gzip");
				builder.setHeader("X-BetterPV-Correlation-ID", timing.correlationId());
			}
			long startedNanos = System.nanoTime();
			HttpResponse<InputStream> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
			long headersNanos = System.nanoTime();
			boolean streamed = "1".equals(response.headers().firstValue("x-hypixel-stream").orElse(""));
			byte[] bytes;
			BodyReadTiming bodyRead = new BodyReadTiming(response.body(), headersNanos);
			try (bodyRead) {
				bytes = bodyRead.readAllBytes();
			} catch (IOException exception) {
				logTiming(url, response, bodyRead.bytesRead(), timing, startedNanos, headersNanos, System.nanoTime(), 0L, 0L, bodyRead);
				if (streamed && allowStreamRetry) {
					BetterPV.LOGGER.warn("Hypixel GET {} stream broke ({}), retrying once", url, exception.toString());
					return getJson(url, allowReauth, false, RequestTiming.retry(timing.correlationId()));
				}
				throw exception;
			}
			long bodyNanos = System.nanoTime();
			if (response.statusCode() == 401 && needsProxyAuth) {
				logTiming(url, response, bytes.length, timing, startedNanos, headersNanos, bodyNanos, 0L, 0L, bodyRead);
				FishProxyAuth.handleUnauthorized(
					response.request(),
					new String(bytes, StandardCharsets.UTF_8),
					TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
				);
				if (allowReauth) {
					return getJson(url, false, allowStreamRetry, RequestTiming.retry(timing.correlationId()));
				}
				BetterPV.LOGGER.warn("Hypixel GET {} unauthorized after re-auth", url);
				return Optional.empty();
			}
			if (response.statusCode() == 503 && needsProxyAuth) {
				logTiming(url, response, bytes.length, timing, startedNanos, headersNanos, bodyNanos, 0L, 0L, bodyRead);
				BetterPV.LOGGER.warn("Hypixel GET {} session auth unavailable (503)", url);
				return Optional.empty();
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300 || bytes.length == 0) {
				logTiming(url, response, bytes.length, timing, startedNanos, headersNanos, bodyNanos, 0L, 0L, bodyRead);
				BetterPV.LOGGER.warn("Hypixel GET {} failed status={}", url, response.statusCode());
				return Optional.empty();
			}
			JsonElement element;
			long parseStartedNanos = System.nanoTime();
			long gzipNanos = 0L;
			try {
				InputStream body = new ByteArrayInputStream(bytes);
				if (response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip")) {
					long gzipStartedNanos = System.nanoTime();
					body = new GZIPInputStream(body, 64 * 1024);
					gzipNanos = System.nanoTime() - gzipStartedNanos;
				}
				TimedInputStream timedBody = new TimedInputStream(body);
				try (Reader reader = new InputStreamReader(timedBody, StandardCharsets.UTF_8)) {
					element = JsonParser.parseReader(reader);
				}
				if (body instanceof GZIPInputStream) {
					gzipNanos += timedBody.readNanos();
				}
			} catch (IOException | JsonParseException exception) {
				long parseNanos = System.nanoTime() - parseStartedNanos;
				logTiming(url, response, bytes.length, timing, startedNanos, headersNanos, bodyNanos, gzipNanos, Math.max(0L, parseNanos - gzipNanos), bodyRead);
				if (streamed && allowStreamRetry) {
					BetterPV.LOGGER.warn("Hypixel GET {} stream incomplete ({}), retrying once", url, exception.toString());
					return getJson(url, allowReauth, false, RequestTiming.retry(timing.correlationId()));
				}
				throw exception;
			}
			long parseNanos = System.nanoTime() - parseStartedNanos;
			logTiming(url, response, bytes.length, timing, startedNanos, headersNanos, bodyNanos, gzipNanos, Math.max(0L, parseNanos - gzipNanos), bodyRead);
			if (!element.isJsonObject()) {
				return Optional.empty();
			}
			JsonObject root = element.getAsJsonObject();
			if (root.has("success") && root.get("success").isJsonPrimitive() && !root.get("success").getAsBoolean()) {
				return Optional.empty();
			}
			if (cacheKey != null) {
				ResponseCache.put(cacheKey, root);
			}
			return Optional.of(root);
		} catch (IOException | InterruptedException exception) {
			BetterPV.LOGGER.warn("Hypixel GET {} failed", url, exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		}
	}

	private static void logTiming(
		String url,
		HttpResponse<?> response,
		int bytes,
		RequestTiming timing,
		long startedNanos,
		long headersNanos,
		long bodyNanos,
		long gzipNanos,
		long jsonNanos,
		BodyReadTiming bodyRead
	) {
		long completedNanos = System.nanoTime();
		String path = url.startsWith(WORKER_BASE) ? url.substring(WORKER_BASE.length()) : url;
		BetterPV.LOGGER.info(
			"[PV timing] event=http_complete correlation_id={} path={} queue_ms={} admission_ms={} headers_ms={} body_ms={} first_body_byte_ms={} max_read_wait_ms={} read_calls={} http_version={} content_length={} transfer_encoding={} gzip_ms={} json_ms={} total_ms={} stream={} status={} bytes={} encoding={} cache={} server=[{}] upstream=[bytes={} enc={} prevAge={} {}] arrival=[{}]",
			timing.correlationId(),
			path,
			millis(timing.taskStartedNanos() - timing.queuedNanos()),
			millis(startedNanos - timing.taskStartedNanos()),
			millis(headersNanos - startedNanos),
			millis(bodyNanos - headersNanos),
			bodyRead.firstByteMillis(),
			bodyRead.maxReadWaitMillis(),
			bodyRead.readCalls(),
			response.version(),
			response.headers().firstValue("Content-Length").orElse("-"),
			response.headers().firstValue("Transfer-Encoding").orElse("-"),
			millis(gzipNanos),
			millis(jsonNanos),
			millis(completedNanos - timing.queuedNanos()),
			response.headers().firstValue("x-hypixel-stream").orElse("0"),
			response.statusCode(),
			bytes,
			response.headers().firstValue("Content-Encoding").orElse("none"),
			response.headers().firstValue("x-hypixel-cache").orElse("-"),
			response.headers().firstValue("server-timing").orElse(""),
			response.headers().firstValue("x-hypixel-upstream-bytes").orElse("-"),
			response.headers().firstValue("x-hypixel-upstream-encoding").orElse("-"),
			response.headers().firstValue("x-hypixel-prev-age").orElse("-"),
			response.headers().firstValue("x-hypixel-shape").orElse(""),
			response.headers().firstValue("x-hypixel-arrival").orElse("")
		);
	}

	private static long millis(long nanos) {
		return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(nanos));
	}

	private static String validCorrelationId(String value) {
		if (value != null && value.matches("[A-Za-z0-9._:-]{1,80}")) {
			return value;
		}
		return UUID.randomUUID().toString();
	}

	private record RequestTiming(String correlationId, long queuedNanos, long taskStartedNanos) {
		private static RequestTiming retry(String correlationId) {
			long now = System.nanoTime();
			return new RequestTiming(correlationId, now, now);
		}
	}

	static final class BodyReadTiming extends InputStream {
		private final InputStream delegate;
		private final long headersNanos;
		private long firstByteNanos;
		private long maxReadWaitNanos;
		private int readCalls;
		private int bytesRead;

		BodyReadTiming(InputStream delegate, long headersNanos) {
			this.delegate = delegate;
			this.headersNanos = headersNanos;
		}

		@Override
		public int read() throws IOException {
			long started = System.nanoTime();
			try {
				int value = this.delegate.read();
				recordRead(started, value < 0 ? 0 : 1);
				return value;
			} catch (IOException exception) {
				recordRead(started, 0);
				throw exception;
			}
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			long started = System.nanoTime();
			try {
				int count = this.delegate.read(bytes, offset, length);
				recordRead(started, Math.max(0, count));
				return count;
			} catch (IOException exception) {
				recordRead(started, 0);
				throw exception;
			}
		}

		private void recordRead(long started, int count) {
			long finished = System.nanoTime();
			this.readCalls++;
			this.maxReadWaitNanos = Math.max(this.maxReadWaitNanos, finished - started);
			if (count > 0) {
				if (this.firstByteNanos == 0L) {
					this.firstByteNanos = finished;
				}
				this.bytesRead += count;
			}
		}

		@Override
		public void close() throws IOException {
			this.delegate.close();
		}

		int bytesRead() {
			return this.bytesRead;
		}

		int readCalls() {
			return this.readCalls;
		}

		long firstByteMillis() {
			return this.firstByteNanos == 0L ? -1L : millis(this.firstByteNanos - this.headersNanos);
		}

		long maxReadWaitMillis() {
			return millis(this.maxReadWaitNanos);
		}
	}

	private static final class TimedInputStream extends InputStream {
		private final InputStream delegate;
		private long readNanos;

		private TimedInputStream(InputStream delegate) {
			this.delegate = delegate;
		}

		@Override
		public int read() throws IOException {
			long started = System.nanoTime();
			try {
				return this.delegate.read();
			} finally {
				this.readNanos += System.nanoTime() - started;
			}
		}

		@Override
		public int read(byte[] bytes, int offset, int length) throws IOException {
			long started = System.nanoTime();
			try {
				return this.delegate.read(bytes, offset, length);
			} finally {
				this.readNanos += System.nanoTime() - started;
			}
		}

		@Override
		public void close() throws IOException {
			this.delegate.close();
		}

		private long readNanos() {
			return this.readNanos;
		}
	}

	/**
	 * Enforces ~{@code SPACING_MS} between request starts without holding a lock during sleep.
	 * Callers sleep only for their own booked slot; other workers can claim later slots meanwhile.
	 */
	private static void waitForSlot() {
		long sleepMs;
		while (true) {
			long now = System.currentTimeMillis();
			long current = NEXT_REQUEST_AT_MILLIS.get();
			long scheduled = Math.max(now, current);
			if (NEXT_REQUEST_AT_MILLIS.compareAndSet(current, scheduled + SPACING_MS)) {
				sleepMs = scheduled - now;
				break;
			}
		}
		if (sleepMs <= 0L) {
			return;
		}
		try {
			Thread.sleep(sleepMs);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	public static String undashed(UUID uuid) {
		return uuid == null ? "" : uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	public static UUID parseUndashedUuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String hex = raw.trim().replace("-", "").toLowerCase(Locale.ROOT);
		if (hex.length() != 32) {
			return null;
		}
		try {
			String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
				+ "-" + hex.substring(16, 20) + "-" + hex.substring(20);
			return UUID.fromString(dashed);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	
	public static CompletableFuture<Optional<JsonObject>> status(UUID uuid) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(() -> {
			waitForSlot();
			return getJson(WORKER_BASE + "/hypixel/status/" + id);
		}, EXECUTOR);
	}


	private static final Duration NAME_HISTORY_TIMEOUT = Duration.ofSeconds(8);
	private static final String BROWSER_UA =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

	/**
	 * Public username history (oldest to newest).
	 * Parallel Laby + Crafty + NameMC; Ashcon only if all miss.
	 */
	public static CompletableFuture<Optional<com.google.gson.JsonArray>> usernameHistory(UUID uuid) {
		String id = undashed(uuid);
		String dashed = uuid == null ? "" : uuid.toString();
		String labyId = dashed.isBlank() ? id : dashed;
		CompletableFuture<Optional<com.google.gson.JsonArray>> laby =
			CompletableFuture.supplyAsync(() -> fetchLabyNameHistory(labyId), EXECUTOR)
				.exceptionally(error -> {
					BetterPV.LOGGER.warn("Laby name history future failed for {}", labyId, error);
					return Optional.empty();
				});
		CompletableFuture<Optional<com.google.gson.JsonArray>> crafty =
			CompletableFuture.supplyAsync(() -> fetchCraftyNameHistory(labyId), EXECUTOR)
				.exceptionally(error -> {
					BetterPV.LOGGER.warn("Crafty name history future failed for {}", labyId, error);
					return Optional.empty();
				});
		CompletableFuture<Optional<com.google.gson.JsonArray>> namemc =
			CompletableFuture.supplyAsync(() -> fetchNameMcNameHistory(labyId), EXECUTOR)
				.exceptionally(error -> {
					BetterPV.LOGGER.warn("NameMC name history future failed for {}", labyId, error);
					return Optional.empty();
				});
		return CompletableFuture.allOf(laby, crafty, namemc).thenApply(ignored -> {
			com.google.gson.JsonArray merged = new com.google.gson.JsonArray();
			mergeNameHistory(merged, laby.join());
			mergeNameHistory(merged, crafty.join());
			mergeNameHistory(merged, namemc.join());
			if (merged.size() == 0) {
				mergeNameHistory(merged, fetchAshconNameHistory(id));
			}
			if (merged.size() == 0) {
				return Optional.empty();
			}
			com.google.gson.JsonArray cleaned = collapseConsecutiveNameDupes(sortNameHistoryOldestFirst(merged));
			BetterPV.LOGGER.info("Username history for {}: {} name(s)", id, cleaned.size());
			return Optional.of(cleaned);
		});
	}

	private static Optional<com.google.gson.JsonArray> fetchLabyNameHistory(String uuidPath) {
		if (uuidPath == null || uuidPath.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = URI.create("https://laby.net/api/v3/user/" + uuidPath + "/names");
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(NAME_HISTORY_TIMEOUT)
				.header("User-Agent", BROWSER_UA)
				.header("Accept", "application/json")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300
				|| response.body() == null || response.body().isBlank()) {
				return Optional.empty();
			}
			JsonElement element = JsonParser.parseString(response.body());
			if (element.isJsonArray()) {
				return Optional.of(normalizeNameHistoryArray(element.getAsJsonArray()));
			}
			if (element.isJsonObject()) {
				JsonObject root = element.getAsJsonObject();
				if (root.has("username_history") && root.get("username_history").isJsonArray()) {
					return Optional.of(normalizeNameHistoryArray(root.getAsJsonArray("username_history")));
				}
				if (root.has("name_history") && root.get("name_history").isJsonArray()) {
					return Optional.of(normalizeNameHistoryArray(root.getAsJsonArray("name_history")));
				}
			}
			return Optional.empty();
		} catch (IOException | InterruptedException exception) {
			BetterPV.LOGGER.warn("Laby name history failed for {}", uuidPath, exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Laby name history parse failed for {}", uuidPath, exception);
			return Optional.empty();
		}
	}

	private static Optional<com.google.gson.JsonArray> fetchCraftyNameHistory(String uuidPath) {
		if (uuidPath == null || uuidPath.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = URI.create("https://api.crafty.gg/api/v2/players/" + uuidPath);
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(NAME_HISTORY_TIMEOUT)
				.header("User-Agent", BROWSER_UA)
				.header("Accept", "application/json")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300
				|| response.body() == null || response.body().isBlank()
				|| response.body().trim().startsWith("<")) {
				return Optional.empty();
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			JsonObject data = root.has("data") && root.get("data").isJsonObject()
				? root.getAsJsonObject("data") : root;
			if (!data.has("usernames") || !data.get("usernames").isJsonArray()) {
				return Optional.empty();
			}
			com.google.gson.JsonArray raw = data.getAsJsonArray("usernames");
			com.google.gson.JsonArray out = new com.google.gson.JsonArray();
			for (JsonElement el : raw) {
				if (el == null || !el.isJsonObject()) {
					continue;
				}
				JsonObject obj = el.getAsJsonObject();
				if (obj.has("hidden") && obj.get("hidden").isJsonPrimitive() && obj.get("hidden").getAsBoolean()) {
					continue;
				}
				String name = obj.has("username") && obj.get("username").isJsonPrimitive()
					? obj.get("username").getAsString() : "";
				if (name == null || name.isBlank()) {
					continue;
				}
				JsonObject normalized = new JsonObject();
				normalized.addProperty("username", name);
				if (obj.has("changed_at") && !obj.get("changed_at").isJsonNull()
					&& obj.get("changed_at").isJsonPrimitive()) {
					normalized.addProperty("changed_at", obj.get("changed_at").getAsString());
				}
				out.add(normalized);
			}
			return out.size() == 0 ? Optional.empty() : Optional.of(out);
		} catch (IOException | InterruptedException exception) {
			BetterPV.LOGGER.warn("Crafty name history failed for {}", uuidPath, exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Crafty name history parse failed for {}", uuidPath, exception);
			return Optional.empty();
		}
	}

	/**
	 * NameMC has no documented JSON name-history API - only the public profile HTML.
	 * Fetched once on user click; redacted rows ({@code &mdash;}) are skipped.
	 */
	private static Optional<com.google.gson.JsonArray> fetchNameMcNameHistory(String uuidPath) {
		if (uuidPath == null || uuidPath.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = URI.create("https://namemc.com/profile/" + uuidPath);
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(NAME_HISTORY_TIMEOUT)
				.header("User-Agent", BROWSER_UA)
				.header("Accept", "text/html,application/xhtml+xml")
				.header("Accept-Language", "en-US,en;q=0.9")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300
				|| response.body() == null || response.body().isBlank()) {
				return Optional.empty();
			}
			return parseNameMcHistoryHtml(response.body());
		} catch (IOException | InterruptedException exception) {
			BetterPV.LOGGER.warn("NameMC name history failed for {}", uuidPath, exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("NameMC name history parse failed for {}", uuidPath, exception);
			return Optional.empty();
		}
	}

	private static Optional<com.google.gson.JsonArray> parseNameMcHistoryHtml(String html) {
		if (html == null || html.isBlank()) {
			return Optional.empty();
		}
		int start = html.indexOf("<strong>Name History</strong>");
		if (start < 0) {
			return Optional.empty();
		}
		int end = html.indexOf("</table>", start);
		if (end < 0) {
			end = Math.min(html.length(), start + 80_000);
		}
		String chunk = html.substring(start, end);
		com.google.gson.JsonArray out = new com.google.gson.JsonArray();
		// Desktop rows only (skip mobile duplicate rows).
		java.util.regex.Pattern row = java.util.regex.Pattern.compile(
			"<tr>(?!\\s*class=\"d-lg-none)[\\s\\S]*?</tr>",
			java.util.regex.Pattern.CASE_INSENSITIVE
		);
		java.util.regex.Pattern nameLink = java.util.regex.Pattern.compile(
			"href=\"/search\\?q=([^\"]+)\"[^>]*>([^<]+)</a>"
		);
		java.util.regex.Pattern timePat = java.util.regex.Pattern.compile(
			"datetime=\"([^\"]+)\""
		);
		java.util.regex.Matcher rows = row.matcher(chunk);
		while (rows.find()) {
			String tr = rows.group();
			if (!tr.contains("fw-bold")) {
				continue;
			}
			java.util.regex.Matcher nm = nameLink.matcher(tr);
			if (!nm.find()) {
				continue; // redacted (&mdash;) or empty
			}
			String name = nm.group(2).trim();
			if (name.isBlank() || "-".equals(name) || "&mdash;".equalsIgnoreCase(name)) {
				continue;
			}
			JsonObject normalized = new JsonObject();
			normalized.addProperty("username", name);
			java.util.regex.Matcher tm = timePat.matcher(tr);
			if (tm.find()) {
				normalized.addProperty("changed_at", tm.group(1));
			}
			out.add(normalized);
		}
		return out.size() == 0 ? Optional.empty() : Optional.of(out);
	}

	private static void mergeNameHistory(com.google.gson.JsonArray into, Optional<com.google.gson.JsonArray> source) {
		if (into == null || source == null || source.isEmpty()) {
			return;
		}
		java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
		for (JsonElement el : into) {
			if (el != null && el.isJsonObject()) {
				String key = nameHistoryKey(el.getAsJsonObject());
				if (!key.isBlank()) {
					seen.add(key);
				}
			}
		}
		for (JsonElement el : source.get()) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject obj = el.getAsJsonObject();
			String key = nameHistoryKey(obj);
			if (key.isBlank() || seen.contains(key)) {
				continue;
			}
			seen.add(key);
			into.add(obj);
		}
	}

	private static String nameHistoryKey(JsonObject obj) {
		if (obj == null) {
			return "";
		}
		String name = "";
		if (obj.has("username") && obj.get("username").isJsonPrimitive()) {
			name = obj.get("username").getAsString();
		}
		if (name == null || name.isBlank()) {
			return "";
		}
		String changed = "";
		if (obj.has("changed_at") && obj.get("changed_at").isJsonPrimitive()) {
			changed = obj.get("changed_at").getAsString();
		}
		String day = "";
		if (changed != null && changed.length() >= 10) {
			day = changed.substring(0, 10);
		}
		return name.toLowerCase(Locale.ROOT) + "|" + day;
	}

	private static com.google.gson.JsonArray sortNameHistoryOldestFirst(com.google.gson.JsonArray raw) {
		java.util.ArrayList<JsonObject> list = new java.util.ArrayList<>();
		for (JsonElement el : raw) {
			if (el != null && el.isJsonObject()) {
				list.add(el.getAsJsonObject());
			}
		}
		list.sort((a, b) -> {
			String ca = a.has("changed_at") && a.get("changed_at").isJsonPrimitive()
				? a.get("changed_at").getAsString() : "";
			String cb = b.has("changed_at") && b.get("changed_at").isJsonPrimitive()
				? b.get("changed_at").getAsString() : "";
			if (ca.isBlank() && cb.isBlank()) {
				return 0;
			}
			if (ca.isBlank()) {
				return -1;
			}
			if (cb.isBlank()) {
				return 1;
			}
			return ca.compareTo(cb);
		});
		com.google.gson.JsonArray out = new com.google.gson.JsonArray();
		for (JsonObject obj : list) {
			out.add(obj);
		}
		return out;
	}

	private static com.google.gson.JsonArray collapseConsecutiveNameDupes(com.google.gson.JsonArray sorted) {
		com.google.gson.JsonArray out = new com.google.gson.JsonArray();
		String prev = null;
		for (JsonElement el : sorted) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject obj = el.getAsJsonObject();
			String name = obj.has("username") && obj.get("username").isJsonPrimitive()
				? obj.get("username").getAsString() : "";
			if (name == null || name.isBlank()) {
				continue;
			}
			String key = name.toLowerCase(Locale.ROOT);
			if (prev != null && prev.equals(key)) {
				continue;
			}
			prev = key;
			out.add(obj);
		}
		return out;
	}

	private static Optional<com.google.gson.JsonArray> fetchAshconNameHistory(String undashedUuid) {
		try {
			URI uri = URI.create("https://api.ashcon.app/mojang/v2/user/" + undashedUuid);
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(NAME_HISTORY_TIMEOUT)
				.header("User-Agent", BROWSER_UA)
				.header("Accept", "application/json")
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300
				|| response.body() == null || response.body().isBlank()) {
				return Optional.empty();
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			if (root.has("username_history") && root.get("username_history").isJsonArray()) {
				return Optional.of(normalizeNameHistoryArray(root.getAsJsonArray("username_history")));
			}
			return Optional.empty();
		} catch (IOException | InterruptedException exception) {
			BetterPV.LOGGER.warn("Ashcon name history failed for {}", undashedUuid, exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Ashcon name history parse failed for {}", undashedUuid, exception);
			return Optional.empty();
		}
	}

	private static com.google.gson.JsonArray normalizeNameHistoryArray(com.google.gson.JsonArray raw) {
		com.google.gson.JsonArray out = new com.google.gson.JsonArray();
		if (raw == null) {
			return out;
		}
		for (JsonElement el : raw) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject obj = el.getAsJsonObject();
			String name = "";
			if (obj.has("username") && obj.get("username").isJsonPrimitive()) {
				name = obj.get("username").getAsString();
			} else if (obj.has("name") && obj.get("name").isJsonPrimitive()) {
				name = obj.get("name").getAsString();
			}
			if (name == null || name.isBlank()) {
				continue;
			}
			String changed = "";
			if (obj.has("changed_at") && obj.get("changed_at").isJsonPrimitive()
				&& !obj.get("changed_at").isJsonNull()) {
				try {
					changed = obj.get("changed_at").getAsString();
				} catch (IllegalStateException | ClassCastException | UnsupportedOperationException ignored) {
					changed = "";
				}
			} else if (obj.has("changedAt") && obj.get("changedAt").isJsonPrimitive()) {
				changed = obj.get("changedAt").getAsString();
			}
			JsonObject normalized = new JsonObject();
			normalized.addProperty("username", name);
			if (changed != null && !changed.isBlank()) {
				normalized.addProperty("changed_at", changed);
			}
			out.add(normalized);
		}
		return out;
	}

	public record UuidName(UUID uuid, String name) {
	}
}
