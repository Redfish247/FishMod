/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import fishmod.pv.data.FarmingFortuneEstimate.Garden
import fishmod.pv.data.FarmingFortuneEstimate.Kind
import fishmod.pv.data.FarmingFortuneEstimate.Line
import fishmod.pv.networth.NbtAttrs
import java.util.Locale

/**
 * Temporary and situational Farming Fortune. None of it is part of a total: a source is only {@link Status#ACTIVE}
 * when saved profile data proves it, and sources the API can't show stay {@link Status#UNKNOWN}.
 * Values follow the Hypixel wiki (Farming Fortune, Garden Chips and each item's page) as of SkyBlock 0.27.
 */
object FarmingFortuneTemporary {
	enum class Status { ACTIVE, UNKNOWN, INACTIVE }

	@JvmRecord
	data class Source(
		val label: String, val value: Double, val status: Status, val crop: Boolean, val state: String,
		val condition: String, val iconId: String, val parts: List<Line>
	)

	@JvmRecord
	data class Buffs(val known: Boolean, val harvestHarbingerTicks: Long, val cakeExpiresAt: Long, val hypercharge: Int, val overdrive: Int) {
		companion object {
			@JvmField
			val NONE = Buffs(false, 0L, 0L, 0, 0)
		}
	}

	internal const val HARVEST_HARBINGER = 50.0
	internal const val CENTURY_CAKE = 5.0
	internal const val PER_PEST = 5.0
	internal const val MAX_PESTS = 40
	internal const val CROP_FEVER = 100.0
	internal const val TRUFFLE = 30.0
	internal const val MAGIC_8_BALL = 25.0
	internal const val ATMOSPHERIC_FILTER = 25.0
	internal const val EARTHWORM_PER_LEVEL = 3.0
	internal const val CRICKET_PER_LEVEL = 5.0
	internal val ANITA_FAMILY: List<String> = listOf("ANITA_ARTIFACT", "ANITA_RING", "ANITA_TALISMAN")
	private val ANITA_FORTUNE: Map<String, Double> = mapOf(
		"ANITA_ARTIFACT" to 25.0, "ANITA_RING" to 15.0, "ANITA_TALISMAN" to 5.0
	)
	private val ANITA_NAMES: Map<String, String> = mapOf(
		"ANITA_ARTIFACT" to "Anita's Artifact", "ANITA_RING" to "Anita's Ring", "ANITA_TALISMAN" to "Anita's Talisman"
	)
	private const val PHILLIP_GARDEN_LEVEL = 5
	private const val TICK_MS = 50L
	private const val SKYBLOCK_EPOCH_MS = 1_560_275_700_000L
	private const val YEAR_MS = 124L * 60L * 60L * 1000L
	private const val SPRING_MS = YEAR_MS / 4L

	internal fun buffs(playerData: JsonObject?): Buffs {
		if (playerData == null) {
			return Buffs.NONE
		}
		var potion = 0L
		val effects = playerData.get("active_effects")
		if (effects != null && effects.isJsonArray) {
			for (el in effects.asJsonArray) {
				val effect = Leveling.obj(el) ?: continue
				val id = effect.get("effect")
				if (id == null || !id.isJsonPrimitive || !id.asString.lowercase(Locale.ROOT).contains("harvest_harbinger")) {
					continue
				}
				val infinite = effect.get("infinite")
				val ticks = if (infinite != null && infinite.isJsonPrimitive && infinite.asBoolean) {
					Long.MAX_VALUE
				} else {
					Leveling.longAmount(effect.get("ticks_remaining"))
				}
				potion = maxOf(potion, ticks)
			}
		}
		var cake = 0L
		val temp = playerData.get("temp_stat_buffs")
		if (temp != null && temp.isJsonArray) {
			for (el in temp.asJsonArray) {
				val buff = Leveling.obj(el) ?: continue
				val key = buff.get("key")
				if (key == null || !key.isJsonPrimitive || !key.asString.lowercase(Locale.ROOT).contains("farming_fortune")) {
					continue
				}
				val expires = Leveling.longAmount(buff.get("expire_at"))
				cake = maxOf(cake, if (expires < 100_000_000_000L) expires * 1000L else expires)
			}
		}
		val chips = Leveling.obj(playerData.get("garden_chips"))
		return Buffs(true, potion, cake, FarmingFortuneEstimate.chipLevel(chips, "hypercharge"),
			FarmingFortuneEstimate.chipLevel(chips, "overdrive"))
	}

	internal fun hyperchargeBonus(level: Int): Double {
		if (level <= 0) {
			return 0.0
		}
		val per = if (level > 15) 5 else if (level > 10) 4 else 3
		return per * level / 100.0
	}

