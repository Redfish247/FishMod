/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fishmod.pv.api.CoflnetApiClient
import fishmod.pv.api.HypixelApiClient
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.networth.InventoryDecoder
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID

class AuctionSnapshot private constructor(
	@get:JvmName("playerUuid") val playerUuid: UUID?,
	active: List<Listing>?,
	sold: List<Listing>?,
	bought: List<Listing>?,
	stats: Stats?,
	soldPage: Int,
	boughtPage: Int,
	@get:JvmName("soldHasMore") val soldHasMore: Boolean,
	@get:JvmName("boughtHasMore") val boughtHasMore: Boolean,
	@get:JvmName("historyLoading") val historyLoading: Boolean
) {
	enum class Bucket {
		ACTIVE,
		SOLD,
		BOUGHT
	}

	@JvmRecord
	data class Listing(
		val auctionId: String,
		val tag: String,
		val itemName: String,
		val tier: String,
		val bin: Boolean,
		val startingBid: Long,
		val highestBid: Long,
		val endMs: Long,
		val slot: InventorySnapshot.Slot?,
		val detailLines: List<String>,
		val profileId: String
	) {
		fun price(): Long = if (highestBid > 0L) highestBid else startingBid

		fun ended(nowMs: Long): Boolean = endMs > 0L && endMs <= nowMs

	}

	@JvmRecord
	data class Stats(
		val bids: Long,
		val highestBid: Long,
		val won: Long,
		val created: Long,
		val fees: Long,
	) {
		companion object {
			@JvmStatic
			fun empty(): Stats = Stats(0L, 0L, 0L, 0L, 0L)

			@JvmStatic
			fun fromMember(member: JsonObject?): Stats {
				if (member == null) {
					return empty()
				}
				val playerStats = if (member.has("player_stats") && member.get("player_stats").isJsonObject) {
					member.getAsJsonObject("player_stats")
				} else {
					null
				}
				if (playerStats == null || !playerStats.has("auctions") || !playerStats.get("auctions").isJsonObject) {
					return empty()
				}
				val a = playerStats.getAsJsonObject("auctions")
				return Stats(
					longVal(a, "bids"),
					longVal(a, "highest_bid"),
					longVal(a, "won"),
					longVal(a, "created"),
					longVal(a, "fees"),
				)
			}
		}
	}

	constructor(
		playerUuid: UUID?,
		active: List<Listing>?,
		sold: List<Listing>?,
		bought: List<Listing>?,
		stats: Stats?,
		soldPage: Int,
		boughtPage: Int,
		soldHasMore: Boolean,
		boughtHasMore: Boolean
	) : this(playerUuid, active, sold, bought, stats, soldPage, boughtPage, soldHasMore, boughtHasMore, false)

	@get:JvmName("active") val active: List<Listing> = java.util.List.copyOf(active ?: listOf())
	@get:JvmName("sold") val sold: List<Listing> = java.util.List.copyOf(sold ?: listOf())
	@get:JvmName("bought") val bought: List<Listing> = java.util.List.copyOf(bought ?: listOf())
	@get:JvmName("stats") val stats: Stats = stats ?: Stats.empty()
	@get:JvmName("soldPage") val soldPage: Int = maxOf(0, soldPage)
	@get:JvmName("boughtPage") val boughtPage: Int = maxOf(0, boughtPage)
	@get:JvmName("creditUrl") val creditUrl: String = run {
		val id = if (playerUuid == null) "" else HypixelApiClient.undashed(playerUuid)
		if (id.isBlank()) "https://sky.coflnet.com/" else "https://sky.coflnet.com/player/$id"
	}

	private fun rebuild(
		active: List<Listing> = this.active,
		sold: List<Listing> = this.sold,
		bought: List<Listing> = this.bought,
		stats: Stats? = this.stats,
		soldPage: Int = this.soldPage,
		boughtPage: Int = this.boughtPage,
		soldHasMore: Boolean = this.soldHasMore,
		boughtHasMore: Boolean = this.boughtHasMore,
		historyLoading: Boolean = this.historyLoading
	): AuctionSnapshot = AuctionSnapshot(
		playerUuid, active, sold, bought, stats, soldPage, boughtPage, soldHasMore, boughtHasMore, historyLoading
	)

	fun withHistoryLoading(loading: Boolean): AuctionSnapshot =
		if (loading == historyLoading) this else rebuild(historyLoading = loading)

	fun withStats(stats: Stats?): AuctionSnapshot = rebuild(stats = stats)

	companion object {
		@JvmStatic
		fun empty(): AuctionSnapshot = AuctionSnapshot(null, listOf(), listOf(), listOf(), Stats.empty(), 0, 0, false, false)

		private fun normalizeId(id: String): String = id.replace("-", "").lowercase(Locale.ROOT)

		private fun listing(
			auctionId: String, tag: String, itemName: String, tier: String, bin: Boolean, startingBid: Long, highestBid: Long,
			endMs: Long, slot: InventorySnapshot.Slot?, detailLines: List<String>?, profileId: String?
		): Listing = Listing(
			auctionId, tag, itemName, tier, bin, startingBid, highestBid, endMs, slot,
			java.util.List.copyOf(detailLines ?: listOf()),
			profileId?.let { normalizeId(it) } ?: ""
		)

		private fun obj(root: JsonObject?, key: String?): JsonObject? {
			if (root == null || key == null || !root.has(key) || !root.get(key).isJsonObject) {
				return null
			}
			return root.getAsJsonObject(key)
		}

		@JvmStatic
		fun build(
			playerUuid: UUID?,
			hypixelAuctionRoot: JsonObject?,
			coflSoldPage0: JsonArray?,
			coflBidsPage0: JsonArray?
		): AuctionSnapshot {
			val player = if (playerUuid == null) "" else HypixelApiClient.undashed(playerUuid).lowercase(Locale.ROOT)
			val now = System.currentTimeMillis()
			val active = parseHypixelActive(hypixelAuctionRoot, player, now)
			val sold = parseCoflSold(coflSoldPage0, now)
			val bought = parseCoflBought(coflBidsPage0, now)
			val soldMore = coflSoldPage0 != null && coflSoldPage0.size() >= 10
			val boughtMore = coflBidsPage0 != null && coflBidsPage0.size() >= 10
			return AuctionSnapshot(playerUuid, active, sold, bought, Stats.empty(), 0, 0, soldMore, boughtMore)
		}

		@JvmStatic
		@JvmOverloads
		fun parseCoflSold(array: JsonArray?, nowMs: Long = System.currentTimeMillis()): List<Listing> =
			CoflnetApiClient.objects(array)
				.filter { longVal(it, "highestBid") > 0L && coflEnded(it, nowMs) }
				.map { fromCoflSummary(it, false) }

		@JvmStatic
		@JvmOverloads
		fun parseCoflBought(array: JsonArray?, nowMs: Long = System.currentTimeMillis()): List<Listing> =
			CoflnetApiClient.objects(array).filter {
				val own = longVal(it, "highestOwnBid")
				val high = longVal(it, "highestBid")
				own > 0L && high > 0L && own >= high && coflEnded(it, nowMs)
			}.map { fromCoflSummary(it, true) }

		private fun parseHypixelActive(root: JsonObject?, player: String, nowMs: Long): List<Listing> {
			val out = ArrayList<Listing>()
			if (root == null || !root.has("auctions") || !root.get("auctions").isJsonArray) {
				return out
			}
			for (el in root.getAsJsonArray("auctions")) {
				if (el == null || !el.isJsonObject) {
					continue
				}
				val a = el.asJsonObject
				val auctioneer = normalizeId(str(a, "auctioneer"))
				val isSeller = player.isNotBlank() && player == auctioneer
				if (!isSeller || longVal(a, "end") <= nowMs || bool(a, "claimed")) {
					continue
				}
				out.add(fromHypixel(a))
			}
			out.sortBy { it.endMs }
			return out
		}

		private fun fromHypixel(a: JsonObject): Listing {
			val id = str(a, "uuid").ifBlank { str(a, "auction_id") }
			val name = str(a, "item_name")
			val tier = str(a, "tier")
			val bin = bool(a, "bin")
			val startBid = longVal(a, "starting_bid")
			val high = longVal(a, "highest_bid_amount")
			val end = longVal(a, "end")
			var slot: InventorySnapshot.Slot? = null
			if (a.has("item_bytes")) {
				slot = InventoryDecoder.slotFromItemBytes(a.get("item_bytes"))
			}
			val tag = slot?.id ?: ""
			if (slot == null && name.isNotBlank()) {
				slot = InventoryDecoder.slotFromTag("PAPER", name)
			}
			return listing(
				id, tag, name.ifBlank { "Unknown" }, SkyBlockItemFactory.normalizeTier(tier),
				bin, startBid, high, end, slot, listOf(), str(a, "profile_id")
			)
		}

		private fun coflEnded(obj: JsonObject, nowMs: Long): Boolean {
			val end = parseCoflTime(str(obj, "end"))
			return end > 0L && end <= nowMs
		}

		private fun fromCoflSummary(obj: JsonObject, bought: Boolean): Listing {
			val id = str(obj, "auctionId").ifBlank { str(obj, "uuid") }
			val tag = str(obj, "tag")
			val name = str(obj, "itemName")
			val bin = bool(obj, "bin")
			val startBid = longVal(obj, "startingBid")
			var high = if (bought) longVal(obj, "highestOwnBid") else longVal(obj, "highestBid")
			if (high <= 0L) {
				high = longVal(obj, "highestBid")
			}
			val end = parseCoflTime(str(obj, "end"))
			val slot = InventoryDecoder.slotFromTag(tag, name, SkyBlockItemFactory.normalizeTier(str(obj, "tier")))
			// Prefer Cofl's auction tier when present - NEU rarity is often wrong for AH rows.
			val tier = SkyBlockItemFactory.normalizeTier(str(obj, "tier"))
				.ifBlank { SkyBlockItemFactory.normalizeTier(SkyBlockItemFactory.resolveTier(tag)) }
			return listing(
				id,
				tag,
				name.ifBlank { tag.ifBlank { "Unknown" } },
				tier,
				bin,
				startBid,
				high,
				end,
				slot,
				listOf(),
				""
			)
		}

		private fun key(listing: Listing): String {
			if (listing.auctionId.isNotBlank()) {
				return listing.auctionId.lowercase(Locale.ROOT)
			}
			return listing.itemName + "|" + listing.endMs + "|" + listing.price()
		}

		private fun parseCoflTime(raw: String?): Long {
			if (raw.isNullOrBlank()) {
				return 0L
			}
			return try {
				Instant.parse(if (raw.endsWith("Z")) raw else raw + "Z").toEpochMilli()
			} catch (ignored: DateTimeParseException) {
				raw.toLongOrNull() ?: 0L
			}
		}

		private fun str(obj: JsonObject?, key: String?): String {
			if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull) {
				return ""
			}
			return try {
				obj.get(key).asString
			} catch (ignored: RuntimeException) {
				""
			}
		}

		private fun bool(obj: JsonObject?, key: String): Boolean {
			val el = obj?.get(key)
			return el != null && el.isJsonPrimitive && el.asBoolean
		}

		private fun longVal(obj: JsonObject?, key: String?): Long {
			if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull) {
				return 0L
			}
			return try {
				obj.get(key).asLong
			} catch (ignored: RuntimeException) {
				try {
					obj.get(key).asDouble.toLong()
				} catch (ignored2: RuntimeException) {
					0L
				}
			}
		}
	}
}
