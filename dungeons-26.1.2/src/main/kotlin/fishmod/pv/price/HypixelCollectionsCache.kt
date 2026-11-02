/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.price

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.pv.api.FishProxyAuth
import fishmod.pv.api.HypixelApiClient
import fishmod.pv.api.PvExecutors
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

object HypixelCollectionsCache {
	private val COLLECTIONS_URI: URI = URI.create("https://api.hypixel.net/v2/resources/skyblock/collections")
	private val TIMEOUT: Duration = Duration.ofSeconds(20)
	private const val REFRESH_HOURS = 12L
	private const val RETRY_SECONDS = 60L
	private val REFRESHING = AtomicBoolean()
	private val RETRY_PENDING = AtomicBoolean()
	private val HTTP = HypixelApiClient.http()
	private val EXECUTOR = PvExecutors.SCHEDULER

	@JvmRecord
	data class Tier(val tier: Int, val amountRequired: Long, val unlocks: List<String>)

	@JvmRecord
	data class Item(val id: String, val name: String, val maxTiers: Int, val tiers: List<Tier>) {
		fun tierFor(amount: Long): Int {
			var reached = 0
			for (tier in tiers) {
				if (amount >= tier.amountRequired) reached = max(reached, tier.tier)
			}
			return reached
		}

		fun progressToNext(amount: Long): Float {
			if (tiers.isEmpty()) return if (amount > 0) 1f else 0f
			var next: Tier? = null
			var prev: Tier? = null
			for (tier in tiers) {
				if (amount < tier.amountRequired) {
					next = tier
					break
				}
				prev = tier
			}
			if (next == null) return 1f
			val from = prev?.amountRequired ?: 0L
			val span = max(1L, next.amountRequired - from)
			return max(0f, min(1f, (amount - from) / span.toFloat()))
		}

		fun nextTier(amount: Long): Tier? = tiers.firstOrNull { amount < it.amountRequired } ?: tiers.lastOrNull()

		fun currentTier(amount: Long): Tier? = tiers.lastOrNull { amount >= it.amountRequired }
	}

	@JvmRecord
	data class Category(val id: String, val name: String, val items: List<Item>)

	@Volatile
	private var categories: List<Category> = emptyList()

	@Volatile
	private var itemsById: Map<String, Item> = emptyMap()

	@JvmStatic
	fun start() {
		requestRefresh()
		EXECUTOR.scheduleAtFixedRate(::refreshSafely, REFRESH_HOURS, REFRESH_HOURS, TimeUnit.HOURS)
	}

	private fun requestRefresh() {
		if (REFRESHING.compareAndSet(false, true)) {
			EXECUTOR.execute {
				try {
					refreshSafely()
				} finally {
					REFRESHING.set(false)
				}
			}
		}
	}

	@JvmStatic
	fun isReady(): Boolean = categories.isNotEmpty()

	@JvmStatic
	fun categories(): List<Category> = categories

	@JvmStatic
	fun item(id: String?): Item? {
		if (id.isNullOrBlank()) return null
		itemsById[id]?.let { return it }
		val upper = id.uppercase(Locale.ROOT)
		return itemsById[upper] ?: itemsById[upper.replace('-', ':')]
	}

	/** Wait briefly so profile parse can attach tier tables. */
	@JvmStatic
	fun awaitReady(timeoutMs: Long) {
		if (!isReady()) requestRefresh()
		val deadline = System.currentTimeMillis() + max(0L, timeoutMs)
		while (!isReady() && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(50L)
			} catch (_: InterruptedException) {
				Thread.currentThread().interrupt()
				return
			}
		}
	}

	private fun refreshSafely() {
		try {
			refresh(true)
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.warn("Failed to refresh Hypixel collections", exception)
			// Startup can run before session auth works; don't wait for the 12h refresh.
			if (!isReady() && RETRY_PENDING.compareAndSet(false, true)) {
				EXECUTOR.schedule({
					RETRY_PENDING.set(false)
					requestRefresh()
				}, max(RETRY_SECONDS * 1000L, 0L), TimeUnit.MILLISECONDS)
			}
		}
	}

	private fun refresh(allowReauth: Boolean) {
		val builder = HttpRequest.newBuilder(COLLECTIONS_URI).timeout(TIMEOUT).GET()
		val startedNanos = System.nanoTime()
		val response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString())
		if (response.statusCode() == 401) {
			FishProxyAuth.handleUnauthorized(
				response.request(),
				response.body(),
				TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
			)
			if (allowReauth) {
				refresh(false)
				return
			}
			throw IOException("Hypixel collections unauthorized after re-auth")
		}
		if (response.statusCode() !in 200..299) throw IOException("Hypixel collections HTTP " + response.statusCode())
		val root = JsonParser.parseString(response.body()).asJsonObject
		val collections = root.get("collections")?.takeIf { it.isJsonObject }?.asJsonObject
			?: throw IOException("Hypixel collections missing object")

		val nextCategories = ArrayList<Category>()
		val nextItems = LinkedHashMap<String, Item>()
		for ((key, value) in collections.entrySet()) {
			if (!value.isJsonObject) continue
			val catObj = value.asJsonObject
			val catId = key.uppercase(Locale.ROOT)
			val catName = if (catObj.has("name")) catObj.get("name").asString else title(catId)
			val itemsObj = catObj.get("items")?.takeIf { it.isJsonObject }?.asJsonObject
			val items = ArrayList<Item>()
			if (itemsObj != null) {
				for ((itemKey, itemValue) in itemsObj.entrySet()) {
					if (!itemValue.isJsonObject) continue
					val item = parseItem(itemKey, itemValue.asJsonObject)
					items.add(item)
					nextItems[item.id] = item
					val alt = item.id.replace(':', '-')
					if (alt != item.id) nextItems.putIfAbsent(alt, item)
				}
			}
			nextCategories.add(Category(catId, catName.ifBlank { catId }, java.util.List.copyOf(items)))
		}

		if (nextCategories.isNotEmpty()) {
			categories = java.util.List.copyOf(nextCategories)
			itemsById = java.util.Map.copyOf(nextItems)
			ProfileViewer.LOGGER.info(
				"Loaded {} Hypixel collection categories ({} items)",
				nextCategories.size,
				nextItems.size
			)
		}
	}

	private fun parseItem(id: String, obj: JsonObject): Item {
		val name = if (obj.has("name")) obj.get("name").asString else id
		var maxTiers = if (obj.has("maxTiers")) obj.get("maxTiers").asInt else 0
		val tiers = ArrayList<Tier>()
		val tiersArr = obj.get("tiers")?.takeIf { it.isJsonArray }?.asJsonArray
		if (tiersArr != null) {
			for (el in tiersArr) {
				if (!el.isJsonObject) continue
				val t = el.asJsonObject
				val tier = if (t.has("tier")) t.get("tier").asInt else 0
				val amount = if (t.has("amountRequired")) t.get("amountRequired").asDouble.roundToLong() else 0L
				val unlocks = ArrayList<String>()
				t.get("unlocks")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { u ->
					if (u.isJsonPrimitive) unlocks.add(u.asString)
				}
				tiers.add(Tier(tier, amount, java.util.List.copyOf(unlocks)))
			}
		}
		if (maxTiers <= 0) maxTiers = tiers.size
		return Item(id, name.ifBlank { id }, maxTiers, java.util.List.copyOf(tiers))
	}

	private fun title(id: String?): String {
		if (id.isNullOrBlank()) return ""
		val lower = id.lowercase(Locale.ROOT).replace('_', ' ')
		return lower[0].uppercaseChar() + lower.substring(1)
	}
}
