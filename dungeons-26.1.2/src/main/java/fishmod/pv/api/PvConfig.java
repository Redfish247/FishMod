/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.ProfileViewer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class PvConfig {
	private static final String FILE_NAME = "config.json";
	private static final String PROFILE_SCALE_KEY = "profileViewerScalePercent";
	// Finished layout trials; drop their old keys from existing configs.
	static final String[] REMOVED_TRIAL_KEYS = { "trialEssenceTab", "trialSkillOverviews", "trialFishingTrophies" };
	private static final String SEARCH_HISTORY_KEY = "searchHistory";
	private static volatile int profileViewerScalePercent = 100;
	private static volatile SearchHistory searchHistory = SearchHistory.empty();
	private static JsonObject document = new JsonObject();

	private PvConfig() {
	}

	public static synchronized void load() {
		Path path = configPath();
		if (Files.isRegularFile(path)) {
			try {
				JsonObject loaded = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
				document = loaded;
				if (loaded.has(PROFILE_SCALE_KEY) && loaded.get(PROFILE_SCALE_KEY).isJsonPrimitive()) {
					profileViewerScalePercent = normalizeProfileScale(loaded.get(PROFILE_SCALE_KEY).getAsInt());
				}
				searchHistory = readSearchHistory(loaded);
			} catch (Exception exception) {
				ProfileViewer.LOGGER.warn("Could not load PV config", exception);
				document = new JsonObject();
				profileViewerScalePercent = 100;
				searchHistory = SearchHistory.empty();
			}
		}
		if (dropRemovedTrialKeys(document)) {
			save();
		}
		ProfileViewer.LOGGER.info("Hypixel: fishmod.dev proxy");
	}

	public static int profileViewerScalePercent() {
		return normalizeProfileScale(fishmod.utils.config.values.FishSettings.guiScaleProfileViewer);
	}

	/** Optional override for the proxy fallback host (config "fallbackHost"). */
	public static String fallbackHost() {
		return document.has("fallbackHost") && document.get("fallbackHost").isJsonPrimitive()
			? document.get("fallbackHost").getAsString().trim() : "";
	}

	public static synchronized void setProfileViewerScalePercent(int percent) {
		int normalized = normalizeProfileScale(percent);
		if (normalized == profileViewerScalePercent) {
			return;
		}
		profileViewerScalePercent = normalized;
		document.addProperty(PROFILE_SCALE_KEY, normalized);
		save();
	}

	public static SearchHistory searchHistory() {
		return searchHistory;
	}

	public static synchronized void recordSearch(UUID uuid, String name) {
		SearchHistory next = searchHistory.record(uuid, name);
		if (next.entries().equals(searchHistory.entries())) {
			return;
		}
		searchHistory = next;
		writeSearchHistory(document, next);
		save();
	}

	static SearchHistory readSearchHistory(JsonObject doc) {
		return SearchHistory.fromJson(doc.get(SEARCH_HISTORY_KEY));
	}

	static void writeSearchHistory(JsonObject doc, SearchHistory history) {
		doc.add(SEARCH_HISTORY_KEY, history.toJson());
	}

	static boolean dropRemovedTrialKeys(JsonObject doc) {
		boolean removed = false;
		for (String key : REMOVED_TRIAL_KEYS) {
			removed |= doc.remove(key) != null;
		}
		return removed;
	}

	static int normalizeProfileScale(int percent) {
		int clamped = Math.max(75, Math.min(150, percent));
		return Math.round(clamped / 5.0F) * 5;
	}

	private static Path configPath() {
		return ProfileViewer.DIR.resolve(FILE_NAME);
	}

	private static void save() {
		Path path = configPath();
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(temporary, document.toString(), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException ignored) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			ProfileViewer.LOGGER.warn("Could not save PV config", exception);
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException ignored) {
			}
		}
	}
}
