/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale

object GardenData {
	@Volatile
	private var root: JsonObject? = null

	@Volatile
	private var gardenXp = LongArray(0)

	@Volatile
	private var cropMilestones: Map<String, LongArray> = mapOf()

	@Volatile
	private var visitorMilestones = LongArray(0)

	@Volatile
	private var uniqueVisitorMilestones = LongArray(0)

	@Volatile
	private var visitorIds: List<String> = listOf()

	@Volatile
	private var visitorRarities: Map<String, String> = mapOf()

	@Volatile
	private var maxPlots = 24

	private val CAMEL_BOUNDARY = Regex("([a-z])([A-Z])")
	private val WORD_SEPARATORS = Regex("[\\s_]+")

	/** Garden caps at 15; past that, every 10,000 XP (the cost of levels 11-15) is one overflow level. */
	internal const val GARDEN_OVERFLOW_STEP = 10_000L

	@JvmField
	val CHIP_ORDER: List<String> = listOf(
		"sowledge", "rarefinder", "hypercharge", "quickdraw", "vermin_vaporize",
		"mechamind", "cropshot", "overdrive", "synthesis", "evergreen"
	)

	@JvmRecord
	data class LevelProgress(val level: Int, val intoLevel: Long, val needForNext: Long, val fill: Float, val maxed: Boolean)

	@JvmStatic
	fun ensureLoaded() {
		if (root != null) {
			return
		}
		synchronized(this) {
			if (root != null) {
				return
			}
			try {
				val stream = GardenData::class.java.classLoader.getResourceAsStream("assets/fishmod/pv/data/garden.json")
				if (stream == null) {
					ProfileViewer.LOGGER.warn("Missing garden.json")
					root = JsonObject()
					return
				}
				stream.use { load(JsonParser.parseReader(InputStreamReader(it, StandardCharsets.UTF_8)).asJsonObject) }
			} catch (exception: IOException) {
				ProfileViewer.LOGGER.warn("Failed to load garden.json", exception)
				root = JsonObject()
			} catch (exception: RuntimeException) {
				if (!SoftDataFailure.isSoft(exception)) {
					throw exception
				}
				ProfileViewer.LOGGER.warn("Failed to load garden.json", exception)
				root = JsonObject()
			}
		}
	}

	private fun load(parsed: JsonObject) {
		gardenXp = readLongArray(parsed.get("garden_xp_cumulative"))
		visitorMilestones = readLongArray(parsed.get("visitor_milestone_cumulative"))
		uniqueVisitorMilestones = readLongArray(parsed.get("unique_visitor_milestone_cumulative"))
		if (parsed.has("max_plots")) {
			maxPlots = parsed.get("max_plots").asInt
		}
		val crops = LinkedHashMap<String, LongArray>()
		if (parsed.has("crop_milestones") && parsed.get("crop_milestones").isJsonObject) {
			for ((key, value) in parsed.getAsJsonObject("crop_milestones").entrySet()) {
				crops[key.uppercase(Locale.ROOT)] = readLongArray(value)
			}
		}
		cropMilestones = Collections.unmodifiableMap(crops)

		val visitors = ArrayList<String>()
		val rarities = LinkedHashMap<String, String>()
		if (parsed.has("visitors") && parsed.get("visitors").isJsonObject) {
			for ((key, value) in parsed.getAsJsonObject("visitors").entrySet()) {
				val id = key.lowercase(Locale.ROOT)
				visitors.add(id)
				if (value != null && value.isJsonPrimitive) {
					val rarity: String? = value.asString
					if (!rarity.isNullOrBlank()) {
						rarities[id] = rarity.uppercase(Locale.ROOT)
					}
				}
			}
			visitors.sort()
		}
		visitorIds = java.util.List.copyOf(visitors)
		visitorRarities = java.util.Map.copyOf(rarities)
		// Publish last so readers never see root set with tables still empty.
		root = parsed
	}

	@JvmStatic
	fun maxPlots(): Int {
		ensureLoaded()
		return maxPlots
	}

	@JvmStatic
	fun allVisitorIds(): List<String> {
		ensureLoaded()
		return visitorIds
	}

	@JvmStatic
	fun visitorRarity(id: String?): String {
		ensureLoaded()
		if (id.isNullOrBlank()) {
			return ""
		}
		return visitorRarities[id.lowercase(Locale.ROOT)] ?: ""
	}

