/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.world.item.ItemStack

/**
 * Shared base-stack and Hypixel item-model caches for [SkyBlockItemFactory] resolvers.
 * One owner only: do not allocate parallel maps in NEU / Hypixel helpers.
 */
object ItemStackCache {
	private val BASE_CACHE = ConcurrentHashMap<String, ItemStack>()
	private val ITEM_MODEL_BY_ID = ConcurrentHashMap<String, String>()

	@JvmStatic
	fun clear() {
		BASE_CACHE.clear()
		ITEM_MODEL_BY_ID.clear()
	}

	@JvmStatic
	fun getBase(candidate: String?): ItemStack? = if (candidate == null) null else BASE_CACHE[candidate]

	@JvmStatic
	fun putBase(candidate: String?, stack: ItemStack?) {
		if (candidate == null || stack == null) {
			return
		}
		BASE_CACHE[candidate] = stack
	}

	@JvmStatic
	fun getItemModel(skyblockId: String?): String? {
		if (skyblockId.isNullOrBlank()) {
			return null
		}
		return ITEM_MODEL_BY_ID[skyblockId.uppercase(Locale.ROOT)]
	}

	@JvmStatic
	fun rememberItemModel(skyblockId: String?, model: String?) {
		if (skyblockId.isNullOrBlank() || model.isNullOrBlank()) {
			return
		}
		if (!model.lowercase(Locale.ROOT).startsWith("hypixel_skyblock:")) {
			return
		}
		ITEM_MODEL_BY_ID[skyblockId.uppercase(Locale.ROOT)] = model
		SkyBlockItemIconCache.getOrRequest(model)
	}

	@JvmStatic
	fun copyItemModel(fromId: String?, toId: String?) {
		if (fromId == null || toId == null || fromId.equals(toId, ignoreCase = true)) {
			return
		}
		val model = ITEM_MODEL_BY_ID[fromId.uppercase(Locale.ROOT)]
		if (model != null) {
			ITEM_MODEL_BY_ID[toId.uppercase(Locale.ROOT)] = model
		}
	}
}
