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
import fishmod.pv.BetterPV;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Five-minute cache for proxy responses: in memory, mirrored to disk so a restart still hits. */
final class ResponseCache {
	private static final long TTL_MS = 5L * 60L * 1000L;
	private static final int MAX_MEMORY = 24;
	private static final Path DIR = Path.of(System.getProperty("user.home"), ".betterpv", "response-cache");

	private record Cached(JsonObject json, long storedAtMs) {}

	private static final Map<String, Cached> MEMORY = Collections.synchronizedMap(
		new LinkedHashMap<String, Cached>(32, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
				return size() > MAX_MEMORY;
			}
		}
	);

	private ResponseCache() {}

	static Optional<JsonObject> get(String key) {
		long now = System.currentTimeMillis();
		Cached hit = MEMORY.get(key);
		if (hit != null) {
			if (now - hit.storedAtMs < TTL_MS) {
				return Optional.of(hit.json.deepCopy());
			}
			MEMORY.remove(key);
		}
		Path file = fileFor(key);
		try {
			if (Files.isRegularFile(file)) {
				long stored = Files.getLastModifiedTime(file).toMillis();
				if (now - stored < TTL_MS) {
					try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
						JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
						MEMORY.put(key, new Cached(json, stored));
						return Optional.of(json.deepCopy());
					}
				}
				Files.deleteIfExists(file);
			}
		} catch (IOException | RuntimeException exception) {
			BetterPV.LOGGER.debug("Response cache read failed for {}", key, exception);
		}
		return Optional.empty();
	}

	static void put(String key, JsonObject original) {
		// Own copy: caller mutates its object while the disk write serializes on another thread.
		JsonObject json = original.deepCopy();
		MEMORY.put(key, new Cached(json, System.currentTimeMillis()));
		HypixelApiClient.networkExecutor().execute(() -> {
			try {
				Files.createDirectories(DIR);
				Path file = fileFor(key);
				Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
				Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8);
				Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException | RuntimeException exception) {
				BetterPV.LOGGER.debug("Response cache write failed for {}", key, exception);
			}
		});
	}

	private static Path fileFor(String key) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return DIR.resolve(sb + ".json");
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
