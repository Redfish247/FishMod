/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import java.util.Locale

/**
 * ColeWeight (ninjune / Vinxey thresholds): each listed amount equals one weight unit.
 * Categories: experience, powder, collection, miscellaneous.
 */
object ColeWeight {
	@JvmRecord
	data class Line(val category: String, val label: String, val weight: Double, val amount: Double, val perUnit: Double)

	@JvmRecord
	data class Result(val total: Double, val lines: List<Line>) {
		fun byCategory(): MutableMap<String, MutableList<Line>> {
			val out = LinkedHashMap<String, MutableList<Line>>()
			for (line in lines) {
				out.getOrPut(line.category) { ArrayList() }.add(line)
			}
			return out
		}

		companion object {
			@JvmStatic
			fun empty(): Result = Result(0.0, listOf())
		}
	}

	private data class Threshold(val category: String, val label: String, val cost: Double, val collectionId: String)

	private val COLLECTIONS: List<Threshold> = listOf(
		Threshold("collection", "Mithril", 350_000.0, "MITHRIL_ORE"),
		Threshold("collection", "Gemstone", 1_450_000.0, "GEMSTONE_COLLECTION"),
		Threshold("collection", "Gold Ingot", 1_500_000.0, "GOLD_INGOT"),
		Threshold("collection", "Netherrack", 1_500_000.0, "NETHERRACK"),
		Threshold("collection", "Diamond", 460_000.0, "DIAMOND"),
		Threshold("collection", "Ice", 1_000_000.0, "ICE"),
		Threshold("collection", "Redstone", 1_000_000.0, "REDSTONE"),
		Threshold("collection", "Lapis", 1_000_000.0, "INK_SACK:4"),
		Threshold("collection", "Sulphur", 9_999_999_999.0, "SULPHUR"),
		Threshold("collection", "Coal", 650_000.0, "COAL"),
		Threshold("collection", "Emerald", 600_000.0, "EMERALD"),
		Threshold("collection", "End Stone", 2_000_000.0, "ENDER_STONE"),
		Threshold("collection", "Glowstone Dust", 400_000.0, "GLOWSTONE_DUST"),
		Threshold("collection", "Gravel", 450_000.0, "GRAVEL"),
		Threshold("collection", "Iron Ingot", 500_000.0, "IRON_INGOT"),
		Threshold("collection", "Mycelium", 600_000.0, "MYCEL"),
		Threshold("collection", "Quartz", 550_000.0, "QUARTZ"),
		Threshold("collection", "Obsidian", 400_000.0, "OBSIDIAN"),
		Threshold("collection", "Red Sand", 600_000.0, "SAND:1"),
		Threshold("collection", "Sand", 800_000.0, "SAND"),
		Threshold("collection", "Cobblestone", 2_000_000.0, "COBBLESTONE"),
		Threshold("collection", "Hard Stone", 2_000_000.0, "HARD_STONE"),
		Threshold("collection", "Living Metal Heart", 40.0, "METAL_HEART"),
		Threshold("collection", "Glacite", 80_000.0, "GLACITE"),
		Threshold("collection", "Tungsten", 53_000.0, "TUNGSTEN"),
		Threshold("collection", "Umber", 48_000.0, "UMBER")
	)

	@JvmStatic
	fun calculate(miningIn: MiningSnapshot?, collections: CollectionSnapshot?, member: JsonObject?): Result {
		val mining = miningIn ?: MiningSnapshot.empty()
		val lines = ArrayList<Line>()

		val miningXp = Leveling.readSkillXp(member, "mining").toDouble()
		add(lines, "experience", "Mining Experience", miningXp, 2_500_000.0)

		add(lines, "powder", "Mithril Powder", mining.mithril.total().toDouble(), 400_000.0)
		add(lines, "powder", "Gemstone Powder", mining.gemstone.total().toDouble(), 400_000.0)
		add(lines, "powder", "Glacite Powder", mining.glacite.total().toDouble(), 400_000.0)

		for (t in COLLECTIONS) {
			val amount = collections?.viewedAmount(t.collectionId) ?: 0L
			add(lines, t.category, t.label, amount.toDouble(), t.cost)
		}

		val scatha = killCount(member, "scatha", "scatha_10")
		val worm = killCount(member, "worm", "worm_5")
		add(lines, "miscellaneous", "Scatha Kills", scatha, 8.0)
		add(lines, "miscellaneous", "Worm Kills", worm, 12.0)
		add(lines, "miscellaneous", "Nucleus Runs", mining.nucleusRuns().toDouble(), 2.5)

		var total = 0.0
		for (line in lines) {
			total += line.weight
		}
		return Result(total, java.util.List.copyOf(lines))
	}

	private fun add(lines: MutableList<Line>, category: String, label: String, amount: Double, cost: Double) {
		if (cost <= 0) {
			return
		}
		lines.add(Line(category, label, amount / cost, amount, cost))
	}

	private fun killCount(member: JsonObject?, killKey: String, bestiaryKey: String): Double {
		if (member == null) {
			return 0.0
		}
		val bestiary = Leveling.obj(member.get("bestiary"))
		val kills = bestiary?.let { Leveling.obj(it.get("kills")) }
		var fromBestiary = numAt(kills, bestiaryKey)
		if (fromBestiary <= 0) {
			fromBestiary = numAt(kills, killKey)
		}
		if (fromBestiary > 0) {
			return fromBestiary
		}

		val playerStats = Leveling.obj(member.get("player_stats"))
		val psKills = playerStats?.let { Leveling.obj(it.get("kills")) }
		var fromStats = numAt(psKills, killKey)
		if (fromStats <= 0) {
			fromStats = sumMatching(psKills, killKey)
		}
		return maxOf(0.0, fromStats)
	}

	private fun numAt(obj: JsonObject?, key: String?): Double {
		if (obj == null || key == null) {
			return 0.0
		}
		return Leveling.num(obj.get(key))?.toDouble() ?: 0.0
	}

	private fun sumMatching(obj: JsonObject?, needle: String?): Double {
		if (obj == null || needle.isNullOrBlank()) {
			return 0.0
		}
		val lower = needle.lowercase(Locale.ROOT)
		var sum = 0.0
		for ((key, value) in obj.entrySet()) {
			if (key != null && key.lowercase(Locale.ROOT).contains(lower)) {
				Leveling.num(value)?.let { sum += it.toDouble() }
			}
		}
		return sum
	}
}
