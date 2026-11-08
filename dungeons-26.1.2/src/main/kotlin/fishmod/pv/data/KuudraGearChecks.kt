/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import fishmod.utils.TextStrip

import com.google.gson.JsonObject
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.NbtAttrs
import fishmod.pv.price.HypixelItemsCache
import java.util.Locale
import java.util.function.Function

/**
 * Kuudra gear checklists. Hard requirements ({@link CheckState#MISSING}) are kept apart from recommended
 * upgrades ({@link CheckState#UPGRADE}) so a usable item is never reported as missing just because it lacks
 * an optimisation. Armour sets are summarised per slot using the best matching piece for that set's rules,
 * and every set searches all owned pieces on its own.
 */
object KuudraGearChecks {
	enum class Status { COMPLETE, INCOMPLETE, MISSING, UNKNOWN }

	enum class CheckState { OK, UPGRADE, MISSING, WARN, INFO, UNKNOWN }

	/** {@code upgradeTo} is the recommended level or tier when the value is below it. */
	@JvmRecord
	data class Check(val label: String, val value: String, val state: CheckState, val upgradeTo: String?, val highlight: Boolean) {
		companion object {
			internal fun of(label: String, value: String, state: CheckState): Check = Check(label, value, state, null, false)
		}
	}

	/** {@code value} is set for slot style rows ("Chestplate: Infernal Terror"), null for item rows. */
	@JvmRecord
	data class Entry(
		val name: String, val value: String?, val iconId: String, val status: Status, val checks: List<Check>, val source: String?,
		val stack: InventoryDecoder.Stack?, val pet: PetSnapshot.Entry?
	)

	@JvmRecord
	data class Group(
		val title: String, val iconId: String, val status: Status, val owned: Int, val entries: List<Entry>, val checks: List<Check>,
		val informational: Boolean
	)

	@JvmRecord
	data class Result(val items: List<Group>, val armour: List<Group>) {
		companion object {
			@JvmStatic
			fun empty(): Result = Result(listOf(), listOf())
		}
	}

	@JvmRecord
	data class Owned(val stack: InventoryDecoder.Stack, val source: String)

	/** {@code abilityId} is null when the profile has no HOTM data, blank when nothing is selected. */
	@JvmRecord
	data class Inputs(
		val items: List<Owned>,
		val inventoryKnown: Boolean,
		val pets: List<PetSnapshot.Entry?>,
		val petsKnown: Boolean,
		val presets: List<InventoryDecoder.WardrobePreset>,
		val abilityId: String?,
		val itemName: Function<String, String>,
		val category: Function<InventoryDecoder.Stack, String>,
		val abilityName: Function<String, String>
	)

	private class Numbering {
		private val counts = HashMap<String, Int>()

		fun next(key: String): Int {
			val n = (counts[key] ?: 0) + 1
			counts[key] = n
			return n
		}
	}

	// Item sources Kuudra gear can be pulled from, in dedup priority order.
	private val SOURCES = arrayOf(
		"armor" to "Armour", "equipment" to "Equipment", "inventory" to "Inventory",
		"enderchest" to "Ender Chest", "storage" to "Backpack", "wardrobe" to "Wardrobe",
		"personal_vault" to "Personal Vault"
	)
	private val WITHER_BLADES = setOf("HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE", "NECRON_BLADE")
	private val KUUDRA_SETS = setOf("CRIMSON", "AURORA", "TERROR", "HOLLOW", "FERVOR")
	private val RCM_REFORGES = setOf("necrotic", "loving")
	private val SLOTS = listOf("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")
	private val BODY_SLOTS = listOf("CHESTPLATE", "LEGGINGS", "BOOTS")
	private val RARITIES = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL")
	// Max levels from the NEU enchanted book items (e.g. CUBISM;6, PROSECUTE;6, TABASCO;3).
	private const val MAX_CUBISM = 6
	private const val MAX_SMOLDERING = 5
	private const val MAX_POWER = 7
	private const val MAX_GIANT_KILLER = 7
	private const val MAX_PROSECUTE = 6
	private const val MAX_TABASCO = 3
	private const val MAX_ULTIMATE = 5
	private const val MAX_CANDY = 10
	private val DUPLEX = arrayOf("ultimate_reiterate", "ultimate_duplex")
	private val API_OFF = Check.of("Inventory API", "Off", CheckState.UNKNOWN)
	private val ULTIMATE_MAX: Map<String, Int> = mapOf(
		"ultimate_reiterate" to 5, "ultimate_duplex" to 5, "ultimate_rend" to 5,
		"ultimate_chimera" to 5, "ultimate_swarm" to 5, "ultimate_wise" to 5,
		"ultimate_soul_eater" to 5, "ultimate_fatal_tempo" to 5, "ultimate_inferno" to 5,
		"ultimate_combo" to 5, "ultimate_one_for_all" to 1
	)
	private val NUMERALS = arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

	const val MISSING_TEXT = "Not found"

