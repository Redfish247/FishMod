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
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import fishmod.pv.data.SoftDataFailure;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SkyCofl public auction history ({@code https://sky.coflnet.com/api}).
 * No API key for player auctions/bids pages. Respect ~1 req/s pacing.
 */
public final class CoflnetApiClient {
	private static final String BASE = "https://sky.coflnet.com/api";
	private static final Duration TIMEOUT = Duration.ofSeconds(12);
	private static final long SPACING_MS = 350L;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "BetterPV-Coflnet");
		t.setDaemon(true);
		return t;
	});
	private static long nextRequestAtMillis;

	private CoflnetApiClient() {
	}

	/** Auctions the player created. */
	public static CompletableFuture<Optional<JsonArray>> playerAuctions(UUID uuid, int page) {
		String id = HypixelApiClient.undashed(uuid);
		int p = Math.max(0, page);
		long queuedNanos = System.nanoTime();
		return CompletableFuture.supplyAsync(
			() -> getArray(BASE + "/player/" + id + "/auctions?page=" + p, queuedNanos),
			EXECUTOR
		);
	}

	/** Bids the player made. */
	public static CompletableFuture<Optional<JsonArray>> playerBids(UUID uuid, int page) {
		String id = HypixelApiClient.undashed(uuid);
		int p = Math.max(0, page);
		long queuedNanos = System.nanoTime();
		return CompletableFuture.supplyAsync(
			() -> getArray(BASE + "/player/" + id + "/bids?page=" + p, queuedNanos),
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> auction(String auctionId) {
		if (auctionId == null || auctionId.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String id = auctionId.replace("-", "").toLowerCase();
		long queuedNanos = System.nanoTime();
		return CompletableFuture.supplyAsync(
			() -> getObject(BASE + "/auction/" + id, queuedNanos),
			EXECUTOR
		);
	}

	private static Optional<JsonArray> getArray(String url, long queuedNanos) {
		Optional<JsonElement> root = getJson(url, queuedNanos);
		if (root.isEmpty()) {
			return Optional.empty();
		}
		JsonElement el = root.get();
		if (el.isJsonArray()) {
			return Optional.of(el.getAsJsonArray());
		}
		return Optional.empty();
	}

	private static Optional<JsonObject> getObject(String url, long queuedNanos) {
		Optional<JsonElement> root = getJson(url, queuedNanos);
		if (root.isEmpty() || !root.get().isJsonObject()) {
			return Optional.empty();
		}
		return Optional.of(root.get().getAsJsonObject());
	}

	private static Optional<JsonElement> getJson(String url, long queuedNanos) {
		long dequeuedNanos = System.nanoTime();
		waitForSlot();
		long sentNanos = System.nanoTime();
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			BetterPV.LOGGER.info(
				"[PV timing] Coflnet {} {}ms queue={}ms spacing={}ms http={}ms status={} chars={}",
				url.substring(BASE.length()),
				(System.nanoTime() - queuedNanos) / 1_000_000L,
				(dequeuedNanos - queuedNanos) / 1_000_000L,
				(sentNanos - dequeuedNanos) / 1_000_000L,
				(System.nanoTime() - sentNanos) / 1_000_000L,
				response.statusCode(),
				response.body() == null ? 0 : response.body().length()
			);
			if (response.statusCode() < 200 || response.statusCode() >= 300
				|| response.body() == null || response.body().isBlank()) {
				BetterPV.LOGGER.warn("Coflnet {} → HTTP {}", url, response.statusCode());
				return Optional.empty();
			}
			return Optional.of(JsonParser.parseString(response.body()));
		} catch (IOException | InterruptedException exception) {
			BetterPV.LOGGER.warn(
				"Coflnet request failed after {}ms: {}",
				(System.nanoTime() - sentNanos) / 1_000_000L,
				url,
				exception
			);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Coflnet parse failed: {}", url, exception);
			return Optional.empty();
		}
	}

	private static synchronized void waitForSlot() {
		long now = System.currentTimeMillis();
		long wait = nextRequestAtMillis - now;
		if (wait > 0L) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			now = System.currentTimeMillis();
		}
		nextRequestAtMillis = now + SPACING_MS;
	}

	public static List<JsonObject> objects(JsonArray array) {
		List<JsonObject> out = new ArrayList<>();
		if (array == null) {
			return out;
		}
		for (JsonElement el : array) {
			if (el != null && el.isJsonObject()) {
				out.add(el.getAsJsonObject());
			}
		}
		return out;
	}
}
