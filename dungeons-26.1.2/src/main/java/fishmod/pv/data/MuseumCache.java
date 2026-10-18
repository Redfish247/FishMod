/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-lifetime museum cache. Fetched only when the Museum tab is opened (or refreshed).
 * Refresh is rate-limited to once per {@link #REFRESH_COOLDOWN_MS}.
 */
public final class MuseumCache {
	public static final long REFRESH_COOLDOWN_MS = 30L * 60L * 1000L;

	public record Entry(JsonObject museumMember, long fetchedAtMs, int itemCount) {
		public Entry {
			museumMember = museumMember == null ? new JsonObject() : museumMember;
			itemCount = Math.max(0, itemCount);
		}
	}

	private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();
	/** Earliest time a manual refresh is allowed (after a successful fetch/refresh). */
	private static final ConcurrentHashMap<String, Long> NEXT_REFRESH_AT_MS = new ConcurrentHashMap<>();

	private MuseumCache() {
	}

	public static String key(UUID uuid, String profileId) {
		String u = uuid == null ? "" : uuid.toString().replace("-", "").toLowerCase();
		String p = profileId == null ? "" : profileId.trim().toLowerCase();
		return u + "|" + p;
	}

	public static Entry get(UUID uuid, String profileId) {
		return CACHE.get(key(uuid, profileId));
	}

	public static boolean has(UUID uuid, String profileId) {
		return get(uuid, profileId) != null;
	}

	public static void put(UUID uuid, String profileId, JsonObject museumMember, int itemCount) {
		String k = key(uuid, profileId);
		CACHE.put(k, new Entry(museumMember, System.currentTimeMillis(), itemCount));
		NEXT_REFRESH_AT_MS.put(k, System.currentTimeMillis() + REFRESH_COOLDOWN_MS);
	}

	public static void invalidate(UUID uuid, String profileId) {
		String k = key(uuid, profileId);
		CACHE.remove(k);
		// Keep NEXT_REFRESH_AT_MS so manual refresh cooldown still applies.
	}

	public static boolean canRefresh(UUID uuid, String profileId) {
		Long next = NEXT_REFRESH_AT_MS.get(key(uuid, profileId));
		return next == null || System.currentTimeMillis() >= next;
	}

	public static long refreshReadyInMs(UUID uuid, String profileId) {
		Long next = NEXT_REFRESH_AT_MS.get(key(uuid, profileId));
		if (next == null) {
			return 0L;
		}
		return Math.max(0L, next - System.currentTimeMillis());
	}

	/** Clear everything (e.g. when viewing a different profile). */
	public static void clearAll() {
		CACHE.clear();
		NEXT_REFRESH_AT_MS.clear();
	}
}
