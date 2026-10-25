/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.price;

import java.util.Locale;

public final class ItemPricer {
	private ItemPricer() {
	}

	public static void start() {
		AthenPriceCache.start();
		SkyHelperPriceCache.start();
		HypixelItemsCache.start();
		HypixelCollectionsCache.start();
	}

	public static boolean isReady() {
		return AthenPriceCache.isReady() || SkyHelperPriceCache.isReady();
	}

	public static void awaitReady(long timeoutMillis) {
		long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
		while (!isReady() && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(100L);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	public static double price(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return 0;
		}
		String id = itemId.toUpperCase(Locale.ROOT);
		Double athen = AthenPriceCache.get(id);
		if (athen != null && athen > 0) {
			return athen;
		}
		Double skyHelper = SkyHelperPriceCache.get(id);
		return skyHelper == null ? 0 : skyHelper;
	}

	/**
	 * Sack / material valuation: bazaar only (never AH LBIN), then SkyHelper.
	 * Prevents inflated sack totals when Athen has a rare AH listing for a material id.
	 */
	public static double materialPrice(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return 0;
		}
		String id = itemId.toUpperCase(Locale.ROOT);
		Double bazaar = AthenPriceCache.getBazaar(id);
		if (bazaar != null && bazaar > 0) {
			return bazaar;
		}
		Double skyHelper = SkyHelperPriceCache.get(id);
		return skyHelper == null ? 0 : skyHelper;
	}

	/** Bazaar first, then any market price; null when no source prices the item (unlike the 0 above). */
	public static Double knownPrice(String itemId) {
		double bazaar = materialPrice(itemId);
		if (bazaar > 0) {
			return bazaar;
		}
		double any = price(itemId);
		return any > 0 ? any : null;
	}
}
