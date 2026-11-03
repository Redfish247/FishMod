/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import fishmod.pv.price.HypixelCollectionsCache
import java.util.Locale

/**
 * Catacombs / Kuudra boss collections (not in Hypixel's collections resource).
 * Amounts mirror SkyCrypt: floor completions + 2× master, Kuudra weighted by tier.
 */
object BossCollections {
	@JvmRecord
	data class BossDef(val id: String, val name: String, val iconId: String, val thresholds: LongArray) {
		fun toItem(): HypixelCollectionsCache.Item {
			val tiers = thresholds.mapIndexed { i, amount -> HypixelCollectionsCache.Tier(i + 1, amount, listOf()) }
			return HypixelCollectionsCache.Item(id, name, thresholds.size, tiers)
		}
	}

	private val BOSSES: List<BossDef> = listOf(
		BossDef("BOSS_BONZO", "Bonzo", "GOLD_BONZO_HEAD", longArrayOf(25, 50, 100, 150, 250, 1000)),
		BossDef("BOSS_SCARF", "Scarf", "GOLD_SCARF_HEAD", longArrayOf(25, 50, 100, 150, 250, 1000)),
		BossDef("BOSS_PROFESSOR", "Professor", "GOLD_PROFESSOR_HEAD", longArrayOf(25, 50, 100, 150, 250, 1000)),
		BossDef("BOSS_THORN", "Thorn", "GOLD_THORN_HEAD", longArrayOf(50, 100, 150, 250, 400, 1000)),
		BossDef("BOSS_LIVID", "Livid", "GOLD_LIVID_HEAD", longArrayOf(50, 100, 150, 250, 500, 750, 1000)),
		BossDef("BOSS_SADAN", "Sadan", "GOLD_SADAN_HEAD", longArrayOf(50, 100, 150, 250, 500, 750, 1000)),
		BossDef("BOSS_NECRON", "Necron", "GOLD_NECRON_HEAD", longArrayOf(50, 100, 150, 250, 500, 750, 1000)),
		BossDef("BOSS_KUUDRA", "Kuudra", "KUUDRA;4", longArrayOf(10, 100, 500, 2000, 5000))
	)

	private val KUUDRA_TIERS = arrayOf("none", "hot", "burning", "fiery", "infernal")

	@JvmStatic
	fun isBossId(id: String?): Boolean = id != null && id.uppercase(Locale.ROOT).startsWith("BOSS_")

	@JvmStatic
	fun category(): HypixelCollectionsCache.Category =
		HypixelCollectionsCache.Category("BOSS", "Boss", BOSSES.map { it.toItem() })

	@JvmStatic
	fun bosses(): List<BossDef> = BOSSES

	/** Floor / Kuudra completion amounts keyed by {@code BOSS_*} ids. */
	@JvmStatic
	fun amountsFromMember(member: JsonObject?): MutableMap<String, Long> {
		val out = LinkedHashMap<String, Long>()
		if (member == null) {
			return out
		}
		val dungeons = Leveling.obj(member.get("dungeons"))
		val types = dungeons?.let { Leveling.obj(it.get("dungeon_types")) }
		val normal = types?.let { Leveling.obj(it.get("catacombs")) }
		val master = types?.let { Leveling.obj(it.get("master_catacombs")) }
		val normalCompletions = normal?.let { Leveling.obj(it.get("tier_completions")) }
		val masterCompletions = master?.let { Leveling.obj(it.get("tier_completions")) }

		// Bonzo..Necron map to floors 1..7
		for (i in 0 until 7) {
			val floor = (i + 1).toString()
			val amount = readLong(normalCompletions, floor) + readLong(masterCompletions, floor) * 2L
			if (amount > 0L) {
				out[BOSSES[i].id] = amount
			}
		}

		val kuudra = kuudraAmount(member)
		if (kuudra > 0L) {
			out["BOSS_KUUDRA"] = kuudra
		}
		return out
	}

	private fun kuudraAmount(member: JsonObject): Long {
		val nether = Leveling.obj(member.get("nether_island_player_data"))
		val tiers = nether?.let { Leveling.obj(it.get("kuudra_completed_tiers")) } ?: return 0L
		var sum = 0L
		for (i in KUUDRA_TIERS.indices) {
			sum += readLong(tiers, KUUDRA_TIERS[i]) * (i + 1L)
		}
		return sum
	}

	private fun readLong(obj: JsonObject?, key: String?): Long {
		if (obj == null || key == null || !obj.has(key)) {
			return 0L
		}
		return Leveling.longAmount(obj.get(key))
	}
}