	@JvmStatic
	fun fromMember(member: JsonObject?, categories: Map<String, List<InventoryDecoder.Stack?>>?, pets: PetSnapshot?): Result {
		if (member == null) {
			return Result.empty()
		}
		val petsKnown = member.has("pets_data") || member.has("pets")
		return compute(Inputs(
			collect(categories),
			InventoryDecoder.hasInventoryApi(member),
			pets?.pets ?: listOf(),
			petsKnown,
			InventoryDecoder.wardrobePresets(member),
			selectedAbility(member),
			{ itemName(it) },
			{ category(it) },
			{ MiningHotmData.displayName(it) }
		))
	}

	@JvmStatic
	fun previewTitle(entry: Entry): String {
		entry.pet?.let { return PetLoreResolver.displayNameFor(it) }
		val stack = entry.stack ?: return entry.name
		val named = CrimsonKuudraCard.ensureColoredName(stack.displayName, entry.iconId)
		return CrimsonKuudraCard.withStarsInName(if (named.isBlank()) entry.name else named, stars(stack))
	}

	@JvmStatic
	fun previewLore(entry: Entry): List<String> {
		entry.pet?.let { return PetLoreResolver.loreFor(it) }
		return entry.stack?.lore ?: listOf()
	}

	/** Flattens the accessible item sources, keeping one copy per item uuid. */
	internal fun collect(categories: Map<String, List<InventoryDecoder.Stack?>>?): List<Owned> {
		val out = ArrayList<Owned>()
		if (categories == null) {
			return out
		}
		val seen = HashSet<String>()
		for ((key, label) in SOURCES) {
			for (stack in categories[key].orEmpty()) {
				if (stack == null || stack.id.isBlank()) {
					continue
				}
				val uuid = NbtAttrs.string(stack.extraAttributes, "uuid")
				if (!uuid.isNullOrBlank() && !seen.add(uuid)) {
					continue
				}
				out.add(Owned(stack, label))
			}
		}
		return out
	}

	internal fun compute(input: Inputs): Result {
		val items = ArrayList<Group>()
		items.add(witherBlades(input))
		items.add(goldenDragons(input))
		addTerminators(input, items)
		items.add(ragnarock(input))
		items.add(sosFlare(input))
		items.add(rendBonemerang(input))
		items.add(swarmAots(input))
		items.add(lastBreath(input))
		items.add(stun(input))

		val armour = listOf(
			dpsTerror(input), rcm(input), hollow(input), reaper(input), tuxedo(input), racingHelmet(input), crownOfAvarice(input)
		)
		return Result(java.util.List.copyOf(items), java.util.List.copyOf(armour))
	}

	private fun witherBlades(input: Inputs): Group {
		val numbering = Numbering()
		val entries = ArrayList<Entry>()
		for (owned in matching(input) { it in WITHER_BLADES }) {
			val ench = enchants(owned.stack)
			val ultimate = ultimateKey(ench)
			val checks = ArrayList<Check>()
			checks.add(
				if (ultimate == null) Check("Ultimate", "None", CheckState.UPGRADE, null, false) else ultimateCheck(ultimate, ench[ultimate])
			)
			checks.add(starsCheck(owned.stack))
			checks.add(reforgeCheck(owned.stack))
			entries.add(entry(input, owned, numbering, checks))
		}
		return itemGroup(input, "Wither Blades", "HYPERION", entries, listOf())
	}

	private fun goldenDragons(input: Inputs): Group {
		if (!input.petsKnown) {
			return Group("Golden Dragon", "GOLDEN_DRAGON;4", Status.UNKNOWN, 0, listOf(),
				listOf(Check.of("Pets", "Not in API", CheckState.UNKNOWN)), false)
		}
		val entries = ArrayList<Entry>()
		var n = 0
		for (pet in input.pets) {
			if (pet == null || !"GOLDEN_DRAGON".equals(pet.type, ignoreCase = true)) {
				continue
			}
			n++
			val checks = ArrayList<Check>()
			val maxed = pet.level >= pet.maxLevel
			checks.add(Check("Level", pet.level.toString(), if (maxed) CheckState.OK else CheckState.UPGRADE,
				if (maxed) null else pet.maxLevel.toString(), false))
			checks.add(Check.of("Pet item", if (pet.hasHeldItem()) input.itemName.apply(pet.heldItem) else "None", CheckState.INFO))
			checks.add(Check.of("Candies", pet.candyUsed.toString() + " / " + MAX_CANDY, CheckState.INFO))
			entries.add(Entry("Golden Dragon ($n)", null, pet.neuId, statusOf(checks), checks, "Pets", null, pet))
		}
		return Group("Golden Dragon", "GOLDEN_DRAGON;4", groupStatus(entries), entries.size, entries, listOf(), false)
	}