	@JvmStatic
	fun prettyVisitorRarity(rarity: String?): String {
		if (rarity.isNullOrBlank()) {
			return "?"
		}
		return when (rarity.uppercase(Locale.ROOT)) {
			"UNCOMMON" -> "Uncommon"
			"RARE" -> "Rare"
			"EPIC" -> "Epic"
			"LEGENDARY" -> "Legendary"
			"MYTHIC" -> "Mythic"
			"SPECIAL", "VERY_SPECIAL" -> "Special"
			"COMMON" -> "Common"
			else -> titleCase(rarity.replace('_', ' '))
		}
	}

	@JvmStatic
	fun visitorRarityOrder(): List<String> {
		return listOf("UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL", "COMMON")
	}

	@JvmStatic
	fun gardenLevel(xp: Double): LevelProgress {
		ensureLoaded()
		return progressFromCumulative(gardenXp, maxOf(0L, Math.round(xp)))
	}

	/** Overflow level progress past the Garden cap, or null below max. */
	@JvmStatic
	fun gardenOverflow(xp: Double): LevelProgress? = overflowFrom(gardenLevel(xp), GARDEN_OVERFLOW_STEP)

	@JvmStatic
	fun overflowFrom(base: LevelProgress?, step: Long): LevelProgress? {
		if (base == null || !base.maxed || step <= 0L) {
			return null
		}
		val extra = maxOf(0L, base.intoLevel)
		val levels = extra / step
		val into = extra % step
		val level = minOf(Int.MAX_VALUE.toLong(), base.level + levels).toInt()
		return LevelProgress(level, into, step, into / step.toFloat(), false)
	}

	@JvmStatic
	fun cropMilestone(cropId: String?, amount: Long): LevelProgress {
		ensureLoaded()
		var table = cropMilestones[normalizeCropKey(cropId)]
		if (table == null || table.isEmpty()) {
			table = cropMilestones["WHEAT"]
		}
		return progressFromCumulative(table ?: LongArray(0), maxOf(0L, amount))
	}

	@JvmStatic
	fun visitorMilestone(completed: Long): LevelProgress {
		ensureLoaded()
		return progressFromCumulative(visitorMilestones, maxOf(0L, completed))
	}

	@JvmStatic
	fun uniqueVisitorMilestone(uniqueServed: Long): LevelProgress {
		ensureLoaded()
		return progressFromCumulative(uniqueVisitorMilestones, maxOf(0L, uniqueServed))
	}

	@JvmStatic
	fun normalizeCropKey(id: String?): String {
		if (id.isNullOrBlank()) {
			return ""
		}
		return when (id.uppercase(Locale.ROOT)) {
			"CARROT_ITEM", "CARROT" -> "CARROT"
			"POTATO_ITEM", "POTATO" -> "POTATO"
			"INK_SACK:3", "COCOA", "COCOA_BEANS" -> "COCOA_BEANS"
			"MUSHROOM_COLLECTION", "MUSHROOM" -> "MUSHROOM"
			"NETHER_STALK", "NETHER_WART" -> "NETHER_WART"
			"DOUBLE_PLANT", "SUNFLOWER" -> "SUNFLOWER"
			"MELON", "MELON_SLICE" -> "MELON"
			"SEEDS", "WHEAT_SEEDS" -> "SEEDS"
			else -> id.uppercase(Locale.ROOT)
		}
	}

	@JvmStatic
	fun prettyCrop(id: String?): String {
		if (id.isNullOrBlank()) {
			return "?"
		}
		return when (normalizeCropKey(id)) {
			"WHEAT" -> "Wheat"
			"CARROT" -> "Carrot"
			"POTATO" -> "Potato"
			"PUMPKIN" -> "Pumpkin"
			"SUGAR_CANE" -> "Sugar Cane"
			"MELON" -> "Melon"
			"CACTUS" -> "Cactus"
			"COCOA_BEANS" -> "Cocoa Beans"
			"MUSHROOM" -> "Mushroom"
			"NETHER_WART" -> "Nether Wart"
			"SUNFLOWER" -> "Sunflower"
			"MOONFLOWER" -> "Moonflower"
			"WILD_ROSE" -> "Wild Rose"
			"SEEDS", "WHEAT_SEEDS" -> "Seeds"
			else -> titleCase(id)
		}
	}

	@JvmStatic
	fun cropIconId(id: String?): String = when (val key = normalizeCropKey(id)) {
		"CARROT" -> "CARROT_ITEM"
		"POTATO" -> "POTATO_ITEM"
		"COCOA_BEANS" -> "INK_SACK-3"
		"MUSHROOM" -> "RED_MUSHROOM"
		"NETHER_WART" -> "NETHER_STALK"
		"SUNFLOWER" -> "DOUBLE_PLANT"
		"MELON" -> "MELON"
		"SEEDS", "WHEAT_SEEDS" -> "SEEDS"
		"MOONFLOWER" -> "MOONFLOWER"
		"WILD_ROSE" -> "WILD_ROSE"
		else -> key
	}

