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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HypixelCollectionsCache {
	private static final URI COLLECTIONS_URI = URI.create("https://api.hypixel.net/v2/resources/skyblock/collections");
	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final long REFRESH_HOURS = 12L;
	private static final long RETRY_SECONDS = 60L;
	private static final AtomicBoolean REFRESHING = new AtomicBoolean();
	private static final AtomicBoolean RETRY_PENDING = new AtomicBoolean();
	private static final HttpClient HTTP = HypixelApiClient.http();
	private static final ScheduledExecutorService EXECUTOR = fishmod.pv.api.PvExecutors.SCHEDULER;

	public record Tier(int tier, long amountRequired, List<String> unlocks) {
		public Tier {
			unlocks = unlocks == null ? List.of() : List.copyOf(unlocks);
		}
	}

	public record Item(String id, String name, int maxTiers, List<Tier> tiers) {
		public Item {
			id = id == null ? "" : id;
			name = name == null || name.isBlank() ? id : name;
			tiers = tiers == null ? List.of() : List.copyOf(tiers);
		}

		public int tierFor(long amount) {
			int reached = 0;
			for (Tier tier : tiers) {
				if (amount >= tier.amountRequired()) {
					reached = Math.max(reached, tier.tier());
				}
			}
			return reached;
		}

		public float progressToNext(long amount) {
			if (tiers.isEmpty()) {
				return amount > 0 ? 1f : 0f;
			}
			Tier next = null;
			Tier prev = null;
			for (Tier tier : tiers) {
				if (amount < tier.amountRequired()) {
					next = tier;
					break;
				}
				prev = tier;
			}
			if (next == null) {
				return 1f;
			}
			long from = prev == null ? 0L : prev.amountRequired();
			long span = Math.max(1L, next.amountRequired() - from);
			return Math.max(0f, Math.min(1f, (amount - from) / (float) span));
		}

		public Tier nextTier(long amount) {
			for (Tier tier : tiers) {
				if (amount < tier.amountRequired()) {
					return tier;
				}
			}
			return tiers.isEmpty() ? null : tiers.get(tiers.size() - 1);
		}

		public Tier currentTier(long amount) {
			Tier best = null;
			for (Tier tier : tiers) {
				if (amount >= tier.amountRequired()) {
					best = tier;
				}
			}
			return best;
		}
	}

	public record Category(String id, String name, List<Item> items) {
		public Category {
			id = id == null ? "" : id;
			name = name == null || name.isBlank() ? id : name;
			items = items == null ? List.of() : List.copyOf(items);
		}
	}

	private static volatile List<Category> categories = List.of();
	private static volatile Map<String, Item> itemsById = Map.of();

	private HypixelCollectionsCache() {
	}

	public static void start() {
		requestRefresh();
		EXECUTOR.scheduleAtFixedRate(HypixelCollectionsCache::refreshSafely, REFRESH_HOURS, REFRESH_HOURS, TimeUnit.HOURS);
	}

	private static void requestRefresh() {
		if (REFRESHING.compareAndSet(false, true)) {
			EXECUTOR.execute(() -> {
				try {
					refreshSafely();
				} finally {
					REFRESHING.set(false);
				}
			});
		}
	}

	public static boolean isReady() {
		return !categories.isEmpty();
	}

	public static List<Category> categories() {
		return categories;
	}

	public static Item item(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		Item direct = itemsById.get(id);
		if (direct != null) {
			return direct;
		}
		String upper = id.toUpperCase(Locale.ROOT);
		Item byUpper = itemsById.get(upper);
		if (byUpper != null) {
			return byUpper;
		}
		return itemsById.get(upper.replace('-', ':'));
	}

	/** Wait briefly so profile parse can attach tier tables. */
	public static void awaitReady(long timeoutMs) {
		if (!isReady()) {
			requestRefresh();
		}
		long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
		while (!isReady() && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(50L);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static void refreshSafely() {
		try {
			refresh(true);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to refresh Hypixel collections", exception);
			// Startup can run before session auth works; don't wait for the 12h refresh.
			if (!isReady() && RETRY_PENDING.compareAndSet(false, true)) {
				EXECUTOR.schedule(() -> {
					RETRY_PENDING.set(false);
					requestRefresh();
				}, Math.max(RETRY_SECONDS * 1000L, 0L), TimeUnit.MILLISECONDS);
			}
		}
	}

	private static void refresh(boolean allowReauth) throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(COLLECTIONS_URI).timeout(TIMEOUT).GET();
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
			throw new IOException("Hypixel collections unauthorized after re-auth");
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Hypixel collections HTTP " + response.statusCode());
		}
		JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
		JsonObject collections = root.has("collections") && root.get("collections").isJsonObject()
			? root.getAsJsonObject("collections")
			: null;
		if (collections == null) {
			throw new IOException("Hypixel collections missing object");
		}

		List<Category> nextCategories = new ArrayList<>();
		Map<String, Item> nextItems = new LinkedHashMap<>();
		for (var catEntry : collections.entrySet()) {
			if (!catEntry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject catObj = catEntry.getValue().getAsJsonObject();
			String catId = catEntry.getKey().toUpperCase(Locale.ROOT);
			String catName = catObj.has("name") ? catObj.get("name").getAsString() : title(catId);
			JsonObject itemsObj = catObj.has("items") && catObj.get("items").isJsonObject()
				? catObj.getAsJsonObject("items")
				: null;
			List<Item> items = new ArrayList<>();
			if (itemsObj != null) {
				for (var itemEntry : itemsObj.entrySet()) {
					if (!itemEntry.getValue().isJsonObject()) {
						continue;
					}
					Item item = parseItem(itemEntry.getKey(), itemEntry.getValue().getAsJsonObject());
					items.add(item);
					nextItems.put(item.id(), item);
					String alt = item.id().replace(':', '-');
					if (!alt.equals(item.id())) {
						nextItems.putIfAbsent(alt, item);
					}
				}
			}
			nextCategories.add(new Category(catId, catName, items));
		}

		if (!nextCategories.isEmpty()) {
			categories = List.copyOf(nextCategories);
			itemsById = Map.copyOf(nextItems);
			BetterPV.LOGGER.info(
				"Loaded {} Hypixel collection categories ({} items)",
				nextCategories.size(),
				nextItems.size()
			);
		}
	}

	private static Item parseItem(String id, JsonObject obj) {
		String name = obj.has("name") ? obj.get("name").getAsString() : id;
		int maxTiers = obj.has("maxTiers") ? obj.get("maxTiers").getAsInt() : 0;
		List<Tier> tiers = new ArrayList<>();
		if (obj.has("tiers") && obj.get("tiers").isJsonArray()) {
			JsonArray arr = obj.getAsJsonArray("tiers");
			for (JsonElement el : arr) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject t = el.getAsJsonObject();
				int tier = t.has("tier") ? t.get("tier").getAsInt() : 0;
				long amount = t.has("amountRequired") ? Math.round(t.get("amountRequired").getAsDouble()) : 0L;
				List<String> unlocks = new ArrayList<>();
				if (t.has("unlocks") && t.get("unlocks").isJsonArray()) {
					for (JsonElement u : t.getAsJsonArray("unlocks")) {
						if (u.isJsonPrimitive()) {
							unlocks.add(u.getAsString());
						}
					}
				}
				tiers.add(new Tier(tier, amount, unlocks));
			}
		}
		if (maxTiers <= 0) {
			maxTiers = tiers.size();
		}
		return new Item(id, name, maxTiers, tiers);
	}

	private static String title(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String lower = id.toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
