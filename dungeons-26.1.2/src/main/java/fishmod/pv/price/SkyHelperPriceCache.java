/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.price;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
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

/**
 * SkyHelper {@code pricesV2.json} - needed for leveled pet keys and missing Athen IDs.
 */
public final class SkyHelperPriceCache {
	private static final URI PRICES_URI = URI.create(
		"https://raw.githubusercontent.com/SkyHelperBot/Prices/main/pricesV2.json"
	);
	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final long REFRESH_MINUTES = 30L;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "BetterPV-SkyHelperPrices");
		t.setDaemon(true);
		return t;
	});

	private static volatile Map<String, Double> prices = Map.of();
	private static volatile boolean ready;

	private SkyHelperPriceCache() {
	}

	public static void start() {
		EXECUTOR.execute(SkyHelperPriceCache::refreshSafely);
		EXECUTOR.scheduleAtFixedRate(SkyHelperPriceCache::refreshSafely, REFRESH_MINUTES, REFRESH_MINUTES, TimeUnit.MINUTES);
	}

	public static boolean isReady() {
		return ready && !prices.isEmpty();
	}

	public static Double get(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		return prices.get(itemId.toUpperCase(java.util.Locale.ROOT));
	}

	private static void refreshSafely() {
		try {
			refresh();
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to refresh SkyHelper prices", exception);
		}
	}

	private static void refresh() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(PRICES_URI).timeout(TIMEOUT).GET().build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("SkyHelper prices HTTP " + response.statusCode());
		}
		JsonElement root = JsonParser.parseString(response.body());
		if (!root.isJsonObject()) {
			throw new IOException("SkyHelper prices not an object");
		}
		Map<String, Double> next = new ConcurrentHashMap<>();
		for (var entry : root.getAsJsonObject().entrySet()) {
			JsonElement value = entry.getValue();
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				double price = value.getAsDouble();
				if (price > 0) {
					next.put(entry.getKey().toUpperCase(java.util.Locale.ROOT), price);
				}
			}
		}
		if (!next.isEmpty()) {
			prices = Map.copyOf(next);
			ready = true;
			BetterPV.LOGGER.info("Loaded {} SkyHelper price entries", next.size());
		}
	}
}