	private fun addTerminators(input: Inputs, out: MutableList<Group>) {
		val duplexN = Numbering()
		val rendN = Numbering()
		val otherN = Numbering()
		val duplex = ArrayList<Entry>()
		val rend = ArrayList<Entry>()
		val other = ArrayList<Entry>()
		for (owned in matching(input) { it == "TERMINATOR" }) {
			val ench = enchants(owned.stack)
			val duplexLvl = level(ench, *DUPLEX)
			val rendLvl = level(ench, "ultimate_rend")
			val checks = ArrayList<Check>()
			if (duplexLvl > 0) {
				checks.add(enchCheck("Duplex", duplexLvl, MAX_ULTIMATE, CheckState.MISSING))
				checks.add(starsCheck(owned.stack))
				val cubism = level(ench, "cubism")
				checks.add(enchCheck("Cubism", cubism, MAX_CUBISM, CheckState.UPGRADE).copy(highlight = cubism >= MAX_CUBISM))
				checks.add(enchCheck("Smoldering", level(ench, "smoldering"), MAX_SMOLDERING, CheckState.UPGRADE))
				checks.add(enchCheck("Power", level(ench, "power"), MAX_POWER, CheckState.UPGRADE))
				checks.add(piercingWarning(level(ench, "piercing")))
				checks.add(reforgeCheck(owned.stack))
				duplex.add(entry(input, owned, duplexN, checks, "Duplex Terminator"))
			} else if (rendLvl > 0) {
				checks.add(enchCheck("Rend", rendLvl, MAX_ULTIMATE, CheckState.MISSING))
				checks.add(enchCheck("Cubism", level(ench, "cubism"), MAX_CUBISM, CheckState.UPGRADE))
				checks.add(enchCheck("Power", level(ench, "power"), MAX_POWER, CheckState.UPGRADE))
				checks.add(enchCheck("Smoldering", level(ench, "smoldering"), MAX_SMOLDERING, CheckState.UPGRADE))
				checks.add(starsCheck(owned.stack))
				checks.add(reforgeCheck(owned.stack))
				rend.add(entry(input, owned, rendN, checks, "Rend Terminator"))
			} else {
				val ultimate = ultimateKey(ench)
				val ultLvl = if (ultimate == null) 0 else ench.getValue(ultimate)
				checks.add(Check.of("Ultimate", if (ultimate == null) "None" else ultimateName(ultimate) + " " + roman(ultLvl), CheckState.INFO))
				val power = level(ench, "power")
				val cubism = level(ench, "cubism")
				checks.add(Check.of("Power", if (power > 0) roman(power) else "None", CheckState.INFO))
				checks.add(Check.of("Cubism", if (cubism > 0) roman(cubism) else "None", CheckState.INFO))
				checks.add(starsCheck(owned.stack))
				checks.add(reforgeCheck(owned.stack))
				other.add(entry(input, owned, otherN, checks, "Terminator"))
			}
		}
		out.add(itemGroup(input, "Duplex Terminator", "TERMINATOR", duplex, listOf()))
		out.add(itemGroup(input, "Rend Terminator", "TERMINATOR", rend, listOf()))
		if (other.isNotEmpty()) {
			out.add(Group("Other Terminators", "TERMINATOR", Status.COMPLETE, other.size, other, listOf(), true))
		}
	}

	private fun ragnarock(input: Inputs): Group {
		val numbering = Numbering()
		val entries = ArrayList<Entry>()
		for (owned in matching(input) { it == "RAGNAROCK_AXE" }) {
			val ench = enchants(owned.stack)
			val checks = listOf(
				enchCheck("Chimera", level(ench, "ultimate_chimera"), MAX_ULTIMATE, CheckState.MISSING),
				starsCheck(owned.stack),
				reforgeCheck(owned.stack)
			)
			entries.add(entry(input, owned, numbering, checks))
		}
		return itemGroup(input, "Ragnarock", "RAGNAROCK_AXE", entries, listOf())
	}

	private fun sosFlare(input: Inputs): Group {
		val entries = ArrayList<Entry>()
		var total = 0
		for (owned in matching(input) { it == "SOS_FLARE" }) {
			val count = maxOf(1, owned.stack.count)
			total += count
			val checks = if (count > 1) listOf(Check.of("Count", count.toString(), CheckState.INFO)) else listOf()
			entries.add(Entry(input.itemName.apply("SOS_FLARE"), null, "SOS_FLARE", Status.COMPLETE, checks, owned.source, owned.stack, null))
		}
		val group = itemGroup(input, "SOS Flare", "SOS_FLARE", entries, listOf())
		return Group(group.title, group.iconId, group.status, total, entries, group.checks, false)
	}

	/** Copies without Rend are only listed when no Rend copy exists, so the reason is visible. */
	private fun rendBonemerang(input: Inputs): Group {
		val numbering = Numbering()
		val withRend = ArrayList<Entry>()
		val without = ArrayList<Entry>()
		for (owned in matching(input) { it == "BONE_BOOMERANG" }) {
			val ench = enchants(owned.stack)
			val rendLvl = level(ench, "ultimate_rend")
			val piercing = level(ench, "piercing")
			val checks = listOf(
				enchCheck("Rend", rendLvl, MAX_ULTIMATE, CheckState.MISSING),
				enchCheck("Cubism", level(ench, "cubism"), MAX_CUBISM, CheckState.UPGRADE),
				enchCheck("Power", level(ench, "power"), MAX_POWER, CheckState.UPGRADE),
				enchCheck("Smoldering", level(ench, "smoldering"), MAX_SMOLDERING, CheckState.UPGRADE),
				enchCheck("Tabasco", level(ench, "tabasco"), MAX_TABASCO, CheckState.UPGRADE),
				Check.of("Piercing", if (piercing > 0) roman(piercing) else "None", CheckState.INFO),
				starsCheck(owned.stack)
			)
			(if (rendLvl > 0) withRend else without).add(entry(input, owned, numbering, checks, "Bonemerang"))
		}
		return itemGroup(input, "Rend Bonemerang", "BONE_BOOMERANG", withRend.ifEmpty { without }, listOf())
	}

