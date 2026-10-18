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

public final class AthenPriceCache {
	private static final URI PRICES_URI = URI.create("https://athen.aerii.xyz/prices");
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final long REFRESH_MINUTES = 10L;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "BetterPV-AthenPrices");
		t.setDaemon(true);
		return t;
	});

	private static volatile Map<String, Double> prices = Map.of();
	/** Bazaar-only unit prices (preferred for sack / material valuation). */
	private static volatile Map<String, Double> bazaarPrices = Map.of();
	private static volatile boolean ready;

	private AthenPriceCache() {
	}

	public static void start() {
		EXECUTOR.execute(AthenPriceCache::refreshSafely);
		EXECUTOR.scheduleAtFixedRate(AthenPriceCache::refreshSafely, REFRESH_MINUTES, REFRESH_MINUTES, TimeUnit.MINUTES);
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

	/** Instant / bazaar sell-side price only — never AH LBIN. */
	public static Double getBazaar(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		return bazaarPrices.get(itemId.toUpperCase(java.util.Locale.ROOT));
	}

	private static void refreshSafely() {
		try {
			refresh();
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to refresh Vyriv prices", exception);
		}
	}

	private static void refresh() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(PRICES_URI).timeout(TIMEOUT).GET().build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Vyriv prices HTTP " + response.statusCode());
		}
		JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
		Map<String, Double> next = new ConcurrentHashMap<>();
		Map<String, Double> nextBazaar = new ConcurrentHashMap<>();
		// Bazaar first so materials use IB/sell — AH LBIN must not stomp sack prices.
		loadBazaar(root.getAsJsonObject("bazaar"), next, nextBazaar);
		loadAuctionHouse(root.getAsJsonObject("auction_house"), next);
		if (!next.isEmpty()) {
			prices = Map.copyOf(next);
			bazaarPrices = Map.copyOf(nextBazaar);
			ready = true;
			BetterPV.LOGGER.info("Loaded {} Vyriv price entries ({} bazaar)", next.size(), nextBazaar.size());
		}
	}

	private static void loadAuctionHouse(JsonObject section, Map<String, Double> out) {
		if (section == null) {
			return;
		}
		for (var entry : section.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			String key = entry.getKey().toUpperCase(java.util.Locale.ROOT);
			if (out.containsKey(key)) {
				continue;
			}
			JsonObject obj = entry.getValue().getAsJsonObject();
			Double price = firstPositive(num(obj, "p3d"), num(obj, "lbin"), num(obj, "p7d"));
			if (price != null) {
				out.put(key, price);
			}
		}
	}

	private static void loadBazaar(JsonObject section, Map<String, Double> out, Map<String, Double> bazaarOut) {
		if (section == null) {
			return;
		}
		for (var entry : section.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject obj = entry.getValue().getAsJsonObject();
			String key = entry.getKey().toUpperCase(java.util.Locale.ROOT);
			Double price = firstPositive(num(obj, "ib"), num(obj, "tb"), num(obj, "is"), num(obj, "ts"));
			if (price != null) {
				out.put(key, price);
				bazaarOut.put(key, price);
			}
		}
	}

	private static Double num(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			double value = element.getAsDouble();
			return value > 0 ? value : null;
		}
		return null;
	}

	private static Double firstPositive(Double... values) {
		for (Double value : values) {
			if (value != null && value > 0) {
				return value;
			}
		}
		return null;
	}
}