	internal fun overdriveFortune(level: Int): Double {
		val per = if (level > 15) 7 else if (level > 10) 6 else 5
		return (per * maxOf(0, level)).toDouble()
	}

	internal fun intoYear(nowMs: Long): Long = Math.floorMod(nowMs - SKYBLOCK_EPOCH_MS, YEAR_MS)

	internal fun spring(nowMs: Long): Boolean = intoYear(nowMs) < SPRING_MS

	internal fun sources(est: FarmingFortuneEstimate, crop: String?, garden: Garden, nowMs: Long): List<Source> {
		val input = est.inputs
		val buffs = input.buffs
		val hyper = hyperchargeBonus(buffs.hypercharge)
		val out = ArrayList<Source>()

		val potion = if (!buffs.known) Status.UNKNOWN else if (buffs.harvestHarbingerTicks > 0L) Status.ACTIVE else Status.INACTIVE
		val potionState = when (potion) {
			Status.ACTIVE -> if (buffs.harvestHarbingerTicks == Long.MAX_VALUE) {
				"Active"
			} else {
				FormatUtil.prettySpan(buffs.harvestHarbingerTicks * TICK_MS) + " left"
			}
			Status.INACTIVE -> "When last saved"
			Status.UNKNOWN -> "Not in the API"
		}
		out.add(Source("Harvest Harbinger Potion", HARVEST_HARBINGER, potion, false, potionState,
			"Lasts 25 minutes of play time.", "POTION_HARVEST_HARBINGER;5", listOf()))

		val cake = if (!buffs.known) Status.UNKNOWN else if (buffs.cakeExpiresAt > nowMs) Status.ACTIVE else Status.INACTIVE
		val cakeState = when (cake) {
			Status.ACTIVE -> FormatUtil.prettySpan(buffs.cakeExpiresAt - nowMs) + " left"
			Status.INACTIVE -> "Expired or not eaten"
			Status.UNKNOWN -> "Not in the API"
		}
		out.add(hypercharged(Source("Chocolate Century Cake", CENTURY_CAKE, cake, false, cakeState,
			"Lasts 48 hours after eating.", "EPOCH_CAKE_BROWN", listOf()), hyper, buffs.hypercharge))

		if (!garden.loaded || garden.level >= PHILLIP_GARDEN_LEVEL) {
			out.add(hypercharged(Source("Pest turn-in", PER_PEST * MAX_PESTS, Status.UNKNOWN, false, "Not in the API",
				"Lasts 30m after giving Phillip pests.", "PESTHUNTER_PHILLIP_NPC",
				listOf(Line("Pests", PER_PEST * MAX_PESTS, Kind.COUNTED, MAX_PESTS.toString() + " x " + PER_PEST.toInt()))),
				hyper, buffs.hypercharge))
		}

		if (est.atmosphericFilter) {
			val mite = input.shards["filter_upgrade"] ?: 0
			val parts = ArrayList<Line>()
			parts.add(Line("Spring", ATMOSPHERIC_FILTER, Kind.COUNTED, ""))
			if (mite > 0) {
				parts.add(Line("Mite shard", ATMOSPHERIC_FILTER * 0.02 * mite, Kind.COUNTED, "+" + 2 * mite + "%"))
			}
			val spring = spring(nowMs)
			val into = intoYear(nowMs)
			val state = if (spring) {
				"Spring, " + FormatUtil.prettySpan(SPRING_MS - into) + " left"
			} else {
				"Spring in " + FormatUtil.prettySpan(YEAR_MS - into)
			}
			out.add(hypercharged(Source("Atmospheric Filter", ATMOSPHERIC_FILTER * (1.0 + 0.02 * mite),
				if (spring) Status.ACTIVE else Status.INACTIVE, false, state, "Active during Spring on the Garden.",
				"ATMOSPHERIC_FILTER", java.util.List.copyOf(parts)), hyper, buffs.hypercharge))
		}

		val ball = est.magic8Ball
		if (ball != null) {
			val rolled = PlayerStatsCalculator.loreStat(ball.lore, "farming_fortune")
			if (rolled > 0.0) {
				out.add(Source("Magic 8 Ball", rolled, Status.ACTIVE, false, "Rolled this season",
					"Rolls a new bonus every season.", "MAGIC_8_BALL", listOf()))
			} else {
				val status = if (rolledOtherStat(ball.lore)) Status.INACTIVE else Status.UNKNOWN
				out.add(hypercharged(Source("Magic 8 Ball", MAGIC_8_BALL, status, false,
					if (status == Status.INACTIVE) "Rolled another bonus" else "Roll not in the API",
					"Rolls a new bonus every season.", "MAGIC_8_BALL", listOf()), hyper, buffs.hypercharge))
			}
		}

		val tool = if (crop == null) null else est.tool(crop, garden)
		if (tool != null && (NbtAttrs.intMap(tool.stack.extraAttributes, "enchantments")["ultimate_crop_fever"] ?: 0) > 0) {
			out.add(hypercharged(Source("Crop Fever", CROP_FEVER, Status.UNKNOWN, false, "Random",
				"Random 60s bonus while farming.", "ULTIMATE_CROP_FEVER;1", listOf()), hyper, buffs.hypercharge))
		}

		val earthworm = input.shards["infiltration"] ?: 0
		if (earthworm > 0) {
			out.add(Source("Earthworm shard", EARTHWORM_PER_LEVEL * earthworm, Status.UNKNOWN, false, "Level $earthworm",
				"Active on plots with a pest.", "ATTRIBUTE_SHARD_INFILTRATION;1", listOf()))
		}
		val cricket = input.shards["pest_fortune"] ?: 0
		if (cricket > 0) {
			out.add(Source("Cricket shard", CRICKET_PER_LEVEL * cricket, Status.UNKNOWN, false, "Level $cricket",
				"Only on pests.", "ATTRIBUTE_SHARD_PEST_FORTUNE;1", listOf()))
		}
		val solar = input.shards["solar_power"] ?: 0
		val lunar = input.shards["lunar_power"] ?: 0
		if (lunar > solar) {
			out.add(Source("Lunar Moth shard", 5.0 * (lunar - solar), Status.UNKNOWN, false, "Level $lunar",
				"Extra at night, above the Firefly level.", "ATTRIBUTE_SHARD_LUNAR_POWER;1", listOf()))
		} else if (solar > lunar) {
			out.add(Source("Firefly shard", 5.0 * (solar - lunar), Status.UNKNOWN, false, "Level $solar",
				"Extra by day, above the Lunar Moth level.", "ATTRIBUTE_SHARD_SOLAR_POWER;1", listOf()))
		}

		val pet = est.bestPet(crop, garden)
		if (pet != null) {
			for (line in pet.lines) {
				if (line.kind == Kind.CONDITIONAL) {
					out.add(Source(line.label, line.value, Status.UNKNOWN, false, pet.pet.displayName,
						sentence(line.note), pet.pet.neuId, listOf()))
				}
			}
		}

		if (crop != null) {
			val anita = est.anita
			if (anita != null) {
				val id = anita.id.uppercase(Locale.ROOT)
				out.add(Source(ANITA_NAMES.getValue(id), ANITA_FORTUNE.getValue(id), Status.UNKNOWN, true, "Random crop",
					"One random crop each Jacob's Contest.", id, listOf()))
			}
			if (buffs.overdrive > 0) {
				out.add(Source("Overdrive Chip", overdriveFortune(buffs.overdrive), Status.UNKNOWN, true,
					"Level " + buffs.overdrive, "Contest crops during Jacob's Contests.", "OVERDRIVE_GARDEN_CHIP", listOf()))
			}
			if (crop == "Cocoa Beans" && est.truffle) {
				out.add(Source("Refined Dark Cacao Truffle", TRUFFLE, Status.UNKNOWN, true, "Not in the API",
					"Lasts 60 minutes after eating.", "REFINED_DARK_CACAO_TRUFFLE", listOf()))
			}
		}
		out.sortBy { it.status.ordinal }
		return out
	}