	private fun swarmAots(input: Inputs): Group {
		val numbering = Numbering()
		val withSwarm = ArrayList<Entry>()
		val without = ArrayList<Entry>()
		for (owned in matching(input) { it == "AXE_OF_THE_SHREDDED" }) {
			val ench = enchants(owned.stack)
			val swarm = level(ench, "ultimate_swarm")
			val checks = listOf(
				enchCheck("Swarm", swarm, MAX_ULTIMATE, CheckState.MISSING),
				enchCheck("Cubism", level(ench, "cubism"), MAX_CUBISM, CheckState.UPGRADE),
				enchCheck("Giant Killer", level(ench, "giant_killer"), MAX_GIANT_KILLER, CheckState.UPGRADE),
				enchCheck("Prosecute", level(ench, "prosecute"), MAX_PROSECUTE, CheckState.UPGRADE),
				enchCheck("Smoldering", level(ench, "smoldering"), MAX_SMOLDERING, CheckState.UPGRADE),
				enchCheck("Tabasco", level(ench, "tabasco"), MAX_TABASCO, CheckState.UPGRADE),
				starsCheck(owned.stack)
			)
			(if (swarm > 0) withSwarm else without).add(entry(input, owned, numbering, checks, "AOTS"))
		}
		return itemGroup(input, "Swarm AOTS", "AXE_OF_THE_SHREDDED", withSwarm.ifEmpty { without }, listOf())
	}

	private fun lastBreath(input: Inputs): Group {
		val numbering = Numbering()
		val entries = ArrayList<Entry>()
		for (owned in matching(input) { it == "LAST_BREATH" }) {
			val duplex = level(enchants(owned.stack), *DUPLEX)
			val check = if (duplex == 1) {
				Check.of("Duplex", "I", CheckState.OK)
			} else {
				Check.of("Duplex", if (duplex > 1) roman(duplex) + ", needs I" else "None, needs I", CheckState.MISSING)
			}
			entries.add(entry(input, owned, numbering, listOf(check, starsCheck(owned.stack))))
		}
		return itemGroup(input, "Last Breath (Duplex I)", "LAST_BREATH", entries, listOf())
	}

	/** One representative tool plus the selected HOTM ability; owning many pickaxes adds nothing. */
	private fun stun(input: Inputs): Group {
		var tool: Owned? = null
		var toolScore = Int.MIN_VALUE
		for (owned in input.items) {
			val category = input.category.apply(owned.stack)
			if (category != "PICKAXE" && category != "DRILL") {
				continue
			}
			val score = toolScore(owned.stack)
			if (score > toolScore) {
				tool = owned
				toolScore = score
			}
		}
		val toolRow = when {
			!input.inventoryKnown -> slotRow("Suitable pickaxe/drill", "Unknown", "DIAMOND_PICKAXE", Status.UNKNOWN, listOf(), null)
			tool == null -> slotRow("Suitable pickaxe/drill", MISSING_TEXT, "DIAMOND_PICKAXE", Status.MISSING, listOf(), null)
			else -> {
				val id = baseId(tool.stack)
				slotRow("Suitable pickaxe/drill", "Owned", id, Status.COMPLETE,
					listOf(Check.of("Tool", input.itemName.apply(id), CheckState.INFO)), tool)
			}
		}
		val abilityId = input.abilityId
		val abilityRow = when {
			abilityId == null -> slotRow("Selected HOTM ability", "Unknown", "", Status.UNKNOWN, listOf(), null)
			isPickobulus(abilityId) -> slotRow("Selected HOTM ability", input.abilityName.apply(abilityId), "", Status.COMPLETE, listOf(), null)
			else -> {
				val name = if (abilityId.isBlank()) "None" else input.abilityName.apply(abilityId)
				slotRow("Selected HOTM ability", name, "", Status.MISSING, listOf(Check.of("Required", "Pickobulus", CheckState.MISSING)), null)
			}
		}
		val rows = listOf(toolRow, abilityRow)
		return Group("Stun", "DIVAN_DRILL", slotGroupStatus(rows), if (tool == null) 0 else 1, rows, listOf(), false)
	}

