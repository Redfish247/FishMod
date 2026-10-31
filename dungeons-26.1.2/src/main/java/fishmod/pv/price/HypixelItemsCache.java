/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.price;

import com.google.gson.JsonObject;
import fishmod.utils.networth.ItemsDb;

// Items resource is fetched and parsed once, by ItemsDb.
public final class HypixelItemsCache {
	private HypixelItemsCache() {
	}

	public static void start() {
		ItemsDb.initAsync();
	}

	public static void requestIfEmpty() {
		ItemsDb.ensureLoaded();
	}

	public static JsonObject get(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		JsonObject direct = ItemsDb.get(itemId);
		if (direct != null) {
			return direct;
		}
		return ItemsDb.get(itemId.toUpperCase(java.util.Locale.ROOT));
	}

	public static java.util.Collection<JsonObject> allItems() {
		return ItemsDb.allItems();
	}
}
