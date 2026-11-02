/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import fishmod.pv.neu.NeuRepoCache

/** Forge durations from NEU forge recipes, with the profile's Quick Forge reduction applied. */
object ForgeTimes {
	// Quick Forge: -(10 + 0.5 * level)% forge time, jumping to -30% at level 20.
	@JvmStatic
	fun quickForgeMultiplier(level: Int): Double = when {
		level <= 0 -> 1.0
		level >= 20 -> 0.7
		else -> 1.0 - (10.0 + 0.5 * level) / 100.0
	}

	/** Base recipe duration in ms, or 0 when NEU has no forge recipe for the item (or isn't loaded yet). */
	@JvmStatic
	fun baseDurationMs(itemId: String?): Long {
		val item = NeuRepoCache.get(itemId) ?: return 0L
		if (!item.has("recipes") || !item.get("recipes").isJsonArray) {
			return 0L
		}
		for (el in item.getAsJsonArray("recipes")) {
			if (el == null || !el.isJsonObject) {
				continue
			}
			val recipe = el.asJsonObject
			try {
				if (!recipe.has("type") || recipe.get("type").asString != "forge" || !recipe.has("duration")) {
					continue
				}
				val seconds = recipe.get("duration").asDouble
				return if (seconds > 0.0) Math.round(seconds * 1000.0) else 0L
			} catch (ignored: RuntimeException) {
				return 0L
			}
		}
		return 0L
	}

	@JvmStatic
	fun durationMs(itemId: String?, quickForgeLevel: Int): Long {
		val base = baseDurationMs(itemId)
		return if (base <= 0L) 0L else Math.round(base * quickForgeMultiplier(quickForgeLevel))
	}
}
