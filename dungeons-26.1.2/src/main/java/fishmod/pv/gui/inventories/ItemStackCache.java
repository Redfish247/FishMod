/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.ItemStack;

/**
 * Shared base-stack and Hypixel item-model caches for {@link SkyBlockItemFactory} resolvers.
 * One owner only: do not allocate parallel maps in NEU / Hypixel helpers.
 */
final class ItemStackCache {
	private static final Map<String, ItemStack> BASE_CACHE = new ConcurrentHashMap<>();
	/** SkyBlock id → {@code hypixel_skyblock:item/...} for custom GUI icons. */
	private static final Map<String, String> ITEM_MODEL_BY_ID = new ConcurrentHashMap<>();

	private ItemStackCache() {
	}

	static void clear() {
		BASE_CACHE.clear();
		ITEM_MODEL_BY_ID.clear();
	}

	static ItemStack getBase(String candidate) {
		return candidate == null ? null : BASE_CACHE.get(candidate);
	}

	static void putBase(String candidate, ItemStack stack) {
		if (candidate == null || stack == null) {
			return;
		}
		BASE_CACHE.put(candidate, stack);
	}

	static String getItemModel(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return null;
		}
		return ITEM_MODEL_BY_ID.get(skyblockId.toUpperCase(Locale.ROOT));
	}

	static void rememberItemModel(String skyblockId, String model) {
		if (skyblockId == null || skyblockId.isBlank() || model == null || model.isBlank()) {
			return;
		}
		if (!model.toLowerCase(Locale.ROOT).startsWith("hypixel_skyblock:")) {
			return;
		}
		ITEM_MODEL_BY_ID.put(skyblockId.toUpperCase(Locale.ROOT), model);
		SkyBlockItemIconCache.getOrRequest(model);
	}

	static void copyItemModel(String fromId, String toId) {
		if (fromId == null || toId == null || fromId.equalsIgnoreCase(toId)) {
			return;
		}
		String model = ITEM_MODEL_BY_ID.get(fromId.toUpperCase(Locale.ROOT));
		if (model != null) {
			ITEM_MODEL_BY_ID.put(toId.toUpperCase(Locale.ROOT), model);
		}
	}
}
