/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Name to UUID lookups kept across sessions so repeat /pv targets skip Mojang. */
final class NameUuidStore {
	// Mojang holds a changed name for 37 days before anyone else can claim it, so a
	// shorter TTL can never map a name to the wrong account.
	private static final long TTL_MS = TimeUnit.DAYS.toMillis(30);
	private static final int MAX_ENTRIES = 2000;
	private static final Path FILE = Path.of(System.getProperty("user.home"), ".betterpv", "name-uuid-cache.json");

	private record Entry(UUID uuid, String name, long savedAtMs) {
		boolean fresh(long now) {
			return now - this.savedAtMs < TTL_MS;
		}
	}

	private static final ConcurrentHashMap<String, Entry> ENTRIES = new ConcurrentHashMap<>();
	private static final AtomicBoolean SAVE_SCHEDULED = new AtomicBoolean();
	private static volatile boolean loaded;

	private NameUuidStore() {
	}

	record Stored(HypixelApiClient.UuidName value, long savedAtMs) {
	}

	static Stored get(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		ensureLoaded();
		Entry entry = ENTRIES.get(name.toLowerCase(Locale.ROOT));
		if (entry == null || !entry.fresh(System.currentTimeMillis())) {
			return null;
		}
		return new Stored(new HypixelApiClient.UuidName(entry.uuid(), entry.name()), entry.savedAtMs());
	}

	// savedAtMs is when the mapping was last confirmed, not when it was copied here,
	// so an entry taken from the server cache can't outlive the 37 day hold.
	static void put(String name, UUID uuid, String resolvedName, long savedAtMs) {
		if (name == null || name.isBlank() || uuid == null) {
			return;
		}
		ensureLoaded();
		long now = System.currentTimeMillis();
		Entry entry = new Entry(uuid, resolvedName == null || resolvedName.isBlank() ? name : resolvedName, Math.min(savedAtMs, now));
		if (!entry.fresh(now)) {
			return;
		}
		ENTRIES.put(name.toLowerCase(Locale.ROOT), entry);
		ENTRIES.put(entry.name().toLowerCase(Locale.ROOT), entry);
		forgetOtherNames(uuid, entry.name());
		scheduleSave();
	}

	static void forget(String name) {
		if (name == null || name.isBlank()) {
			return;
		}
		ensureLoaded();
		if (ENTRIES.remove(name.toLowerCase(Locale.ROOT)) != null) {
			scheduleSave();
		}
	}

	// Drops names this account no longer has once its current name is confirmed.
	static void forgetOtherNames(UUID uuid, String currentName) {
		if (uuid == null || currentName == null) {
			return;
		}
		ensureLoaded();
		String current = currentName.toLowerCase(Locale.ROOT);
		boolean removed = ENTRIES.entrySet().removeIf(e -> uuid.equals(e.getValue().uuid()) && !e.getKey().equals(current));
		if (removed) {
			scheduleSave();
		}
	}

	private static synchronized void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.isRegularFile(FILE)) {
			return;
		}
		long now = System.currentTimeMillis();
		try {
			JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				if (!e.getValue().isJsonObject()) {
					continue;
				}
				JsonObject value = e.getValue().getAsJsonObject();
				UUID uuid = HypixelApiClient.parseUndashedUuid(value.has("uuid") ? value.get("uuid").getAsString() : null);
				String name = value.has("name") ? value.get("name").getAsString() : null;
				long savedAt = value.has("savedAt") ? value.get("savedAt").getAsLong() : 0L;
				Entry entry = new Entry(uuid, name, savedAt);
				if (uuid != null && name != null && entry.fresh(now)) {
					ENTRIES.put(e.getKey(), entry);
				}
			}
		} catch (IOException | RuntimeException exception) {
			BetterPV.LOGGER.warn("Could not read name cache {}", FILE, exception);
		}
	}

	private static void scheduleSave() {
		if (SAVE_SCHEDULED.compareAndSet(false, true)) {
			CompletableFuture.runAsync(NameUuidStore::save, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS));
		}
	}

	private static void save() {
		SAVE_SCHEDULED.set(false);
		long now = System.currentTimeMillis();
		List<Map.Entry<String, Entry>> live = new ArrayList<>();
		for (Map.Entry<String, Entry> e : ENTRIES.entrySet()) {
			if (e.getValue().fresh(now)) {
				live.add(e);
			} else {
				ENTRIES.remove(e.getKey(), e.getValue());
			}
		}
		live.sort((a, b) -> Long.compare(b.getValue().savedAtMs(), a.getValue().savedAtMs()));
		JsonObject root = new JsonObject();
		for (int i = 0; i < live.size(); i++) {
			Map.Entry<String, Entry> e = live.get(i);
			if (i >= MAX_ENTRIES) {
				ENTRIES.remove(e.getKey(), e.getValue());
				continue;
			}
			JsonObject value = new JsonObject();
			value.addProperty("uuid", HypixelApiClient.undashed(e.getValue().uuid()));
			value.addProperty("name", e.getValue().name());
			value.addProperty("savedAt", e.getValue().savedAtMs());
			root.add(e.getKey(), value);
		}
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			BetterPV.LOGGER.warn("Could not save name cache {}", FILE, exception);
		}
	}
}
