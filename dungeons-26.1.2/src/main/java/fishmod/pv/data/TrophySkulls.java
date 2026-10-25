/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Legacy NEU skull texture Values for trophy fish/frogs (pre-ItemModel paper). */
public final class TrophySkulls {
	private static volatile boolean loaded;
	private static final Map<String, String> VALUES = new ConcurrentHashMap<>();

	private TrophySkulls() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (TrophySkulls.class) {
			if (loaded) {
				return;
			}
			load();
			loaded = true;
		}
	}

	public static String value(String skyblockId) {
		ensureLoaded();
		if (skyblockId == null || skyblockId.isBlank()) {
			return null;
		}
		return VALUES.get(skyblockId.toUpperCase(Locale.ROOT));
	}

	private static void load() {
		try (InputStream in = TrophySkulls.class.getResourceAsStream("/assets/betterpv/data/trophy_skulls.json")) {
			if (in == null) {
				BetterPV.LOGGER.warn("Missing trophy_skulls.json");
				return;
			}
			JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			if (root == null || !root.isJsonObject()) {
				return;
			}
			JsonObject obj = root.getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
				if (e.getKey() == null || e.getValue() == null || !e.getValue().isJsonPrimitive()) {
					continue;
				}
				String v = e.getValue().getAsString();
				if (v != null && !v.isBlank()) {
					VALUES.put(e.getKey().toUpperCase(Locale.ROOT), v);
				}
			}
		} catch (IOException | RuntimeException ex) {
			if (ex instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			BetterPV.LOGGER.warn("Failed to load trophy_skulls.json", ex);
		}
	}
}