	@JvmStatic
	fun cropPackModel(id: String?): String = when (val key = normalizeCropKey(id).lowercase(Locale.ROOT)) {
		"moonflower", "sunflower", "wild_rose" -> "hypixel_skyblock:item/island_relevant/garden/greenhouse/$key"
		else -> ""
	}

	@JvmStatic
	fun eliteCropId(crop: Int): String = when (crop) {
		0 -> "CACTUS"
		1 -> "CARROT_ITEM"
		2 -> "COCOA_BEANS"
		3 -> "MELON"
		4 -> "MUSHROOM"
		5 -> "NETHER_WART"
		6 -> "POTATO_ITEM"
		7 -> "PUMPKIN"
		8 -> "SUGAR_CANE"
		9 -> "WHEAT"
		10 -> "SEEDS"
		11 -> "SUNFLOWER"
		12 -> "MOONFLOWER"
		13 -> "WILD_ROSE"
		else -> ""
	}

	@JvmStatic
	fun eliteCropId(raw: String?): String {
		if (raw.isNullOrBlank()) {
			return ""
		}
		val trimmed = raw.trim()
		trimmed.toIntOrNull()?.let { return eliteCropId(it) }
		val key = trimmed.uppercase(Locale.ROOT).replace(' ', '_')
		return when (key) {
			"CACTUS" -> "CACTUS"
			"CARROT", "CARROT_ITEM" -> "CARROT_ITEM"
			"COCOABEANS", "COCOA_BEANS", "INK_SACK:3", "INK_SACK-3" -> "COCOA_BEANS"
			"MELON" -> "MELON"
			"MUSHROOM", "MUSHROOM_COLLECTION" -> "MUSHROOM"
			"NETHERWART", "NETHER_WART", "NETHER_STALK" -> "NETHER_WART"
			"POTATO", "POTATO_ITEM" -> "POTATO_ITEM"
			"PUMPKIN" -> "PUMPKIN"
			"SUGARCANE", "SUGAR_CANE" -> "SUGAR_CANE"
			"WHEAT" -> "WHEAT"
			"SEEDS", "WHEAT_SEEDS" -> "SEEDS"
			"SUNFLOWER", "DOUBLE_PLANT" -> "SUNFLOWER"
			"MOONFLOWER" -> "MOONFLOWER"
			"WILDROSE", "WILD_ROSE" -> "WILD_ROSE"
			else -> {
				val camel = CAMEL_BOUNDARY.replace(trimmed, "\$1_\$2").uppercase(Locale.ROOT)
				if (camel != key) eliteCropId(camel) else key
			}
		}
	}

	@JvmStatic
	fun eliteMedalName(medal: Int): String = when (medal) {
		-1 -> "unclaimable"
		0 -> "none"
		1 -> "bronze"
		2 -> "silver"
		3 -> "gold"
		4 -> "platinum"
		5 -> "diamond"
		else -> "none"
	}

	@JvmStatic
	fun normalizeChipKey(id: String?): String {
		if (id.isNullOrBlank()) {
			return ""
		}
		var key = id.lowercase(Locale.ROOT).replace("garden_chip", "").replace("__", "_")
		if (key.endsWith("_")) {
			key = key.substring(0, key.length - 1)
		}
		return when (key) {
			"vermin_vaporizer", "verminvaporizer" -> "vermin_vaporize"
			else -> key
		}
	}

	@JvmStatic
	fun chipIconId(id: String?): String = when (val key = normalizeChipKey(id)) {
		"vermin_vaporize" -> "VERMIN_VAPORIZER_GARDEN_CHIP"
		else -> if (key.isBlank()) "" else key.uppercase(Locale.ROOT) + "_GARDEN_CHIP"
	}

	@JvmStatic
	fun prettyChip(id: String?): String = when (val key = normalizeChipKey(id)) {
		"sowledge" -> "Sowledge"
		"rarefinder" -> "Rarefinder"
		"hypercharge" -> "Hypercharge"
		"quickdraw" -> "Quickdraw"
		"vermin_vaporize" -> "Vermin Vaporizer"
		"mechamind" -> "Mechamind"
		"cropshot" -> "Cropshot"
		"overdrive" -> "Overdrive"
		"synthesis" -> "Synthesis"
		"evergreen" -> "Evergreen"
		else -> titleCase(key.replace('_', ' '))
	}

