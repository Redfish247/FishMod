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
import fishmod.pv.data.Leveling;
import java.util.Locale;

/** Shared JSON helpers for package-private profile parsers. */
final class ProfileParseSupport {
	private ProfileParseSupport() {
	}

	static String str(JsonElement element) {
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return "";
		}
		try {
			return element.getAsString();
		} catch (IllegalStateException | ClassCastException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	static boolean bool(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) {
			return false;
		}
		try {
			return obj.get(key).getAsBoolean();
		} catch (IllegalStateException | ClassCastException | UnsupportedOperationException ignored) {
			return false;
		}
	}

	static long readLong(JsonObject object, String key) {
		if (object == null || !object.has(key)) {
			return 0L;
		}
		Float num = Leveling.num(object.get(key));
		return num == null ? 0L : Math.round(num);
	}

	static double readDouble(JsonObject object, String key) {
		if (object == null || !object.has(key)) {
			return 0D;
		}
		Float num = Leveling.num(object.get(key));
		return num == null ? 0D : num.doubleValue();
	}

	static String title(String id) {
		return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
	}
}
