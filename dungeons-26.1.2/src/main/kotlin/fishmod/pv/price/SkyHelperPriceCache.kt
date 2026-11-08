/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.price

import fishmod.pv.ProfileViewer
import fishmod.pv.api.PvExecutors
import fishmod.utils.Http
import java.io.IOException
import java.net.URI
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * SkyHelper `pricesV2.json` - needed for leveled pet keys and missing Athen IDs.
 */
object SkyHelperPriceCache {
	private val PRICES_URI: URI = URI.create(
		"https://raw.githubusercontent.com/SkyHelperBot/Prices/main/pricesV2.json"
	)
	private val TIMEOUT: Duration = Duration.ofSeconds(20)
	private const val REFRESH_MINUTES = 30L
	private val EXECUTOR = PvExecutors.SCHEDULER

	@Volatile
	private var prices: Map<String, Double> = emptyMap()

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
	fun snapshot(): Map<String, Double> { start(); return prices }

	@JvmStatic
	fun get(itemId: String?): Double? {
		start()
		if (itemId.isNullOrBlank()) return null
		return prices[itemId.uppercase(Locale.ROOT)]
	}

	private fun refreshSafely() {
		try {
			refresh()
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.warn("Failed to refresh SkyHelper prices", exception)
		}
	}

	private fun refresh() {
		val root = Http.getJson(PRICES_URI.toString(), TIMEOUT)
		if (!root.isJsonObject) throw IOException("SkyHelper prices not an object")
		val next = ConcurrentHashMap<String, Double>()
		for ((key, value) in root.asJsonObject.entrySet()) {
			if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
				val price = value.asDouble
				if (price > 0) next[key.uppercase(Locale.ROOT)] = price
			}
		}
		if (next.isNotEmpty()) {
			prices = java.util.Map.copyOf(next)
			ready = true
			ProfileViewer.LOGGER.info("Loaded {} SkyHelper price entries", next.size)
		}
	}
}
