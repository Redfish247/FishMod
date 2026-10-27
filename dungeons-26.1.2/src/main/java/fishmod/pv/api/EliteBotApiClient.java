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
import fishmod.utils.Http;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Elite Farmers public API ({@code https://api.elitebot.dev}).
 * Used for Jacob contest history; credit in UI like SkyCofl.
 */
public final class EliteBotApiClient {
	private static final String BASE = "https://api.elitebot.dev";
	private static final Duration TIMEOUT = Duration.ofSeconds(15);
	private static final long SPACING_MS = 400L;

	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, PvExecutors.daemon("BetterPV-EliteBot"));
	private static long nextRequestAtMillis;

	private EliteBotApiClient() {
	}

	/** Contest participations for a profile (newest-first from API). */
	public static CompletableFuture<Optional<JsonArray>> contests(UUID playerUuid, String profileId) {
		if (playerUuid == null || profileId == null || profileId.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String player = HypixelApiClient.undashed(playerUuid);
		String profile = profileId.trim().replace("-", "").toLowerCase();
		String url = BASE + "/contests/" + player + "/" + profile;
		return CompletableFuture.supplyAsync(() -> getArray(url), EXECUTOR);
	}

	/** Farming weight for a profile. */
	public static CompletableFuture<Optional<JsonObject>> weight(UUID playerUuid, String profileId) {
		if (playerUuid == null || profileId == null || profileId.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String player = HypixelApiClient.undashed(playerUuid);
		String profile = profileId.trim().replace("-", "").toLowerCase();
		String url = BASE + "/weight/" + player + "/" + profile;
		return CompletableFuture.supplyAsync(() -> getObject(url), EXECUTOR);
	}

	private static Optional<JsonArray> getArray(String url) {
		return get(url).filter(JsonElement::isJsonArray).map(JsonElement::getAsJsonArray);
	}

	private static Optional<JsonObject> getObject(String url) {
		return get(url).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject);
	}

	private static Optional<JsonElement> get(String url) {
		waitForSlot();
		try {
			return Optional.of(Http.getJson(url, TIMEOUT));
		} catch (IOException | InterruptedException exception) {
			BetterPV.LOGGER.warn("Elite GET {} failed", url, exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Elite GET {} parse failed", url, exception);
			return Optional.empty();
		}
	}

	private static synchronized void waitForSlot() {
		long now = System.currentTimeMillis();
		long wait = nextRequestAtMillis - now;
		if (wait > 0L) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}
		nextRequestAtMillis = System.currentTimeMillis() + SPACING_MS;
	}
}