	private fun hypercharged(s: Source, bonus: Double, level: Int): Source {
		if (bonus <= 0.0) {
			return s
		}
		val parts = ArrayList(if (s.parts.isEmpty()) listOf(Line("Base", s.value, Kind.COUNTED, "")) else s.parts)
		parts.add(Line("Hypercharge Chip", s.value * bonus, Kind.COUNTED, "Level " + level + ", +" + Math.round(bonus * 100.0) + "%"))
		return Source(s.label, s.value * (1.0 + bonus), s.status, s.crop, s.state, s.condition, s.iconId, java.util.List.copyOf(parts))
	}

	private fun rolledOtherStat(lore: List<String>): Boolean =
		listOf("foraging_fortune", "mining_fortune", "fishing_speed", "strength").any { PlayerStatsCalculator.loreStat(lore, it) > 0.0 }

	private fun sentence(note: String?): String {
		if (note.isNullOrBlank()) {
			return "Only active some of the time."
		}
		val s = note[0].uppercaseChar() + note.substring(1)
		return if (s.endsWith(".")) s else "$s."
	}

	@JvmStatic
	fun activeTotal(sources: List<Source>): Double = sources.filter { it.status == Status.ACTIVE }.sumOf { it.value }

	@JvmStatic
	fun possibleTotal(sources: List<Source>): Double = sources.filter { it.status == Status.UNKNOWN }.sumOf { it.value }
}
