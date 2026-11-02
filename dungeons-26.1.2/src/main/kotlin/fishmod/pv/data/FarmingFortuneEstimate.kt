/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.NbtAttrs
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.floor

/**
 * Farming Fortune for the best farming setup found on the profile. The API only has what was last saved, so this
 * is an estimate built from item lore plus the permanent bonuses below (checked against EliteFarmers'
 * FarmingWeight constants). Sources the API doesn't expose are listed as unavailable instead of guessed.
 */
class FarmingFortuneEstimate private constructor(
	internal val inputs: Inputs,
	armour: List<Piece>,
	setBonuses: List<SetBonus>,
	equipment: List<Piece>,
	accessories: List<Accessory>,
	@get:JvmName("fermento") val fermento: InventoryDecoder.Stack?,
	internal val magic8Ball: InventoryDecoder.Stack?,
	internal val atmosphericFilter: Boolean,
	internal val anita: InventoryDecoder.Stack?,
	internal val truffle: Boolean,
	tools: List<ToolCandidate>
) {
	/** Only counted lines add to the total; conditional ones are active some of the time and listed separately. */
	enum class Kind { COUNTED, CONDITIONAL, UNAVAILABLE }

	@JvmRecord
	data class Line(val label: String, val value: Double, val kind: Kind, val note: String) {
		companion object {
			@JvmStatic
			fun counted(label: String, value: Double, note: String): Line = Line(label, value, Kind.COUNTED, note)

			@JvmStatic
			fun conditional(label: String, value: Double, note: String): Line = Line(label, value, Kind.CONDITIONAL, note)

			@JvmStatic
			fun unavailable(label: String, note: String): Line = Line(label, 0.0, Kind.UNAVAILABLE, note)
		}
	}

	@JvmRecord
	data class Piece(
		val slot: String, val stack: InventoryDecoder.Stack, val fortune: Double, val pieceBonus: Double,
		val cropFortune: Map<String, Double>
	) {
		fun total(): Double = fortune + pieceBonus
	}

	@JvmRecord
	data class SetBonus(val family: String, val name: String, val pieces: Int, val fortune: Double)

	@JvmRecord
	data class Accessory(val stack: InventoryDecoder.Stack, val fortune: Double)

	/** A tool valued for one crop; {@code parts} is the attribute breakdown. */
	@JvmRecord
	data class Tool(val stack: InventoryDecoder.Stack, val fromToolkit: Boolean, val rarity: String, val parts: List<Line>) {
		fun total(): Double = sum(parts)
	}

	internal data class ToolCandidate(val stack: InventoryDecoder.Stack, val fromToolkit: Boolean, val info: FarmingToolFortune.Info)

	/** A pet valued for one crop: ability and stat lines, plus the held item when it gives fortune. */
	@JvmRecord
	data class PetPick(val pet: PetSnapshot.Entry, val lines: List<Line>, val item: Line?) {
		fun total(): Double = sum(lines) + (if (item == null) 0.0 else sum(listOf(item)))
	}

	/** Island data loads separately from the profile, so plots, garden level and crop upgrades may be missing. */
	@JvmRecord
	data class Garden(
		val loaded: Boolean, val plots: Int, val level: Int, val upgrades: Map<String, Int>, val milestones: Map<String, Int>,
		val uniqueVisitors: Long
	) {
		companion object {
			@JvmField
			val NONE = Garden(false, 0, 0, mapOf(), mapOf(), 0L)

			@JvmStatic
			fun of(snap: GardenSnapshot?): Garden {
				if (snap == null || !snap.islandLoaded) {
					return NONE
				}
				val upgrades = HashMap<String, Int>()
				val milestones = HashMap<String, Int>()
				for (row in snap.crops) {
					upgrades[row.name] = row.upgradeLevel
					milestones[row.name] = row.milestone
				}
				return Garden(true, snap.plotsUnlocked(), snap.gardenLevel, upgrades, milestones, snap.uniqueVisitors)
			}
		}
	}

	/**
	 * {@code toolkit} holds tools still stored in the toolkit; slots marked in use are skipped because the tool is in
	 * the inventory. {@code toolkitUnreadable} lists crops whose slot had data that didn't decode.
	 */
	internal data class Inputs(
		val inventoryKnown: Boolean,
		val toolkitKnown: Boolean,
		val toolkitUnlocked: Boolean,
		val toolkit: List<InventoryDecoder.Stack>,
		val toolkitUnreadable: Set<String>,
		val axedPerk: Boolean,
		val farmingLevel: Int,
		val anitaLevel: Int,
		val pestTiers: Int,
		val gardenTiers: Int,
		val dnaMilestone: Int,
		val cropshotLevel: Int,
		val shards: Map<String, Int>,
		val personalBestsUnlocked: Boolean,
		val personalBests: Map<String, Long>,
		val pets: List<PetSnapshot.Entry>,
		val buffs: FarmingFortuneTemporary.Buffs = FarmingFortuneTemporary.Buffs.NONE
	)

	@get:JvmName("armour") val armour: List<Piece> = java.util.List.copyOf(armour)
	@get:JvmName("setBonuses") val setBonuses: List<SetBonus> = java.util.List.copyOf(setBonuses)
	@get:JvmName("equipment") val equipment: List<Piece> = java.util.List.copyOf(equipment)
	@get:JvmName("accessories") val accessories: List<Accessory> = java.util.List.copyOf(accessories)
	private val tools: List<ToolCandidate> = java.util.List.copyOf(tools)

	/**
	 * Pet abilities and stats for {@code crop} (null for fortune that applies to every crop), without the held item.
	 * Empty when the pet gives no Farming Fortune. Values come from NEU petnums and the current pet lore.
	 */
	internal fun petLines(pet: PetSnapshot.Entry, crop: String?, garden: Garden): List<Line> {
		val type = pet.type.uppercase(Locale.ROOT)
		val rarity = RARITIES.indexOf(pet.tier.uppercase(Locale.ROOT))
		val level = pet.level
		val out = ArrayList<Line>()
		when (type) {
			"ROSE_DRAGON" -> {
				val n = level - 100
				out.add(Line.counted("Farming Fortune stat", petScale(20.4, 40.0, n), ""))
				val farming = minOf(FARMING_LEVEL_CAP, inputs.farmingLevel)
				out.add(Line.counted("Garden Power", petScale(1.52, 3.0, n) * farming, "Farming $farming"))
				if (garden.loaded) {
					val milestones = garden.milestones.values.sum()
					out.add(Line.counted("Rosy Scales", petScale(0.0765, 0.15, n) * milestones, "$milestones crop milestones"))
				} else {
					out.add(Line.unavailable("Rosy Scales", "Needs crop milestones, garden not loaded"))
				}
				if (level >= 200) {
					val maxed = maxedFarmingPets()
					out.add(Line.counted("Symbiosis", 3.0 * maxed, "$maxed other maxed farming pets"))
				}
			}
			"HEDGEHOG" -> {
				out.add(Line.counted("Hunter's Insight", 0.7 * inputs.pestTiers, "${inputs.pestTiers} pest bestiary tiers"))
				out.add(Line.conditional("Fearsome Farmer", petScale(1.0, 100.0, level), "Only on pests"))
			}
			"ELEPHANT" -> {
				if (rarity >= 5) {
					out.add(Line.counted("Farming Fortune stat", petScale(0.5, 50.0, level), ""))
				}
				if (rarity >= 4) {
					out.add(Line.counted("Trunk Efficiency", petScale(1.5, 150.0, level), ""))
				}
			}
			"MOOSHROOM_COW" -> {
				out.add(Line.counted("Farming Fortune stat", petScale(1.0, 100.0, level), ""))
				if (rarity >= 2) {
					out.add(Line.unavailable("Farming Strength", "Needs live Strength"))
				}
			}
			"BEE" -> {
				val per = if (rarity >= 5) 0.4 else if (rarity >= 3) 0.3 else if (rarity == 2) 0.2 else 0.0
				if (per > 0.0) {
					out.add(Line.counted("Farming Fortune stat", per * minOf(100, level), ""))
				}
			}
			"CHICKEN" -> out.add(Line.counted("Farming Fortune stat", petScale(0.5, 50.0, level), ""))
			"PIG" -> {
				if (crop == "Potato") {
					out.add(Line.counted("Potato Fortune stat", petScale(0.2, 20.0, level), "Potato only"))
					if (rarity >= 2) {
						out.add(Line.unavailable("Shining Stampede", "Needs Shiny Pig bestiary"))
					}
				}
			}
			"MOSQUITO" -> {
				if (crop == "Sugar Cane" && rarity >= 2) {
					val per = if (rarity == 2) petScale(0.01, 1.0, level) else petScale(0.02, 2.0, level)
					out.add(
						if (garden.loaded) {
							Line.counted("Buzzin' Barterer", minOf(175.0, per * garden.uniqueVisitors),
								"${garden.uniqueVisitors} unique visitors, max 175")
						} else {
							Line.unavailable("Buzzin' Barterer", "Needs unique visitors, garden not loaded")
						}
					)
				}
			}
		}
		return out
	}

	private fun maxedFarmingPets(): Int {
		val maxed = HashSet<String>()
		for (pet in inputs.pets) {
			val type = pet.type.uppercase(Locale.ROOT)
			if (type in SYMBIOSIS_PETS && pet.maxLevel > 0 && pet.level >= pet.maxLevel) {
				maxed.add(type)
			}
		}
		return maxed.size
	}

	fun inventoryKnown(): Boolean = inputs.inventoryKnown

	fun toolkitKnown(): Boolean = inputs.toolkitKnown

	fun toolkitTools(): Int = inputs.toolkit.size

	/** Best tool for {@code crop}; Dedication depends on crop milestones, so the pick can change once the garden loads. */
	fun tool(crop: String?, garden: Garden): Tool? {
		if (crop == null) {
			return null
		}
		var best: Tool? = null
		for (c in tools) {
			if (!c.info.crops.contains(crop)) {
				continue
			}
			val stack = c.stack
			val rarity = FarmingToolFortune.rarity(c.info, stack.extraAttributes, stack.lore)
			val parts = FarmingToolFortune.parts(
				c.info, stack.extraAttributes, rarity, crop, if (garden.loaded) garden.milestones else null, inputs.axedPerk
			)
			val tool = Tool(named(stack, rarity), c.fromToolkit, rarity, parts)
			if (best == null || tool.total() > best.total()) {
				best = tool
			}
		}
		return best
	}

	fun personalBest(crop: String?): Long = inputs.personalBests[crop] ?: 0L

	fun personalBestsUnlocked(): Boolean = inputs.personalBestsUnlocked

	/** Permanent profile bonuses that apply to every crop. */
	fun permanentLines(garden: Garden): List<Line> {
		val out = ArrayList<Line>()
		val level = minOf(FARMING_LEVEL_CAP, inputs.farmingLevel)
		out.add(Line.counted("Farming level", 4.0 * level, "Level $level"))
		val anita = minOf(ANITA_CAP, inputs.anitaLevel)
		out.add(Line.counted("Anita bonus", 4.0 * anita, "$anita / $ANITA_CAP"))
		out.add(
			if (garden.loaded) {
				Line.counted("Unlocked plots", 3.0 * minOf(PLOTS_MAX, garden.plots), "${garden.plots} / $PLOTS_MAX")
			} else {
				Line.unavailable("Unlocked plots", "Garden not loaded")
			}
		)
		out.add(Line.counted("Garden bestiary", 0.4 * inputs.gardenTiers, "${inputs.gardenTiers} / $GARDEN_TIERS_MAX tiers"))
		if (inputs.dnaMilestone > 0) {
			out.add(Line.counted("DNA Analysis milestone", 5.0 * inputs.dnaMilestone, "${inputs.dnaMilestone} / $DNA_MILESTONE_MAX"))
		}
		if (inputs.cropshotLevel > 0) {
			out.add(Line.counted("Cropshot chip", cropshotFortune(inputs.cropshotLevel), "Level ${inputs.cropshotLevel}"))
		}
		val fly = inputs.shards["fortunate_farmer"] ?: 0
		if (fly > 0) {
			out.add(Line.counted("Fly shard", 2.5 * fly, "Level $fly"))
		}
		val galaxy = inputs.shards["ultimate_dna"] ?: 0
		if (galaxy > 0) {
			out.add(Line.counted("Galaxy Fish shard", galaxy.toDouble(), "Level $galaxy"))
		}
		// Firefly works by day and Lunar Moth by night, so the lower level is always active.
		val both = minOf(inputs.shards["solar_power"] ?: 0, inputs.shards["lunar_power"] ?: 0)
		if (both > 0) {
			out.add(Line.counted("Firefly and Lunar Moth shards", 5.0 * both, "Level $both, day and night"))
		}
		val acc = accessories.sumOf { it.fortune }
		if (acc > 0.0) {
			out.add(Line.counted("Accessories", acc, if (accessories.size == 1) "1 accessory" else "${accessories.size} accessories"))
		}
		if (fermento != null && fermento.id.equals("HELIANTHUS_RELIC", ignoreCase = true)) {
			out.add(Line.counted("Helianthus Relic", FERMENTO_FORTUNE.getValue("HELIANTHUS_RELIC"), "All crops"))
		}
		return out
	}

	/** Temporary and situational bonuses for {@code crop} at {@code nowMs}. Never part of a total. */
	fun temporary(crop: String?, garden: Garden, nowMs: Long): List<FarmingFortuneTemporary.Source> =
		FarmingFortuneTemporary.sources(this, crop, garden, nowMs)

	/** Owned farming pets valued for {@code crop} (null for every crop), best first, one per pet type. */
	fun pets(crop: String?, garden: Garden): List<PetPick> {
		val bestByType = LinkedHashMap<String, PetPick>()
		for (pet in inputs.pets) {
			val lines = petLines(pet, crop, garden)
			if (lines.isEmpty()) {
				continue
			}
			val pick = PetPick(pet, lines, petItem(pet.heldItem, garden))
			val held = bestByType[pet.type]
			if (held == null || pick.total() > held.total()) {
				bestByType[pet.type] = pick
			}
		}
		return bestByType.values.sortedByDescending { it.total() }
	}

	fun bestPet(crop: String?, garden: Garden): PetPick? = pets(crop, garden).firstOrNull()

	fun permanentTotal(garden: Garden): Double = sum(permanentLines(garden))

	fun armourTotal(): Double = armour.sumOf { it.total() } + setBonuses.sumOf { it.fortune }

	fun equipmentTotal(): Double = equipment.sumOf { it.total() }

	fun petTotal(crop: String?, garden: Garden): Double = bestPet(crop, garden)?.total() ?: 0.0

	/** Fortune that applies to every crop: permanent bonuses, best armour, equipment and best all-crop pet. */
	fun general(garden: Garden): Double = permanentTotal(garden) + armourTotal() + equipmentTotal() + petTotal(null, garden)

	/** Fortune that only applies while farming {@code crop}. */
	fun cropLines(crop: String?, garden: Garden): List<Line> {
		val out = ArrayList<Line>()
		val tool = tool(crop, garden)
		out.add(
			when {
				tool != null -> Line.counted("Best tool", tool.total(), if (tool.fromToolkit) "Farming Toolkit" else "Inventory")
				!inputs.inventoryKnown -> Line.unavailable("Best tool", "Inventory API off")
				inputs.toolkitUnreadable.contains(crop) -> Line.unavailable("Best tool", "Toolkit slot unreadable")
				!inputs.toolkitKnown -> Line.unavailable("Best tool", "Toolkit not in API")
				!inputs.toolkitUnlocked -> Line.counted("Best tool", 0.0, "None in inventories, toolkit locked")
				else -> Line.counted("Best tool", 0.0, "None in inventories or toolkit")
			}
		)
		val upgrade = garden.upgrades[crop] ?: 0
		out.add(
			if (garden.loaded) {
				Line.counted("Crop upgrade", CROP_UPGRADE_FORTUNE * upgrade, "Level $upgrade / 9")
			} else {
				Line.unavailable("Crop upgrade", "Garden not loaded")
			}
		)
		val pb = personalBest(crop)
		out.add(
			if (inputs.personalBestsUnlocked) {
				Line.counted("Personal best", personalBestFortune(crop, pb), FormatUtil.commas(pb) + " crops")
			} else {
				Line.counted("Personal best", 0.0, "Perk not unlocked")
			}
		)
		if (fermento != null) {
			val id = fermento.id.uppercase(Locale.ROOT)
			val crops = FERMENTO_CROPS[id]
			if (crops != null && crops.contains(crop)) {
				out.add(Line.counted(FERMENTO_NAMES.getValue(id), FERMENTO_FORTUNE.getValue(id), "Accessory"))
			}
		}
		val gear = armour.sumOf { it.cropFortune[crop] ?: 0.0 } + equipment.sumOf { it.cropFortune[crop] ?: 0.0 }
		if (gear > 0.0) {
			out.add(Line.counted("Gear crop fortune", gear, "Armour and equipment"))
		}
		return out
	}

	fun cropBonus(crop: String?, garden: Garden): Double = sum(cropLines(crop, garden))

	/** The pet is picked for this crop, so it can differ from the one in {@link #general}. */
	fun forCrop(crop: String?, garden: Garden): Double =
		permanentTotal(garden) + armourTotal() + equipmentTotal() + petTotal(crop, garden) + cropBonus(crop, garden)

	companion object {
		@JvmField
		val CROPS: List<String> = listOf(
			"Wheat", "Carrot", "Potato", "Pumpkin", "Sugar Cane", "Melon", "Cactus",
			"Cocoa Beans", "Mushroom", "Nether Wart", "Sunflower", "Moonflower", "Wild Rose"
		)

		@JvmField
		val ARMOUR_SLOTS: List<String> = listOf("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")

		@JvmField
		val EQUIPMENT_SLOTS: List<String> = listOf("NECKLACE", "CLOAK", "BELT", "GLOVES")
		const val CROP_UPGRADE_FORTUNE = 5.0
		internal const val FARMING_LEVEL_CAP = 60
		internal const val ANITA_CAP = 15
		internal const val PLOTS_MAX = 24
		internal const val GARDEN_TIERS_MAX = 250
		internal const val DNA_MILESTONE_MAX = 6
		internal val SHARD_IDS: List<String> = listOf(
			"fortunate_farmer", "ultimate_dna", "solar_power", "lunar_power", "infiltration", "filter_upgrade",
			"pest_fortune"
		)
		private val SOURCES = arrayOf(
			"armor", "equipment", "inventory", "enderchest", "storage", "wardrobe", "personal_vault"
		)
		private val CROP_LINE: Pattern = Pattern.compile("^([A-Za-z ]+?) Fortune: \\+([0-9]+(?:\\.[0-9]+)?)")
		private val PIECE_BONUS: Pattern = Pattern.compile("^Piece Bonus: \\+([0-9]+(?:\\.[0-9]+)?)")
		private val PEST_IDS = listOf(
			"beetle", "cricket", "worm", "fly", "locust", "mite", "mosquito", "moth", "rat", "slug", "mouse",
			"dragonfly", "firefly", "praying_mantis", "lunar_moth"
		)
		// NEU bestiary bracket 7; every other pest uses bracket 6.
		private val SMALL_BRACKET_PESTS = setOf("mouse", "lunar_moth")
		private val PEST_BRACKETS = intArrayOf(1, 2, 3, 5, 7, 9, 14, 17, 21, 25, 50, 80, 125, 175, 250)
		private val MOUSE_BRACKETS = intArrayOf(1, 2, 3, 5, 7, 9, 11, 14, 17, 20, 30, 40, 55, 75, 100)
		// Non-pest garden bestiary mobs: bracket 7, Timestalk Clone capped at 20 kills (10 tiers).
		private val TIMESTALK_BRACKETS = intArrayOf(1, 2, 3, 5, 7, 9, 11, 14, 17, 20)
		private const val DNA_MILESTONE_PREFIX = "dna_analysis_rewardfarming_fortune_"
		private val PB_CROPS_PER_TIER: Map<String, Int> = mapOf(
			"Wheat" to 100, "Carrot" to 300, "Potato" to 300, "Pumpkin" to 100,
			"Melon" to 500, "Mushroom" to 100, "Cactus" to 200, "Sugar Cane" to 200,
			"Nether Wart" to 300, "Cocoa Beans" to 300, "Sunflower" to 200,
			"Moonflower" to 200, "Wild Rose" to 200
		)
		// Highest owned tier wins; Helianthus Relic applies to every crop.
		private val FERMENTO_FAMILY = listOf("HELIANTHUS_RELIC", "FERMENTO_ARTIFACT", "SQUASH_RING", "CROPIE_TALISMAN")
		private val FERMENTO_FORTUNE: Map<String, Double> = mapOf(
			"HELIANTHUS_RELIC" to 40.0, "FERMENTO_ARTIFACT" to 30.0, "SQUASH_RING" to 20.0, "CROPIE_TALISMAN" to 10.0
		)
		private val FERMENTO_NAMES: Map<String, String> = mapOf(
			"FERMENTO_ARTIFACT" to "Fermento Artifact", "SQUASH_RING" to "Squash Ring", "CROPIE_TALISMAN" to "Cropie Talisman"
		)
		private val FERMENTO_CROPS: Map<String, List<String>> = mapOf(
			"FERMENTO_ARTIFACT" to listOf("Wheat", "Carrot", "Potato", "Pumpkin", "Melon", "Mushroom", "Cocoa Beans",
				"Nether Wart", "Cactus", "Sugar Cane"),
			"SQUASH_RING" to listOf("Wheat", "Carrot", "Potato", "Pumpkin", "Melon", "Mushroom", "Cocoa Beans"),
			"CROPIE_TALISMAN" to listOf("Wheat", "Carrot", "Potato")
		)
		// Tiered set bonus for 2, 3 and 4 pieces of one armour family.
		private val SET_BONUS: Map<String, IntArray> = mapOf(
			"MELON" to intArrayOf(10, 20, 30),
			"CROPIE" to intArrayOf(15, 30, 45),
			"SQUASH" to intArrayOf(20, 40, 60),
			"FERMENTO" to intArrayOf(25, 50, 75),
			"HELIANTHUS" to intArrayOf(25, 50, 75),
			"RABBIT" to intArrayOf(0, 0, 10)
		)
		private val SET_NAMES: Map<String, String> = mapOf(
			"MELON" to "Cropier Crops", "CROPIE" to "Squashbuckle", "SQUASH" to "Mento Fermento",
			"FERMENTO" to "Feast", "HELIANTHUS" to "Feast", "RABBIT" to "Bonus Farming Fortune"
		)
		private val RARITIES: List<String> = FarmingToolFortune.RARITIES
		private const val RARITY_CODES = "fa956d"
		// Farming pets other than Rose Dragon, for Symbiosis.
		private val SYMBIOSIS_PETS = setOf(
			"BEE", "CHICKEN", "ELEPHANT", "HEDGEHOG", "MOOSHROOM_COW", "MOSQUITO", "ORCHID_MANTIS", "PIG", "RABBIT", "SLUG"
		)

		@JvmStatic
		fun attach(
			stats: PlayerStatsSnapshot?, member: JsonObject?, categories: Map<String, List<InventoryDecoder.Stack?>>?
		): PlayerStatsSnapshot? {
			if (stats == null || member == null || categories == null) {
				return stats
			}
			return stats.withFarmingFortune(from(inputs(member), categories))
		}

		internal fun from(input: Inputs, all: Map<String, List<InventoryDecoder.Stack?>>): FarmingFortuneEstimate {
			val bestArmour = LinkedHashMap<String, Piece>()
			val bestEquipment = LinkedHashMap<String, Piece>()
			val bestTools = ArrayList<ToolCandidate>()
			val seenTools = HashSet<String>()
			var truffle = false
			for (source in SOURCES) {
				for (stack in all[source].orEmpty()) {
					consider(stack, false, bestArmour, bestEquipment, bestTools, seenTools)
					truffle = truffle or (stack != null && stack.id.equals("REFINED_DARK_CACAO_TRUFFLE", ignoreCase = true))
				}
			}
			for (stack in input.toolkit) {
				consider(stack, true, bestArmour, bestEquipment, bestTools, seenTools)
			}

			val armour = ordered(bestArmour, ARMOUR_SLOTS)
			val families = LinkedHashMap<String, Int>()
			for (p in armour) {
				family(p.stack.id)?.let { families.merge(it, 1) { a, b -> a + b } }
			}
			val sets = ArrayList<SetBonus>()
			for ((family, count) in families) {
				val ff = if (count >= 2) SET_BONUS.getValue(family)[minOf(count, 4) - 2].toDouble() else 0.0
				if (ff > 0.0) {
					sets.add(SetBonus(family, SET_NAMES.getValue(family), count, ff))
				}
			}

			val accessories = ArrayList<Accessory>()
			val seenAccessories = HashSet<String>()
			var fermento: InventoryDecoder.Stack? = null
			var magic8Ball: InventoryDecoder.Stack? = null
			var atmosphericFilter = false
			var anita: InventoryDecoder.Stack? = null
			val anitaFamily = FarmingFortuneTemporary.ANITA_FAMILY
			for (stack in all["accessories"].orEmpty()) {
				if (stack == null) {
					continue
				}
				val id = stack.id.uppercase(Locale.ROOT)
				val tier = FERMENTO_FAMILY.indexOf(id)
				if (tier >= 0) {
					if (fermento == null || tier < FERMENTO_FAMILY.indexOf(fermento.id.uppercase(Locale.ROOT))) {
						fermento = stack
					}
					continue
				}
				val anitaTier = anitaFamily.indexOf(id)
				if (anitaTier >= 0) {
					if (anita == null || anitaTier < anitaFamily.indexOf(anita.id.uppercase(Locale.ROOT))) {
						anita = stack
					}
					continue
				}
				if (id == "MAGIC_8_BALL") {
					magic8Ball = stack
					continue
				}
				if (id == "ATMOSPHERIC_FILTER") {
					atmosphericFilter = true
					continue
				}
				if (!seenAccessories.add(id)) {
					continue
				}
				val ff = fortune(stack)
				if (ff > 0.0) {
					accessories.add(Accessory(stack, ff))
				}
			}
			return FarmingFortuneEstimate(
				input, armour, sets, ordered(bestEquipment, EQUIPMENT_SLOTS), accessories, fermento, magic8Ball,
				atmosphericFilter, anita, truffle, bestTools
			)
		}

		private fun consider(
			stack: InventoryDecoder.Stack?, fromToolkit: Boolean, bestArmour: MutableMap<String, Piece>,
			bestEquipment: MutableMap<String, Piece>, tools: MutableList<ToolCandidate>, seenTools: MutableSet<String>
		) {
			if (stack == null) {
				return
			}
			val info = FarmingToolFortune.info(stack.id)
			if (info != null) {
				val uuid = NbtAttrs.string(stack.extraAttributes, "uuid")
				if (!uuid.isNullOrBlank() && !seenTools.add(uuid)) {
					return
				}
				tools.add(ToolCandidate(stack, fromToolkit, info))
				return
			}
			val slot = slot(stack.lore)
			if (slot in ARMOUR_SLOTS) {
				val family = family(stack.id)
				val ff = fortune(stack)
				if (ff <= 0.0 && family == null) {
					return
				}
				val piece = Piece(slot, stack, ff, 0.0, cropLines(stack))
				val held = bestArmour[slot]
				if (held == null || armourScore(piece) > armourScore(held)) {
					bestArmour[slot] = piece
				}
			} else if (slot in EQUIPMENT_SLOTS) {
				val piece = Piece(slot, stack, fortune(stack), pieceBonus(stack.lore), cropLines(stack))
				if (piece.total() <= 0.0) {
					return
				}
				val held = bestEquipment[slot]
				if (held == null || piece.total() > held.total()) {
					bestEquipment[slot] = piece
				}
			}
		}

		// A set piece is worth its stat line plus its share of the family's set bonus.
		private fun armourScore(piece: Piece): Double {
			val family = family(piece.stack.id)
			return piece.fortune + (if (family == null) 0.0 else SET_BONUS.getValue(family)[0].toDouble())
		}

		internal fun inputs(member: JsonObject): Inputs {
			val inventory = Leveling.obj(member.get("inventory")) ?: Leveling.obj(member.get("inventories"))
			val gpd = Leveling.obj(member.get("garden_player_data"))
			val toolkit = Leveling.obj(gpd?.get("farming_toolkit"))
			val jacobs = Leveling.obj(member.get("jacobs_contest"))
			val perks = jacobs?.let { Leveling.obj(it.get("perks")) }
			val bestiary = Leveling.obj(member.get("bestiary"))
			val playerData = Leveling.obj(member.get("player_data"))

			val farming = try {
				Leveling.skillLevel(member, "farming")
			} catch (e: RuntimeException) {
				0
			}
			val pbs = LinkedHashMap<String, Long>()
			jacobs?.let { Leveling.obj(it.get("personal_bests")) }?.let { pbObj ->
				for ((key, value) in pbObj.entrySet()) {
					val crop = GardenData.prettyCrop(key)
					if (crop in CROPS) {
						pbs.merge(crop, Leveling.longAmount(value)) { a, b -> maxOf(a, b) }
					}
				}
			}
			val pets = try {
				PetSnapshot.fromMember(member).pets
			} catch (e: RuntimeException) {
				listOf()
			}
			val toolkitStacks = ArrayList<InventoryDecoder.Stack>()
			val unreadable = HashSet<String>()
			for (item in InventoryDecoder.parseToolkit(toolkit)) {
				if (item.inUse) {
					continue
				}
				val stack = item.stack
				if (stack != null) {
					toolkitStacks.add(stack)
				} else {
					unreadable.add(GardenData.prettyCrop(item.category))
				}
			}
			val playerPerks = playerData?.let { Leveling.obj(it.get("perks")) }
			val kills = bestiary?.let { Leveling.obj(it.get("kills")) }
			return Inputs(
				inventory != null && inventory.has("inv_contents"),
				toolkit != null,
				boolOf(toolkit, "IS_UNLOCKED"),
				toolkitStacks,
				unreadable,
				intOf(playerPerks, "axed") > 0,
				farming,
				intOf(perks, "double_drops"),
				pestTiers(kills),
				gardenTiers(kills),
				dnaMilestone(member),
				chipLevel(playerData?.let { Leveling.obj(it.get("garden_chips")) }, "cropshot"),
				PlayerStatsCalculator.attributeLevels(member, SHARD_IDS),
				boolOf(perks, "personal_bests"),
				pbs,
				pets,
				FarmingFortuneTemporary.buffs(playerData)
			)
		}

		internal fun pestTiers(kills: JsonObject?): Int {
			if (kills == null) {
				return 0
			}
			return PEST_IDS.sumOf { pest ->
				tiers(kills, "pest_" + pest + "_1", if (pest in SMALL_BRACKET_PESTS) MOUSE_BRACKETS else PEST_BRACKETS)
			}
		}

		/** Every garden bestiary mob, which is what the +0.4 per tier counts. Hunter's Insight only counts pests. */
		internal fun gardenTiers(kills: JsonObject?): Int {
			if (kills == null) {
				return 0
			}
			return pestTiers(kills) + tiers(kills, "zombuddy_1", MOUSE_BRACKETS) +
				tiers(kills, "timestalk_clone_100", TIMESTALK_BRACKETS)
		}

		private fun tiers(kills: JsonObject, key: String, brackets: IntArray): Int {
			val count = Leveling.longAmount(kills.get(key))
			for (i in brackets.indices.reversed()) {
				if (count >= brackets[i]) {
					return i + 1
				}
			}
			return 0
		}

		// Each claimed DNA Analysis reward is an objectives.tutorial entry ending in its level.
		internal fun dnaMilestone(member: JsonObject): Int {
			val objectives = Leveling.obj(member.get("objectives"))
			val tutorial = objectives?.get("tutorial")
			if (tutorial == null || !tutorial.isJsonArray) {
				return 0
			}
			var level = 0
			for (el in tutorial.asJsonArray) {
				if (!el.isJsonPrimitive) {
					continue
				}
				val entry = el.asString
				if (entry.startsWith(DNA_MILESTONE_PREFIX)) {
					entry.substring(DNA_MILESTONE_PREFIX.length).toIntOrNull()?.let { level = maxOf(level, it) }
				}
			}
			return minOf(DNA_MILESTONE_MAX, level)
		}

		internal fun chipLevel(chips: JsonObject?, chip: String): Int {
			if (chips == null) {
				return 0
			}
			for ((key, value) in chips.entrySet()) {
				if (chip != GardenData.normalizeChipKey(key)) {
					continue
				}
				var n = Leveling.num(value)
				if (n == null && value != null && value.isJsonObject) {
					n = Leveling.num(value.asJsonObject.get("level"))
				}
				return if (n == null) 0 else Math.round(n).coerceIn(0, 20)
			}
			return 0
		}

		internal fun cropshotFortune(level: Int): Double {
			val per = if (level > 15) 5 else if (level > 10) 4 else 3
			return (per * level).toDouble()
		}

		internal fun personalBestFortune(crop: String?, amount: Long): Double {
			val per = PB_CROPS_PER_TIER[crop]
			if (per == null || amount <= 0L) {
				return 0.0
			}
			return minOf(100.0, floor(amount.toDouble() / per) / 100.0)
		}

		// NEU petnums values scale linearly from level 1 to max; Rose Dragon's levels 101 to 200 map onto 1 to 100.
		internal fun petScale(atOne: Double, atMax: Double, level: Int): Double {
			val n = level.coerceIn(1, 100)
			return atOne + (atMax - atOne) * (n - 1) / 99.0
		}

		// Toolkit tools have no display name, so build one with the reforge and rarity colour.
		private fun named(stack: InventoryDecoder.Stack, rarity: String): InventoryDecoder.Stack {
			if (!stack.displayName.isNullOrBlank()) {
				return stack
			}
			var name = KuudraGearChecks.itemName(stack.id)
			val reforge = NbtAttrs.string(stack.extraAttributes, "modifier")
			if (!reforge.isNullOrBlank()) {
				name = FarmingToolFortune.pretty(reforge) + " " + name
			}
			return InventoryDecoder.Stack(
				stack.id, stack.count, stack.extraAttributes, stack.lore, stack.soulbound,
				"§" + RARITY_CODES[maxOf(0, RARITIES.indexOf(rarity))] + name, null, null, null
			)
		}

		private fun petItem(heldItem: String?, garden: Garden): Line? {
			if (heldItem == null) {
				return null
			}
			return when (heldItem.uppercase(Locale.ROOT)) {
				"YELLOW_BANDANA" -> Line.counted("Yellow Bandana", 30.0, "")
				"GREEN_BANDANA" -> if (garden.loaded) {
					Line.counted("Green Bandana", 4.0 * garden.level, "Garden level ${garden.level}")
				} else {
					Line.unavailable("Green Bandana", "Garden not loaded")
				}
				else -> null
			}
		}

		private fun sum(lines: List<Line>): Double = lines.filter { it.kind == Kind.COUNTED }.sumOf { it.value }

		internal fun family(id: String?): String? {
			if (id == null) {
				return null
			}
			val upper = id.uppercase(Locale.ROOT)
			for (slot in ARMOUR_SLOTS) {
				if (upper.endsWith("_$slot")) {
					val family = upper.substring(0, upper.length - slot.length - 1)
					return if (SET_BONUS.containsKey(family)) family else null
				}
			}
			return null
		}

		internal fun slot(lore: List<String>): String {
			for (i in lore.indices.reversed()) {
				val line = PlayerStatsCalculator.stripFormatting(lore[i])
				if (line.isEmpty()) {
					continue
				}
				val padded = " " + line.uppercase(Locale.ROOT) + " "
				ARMOUR_SLOTS.firstOrNull { padded.contains(" $it ") }?.let { return it }
				listOf("NECKLACE", "CLOAK", "BELT").firstOrNull { padded.contains(" $it ") }?.let { return it }
				return if (padded.contains(" GLOVES ") || padded.contains(" BRACELET ")) "GLOVES" else ""
			}
			return ""
		}

		internal fun toolCrops(id: String?): List<String> = FarmingToolFortune.info(id)?.crops ?: listOf()

		private fun fortune(stack: InventoryDecoder.Stack?): Double =
			if (stack == null) 0.0 else PlayerStatsCalculator.loreStat(stack.lore, "farming_fortune")

		internal fun pieceBonus(lore: List<String>): Double {
			for (i in lore.indices.reversed()) {
				val m = PIECE_BONUS.matcher(PlayerStatsCalculator.stripFormatting(lore[i]))
				if (m.find()) {
					return m.group(1).toDouble()
				}
			}
			return 0.0
		}

		internal fun cropLines(stack: InventoryDecoder.Stack?): Map<String, Double> {
			val out = LinkedHashMap<String, Double>()
			if (stack == null) {
				return out
			}
			for (raw in stack.lore) {
				val m = CROP_LINE.matcher(PlayerStatsCalculator.stripFormatting(raw))
				if (!m.find()) {
					continue
				}
				val name = if (m.group(1) == "Melon Slice") "Melon" else m.group(1)
				if (name in CROPS) {
					out.merge(name, m.group(2).toDouble()) { a, b -> a + b }
				}
			}
			return out
		}

		private fun ordered(bySlot: Map<String, Piece>, slots: List<String>): List<Piece> = slots.mapNotNull { bySlot[it] }

		private fun intOf(obj: JsonObject?, key: String): Int {
			val n = obj?.let { Leveling.num(it.get(key)) } ?: return 0
			return maxOf(0, Math.round(n))
		}

		private fun boolOf(obj: JsonObject?, key: String): Boolean {
			val el = obj?.get(key)
			if (el == null || !el.isJsonPrimitive) {
				return false
			}
			if (el.asJsonPrimitive.isBoolean) {
				return el.asBoolean
			}
			val n = Leveling.num(el)
			return n != null && n != 0f
		}
	}
}
