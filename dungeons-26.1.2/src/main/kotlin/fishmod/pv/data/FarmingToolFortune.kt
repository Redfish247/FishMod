/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import fishmod.pv.data.FarmingFortuneEstimate.Line
import fishmod.pv.networth.NbtAttrs
import java.util.Locale
import java.util.regex.Pattern
import net.minecraft.nbt.CompoundTag

/**
 * Farming tool fortune worked out from ExtraAttributes. Toolkit tools have no lore, so this is the only way to value
 * them, and held tools use it too so both compare the same way. Values follow EliteFarmers' FarmingWeight tables.
 */
object FarmingToolFortune {
	enum class Type { HOE, AXE }

	@JvmRecord
	data class Info(val crops: List<String>, val type: Type, val tier: Int)

	@JvmField
	val RARITIES: List<String> = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")
	private val REFORGES: Map<String, DoubleArray> = mapOf(
		"blessed" to doubleArrayOf(5.0, 7.0, 9.0, 13.0, 16.0, 20.0),
		"bountiful" to doubleArrayOf(1.0, 2.0, 3.0, 5.0, 7.0, 10.0),
		"robust" to doubleArrayOf(2.0, 3.0, 4.0, 6.0, 8.0, 10.0),
		"green_thumb" to doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0),
		"earthy" to doubleArrayOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0)
	)
	private val REFORGE_TARGET: Map<String, Type> = mapOf(
		"robust" to Type.HOE, "green_thumb" to Type.HOE, "earthy" to Type.AXE
	)
	private val PERIDOT: Map<String, DoubleArray> = mapOf(
		"ROUGH" to doubleArrayOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0),
		"FLAWED" to doubleArrayOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0),
		"FINE" to doubleArrayOf(1.5, 2.0, 3.0, 4.0, 5.0, 6.0),
		"FLAWLESS" to doubleArrayOf(2.0, 3.0, 4.0, 5.0, 6.0, 8.0),
		"PERFECT" to doubleArrayOf(3.0, 4.0, 5.0, 6.0, 8.0, 10.0)
	)
	private val DEDICATION = doubleArrayOf(0.5, 0.75, 1.0, 2.0)
	private val TIER_SUFFIX: Pattern = Pattern.compile("_([0-9]+)$")
	private val TIER_SUFFIX_STRIP = Regex("_[0-9]+$")
	private val WHITESPACE = Regex("\\s+")
	private val TURBO: Map<String, String> = mapOf(
		"Wheat" to "turbo_wheat", "Carrot" to "turbo_carrot", "Potato" to "turbo_potato",
		"Pumpkin" to "turbo_pumpkin", "Melon" to "turbo_melon", "Cactus" to "turbo_cactus",
		"Sugar Cane" to "turbo_cane", "Cocoa Beans" to "turbo_coco",
		"Mushroom" to "turbo_mushrooms", "Nether Wart" to "turbo_warts",
		"Sunflower" to "turbo_sunflower", "Moonflower" to "turbo_moonflower",
		"Wild Rose" to "turbo_rose"
	)

	/** Null when the id isn't a crop-specific farming tool. */
	@JvmStatic
	fun info(id: String?): Info? {
		if (id == null) {
			return null
		}
		val upper = id.uppercase(Locale.ROOT)
		if (upper.startsWith("THEORETICAL_HOE_")) {
			val rest = upper.substring("THEORETICAL_HOE_".length)
			val tier = tierSuffix(rest)
			val crops = when (TIER_SUFFIX_STRIP.replace(rest, "")) {
				"WHEAT" -> listOf("Wheat")
				"CARROT" -> listOf("Carrot")
				"POTATO" -> listOf("Potato")
				"CANE" -> listOf("Sugar Cane")
				"WARTS" -> listOf("Nether Wart")
				"SUNFLOWER" -> listOf("Sunflower", "Moonflower")
				"WILD_ROSE" -> listOf("Wild Rose")
				else -> listOf()
			}
			return if (crops.isEmpty()) null else Info(crops, Type.HOE, tier)
		}
		val tier = tierSuffix(upper)
		return when (TIER_SUFFIX_STRIP.replace(upper, "")) {
			"MELON_DICER" -> Info(listOf("Melon"), Type.AXE, tier)
			"PUMPKIN_DICER" -> Info(listOf("Pumpkin"), Type.AXE, tier)
			"COCO_CHOPPER" -> Info(listOf("Cocoa Beans"), Type.AXE, tier)
			"CACTUS_KNIFE" -> Info(listOf("Cactus"), Type.HOE, tier)
			"FUNGI_CUTTER" -> Info(listOf("Mushroom"), Type.HOE, tier)
			else -> null
		}
	}

	private fun tierSuffix(id: String): Int {
		val m = TIER_SUFFIX.matcher(id)
		return if (m.find()) m.group(1).toInt().coerceIn(1, 3) else 1
	}

	/** Mk. I tools start Uncommon, Mk. II Rare, Mk. III Epic; a recombobulator adds one. */
	@JvmStatic
	fun rarity(info: Info, attrs: CompoundTag?, lore: List<String>?): String {
		rarityFromLore(lore)?.let { return it }
		val upgrades = maxOf(0, NbtAttrs.intValue(attrs, "rarity_upgrades", 0))
		return RARITIES[minOf(RARITIES.size - 1, info.tier + upgrades)]
	}

	@JvmStatic
	fun rarityFromLore(lore: List<String>?): String? {
		if (lore == null) {
			return null
		}
		for (i in lore.indices.reversed()) {
			val line = PlayerStatsCalculator.stripFormatting(lore[i]).trim()
			if (line.isEmpty()) {
				continue
			}
			return line.uppercase(Locale.ROOT).split(WHITESPACE).firstOrNull { it in RARITIES }
		}
		return null
	}

	/**
	 * Breakdown for one crop. {@code milestones} is null until the garden loads, which leaves Dedication unknown
	 * instead of zero.
	 */
	@JvmStatic
	fun parts(
		info: Info, attrs: CompoundTag?, rarity: String?, crop: String, milestones: Map<String, Int>?, axedPerk: Boolean
	): MutableList<Line> {
		val out = ArrayList<Line>()
		val r = maxOf(0, RARITIES.indexOf(rarity))
		val level = maxOf(1, NbtAttrs.intValue(attrs, "levelable_lvl", 1))
		out.add(Line.counted("Tool level", 4.0 * level, "Level $level"))

		val reforge = NbtAttrs.string(attrs, "modifier")
		if (reforge != null) {
			val key = reforge.lowercase(Locale.ROOT)
			val table = REFORGES[key]
			val target = REFORGE_TARGET[key]
			if (table != null && (target == null || target == info.type)) {
				out.add(Line.counted(pretty(key) + " reforge", table[r], titleCase(rarity)))
			}
		}
		val ffd = NbtAttrs.intValue(attrs, "farming_for_dummies_count", 0).coerceIn(0, 5)
		if (ffd > 0) {
			out.add(Line.counted("Farming for Dummies", ffd.toDouble(), "$ffd / 5"))
		}
		val gemCount = IntArray(1)
		val gems = peridot(attrs, r, gemCount)
		if (gems > 0.0) {
			out.add(Line.counted("Peridot gems", gems, if (gemCount[0] == 1) "1 gem" else "${gemCount[0]} gems"))
		}

		val ench = NbtAttrs.intMap(attrs, "enchantments")
		val harvesting = ench["harvesting"] ?: 0
		if (info.type == Type.HOE && harvesting > 0) {
			out.add(Line.counted("Harvesting " + roman(harvesting), 12.5 * minOf(6, harvesting), ""))
		}
		val sunder = ench["sunder"] ?: 0
		if (info.type == Type.AXE && sunder > 0) {
			out.add(Line.counted("Sunder " + roman(sunder), 12.5 * minOf(6, sunder), ""))
		}
		val cultivating = ench["cultivating"] ?: 0
		if (cultivating > 0) {
			out.add(Line.counted("Cultivating " + roman(cultivating), 2.0 * minOf(10, cultivating), ""))
		}
		val dedication = ench["dedication"] ?: 0
		if (dedication > 0) {
			val label = "Dedication " + roman(dedication)
			if (milestones == null) {
				out.add(Line.unavailable(label, "Needs crop milestone, garden not loaded"))
			} else {
				val milestone = milestones[crop] ?: 0
				val per = DEDICATION[minOf(DEDICATION.size, dedication) - 1]
				out.add(Line.counted(label, per * milestone, "$crop milestone $milestone"))
			}
		}
		val turbo = TURBO[crop]?.let { ench[it] } ?: 0
		if (turbo > 0) {
			out.add(Line.counted(turboName(crop) + " " + roman(turbo), 5.0 * turbo, ""))
		}
		if (info.type == Type.AXE && axedPerk) {
			val before = out.filter { it.kind != FarmingFortuneEstimate.Kind.UNAVAILABLE }.sumOf { it.value }
			out.add(Line.counted("Axed perk", before * 0.02, "+2% on axes"))
		}
		return out
	}

	@JvmStatic
	fun peridot(attrs: CompoundTag?, rarity: Int, count: IntArray?): Double {
		val gems = NbtAttrs.compound(attrs, "gems") ?: return 0.0
		var total = 0.0
		for (key in gems.keySet()) {
			if (!key.startsWith("PERIDOT_") || key.endsWith("_gem")) {
				continue
			}
			val value = gems.get(key)
			val quality = if (value is CompoundTag) NbtAttrs.string(value, "quality") else NbtAttrs.string(gems, key)
			val table = quality?.let { PERIDOT[it.uppercase(Locale.ROOT)] }
			if (table != null) {
				total += table[rarity]
				if (count != null) {
					count[0]++
				}
			}
		}
		return total
	}

	private fun turboName(crop: String): String = when (crop) {
		"Cactus" -> "Turbo-Cacti"
		"Sugar Cane" -> "Turbo-Cane"
		"Cocoa Beans" -> "Turbo-Cocoa"
		"Mushroom" -> "Turbo-Mushrooms"
		"Nether Wart" -> "Turbo-Warts"
		"Wild Rose" -> "Turbo-Rose"
		else -> "Turbo-$crop"
	}

	@JvmStatic
	fun roman(n: Int): String = when (n) {
		1 -> "I"
		2 -> "II"
		3 -> "III"
		4 -> "IV"
		5 -> "V"
		6 -> "VI"
		7 -> "VII"
		8 -> "VIII"
		9 -> "IX"
		10 -> "X"
		else -> n.toString()
	}

	@JvmStatic
	fun pretty(key: String): String {
		val out = StringBuilder()
		for (part in key.split("_")) {
			if (part.isNotEmpty()) {
				if (out.isNotEmpty()) {
					out.append(' ')
				}
				out.append(part[0].uppercaseChar()).append(part.substring(1).lowercase(Locale.ROOT))
			}
		}
		return out.toString()
	}

	private fun titleCase(rarity: String?): String = if (rarity.isNullOrEmpty()) "" else pretty(rarity)
}