	private fun dpsTerror(input: Inputs): Group {
		if (!input.inventoryKnown) {
			return unknownArmour("DPS Terror", "INFERNAL_TERROR_CHESTPLATE")
		}
		val rows = ArrayList<Entry>()
		rows.add(helmetRow(input, "WARDEN_HELMET"))
		for (slot in BODY_SLOTS) {
			val best = best(input, { o ->
				val id = baseId(o.stack)
				kuudraSet(id) == "TERROR" && slot == slotOf(id) && reforge(o.stack) !in RCM_REFORGES
			}, { o -> (if (reforge(o.stack) == "ancient") 1000 else 0) + tierRank(baseId(o.stack)) * 20 + stars(o.stack) })
			if (best == null) {
				rows.add(slotRow(pretty(slot), MISSING_TEXT, "INFERNAL_TERROR_$slot", Status.MISSING, listOf(), null))
				continue
			}
			val reforge = reforge(best.stack)
			val checks = listOf(
				tierCheck(baseId(best.stack)),
				if (reforge == "ancient") {
					Check.of("Reforge", "Ancient", CheckState.OK)
				} else {
					Check("Reforge", if (reforge.isEmpty()) "None" else pretty(reforge), CheckState.UPGRADE, "Ancient", false)
				},
				starsCheck(best.stack)
			)
			rows.add(pieceRow(pretty(slot), best, checks))
		}
		return armourGroup("DPS Terror", "INFERNAL_TERROR_CHESTPLATE", rows, false)
	}

	/**
	 * Necrotic or Loving Kuudra-set pieces of any set and tier, Terror preferred. Wither Goggles also count in
	 * the helmet slot. Ancient Terror never qualifies, so DPS and RCM can't claim the same piece.
	 */
	private fun rcm(input: Inputs): Group {
		if (!input.inventoryKnown) {
			return unknownArmour("RCM", "INFERNAL_TERROR_HELMET")
		}
		val rows = ArrayList<Entry>()
		for (slot in SLOTS) {
			val best = best(input, { o ->
				val id = baseId(o.stack)
				reforge(o.stack) in RCM_REFORGES && slot == slotOf(id) && (kuudraSet(id) in KUUDRA_SETS || id == "WITHER_GOGGLES")
			}, { o ->
				val id = baseId(o.stack)
				(if (kuudraSet(id) == "TERROR") 1000 else 0) + tierRank(id) * 20 + stars(o.stack)
			})
			if (best == null) {
				rows.add(slotRow(pretty(slot), MISSING_TEXT, "INFERNAL_TERROR_$slot", Status.MISSING, listOf(), null))
				continue
			}
			val id = baseId(best.stack)
			val checks = ArrayList<Check>()
			if (kuudraSet(id) in KUUDRA_SETS) {
				checks.add(tierCheck(id))
			}
			checks.add(Check.of("Reforge", pretty(reforge(best.stack)), CheckState.OK))
			checks.add(starsCheck(best.stack))
			rows.add(pieceRow(pretty(slot), best, checks))
		}
		return armourGroup("RCM", "INFERNAL_TERROR_HELMET", rows, false)
	}

	private fun hollow(input: Inputs): Group {
		if (!input.inventoryKnown) {
			return unknownArmour("Hollow", "INFERNAL_HOLLOW_HELMET")
		}
		val rows = ArrayList<Entry>()
		for (slot in SLOTS) {
			val best = best(input, { o ->
				val id = baseId(o.stack)
				kuudraSet(id) == "HOLLOW" && slot == slotOf(id)
			}, { o -> tierRank(baseId(o.stack)) * 20 + stars(o.stack) })
			if (best == null) {
				rows.add(slotRow(pretty(slot), MISSING_TEXT, "INFERNAL_HOLLOW_$slot", Status.MISSING, listOf(), null))
				continue
			}
			rows.add(pieceRow(pretty(slot), best, listOf(tierCheck(baseId(best.stack)), reforgeCheck(best.stack), starsCheck(best.stack))))
		}
		return armourGroup("Hollow", "INFERNAL_HOLLOW_HELMET", rows, false)
	}

	private fun reaper(input: Inputs): Group {
		if (!input.inventoryKnown) {
			return unknownArmour("Reaper", "REAPER_MASK")
		}
		val ids = listOf("REAPER_MASK", "REAPER_CHESTPLATE", "REAPER_LEGGINGS", "REAPER_BOOTS")
		val rows = ids.mapIndexed { i, id ->
			val best = best(input, { o -> id == baseId(o.stack) }, { o -> stars(o.stack) })
			if (best == null) {
				slotRow(pretty(SLOTS[i]), MISSING_TEXT, id, Status.MISSING, listOf(), null)
			} else {
				pieceRow(pretty(SLOTS[i]), best, listOf(reforgeCheck(best.stack)))
			}
		}
		val owned = rows.count { it.stack != null }
		val status = when (owned) {
			rows.size -> Status.COMPLETE
			0 -> Status.MISSING
			else -> Status.INCOMPLETE
		}
		return Group("Reaper", "REAPER_MASK", status, owned, rows, listOf(), true)
	}

