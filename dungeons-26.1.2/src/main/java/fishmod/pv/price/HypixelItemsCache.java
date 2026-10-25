/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.price;

import fishmod.pv.api.FishProxyAuth;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import fishmod.pv.api.HypixelApiClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class HypixelItemsCache {
	private static final URI ITEMS_URI = URI.create("https://api.hypixel.net/v2/resources/skyblock/items");
	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final long REFRESH_HOURS = 12L;
	private static final HttpClient HTTP = HypixelApiClient.http();
	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "BetterPV-HypixelItems");
		t.setDaemon(true);
		return t;
	});

	private static final long RETRY_SECONDS = 30L;
	private static final java.util.concurrent.atomic.AtomicBoolean IN_FLIGHT = new java.util.concurrent.atomic.AtomicBoolean();
	private static final java.util.concurrent.atomic.AtomicBoolean RETRY_PENDING = new java.util.concurrent.atomic.AtomicBoolean();
	private static volatile Map<String, JsonObject> items = Map.of();
	private static volatile long lastRequestMs;

	private HypixelItemsCache() {
	}

	public static void start() {
		EXECUTOR.execute(HypixelItemsCache::refreshSafely);
		EXECUTOR.scheduleAtFixedRate(HypixelItemsCache::refreshSafely, REFRESH_HOURS, REFRESH_HOURS, TimeUnit.HOURS);
	}

	/** Kick a fetch now if nothing is loaded yet (the startup fetch fails before session auth exists). */
	public static void requestIfEmpty() {
		long now = System.currentTimeMillis();
		if (items.isEmpty() && 0L == 0L && !IN_FLIGHT.get() && now - lastRequestMs > 5_000L) {
			lastRequestMs = now;
			EXECUTOR.execute(HypixelItemsCache::refreshSafely);
		}
	}

	public static JsonObject get(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		JsonObject direct = items.get(itemId);
		if (direct != null) {
			return direct;
		}
		return items.get(itemId.toUpperCase(java.util.Locale.ROOT));
	}

	public static java.util.Collection<JsonObject> allItems() {
		return items.values();
	}

	private static void refreshSafely() {
		if (!IN_FLIGHT.compareAndSet(false, true)) {
			return;
		}
		try {
			refresh(true);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to refresh Hypixel items: {}", exception.getMessage());
			if (items.isEmpty() && RETRY_PENDING.compareAndSet(false, true)) {
				EXECUTOR.schedule(() -> {
					RETRY_PENDING.set(false);
					refreshSafely();
				}, Math.max(RETRY_SECONDS * 1000L, 0L), TimeUnit.MILLISECONDS);
			}
		} finally {
			IN_FLIGHT.set(false);
		}
	}

	private static void refresh(boolean allowReauth) throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(ITEMS_URI).timeout(TIMEOUT).GET();
		long startedNanos = System.nanoTime();
		HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 401) {
			FishProxyAuth.handleUnauthorized(
				response.request(),
				response.body(),
				TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
			);
			if (allowReauth) {
				refresh(false);
				return;
			}
			throw new IOException("Hypixel items unauthorized after re-auth");
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Hypixel items HTTP " + response.statusCode());
		}
		JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
		JsonArray list = root.has("items") && root.get("items").isJsonArray() ? root.getAsJsonArray("items") : null;
		if (list == null) {
			throw new IOException("Hypixel items missing array");
		}
		Map<String, JsonObject> next = new ConcurrentHashMap<>();
		for (JsonElement element : list) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject item = element.getAsJsonObject();
			if (item.has("id") && item.get("id").isJsonPrimitive()) {
				next.put(item.get("id").getAsString(), item);
			}
		}
		if (!next.isEmpty()) {
			items = Map.copyOf(next);
			BetterPV.LOGGER.info("Loaded {} Hypixel item definitions", next.size());
		}
	}
}