	@JvmStatic
	fun prettyComposterUpgrade(id: String?): String {
		if (id.isNullOrBlank()) {
			return "?"
		}
		return when (id.lowercase(Locale.ROOT)) {
			"speed" -> "Composter Speed"
			"multi_drop" -> "Multi Drop"
			"fuel_cap" -> "Fuel Cap"
			"organic_matter_cap" -> "Organic Matter Cap"
			"cost_reduction" -> "Cost Reduction"
			else -> titleCase(id.replace('_', ' '))
		}
	}

	@JvmStatic
	fun prettyGreenhouse(id: String?): String {
		if (id.isNullOrBlank()) {
			return "?"
		}
		var pretty = prettyCrop(id)
		if (pretty != "?" && pretty != titleCase(id)) {
			return pretty
		}
		pretty = prettyCrop(normalizeCropKey(id))
		if (pretty != "?") {
			return pretty
		}
		return titleCase(id.replace('_', ' '))
	}

	/** Prefer SkyBlock item id; keep mutations as-is (do not remap through crop tables). */
	@JvmStatic
	fun greenhouseIconId(id: String?): String {
		if (id.isNullOrBlank()) {
			return ""
		}
		return when (val upper = id.uppercase(Locale.ROOT)) {
			"TIMESTALK", "TIMESTALK_CLONE" -> "TIMESTALK"
			"ROSEWATER", "ROSEWATER_FLASK_EMPTY" -> "ROSEWATER_FLASK"
			"SUNFLOWER", "DOUBLE_PLANT" -> "DOUBLE_PLANT"
			"MOONFLOWER", "WILD_ROSE", "WHEAT", "POTATO", "POTATO_ITEM",
			"CARROT", "CARROT_ITEM", "PUMPKIN", "MELON", "CACTUS", "SUGAR_CANE",
			"NETHER_WART", "NETHER_STALK", "COCOA_BEANS", "INK_SACK:3", "INK_SACK-3", "MUSHROOM" -> cropIconId(upper)
			else -> upper
		}
	}

	@JvmStatic
	fun greenhousePackModel(id: String?): String {
		if (id.isNullOrBlank()) {
			return ""
		}
		return when (val key = id.lowercase(Locale.ROOT)) {
			"dead_plant", "ethereal_vine", "greenhouse_blueprint",
			"overclocker_3000", "plant_diagnostics_tool" ->
				"hypixel_skyblock:item/island_relevant/garden/greenhouse/$key"
			"moonflower", "sunflower", "wild_rose" ->
				"hypixel_skyblock:item/island_relevant/garden/greenhouse/$key"
			else -> ""
		}
	}

	@JvmStatic
	fun prettyVisitor(id: String?): String {
		if (id.isNullOrBlank()) {
			return "?"
		}
		return when (id.lowercase(Locale.ROOT)) {
			"an" -> "An"
			"tia" -> "Tia the Fairy"
			"fire_guy" -> "Fire Guy"
			"madame_eleanor" -> "Madame Eleanor"
			else -> titleCase(id.replace('_', ' '))
		}
	}

