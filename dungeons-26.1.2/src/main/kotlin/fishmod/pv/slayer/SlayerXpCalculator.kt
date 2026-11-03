/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.slayer

import fishmod.pv.data.Leveling
import fishmod.pv.data.RepoData
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object SlayerXpCalculator {
	private val QUEST_COSTS = intArrayOf(2_000, 7_500, 20_000, 50_000, 100_000)

	@JvmRecord
	data class TierLine(val tier: Int, val bosses: Long, val coins: Long, val color: Int)

	@JvmRecord
	data class Result(
		val slayerId: String,
		val slayerName: String,
		val bossName: String,
		val item: Item,
		val currentLevel: Int,
		val targetLevel: Int,
		val xpNeeded: Float,
		val tiers: List<TierLine>,
		val mods: SlayerMayorMods
	)

	@JvmStatic
	fun maxLevel(slayerId: String?): Int {
		if ("vampire".equals(slayerId, ignoreCase = true)) return 5
		val table = RepoData.slayerXp(slayerId)
		if (table == null || table.isEmpty) return 9
		return min(9, table.size())
	}

	@JvmStatic
	fun calculate(
		slayerId: String?,
		slayerName: String?,
		currentXp: Float,
		currentLevel: Int,
		targetLevel: Int,
		mods: SlayerMayorMods?
	): Result {
		val id = slayerId?.lowercase(Locale.ROOT) ?: ""
		val used = mods ?: SlayerMayorMods.none()
		val max = maxLevel(id)
		val target = max(1, min(max, targetLevel))
		val table = RepoData.slayerXp(id)
		val needXp = max(0f, Leveling.xpRequiredForLevel(table, target, true) - max(0f, currentXp))
		val bossXp = RepoData.slayerBossXp(id)
		val highest = RepoData.slayerHighestTier(id)
		val xpMult = used.xpMultiplier()
		val priceMult = used.priceMultiplier()
		val lines = ArrayList<TierLine>()
		var limit = max(1, min(highest, QUEST_COSTS.size))
		if (bossXp != null) limit = min(limit, bossXp.size())
		for (i in 0 until limit) {
			val baseXp = if (bossXp != null && i < bossXp.size()) bossXp.get(i).asFloat else 0f
			val perBoss = (baseXp * xpMult).toFloat()
			var bosses = 0L
			if (needXp > 0f && perBoss > 0f) bosses = ceil((needXp / perBoss).toDouble()).toLong()
			val coins = Math.round(bosses * QUEST_COSTS[i] * priceMult)
			lines.add(TierLine(i + 1, bosses, coins, tierColor(i)))
		}
		return Result(
			id,
			slayerName ?: "",
			bossName(id),
			item(id),
			max(0, currentLevel),
			target,
			needXp,
			java.util.List.copyOf(lines),
			used
		)
	}

	@JvmStatic
	fun bossName(slayerId: String?): String = when (slayerId?.lowercase(Locale.ROOT) ?: "") {
		"zombie" -> "Revenant Horror"
		"spider" -> "Tarantula Broodfather"
		"wolf" -> "Sven Packmaster"
		"enderman" -> "Voidgloom Seraph"
		"blaze" -> "Inferno Demonlord"
		"vampire" -> "Riftstalker Bloodfiend"
		else -> "Slayer"
	}

	@JvmStatic
	fun item(slayerId: String?): Item = when (slayerId?.lowercase(Locale.ROOT) ?: "") {
		"zombie" -> Items.ROTTEN_FLESH
		"spider" -> Items.STRING
		"wolf" -> Items.BONE
		"enderman" -> Items.ENDER_PEARL
		"blaze" -> Items.BLAZE_POWDER
		"vampire" -> Items.REDSTONE
		else -> Items.PAPER
	}

	private fun tierColor(index: Int): Int = when (index) {
		0 -> 0xFF55FF55
		1 -> 0xFFFFFF55
		2 -> 0xFFFF5555
		3 -> 0xFFAA0000
		else -> 0xFFAA00AA
	}.toInt()
}
