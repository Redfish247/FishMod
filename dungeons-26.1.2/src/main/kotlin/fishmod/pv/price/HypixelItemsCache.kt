/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.price

import com.google.gson.JsonObject
import fishmod.utils.networth.ItemsDb
import java.util.Locale

// Items resource is fetched and parsed once, by ItemsDb.
object HypixelItemsCache {
	@JvmStatic
	fun start() {
		ItemsDb.initAsync()
	}

	@JvmStatic
	fun requestIfEmpty() {
		ItemsDb.ensureLoaded()
	}

	@JvmStatic
	fun get(itemId: String?): JsonObject? {
		if (itemId.isNullOrBlank()) return null
		return ItemsDb.get(itemId) ?: ItemsDb.get(itemId.uppercase(Locale.ROOT))
	}

	@JvmStatic
	fun allItems(): Collection<JsonObject> = ItemsDb.allItems()
}