	private fun tuxedo(input: Inputs): Group {
		if (!input.inventoryKnown) {
			return unknownArmour("Tuxedo", "ELEGANT_TUXEDO_CHESTPLATE")
		}
		val rows = ArrayList<Entry>()
		rows.add(helmetRow(input, "WARDEN_HELMET"))
		for (slot in BODY_SLOTS) {
			val best = best(input, { o ->
				val id = baseId(o.stack)
				tuxedoRank(id) > 0 && slot == slotOf(id)
			}, { o -> tuxedoRank(baseId(o.stack)) * 20 + stars(o.stack) })
			if (best == null) {
				rows.add(slotRow(pretty(slot), MISSING_TEXT, "ELEGANT_TUXEDO_$slot", Status.MISSING, listOf(), null))
				continue
			}
			val rank = tuxedoRank(baseId(best.stack))
			val variant = if (rank == 3) "Elegant" else if (rank == 2) "Fancy" else "Cheap"
			val checks = listOf(
				Check("Variant", variant, if (rank == 3) CheckState.OK else CheckState.UPGRADE, if (rank == 3) null else "Elegant", false),
				reforgeCheck(best.stack)
			)
			rows.add(pieceRow(pretty(slot), best, checks))
		}
		return armourGroup("Tuxedo", "ELEGANT_TUXEDO_CHESTPLATE", rows, false)
	}

	private fun racingHelmet(input: Inputs): Group {
		if (!input.inventoryKnown) {
			return unknownArmour("Racing Helmet", "RACING_HELMET")
		}
		return armourGroup("Racing Helmet", "RACING_HELMET", listOf(helmetRow(input, "RACING_HELMET")), false)
	}

	/** Warns per wardrobe set that wears Crown of Avarice with Terror pieces. Owning both alone is fine. */
	private fun crownOfAvarice(input: Inputs): Group {
		if (!input.inventoryKnown) {
			return Group("Crown of Avarice", "CROWN_OF_AVARICE", Status.UNKNOWN, 0, listOf(), listOf(API_OFF), false)
		}
		if (input.presets.isEmpty()) {
			return Group("Crown of Avarice", "CROWN_OF_AVARICE", Status.UNKNOWN, 0, listOf(),
				listOf(Check.of("Wardrobe sets", "Not in API", CheckState.UNKNOWN)), false)
		}
		val checks = ArrayList<Check>()
		val entries = ArrayList<Entry>()
		for (preset in input.presets) {
			val pieces = preset.pieces
			val helmet = pieces.firstOrNull()
			if (helmet == null || baseId(helmet) != "CROWN_OF_AVARICE") {
				continue
			}
			val terror = pieces.drop(1).any { it != null && kuudraSet(baseId(it)) == "TERROR" }
			if (terror) {
				val where = "Wardrobe slot " + preset.slot
				entries.add(Entry(where, "Crown of Avarice in Terror set", "CROWN_OF_AVARICE", Status.INCOMPLETE,
					listOf(Check.of("", "Warden Helmet preferred", CheckState.WARN)), where, helmet, null))
			}
		}
		if (entries.isEmpty()) {
			checks.add(Check.of("Terror wardrobe sets", "No Crown", CheckState.OK))
		}
		val status = if (entries.isEmpty()) Status.COMPLETE else Status.INCOMPLETE
		return Group("Crown of Avarice", "CROWN_OF_AVARICE", status, entries.size, entries, checks, false)
	}

	private fun helmetRow(input: Inputs, id: String): Entry {
		val best = best(input, { o -> id == baseId(o.stack) }, { o -> stars(o.stack) })
			?: return slotRow("Helmet", MISSING_TEXT, id, Status.MISSING, listOf(), null)
		return pieceRow("Helmet", best, listOf(reforgeCheck(best.stack), starsCheck(best.stack)))
	}

	private fun pieceRow(slotLabel: String, piece: Owned, checks: List<Check>): Entry {
		val id = baseId(piece.stack)
		var value = if (kuudraSet(id) in KUUDRA_SETS) tierName(tierRank(id)) + " " + pretty(kuudraSet(id)) else pretty(id)
		if (tuxedoRank(id) > 0) {
			value = pretty(id.substring(0, id.indexOf("_TUXEDO_"))) + " Tuxedo"
		}
		return Entry(slotLabel, value, id, statusOf(checks), java.util.List.copyOf(checks), piece.source, piece.stack, null)
	}

	private fun slotRow(name: String, value: String, iconId: String, status: Status, checks: List<Check>, owned: Owned?): Entry =
		Entry(name, value, iconId, status, checks, owned?.source, owned?.stack, null)

	private fun unknownArmour(title: String, iconId: String): Group =
		Group(title, iconId, Status.UNKNOWN, 0, listOf(), listOf(API_OFF), false)

	private fun armourGroup(title: String, iconId: String, rows: List<Entry>, informational: Boolean): Group =
		Group(title, iconId, slotGroupStatus(rows), rows.count { it.stack != null }, rows, listOf(), informational)

	/** Complete when every row is satisfied, missing when nothing is owned, otherwise incomplete. */
	private fun slotGroupStatus(rows: List<Entry>): Status = when {
		rows.any { it.status == Status.UNKNOWN } -> Status.UNKNOWN
		rows.all { it.status == Status.COMPLETE } -> Status.COMPLETE
		rows.all { it.status == Status.MISSING } -> Status.MISSING
		else -> Status.INCOMPLETE
	}

