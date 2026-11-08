/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.price

import com.google.gson.JsonObject
import fishmod.pv.ProfileViewer
import fishmod.pv.api.PvExecutors
import fishmod.utils.Http
import java.net.URI
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object AthenPriceCache {
	private val PRICES_URI: URI = URI.create("https://athen.aerii.xyz/prices")
	private val TIMEOUT: Duration = Duration.ofSeconds(10)
	private const val REFRESH_MINUTES = 10L
	private val EXECUTOR = PvExecutors.SCHEDULER

	@Volatile
	private var prices: Map<String, Double> = emptyMap()

	/** Bazaar-only unit prices (preferred for sack / material valuation). */
	@Volatile
	private var bazaarPrices: Map<String, Double> = emptyMap()

	@Volatile
	private var ready = false

	private val STARTED = java.util.concurrent.atomic.AtomicBoolean(false)

	@JvmStatic
	fun start() {
		if (!STARTED.compareAndSet(false, true)) return
		EXECUTOR.execute(::refreshSafely)
		EXECUTOR.scheduleAtFixedRate(::refreshSafely, REFRESH_MINUTES, REFRESH_MINUTES, TimeUnit.MINUTES)
	}

	@JvmStatic
	fun isReady(): Boolean { start(); return ready && prices.isNotEmpty() }

	@JvmStatic
	fun get(itemId: String?): Double? {
		start()
		if (itemId.isNullOrBlank()) return null
		return prices[itemId.uppercase(Locale.ROOT)]
	}

	/** Instant / bazaar sell-side price only — never AH LBIN. */
	@JvmStatic
	fun getBazaar(itemId: String?): Double? {
		start()
		if (itemId.isNullOrBlank()) return null
		return bazaarPrices[itemId.uppercase(Locale.ROOT)]
	}

	private fun refreshSafely() {
		try {
			refresh()
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.warn("Failed to refresh Vyriv prices", exception)
		}
	}

	private fun refresh() {
		val root = Http.getJson(PRICES_URI.toString(), TIMEOUT).asJsonObject
		val next = ConcurrentHashMap<String, Double>()
		val nextBazaar = ConcurrentHashMap<String, Double>()
		// Bazaar first so materials use IB/sell — AH LBIN must not stomp sack prices.
		loadBazaar(root.getAsJsonObject("bazaar"), next, nextBazaar)
		loadAuctionHouse(root.getAsJsonObject("auction_house"), next)
		if (next.isNotEmpty()) {
			prices = java.util.Map.copyOf(next)
			bazaarPrices = java.util.Map.copyOf(nextBazaar)
			ready = true
			ProfileViewer.LOGGER.info("Loaded {} Vyriv price entries ({} bazaar)", next.size, nextBazaar.size)
		}
	}

	private fun loadAuctionHouse(section: JsonObject?, out: MutableMap<String, Double>) {
		if (section == null) return
		for ((rawKey, value) in section.entrySet()) {
			if (!value.isJsonObject) continue
			val key = rawKey.uppercase(Locale.ROOT)
			if (out.containsKey(key)) continue
			val obj = value.asJsonObject
			firstPositive(num(obj, "p3d"), num(obj, "lbin"), num(obj, "p7d"))?.let { out[key] = it }
		}
	}

	private fun loadBazaar(section: JsonObject?, out: MutableMap<String, Double>, bazaarOut: MutableMap<String, Double>) {
		if (section == null) return
		for ((rawKey, value) in section.entrySet()) {
			if (!value.isJsonObject) continue
			val obj = value.asJsonObject
			val key = rawKey.uppercase(Locale.ROOT)
			val price = firstPositive(num(obj, "ib"), num(obj, "tb"), num(obj, "is"), num(obj, "ts")) ?: continue
			out[key] = price
			bazaarOut[key] = price
		}
	}

	private fun num(obj: JsonObject, key: String): Double? {
		val element = obj.get(key)
		if (element != null && element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
			val value = element.asDouble
			return if (value > 0) value else null
		}
		return null
	}

	private fun firstPositive(vararg values: Double?): Double? = values.firstOrNull { it != null && it > 0 }
}