	@JvmStatic
	fun visitorNpcId(id: String?): String {
		if (id.isNullOrBlank()) {
			return ""
		}
		return when (val key = id.lowercase(Locale.ROOT)) {
			"jerry", "st_jerry" -> "ST_JERRY_NPC"
			"lumberjack" -> "LUMBER_JACK_NPC"
			"tia" -> "TIA_THE_FAIRY_NPC"
			"madame_eleanor" -> "MADAME_ELEANOR_Q_GOLDSWORTH_III_NPC"
			"seraphine" -> "CLERK_SERAPHINE_NPC"
			"fire_guy", "dirt_guy" -> "DIRT_GUY_NPC"
			"scardius" -> "SCOUT_SCARDIUS_NPC"
			"tyashoi" -> "TYASHOI_ALCHEMIST_NPC"
			"pearl" -> "PEARL_DEALER_NPC"
			"wilson", "emissary_wilson" -> "EMISSARY_WILSON_NPC"
			"royal_resident_neighbour", "royal_resident_neighbor" -> "ROYAL_RESIDENT_NEIGHBOR_NPC"
			"royal_resident_peasant" -> "ROYAL_RESIDENT_NPC"
			"royal_resident_reward" -> "ROYAL_RESIDENT_SNOOTY_NPC"
			"shaggy" -> "SHAGGY_NPC"
			"jacob" -> "JACOB_NPC"
			"jacobus" -> "JACOBUS_NPC"
			"anita" -> "ANITA_NPC"
			"beth" -> "BETH_NPC"
			"carpenter" -> "CARPENTER_NPC"
			"farmhand" -> "FARMHAND_NPC"
			"farm_merchant" -> "FARM_MERCHANT_NPC"
			"farmer_jon" -> "FARMER_JON_NPC"
			"jamie" -> "JAMIE_NPC"
			"liam" -> "LIAM_NPC"
			"rhys" -> "RHYS_NPC"
			"rusty" -> "RUSTY_NPC"
			"tom" -> "TOM_NPC"
			"terry" -> "TERRY_NPC"
			"fragilis" -> "FRAGILIS_NPC"
			"plumber_joe" -> "PLUMBER_JOE_NPC"
			"dulin_tunnels", "dulin" -> "DULIN_NPC"
			"vargul_garden", "vargul" -> "VARGUL_NPC"
			"grandma_wolf" -> "GRANDMA_WOLF_NPC"
			"lift_operator" -> "LIFT_OPERATOR_NPC"
			"mayor_aatrox" -> "AATROX_MAYOR_MONSTER"
			"mayor_cole" -> "COLE_MAYOR_MONSTER"
			"mayor_diana" -> "DIANA_MAYOR_MONSTER"
			"mayor_diaz" -> "DIAZ_MAYOR_MONSTER"
			"mayor_finnegan" -> "FINNEGAN_MAYOR_MONSTER"
			"mayor_foxy" -> "FOXY_MAYOR_MONSTER"
			"mayor_marina" -> "MARINA_MAYOR_MONSTER"
			"mayor_paul" -> "PAUL_MAYOR_MONSTER"
			"dante_goon" -> "DANTE_SPECIAL_MAYOR_MONSTER"
			else -> key.uppercase(Locale.ROOT) + "_NPC"
		}
	}

	@JvmStatic
	fun composterUpgradeIconId(id: String?): String {
		if (id == null) {
			return "COMPOST"
		}
		return when (id.lowercase(Locale.ROOT)) {
			"speed" -> "CLOCK"
			"multi_drop" -> "GOLDEN_HOE"
			"fuel_cap" -> "COAL_BLOCK"
			"organic_matter_cap" -> "COMPOST"
			"cost_reduction" -> "GOLD_INGOT"
			else -> "COMPOST"
		}
	}

	@JvmStatic
	fun composterUpgradePackModel(id: String?): String {
		if (id == null) {
			return ""
		}
		return when (id.lowercase(Locale.ROOT)) {
			"organic_matter_cap", "speed" -> "hypixel_skyblock:item/island_relevant/garden/compost"
			else -> ""
		}
	}

	private fun progressFromCumulative(cumulative: LongArray?, total: Long): LevelProgress {
		if (cumulative == null || cumulative.size <= 1) {
			return LevelProgress(0, total, 0L, 1f, true)
		}
		var level = 0
		for (i in 1 until cumulative.size) {
			if (total >= cumulative[i]) {
				level = i
			} else {
				break
			}
		}
		if (level >= cumulative.size - 1) {
			val last = cumulative[cumulative.size - 1]
			return LevelProgress(level, maxOf(0L, total - last), 0L, 1f, true)
		}
		val floor = cumulative[level]
		val next = cumulative[level + 1]
		val need = maxOf(1L, next - floor)
		val into = maxOf(0L, total - floor)
		return LevelProgress(level, into, need, minOf(1f, into / need.toFloat()), false)
	}

	private fun readLongArray(element: JsonElement?): LongArray {
		if (element == null || !element.isJsonArray) {
			return LongArray(0)
		}
		val array = element.asJsonArray
		return LongArray(array.size()) { i ->
			try {
				array.get(i).asLong
			} catch (ignored: IllegalStateException) {
				0L
			} catch (ignored: ClassCastException) {
				0L
			} catch (ignored: NumberFormatException) {
				0L
			} catch (ignored: UnsupportedOperationException) {
				0L
			}
		}
	}

	private fun titleCase(raw: String): String {
		val out = StringBuilder()
		for (part in raw.lowercase(Locale.ROOT).replace('-', ' ').split(WORD_SEPARATORS)) {
			if (part.isEmpty()) {
				continue
			}
			if (out.isNotEmpty()) {
				out.append(' ')
			}
			out.append(part[0].uppercaseChar())
			if (part.length > 1) {
				out.append(part.substring(1))
			}
		}
		return out.toString()
	}

	@JvmStatic
	fun knownCropKeys(): MutableSet<String> {
		ensureLoaded()
		return LinkedHashSet(cropMilestones.keys)
	}
}