	private fun itemGroup(input: Inputs, title: String, iconId: String, entries: List<Entry>, checks: List<Check>): Group {
		if (!input.inventoryKnown) {
			return Group(title, iconId, Status.UNKNOWN, 0, listOf(), listOf(API_OFF), false)
		}
		return Group(title, iconId, groupStatus(entries), entries.size, entries, checks, false)
	}

	/** Owned items are never "missing" as a group: the best copy decides between complete and incomplete. */
	private fun groupStatus(entries: List<Entry>): Status = when {
		entries.isEmpty() -> Status.MISSING
		entries.any { it.status == Status.COMPLETE } -> Status.COMPLETE
		else -> Status.INCOMPLETE
	}

	internal fun statusOf(checks: List<Check>): Status = when {
		checks.any { it.state == CheckState.MISSING } -> Status.MISSING
		checks.any { it.state == CheckState.UPGRADE } -> Status.INCOMPLETE
		else -> Status.COMPLETE
	}

	private fun tierCheck(id: String): Check {
		val rank = tierRank(id)
		return Check("Tier", tierName(rank), if (rank >= 4) CheckState.OK else CheckState.UPGRADE, if (rank >= 4) null else "Infernal", false)
	}

	private fun best(input: Inputs, accept: (Owned) -> Boolean, score: (Owned) -> Int): Owned? {
		var best: Owned? = null
		var bestScore = Int.MIN_VALUE
		for (owned in input.items) {
			if (!accept(owned)) {
				continue
			}
			val s = score(owned)
			if (s > bestScore) {
				best = owned
				bestScore = s
			}
		}
		return best
	}

	private fun matching(input: Inputs, idMatches: (String) -> Boolean): List<Owned> = input.items.filter { idMatches(baseId(it.stack)) }

	/** Higher rarity first; Dungeonbreaker and Pickonimbus sort last since they're limited-use tools. */
	internal fun toolScore(stack: InventoryDecoder.Stack): Int {
		val id = baseId(stack)
		var score = rarityRank(stack) * 10
		if (id.contains("DUNGEONBREAKER") || id.startsWith("PICKONIMBUS")) {
			score -= 1000
		}
		return score
	}

	private fun rarityRank(stack: InventoryDecoder.Stack): Int {
		val lore = stack.lore
		for (i in lore.indices.reversed()) {
			val line = TextStrip.plain(lore[i]).trim().uppercase(Locale.ROOT)
			if (line.isEmpty()) {
				continue
			}
			for (r in RARITIES.indices.reversed()) {
				if (line.startsWith(RARITIES[r])) {
					return r + 1
				}
			}
			return 0
		}
		return 0
	}

	private fun entry(input: Inputs, owned: Owned, numbering: Numbering, checks: List<Check>): Entry =
		entry(input, owned, numbering, checks, input.itemName.apply(baseId(owned.stack)))

	private fun entry(input: Inputs, owned: Owned, numbering: Numbering, checks: List<Check>, name: String): Entry =
		Entry(name + " (" + numbering.next(name) + ")", null, baseId(owned.stack), statusOf(checks),
			java.util.List.copyOf(checks), owned.source, owned.stack, null)

	/** {@code whenAbsent} is MISSING for hard requirements, UPGRADE for recommended enchants. */
	private fun enchCheck(label: String, level: Int, max: Int, whenAbsent: CheckState): Check = when {
		level <= 0 -> Check(label, "None", whenAbsent, if (whenAbsent == CheckState.UPGRADE) roman(max) else null, false)
		level < max -> Check(label, roman(level), CheckState.UPGRADE, roman(max), false)
		else -> Check.of(label, roman(level), CheckState.OK)
	}

	private fun ultimateCheck(key: String, level: Int?): Check {
		val lvl = level ?: 0
		val max = ULTIMATE_MAX[key]
		val upgradeTo = if (max != null && lvl < max) roman(max) else null
		return Check("Ultimate", ultimateName(key) + " " + roman(lvl), if (upgradeTo != null) CheckState.UPGRADE else CheckState.OK,
			upgradeTo, false)
	}

	private fun piercingWarning(level: Int): Check =
		if (level > 0) Check.of("Piercing", roman(level), CheckState.WARN) else Check.of("Piercing", "None", CheckState.OK)

	private fun starsCheck(stack: InventoryDecoder.Stack): Check {
		val stars = stars(stack)
		return Check.of("Stars", if (stars > 0) stars.toString() else "None", CheckState.INFO)
	}

	private fun reforgeCheck(stack: InventoryDecoder.Stack): Check {
		val reforge = reforge(stack)
		return Check.of("Reforge", if (reforge.isEmpty()) "None" else pretty(reforge), CheckState.INFO)
	}

	internal fun ultimateKey(ench: Map<String, Int>): String? =
		ench.entries.firstOrNull { it.key.startsWith("ultimate_") && it.value > 0 }?.key

	internal fun ultimateName(key: String): String = when (val core = key.removePrefix("ultimate_")) {
		"reiterate", "duplex" -> "Duplex"
		"wise" -> "Ultimate Wise"
		else -> pretty(core)
	}

