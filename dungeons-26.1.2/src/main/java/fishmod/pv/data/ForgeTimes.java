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
import fishmod.pv.neu.NeuRepoCache;

/** Forge durations from NEU forge recipes, with the profile's Quick Forge reduction applied. */
public final class ForgeTimes {
	private ForgeTimes() {
	}

	// Quick Forge: -(10 + 0.5 * level)% forge time, jumping to -30% at level 20.
	public static double quickForgeMultiplier(int level) {
		if (level <= 0) {
			return 1D;
		}
		if (level >= 20) {
			return 0.7D;
		}
		return 1D - (10D + 0.5D * level) / 100D;
	}

	/** Base recipe duration in ms, or 0 when NEU has no forge recipe for the item (or isn't loaded yet). */
	public static long baseDurationMs(String itemId) {
		JsonObject item = NeuRepoCache.get(itemId);
		if (item == null || !item.has("recipes") || !item.get("recipes").isJsonArray()) {
			return 0L;
		}
		for (JsonElement el : item.getAsJsonArray("recipes")) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject recipe = el.getAsJsonObject();
			try {
				if (!recipe.has("type") || !"forge".equals(recipe.get("type").getAsString()) || !recipe.has("duration")) {
					continue;
				}
				double seconds = recipe.get("duration").getAsDouble();
				return seconds > 0D ? Math.round(seconds * 1000D) : 0L;
			} catch (RuntimeException ignored) {
				return 0L;
			}
		}
		return 0L;
	}

	public static long durationMs(String itemId, int quickForgeLevel) {
		long base = baseDurationMs(itemId);
		return base <= 0L ? 0L : Math.round(base * quickForgeMultiplier(quickForgeLevel));
	}
}
