/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.price

import java.util.Locale

object ItemPricer {
	@JvmStatic
	fun start() {
		AthenPriceCache.start()
		SkyHelperPriceCache.start()
		HypixelCollectionsCache.start()
	}

	@JvmStatic
	fun isReady(): Boolean = AthenPriceCache.isReady() || SkyHelperPriceCache.isReady()

	@JvmStatic
	fun awaitReady(timeoutMillis: Long) {
		val deadline = System.currentTimeMillis() + maxOf(0L, timeoutMillis)
		while (!isReady() && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(100L)
			} catch (_: InterruptedException) {
				Thread.currentThread().interrupt()
				return
			}
		}
	}

	@JvmStatic
	fun price(itemId: String?): Double {
		if (itemId.isNullOrBlank()) return 0.0
		val id = itemId.uppercase(Locale.ROOT)
		val athen = AthenPriceCache.get(id)
		if (athen != null && athen > 0) return athen
		return SkyHelperPriceCache.get(id) ?: 0.0
	}

	/**
	 * Sack / material valuation: bazaar only (never AH LBIN), then SkyHelper.
	 * Prevents inflated sack totals when Athen has a rare AH listing for a material id.
	 */
	@JvmStatic
	fun materialPrice(itemId: String?): Double {
		if (itemId.isNullOrBlank()) return 0.0
		val id = itemId.uppercase(Locale.ROOT)
		val bazaar = AthenPriceCache.getBazaar(id)
		if (bazaar != null && bazaar > 0) return bazaar
		return SkyHelperPriceCache.get(id) ?: 0.0
	}

	/** Bazaar first, then any market price; null when no source prices the item (unlike the 0 above). */
	@JvmStatic
	fun knownPrice(itemId: String?): Double? {
		val bazaar = materialPrice(itemId)
		if (bazaar > 0) return bazaar
		val any = price(itemId)
		return if (any > 0) any else null
	}
}