	internal fun isPickobulus(abilityId: String?): Boolean =
		"pickaxe_toss".equals(abilityId, ignoreCase = true) || "pickobulus".equals(abilityId, ignoreCase = true)

	internal fun selectedAbility(member: JsonObject): String? {
		val tree = Leveling.obj(member.get("skill_tree"))
		val selected = Leveling.obj(tree?.get("selected_ability"))
		if (selected != null && selected.has("mining") && selected.get("mining").isJsonPrimitive) {
			return selected.get("mining").asString
		}
		val core = Leveling.obj(member.get("mining_core"))
		if (core != null && core.has("selected_pickaxe_ability") && core.get("selected_pickaxe_ability").isJsonPrimitive) {
			return core.get("selected_pickaxe_ability").asString
		}
		return if (tree != null || core != null) "" else null
	}

	internal fun category(stack: InventoryDecoder.Stack): String {
		val def = HypixelItemsCache.get(baseId(stack))
		if (def != null && def.has("category") && def.get("category").isJsonPrimitive) {
			return def.get("category").asString.uppercase(Locale.ROOT)
		}
		val lore = stack.lore
		for (i in lore.indices.reversed()) {
			val line = TextStrip.plain(lore[i]).trim()
			if (line.isEmpty()) {
				continue
			}
			if (line.contains(" DRILL")) {
				return "DRILL"
			}
			return if (line.contains(" PICKAXE")) "PICKAXE" else ""
		}
		return ""
	}

	@JvmStatic
	fun itemName(id: String): String {
		val def = HypixelItemsCache.get(id)
		if (def != null && def.has("name") && def.get("name").isJsonPrimitive) {
			return def.get("name").asString
		}
		val neu = NeuRepoCache.get(id)
		if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive) {
			return TextStrip.plain(neu.get("displayname").asString)
		}
		return pretty(id)
	}

	private fun enchants(stack: InventoryDecoder.Stack): Map<String, Int> = NbtAttrs.intMap(stack.extraAttributes, "enchantments")

	private fun level(ench: Map<String, Int>, vararg keys: String): Int = keys.fold(0) { best, key -> maxOf(best, ench[key] ?: 0) }

	@JvmStatic
	fun stars(stack: InventoryDecoder.Stack): Int {
		val upgrade = NbtAttrs.intValue(stack.extraAttributes, "upgrade_level", 0)
		return if (upgrade > 0) upgrade else NbtAttrs.intValue(stack.extraAttributes, "dungeon_item_level", 0)
	}

	private fun reforge(stack: InventoryDecoder.Stack): String = NbtAttrs.string(stack.extraAttributes, "modifier")?.lowercase(Locale.ROOT) ?: ""

	@JvmStatic
	fun baseId(stack: InventoryDecoder.Stack?): String {
		if (stack == null) {
			return ""
		}
		val id = stack.id.uppercase(Locale.ROOT)
		val cut = id.indexOf(';')
		return if (cut >= 0) id.substring(0, cut) else id
	}

	internal fun slotOf(id: String): String {
		if (id == "REAPER_MASK" || id == "WITHER_GOGGLES") {
			return "HELMET"
		}
		return SLOTS.firstOrNull { id.endsWith("_$it") } ?: ""
	}

	internal fun tierRank(id: String): Int = when {
		id.startsWith("INFERNAL_") -> 4
		id.startsWith("FIERY_") -> 3
		id.startsWith("BURNING_") -> 2
		id.startsWith("HOT_") -> 1
		else -> 0
	}

	private fun tierName(rank: Int): String = when (rank) {
		4 -> "Infernal"
		3 -> "Fiery"
		2 -> "Burning"
		1 -> "Hot"
		else -> "Base"
	}

	/** Kuudra armour set name (e.g. {@code TERROR}) without tier prefix or slot suffix. */
	internal fun kuudraSet(id: String): String {
		val slot = slotOf(id)
		if (slot.isEmpty() || !id.endsWith("_$slot")) {
			return ""
		}
		var core = id.substring(0, id.length - slot.length - 1)
		for (prefix in listOf("INFERNAL_", "FIERY_", "BURNING_", "HOT_")) {
			if (core.startsWith(prefix)) {
				core = core.substring(prefix.length)
				break
			}
		}
		return if (core in KUUDRA_SETS) core else ""
	}

	private fun tuxedoRank(id: String): Int {
		if (slotOf(id) !in BODY_SLOTS || !id.contains("_TUXEDO_")) {
			return 0
		}
		return when {
			id.startsWith("ELEGANT_") -> 3
			id.startsWith("FANCY_") -> 2
			id.startsWith("CHEAP_") -> 1
			else -> 0
		}
	}

	@JvmStatic
	fun roman(n: Int): String = if (n >= 0 && n < NUMERALS.size) NUMERALS[n] else n.toString()

	private fun pretty(raw: String?): String {
		if (raw.isNullOrBlank()) {
			return ""
		}
		val sb = StringBuilder()
		for (part in raw.lowercase(Locale.ROOT).split("_")) {
			if (part.isEmpty()) {
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
