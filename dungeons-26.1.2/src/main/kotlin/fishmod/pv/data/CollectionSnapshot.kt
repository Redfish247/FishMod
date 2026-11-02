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
import fishmod.pv.api.HypixelApiClient
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.price.HypixelCollectionsCache
import java.util.Locale
import java.util.TreeSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class CollectionSnapshot(
	categories: List<HypixelCollectionsCache.Category>?,
	members: List<Member>?,
	viewedUuid: String?,
	minions: List<MinionEntry>?
) {
	@JvmRecord
	data class Member(
		val uuid: String,
		val name: String,
		val amounts: Map<String, Long>,
		val craftedTiers: Map<String, Set<Int>>,
		val unlockedTiers: Map<String, Int>
	) {
		fun amount(itemId: String?): Long = lookupAmount(amounts, itemId)

		fun unlockedTier(itemId: String?): Int {
			if (itemId.isNullOrBlank() || unlockedTiers.isEmpty()) {
				return 0
			}
			var best = 0
			for (key in CollectionIds.lookupKeys(itemId)) {
				unlockedTiers[key]?.let { best = maxOf(best, it) }
			}
			return best
		}

		fun maxCraftedMinion(minionId: String?): Int {
			if (minionId.isNullOrBlank() || craftedTiers.isEmpty()) {
				return 0
			}
			return craftedTiers[minionId.uppercase(Locale.ROOT)]?.maxOrNull() ?: 0
		}
	}

	@JvmRecord
	data class MinionEntry(val id: String, val displayName: String, val tierCap: Int, val craftedTiers: Set<Int>) {
		fun maxCrafted(): Int = craftedTiers.maxOrNull() ?: 0

		fun crafted(tier: Int): Boolean = craftedTiers.contains(tier)

		fun iconId(): String = id + "_" + maxOf(1, maxCrafted())
	}

	@get:JvmName("categories") val categories: List<HypixelCollectionsCache.Category> =
		if (categories == null) listOf() else java.util.List.copyOf(categories)
	@get:JvmName("members") val members: List<Member> = if (members == null) listOf() else java.util.List.copyOf(members)
	@get:JvmName("viewedUuid") val viewedUuid: String = viewedUuid?.lowercase(Locale.ROOT) ?: ""
	@get:JvmName("minions") val minions: List<MinionEntry> = if (minions == null) listOf() else java.util.List.copyOf(minions)
	private val nameOverrides = ConcurrentHashMap<String, String>()
	private val playerRanks = ConcurrentHashMap<String, JsonObject>()
	private val ranksRequested = AtomicBoolean()

	fun viewed(): Member? = members.firstOrNull { it.uuid == viewedUuid } ?: members.firstOrNull()

	fun displayName(member: Member?): String {
		if (member == null) {
			return "?"
		}
		val override = nameOverrides[member.uuid]
		return if (override.isNullOrBlank()) member.name else override
	}

	fun putName(uuid: String?, name: String?) {
		if (uuid == null || name.isNullOrBlank()) {
			return
		}
		nameOverrides[uuid.lowercase(Locale.ROOT)] = name
	}

	fun putPlayerRank(uuid: String?, player: JsonObject?) {
		if (uuid == null || player == null) {
			return
		}
		playerRanks[uuid.lowercase(Locale.ROOT)] = player
		if (player.has("displayname") && player.get("displayname").isJsonPrimitive) {
			val display: String? = player.get("displayname").asString
			if (!display.isNullOrBlank()) {
				putName(uuid, display)
			}
		}
	}

	fun playerRank(uuid: String?): JsonObject? {
		if (uuid.isNullOrBlank()) {
			return null
		}
		return playerRanks[uuid.lowercase(Locale.ROOT)]
	}

	fun viewedAmount(itemId: String?): Long = viewed()?.amount(itemId) ?: 0L

	/**
	 * Amount used for tiers / progress.
	 * Shared Hypixel collections = coop sum; boss collections = viewed player only.
	 */
	fun totalAmount(itemId: String?): Long {
		if (BossCollections.isBossId(itemId)) {
			return viewedAmount(itemId)
		}
		return members.sumOf { it.amount(itemId) }
	}

	/** Highest unlocked tier recorded on any member ({@code unlocked_coll_tiers}). */
	fun unlockedTier(itemId: String?): Int {
		if (BossCollections.isBossId(itemId)) {
			return 0
		}
		return members.maxOfOrNull { it.unlockedTier(itemId) }?.coerceAtLeast(0) ?: 0
	}

	/**
	 * Display tier from {@link #totalAmount}, floored by {@code unlocked_coll_tiers}
	 * for shared collections when amounts are missing or split oddly. Unlocks above the
	 * current tier list are reported by {@link #legacyTier} instead.
	 */
	fun displayTier(item: HypixelCollectionsCache.Item?): Int {
		if (item == null) {
			return 0
		}
		val tier = maxOf(item.tierFor(totalAmount(item.id)), unlockedTier(item.id))
		return if (item.maxTiers > 0) minOf(tier, item.maxTiers) else tier
	}

	// Hypixel keeps old unlocks after shrinking a collection (Redstone went 16 -> 9 tiers in 0.26.1),
	// so REDSTONE_16 can outlive the tier it names.
	fun legacyTier(item: HypixelCollectionsCache.Item?): Int {
		if (item == null || item.maxTiers <= 0) {
			return 0
		}
		val unlocked = unlockedTier(item.id)
		return if (unlocked > item.maxTiers) unlocked else 0
	}

	/** Same basis as {@link #totalAmount} (real amounts only). */
	fun progressAmount(item: HypixelCollectionsCache.Item?): Long = if (item == null) 0L else totalAmount(item.id)

	fun membersByAmount(itemId: String?): List<Member> = members.sortedWith(
		compareByDescending<Member> { it.amount(itemId) }.thenComparing({ displayName(it) }, String.CASE_INSENSITIVE_ORDER)
	)

	/** Coop ranks cost one Hypixel request per member, so they load only once Collections is shown. */
	fun requestMemberRanks() {
		if (ranksRequested.compareAndSet(false, true)) {
			resolveRanksAsync()
		}
	}

	private fun resolveNamesAsync() {
		for (member in members) {
			if (member.uuid.isBlank() || member.uuid == viewedUuid) {
				continue
			}
			if (member.name != shortUuid(member.uuid)) {
				continue
			}
			val uuid = HypixelApiClient.parseUndashedUuid(member.uuid) ?: continue
			HypixelApiClient.resolveName(uuid).thenAccept { opt -> opt.ifPresent { id -> putName(member.uuid, id.name) } }
		}
	}

	private fun resolveRanksAsync() {
		for (member in members) {
			if (member.uuid.isBlank()) {
				continue
			}
			val uuid = HypixelApiClient.parseUndashedUuid(member.uuid) ?: continue
			HypixelApiClient.player(uuid).thenAccept { opt -> opt.ifPresent { player -> putPlayerRank(member.uuid, player) } }
		}
	}

	companion object {
		@JvmStatic
		fun empty(): CollectionSnapshot = CollectionSnapshot(listOf(), listOf(), "", listOf())

		@JvmStatic
		fun fromProfile(members: JsonObject?, viewedUuid: UUID?, viewedName: String?): CollectionSnapshot {
			HypixelCollectionsCache.awaitReady(8_000L)
			val categories = withBossCategory(HypixelCollectionsCache.categories())
			val viewed = HypixelApiClient.undashed(viewedUuid)

			val memberList = ArrayList<Member>()
			val coopCrafted = HashMap<String, MutableSet<Int>>()

			if (members != null) {
				for ((rawUuid, value) in members.entrySet()) {
					if (!value.isJsonObject) {
						continue
					}
					val uuid = rawUuid?.replace("-", "")?.lowercase(Locale.ROOT) ?: ""
					val memberObj = value.asJsonObject
					val amounts = readCollectionAmounts(memberObj)
					for ((bossId, amount) in BossCollections.amountsFromMember(memberObj)) {
						putAmount(amounts, bossId, amount)
					}
					val crafted = readCraftedTiers(memberObj)
					val unlocked = readUnlockedTiers(memberObj)
					mergeCrafted(coopCrafted, crafted)
					val name = if (uuid == viewed && !viewedName.isNullOrBlank()) viewedName else shortUuid(uuid)
					memberList.add(member(uuid, name, amounts, crafted, unlocked))
				}
			}

			memberList.sortWith(compareBy<Member> { it.uuid != viewed }.thenComparing({ it.name }, String.CASE_INSENSITIVE_ORDER))

			val minionEntries = ArrayList<MinionEntry>()
			for ((id, max) in NeuRepoCache.minionMaxTiers()) {
				val tiers = coopCrafted[id] ?: setOf()
				minionEntries.add(minion(id, prettyMinion(id), max, tiers))
			}
			minionEntries.sortWith(Comparator.comparing({ m: MinionEntry -> m.displayName }, String.CASE_INSENSITIVE_ORDER))

			val snapshot = CollectionSnapshot(categories, memberList, viewed, minionEntries)
			snapshot.resolveNamesAsync()
			return snapshot
		}

		private fun member(
			uuid: String?, name: String?, amounts: Map<String, Long>?, craftedTiers: Map<String, Set<Int>>?,
			unlockedTiers: Map<String, Int>?
		): Member {
			val id = uuid?.lowercase(Locale.ROOT) ?: ""
			val crafted = LinkedHashMap<String, Set<Int>>()
			craftedTiers?.forEach { (key, value) -> crafted[key] = java.util.Set.copyOf(value) }
			return Member(
				id,
				if (name.isNullOrBlank()) shortUuid(id) else name,
				java.util.Map.copyOf(amounts ?: mapOf()),
				java.util.Map.copyOf(crafted),
				java.util.Map.copyOf(unlockedTiers ?: mapOf())
			)
		}

		private fun minion(id: String?, displayName: String?, tierCap: Int, craftedTiers: Set<Int>?): MinionEntry {
			val upper = id?.uppercase(Locale.ROOT) ?: ""
			return MinionEntry(
				upper,
				if (displayName.isNullOrBlank()) prettyMinion(upper) else displayName,
				tierCap,
				java.util.Set.copyOf(craftedTiers ?: setOf())
			)
		}

		/** Hypixel categories plus Boss, ordered to match the in-game / SkyCrypt icon bar. */
		private fun withBossCategory(source: List<HypixelCollectionsCache.Category>?): List<HypixelCollectionsCache.Category> {
			val ordered = ArrayList<HypixelCollectionsCache.Category>()
			val boss = BossCollections.category()
			var insertedBoss = false
			var hasBoss = false
			for (category in source.orEmpty()) {
				if ("BOSS".equals(category.id, ignoreCase = true)) {
					hasBoss = true
					ordered.add(category)
					continue
				}
				if (!insertedBoss && "RIFT".equals(category.id, ignoreCase = true)) {
					ordered.add(boss)
					insertedBoss = true
				}
				ordered.add(category)
			}
			if (!insertedBoss && !hasBoss) {
				ordered.add(boss)
			}
			return java.util.List.copyOf(ordered)
		}

		private fun readCollectionAmounts(member: JsonObject): MutableMap<String, Long> {
			val out = LinkedHashMap<String, Long>()
			val collection = Leveling.obj(member.get("collection")) ?: return out
			for ((key, value) in collection.entrySet()) {
				val amount = Leveling.longAmount(value)
				if (amount <= 0L || key.isNullOrBlank()) {
					continue
				}
				putAmount(out, key, amount)
			}
			synthesizeComposites(out)
			return out
		}

		/** Fill aggregate collections from component items when the aggregate key is absent. */
		private fun synthesizeComposites(out: MutableMap<String, Long>) {
			for (aggregate in listOf("MUSHROOM_COLLECTION", "GEMSTONE_COLLECTION")) {
				if (lookupDirect(out, aggregate) > 0L) {
					continue
				}
				val sum = CollectionIds.compositeParts(aggregate).sumOf { lookupDirect(out, it) }
				if (sum > 0L) {
					putAmount(out, aggregate, sum)
				}
			}
		}

		private fun tierArray(member: JsonObject, key: String): JsonArray? {
			val playerData = Leveling.obj(member.get("player_data"))
			if (playerData != null && playerData.has(key) && playerData.get(key).isJsonArray) {
				return playerData.getAsJsonArray(key)
			}
			if (member.has(key) && member.get(key).isJsonArray) {
				return member.getAsJsonArray(key)
			}
			return null
		}

		private fun readUnlockedTiers(member: JsonObject): Map<String, Int> {
			val out = HashMap<String, Int>()
			val array = tierArray(member, "unlocked_coll_tiers") ?: return out
			val knownIds = knownCollectionIds()
			for (el in array) {
				if (!el.isJsonPrimitive) {
					continue
				}
				parseUnlockedEntry(el.asString, knownIds, out)
			}
			return out
		}

		private fun knownCollectionIds(): List<String> {
			val ids = ArrayList<String>()
			for (category in HypixelCollectionsCache.categories()) {
				for (item in category.items) {
					ids.add(item.id)
				}
			}
			ids.sortWith(compareByDescending<String> { it.length }.thenBy { it })
			return ids
		}

		private fun parseUnlockedEntry(raw: String?, knownIds: List<String>, out: MutableMap<String, Int>) {
			if (raw.isNullOrBlank()) {
				return
			}
			val key = raw.trim().uppercase(Locale.ROOT)
			for (id in knownIds) {
				val prefix = id.uppercase(Locale.ROOT) + "_"
				if (!key.startsWith(prefix)) {
					continue
				}
				val tier = key.substring(prefix.length).toIntOrNull() ?: continue
				if (tier > 0) {
					mergeTier(out, id, tier)
				}
				return
			}
			// Fallback when collections cache is empty: split on last '_' outside a trailing number.
			val us = key.lastIndexOf('_')
			if (us <= 0 || us >= key.length - 1) {
				return
			}
			val tier = key.substring(us + 1).toIntOrNull() ?: return
			if (tier > 0) {
				mergeTier(out, key.substring(0, us), tier)
			}
		}

		private fun mergeTier(out: MutableMap<String, Int>, id: String, tier: Int) {
			out.merge(id, tier) { a, b -> maxOf(a, b) }
			for (alias in CollectionIds.lookupKeys(id)) {
				out.merge(alias, tier) { a, b -> maxOf(a, b) }
			}
		}

		private fun readCraftedTiers(member: JsonObject): Map<String, MutableSet<Int>> {
			val out = HashMap<String, MutableSet<Int>>()
			val array = tierArray(member, "crafted_generators") ?: return out
			for (el in array) {
				if (!el.isJsonPrimitive) {
					continue
				}
				parseCraftedEntry(el.asString, out)
			}
			return out
		}

		private fun parseCraftedEntry(raw: String?, out: MutableMap<String, MutableSet<Int>>) {
			if (raw.isNullOrBlank()) {
				return
			}
			val key = raw.trim().uppercase(Locale.ROOT)
			var tier = 0
			var base = key
			val us = key.lastIndexOf('_')
			if (us > 0 && us < key.length - 1) {
				val parsed = key.substring(us + 1).toIntOrNull()
				if (parsed != null) {
					tier = parsed
					base = key.substring(0, us)
				}
			}
			if (tier <= 0) {
				return
			}
			if (!base.endsWith("_GENERATOR")) {
				base += "_GENERATOR"
			}
			out.getOrPut(base) { TreeSet() }.add(tier)
		}

		private fun mergeCrafted(into: MutableMap<String, MutableSet<Int>>, from: Map<String, Set<Int>>) {
			for ((key, value) in from) {
				into.getOrPut(key) { HashSet() }.addAll(value)
			}
		}

		private fun lookupAmount(amounts: Map<String, Long>?, itemId: String?): Long {
			if (amounts == null || itemId.isNullOrBlank()) {
				return 0L
			}
			val direct = lookupDirect(amounts, itemId)
			if (direct > 0L) {
				return direct
			}
			return CollectionIds.compositeParts(itemId).sumOf { lookupDirect(amounts, it) }
		}

		private fun lookupDirect(amounts: Map<String, Long>, itemId: String?): Long {
			for (key in CollectionIds.lookupKeys(itemId)) {
				val value = amounts[key]
				if (value != null && value > 0L) {
					return value
				}
			}
			return 0L
		}

		/** Expand each profile collection key under common aliases so UI ids resolve. */
		private fun putAmount(out: MutableMap<String, Long>, key: String?, amount: Long) {
			if (key.isNullOrBlank() || amount <= 0L) {
				return
			}
			for (alias in CollectionIds.lookupKeys(key)) {
				out.merge(alias, amount) { a, b -> maxOf(a, b) }
			}
		}

		private fun shortUuid(uuid: String?): String {
			if (uuid == null || uuid.length < 8) {
				return uuid ?: "?"
			}
			return uuid.substring(0, 8)
		}

		private fun prettyMinion(id: String?): String {
			if (id.isNullOrBlank()) {
				return "?"
			}
			var base = id.uppercase(Locale.ROOT)
			if (base.endsWith("_GENERATOR")) {
				base = base.substring(0, base.length - "_GENERATOR".length)
			}
			val sb = StringBuilder()
			for (part in base.lowercase(Locale.ROOT).split("_")) {
				if (part.isBlank()) {
					continue
				}
				if (sb.isNotEmpty()) {
					sb.append(' ')
				}
				sb.append(part[0].uppercaseChar()).append(part.substring(1))
			}
			return sb.toString()
		}
	}
}
