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
import fishmod.pv.dungeons.CataXpMath
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.NbtAttrs
import java.util.Locale
import kotlin.math.floor

/** Derived Kuudra party-card stats (score, skills, gear checks, armor). */
class CrimsonKuudraCard private constructor(
	@get:JvmName("kuudraScore") val kuudraScore: Double,
	@get:JvmName("kuudraLevel") val kuudraLevel: Int,
	@get:JvmName("magicalPower") val magicalPower: Int,
	selectedPower: String?,
	@get:JvmName("powerIntelligence") val powerIntelligence: Double,
	@get:JvmName("powerMagicFind") val powerMagicFind: Double,
	@get:JvmName("cataLevel") val cataLevel: Int,
	@get:JvmName("combatLevel") val combatLevel: Int,
	@get:JvmName("foragingLevel") val foragingLevel: Int,
	@get:JvmName("skyBlockLevel") val skyBlockLevel: Int,
	@get:JvmName("vanquisherChancePct") val vanquisherChancePct: Double,
	vanquisherHover: List<String>?,
	importantItems: List<ImportantItem>?,
	mageArmor: List<ArmorPiece>?,
	archerArmor: List<ArmorPiece>?,
	gear: KuudraGearChecks.Result?
) {
	@JvmRecord
	data class ImportantItem(
		val label: String,
		val owned: Boolean,
		val iconId: String,
		val details: List<String>,
		val lore: List<String>,
		val displayName: String
	)

	@JvmRecord
	data class ArmorPiece(
		val slot: String,
		val label: String,
		val iconId: String,
		val owned: Boolean,
		val details: List<String>,
		val lore: List<String>,
		val dyeColor: Int?,
		val stars: Int,
		val dyeItem: String,
		val displayName: String
	)

	private data class Vanq(val pct: Double, val hover: List<String>)

	@get:JvmName("selectedPower") val selectedPower: String = selectedPower ?: ""
	@get:JvmName("vanquisherHover") val vanquisherHover: List<String> = java.util.List.copyOf(vanquisherHover ?: listOf())
	@get:JvmName("importantItems") val importantItems: List<ImportantItem> = java.util.List.copyOf(importantItems ?: listOf())
	@get:JvmName("mageArmor") val mageArmor: List<ArmorPiece> = java.util.List.copyOf(mageArmor ?: listOf())
	@get:JvmName("archerArmor") val archerArmor: List<ArmorPiece> = java.util.List.copyOf(archerArmor ?: listOf())
	@get:JvmName("gear") val gear: KuudraGearChecks.Result = gear ?: KuudraGearChecks.Result.empty()

	fun selectedPowerLabel(): String = if (selectedPower.isBlank()) "None" else prettyId(selectedPower)

	fun withCombatStats(stats: PlayerStatsSnapshot?): CrimsonKuudraCard {
		if (stats == null) {
			return this
		}
		val intel = statValue(stats, "intelligence", powerIntelligence)
		val mf = statValue(stats, "magic_find", powerMagicFind)
		return CrimsonKuudraCard(
			kuudraScore, kuudraLevel, magicalPower, selectedPower,
			intel, mf, cataLevel, combatLevel, foragingLevel, skyBlockLevel,
			vanquisherChancePct, vanquisherHover, importantItems, mageArmor, archerArmor,
			gear
		)
	}

	fun scoreHoverLines(): List<String> = listOf(
		"1 level = 100 Kuudra Score",
		"Score per completion:",
		"Basic 0.5, Hot 1, Burning 2, Fiery 4, Infernal 8"
	)

	companion object {
		private val TIER_WEIGHT = doubleArrayOf(0.5, 1.0, 2.0, 4.0, 8.0)
		private val TIER_PREFIXES = listOf("INFERNAL_", "FIERY_", "BURNING_", "HOT_")

		@JvmStatic
		fun empty(): CrimsonKuudraCard = CrimsonKuudraCard(
			0.0, 0, 0, "", 0.0, 0.0, 0, 0, 0, 0, 1.0 / 640.0 * 100.0,
			listOf("Base 1/640"),
			listOf(), listOf(), listOf(), KuudraGearChecks.Result.empty()
		)

		@JvmStatic
		fun from(member: JsonObject?, kuudra: Map<CrimsonSnapshot.KuudraTier, CrimsonSnapshot.KuudraTierStats>?): CrimsonKuudraCard {
			if (member == null) {
				return empty()
			}
			var score = 0.0
			if (kuudra != null) {
				for ((i, tier) in CrimsonSnapshot.KuudraTier.values().withIndex()) {
					score += (kuudra[tier]?.completions ?: 0) * TIER_WEIGHT[i]
				}
			}
			val level = floor(score / 100.0).toInt()

			val storage = Leveling.obj(member.get("accessory_bag_storage"))
			var mp = MagicalPowerCalculator.fromMember(member)
			if (mp <= 0) {
				mp = if (storage == null) 0 else num(storage.get("highest_magical_power")).toInt()
			}
			val power = if (storage == null) "" else str(storage.get("selected_power"))
			val powerStats = MaxwellPowers.statsFor(power, mp)
			val intel = powerStats["intelligence"] ?: 0.0
			val mf = powerStats["magic_find"] ?: 0.0

			val cataXp = Leveling.readCatacombsXp(member)
			val cata = CataXpMath.progress(cataXp).displayLevel()
			val combat = Leveling.skillLevel(member, "combat")
			val foraging = Leveling.skillLevel(member, "foraging")
			var sb = 0
			val leveling = Leveling.obj(member.get("leveling"))
			if (leveling != null) {
				Leveling.num(leveling.get("experience"))?.let { sb = floor((it / 100f).toDouble()).toInt() }
			}

			val cats = InventoryDecoder.parseCategories(member, null)
			val all = flatten(cats)
			val pets = PetSnapshot.fromMember(member)

			val vanq = vanquisherChance(member, pets, all)
			val items = scanImportant(all, pets)
			val mage = scanMageArmor(all)
			val archer = scanArcherArmor(all)

			return CrimsonKuudraCard(
				score, level, mp, power, intel, mf, cata, combat, foraging, sb,
				vanq.pct, vanq.hover, items, mage, archer, KuudraGearChecks.fromMember(member, cats, pets)
			)
		}

		private fun statValue(stats: PlayerStatsSnapshot, id: String, fallback: Double): Double {
			for (entry in stats.entries) {
				if (id.equals(entry.id, ignoreCase = true) && entry.value.isPresent) {
					return entry.value.asDouble
				}
			}
			return fallback
		}

		private fun vanquisherChance(member: JsonObject, pets: PetSnapshot?, all: List<InventoryDecoder.Stack>): Vanq {
			var chance = 1.0 / 640.0
			val hover = ArrayList<String>()
			hover.add("Base 1/640")

			val kuudraPet = bestKuudraPet(pets)
			if (kuudraPet != null) {
				val mult = when (kuudraPet.tier.uppercase(Locale.ROOT)) {
					"LEGENDARY", "MYTHIC", "EPIC" -> 1.20
					"RARE", "UNCOMMON" -> 1.15
					else -> 1.10
				}
				chance *= mult
				hover.add(String.format(Locale.ROOT, "Kuudra Pet ×%.2f", mult))
			}

			if (hasWitherPiper(member)) {
				chance *= 1.10
				hover.add("Wither Piper ×1.10")
			}
			if (endermanSlayerAtLeast(member, 9)) {
				chance *= 1.15
				hover.add("Enderman IX ×1.15")
			}
			val tracking = maxAttribute(all, "tracking")
			if (tracking > 0) {
				val mult = 1.0 + minOf(15, tracking) * 0.01
				chance *= mult
				hover.add(String.format(Locale.ROOT, "Tracking %d ×%.2f", tracking, mult))
			}
			var serendipity = maxAttribute(all, "crimson_serendipity")
			if (serendipity <= 0) {
				serendipity = maxAttribute(all, "serendipity")
			}
			if (serendipity > 0) {
				val mult = 1.0 + minOf(10, serendipity) * 0.02
				chance *= mult
				hover.add(String.format(Locale.ROOT, "Crimson Serendipity ×%.2f", mult))
			}

			return Vanq(chance * 100.0, hover)
		}

		private fun bestKuudraPet(pets: PetSnapshot?): PetSnapshot.Entry? {
			var best: PetSnapshot.Entry? = null
			for (pet in pets?.pets.orEmpty()) {
				if (!"KUUDRA".equals(pet.type, ignoreCase = true)) {
					continue
				}
				if (best == null || pet.level > best.level) {
					best = pet
				}
			}
			return best
		}

		private fun hasWitherPiper(member: JsonObject): Boolean {
			val playerData = Leveling.obj(member.get("player_data"))
			val perks = playerData?.let { Leveling.obj(it.get("perks")) }
				?: Leveling.obj(member.get("dungeons"))?.let { Leveling.obj(it.get("perks")) }
				?: return false
			for (key in perks.keySet()) {
				if (key != null && key.lowercase(Locale.ROOT).contains("wither_piper")) {
					return num(perks.get(key)) > 0
				}
			}
			return false
		}

		private fun endermanSlayerAtLeast(member: JsonObject, level: Int): Boolean {
			val xp = Leveling.readSlayerXp(member, "enderman")
			val progress = Leveling.getLevel(RepoData.slayerXp("enderman"), xp, 9, true)
			return floor(progress.level.toDouble()).toInt() >= level
		}

		private fun maxAttribute(stacks: List<InventoryDecoder.Stack>, attr: String): Int {
			var best = 0
			val key = attr.lowercase(Locale.ROOT)
			for (stack in stacks) {
				val attrs = NbtAttrs.compound(stack.extraAttributes, "attributes") ?: continue
				for (entry in attrs.keySet()) {
					if (entry != null && entry.lowercase(Locale.ROOT) == key) {
						best = maxOf(best, NbtAttrs.intValue(attrs, entry, 0))
					}
				}
			}
			return best
		}

		private fun scanImportant(all: List<InventoryDecoder.Stack>, pets: PetSnapshot?): List<ImportantItem> = listOf(
			witherImpact(all),
			goldenDragon(pets),
			terminator(all, true),
			terminator(all, false),
			ragnarock(all),
			sosFlare(all)
		)

		private fun witherImpact(all: List<InventoryDecoder.Stack>): ImportantItem {
			val ids = arrayOf("HYPERION", "SCYLLA", "ASTRAEA", "VALKYRIE", "NECRON_BLADE")
			var best: InventoryDecoder.Stack? = null
			for (stack in all) {
				val id = baseId(stack)
				if (ids.any { id == it || id.endsWith("_$it") || id.startsWith(it) }) {
					best = prefer(best, stack)
				}
			}
			if (best == null) {
				return missingItem("Wither Impact Weapon", "HYPERION")
			}
			val details = ArrayList<String>()
			details.add(prettyId(baseId(best)))
			val s = stars(best)
			if (s > 0) {
				details.add("$s★")
			}
			return ownedItem("Wither Impact Weapon", baseId(best), details, best)
		}

		private fun goldenDragon(pets: PetSnapshot?): ImportantItem {
			var gdrag: PetSnapshot.Entry? = null
			for (pet in pets?.pets.orEmpty()) {
				if ("GOLDEN_DRAGON".equals(pet.type, ignoreCase = true) && (gdrag == null || pet.level > gdrag.level)) {
					gdrag = pet
				}
			}
			if (gdrag == null) {
				return missingItem("[Lvl 200] Golden Dragon", "GOLDEN_DRAGON;4")
			}
			val ok = gdrag.level >= 200
			val label = "[Lvl " + gdrag.level + "] Golden Dragon"
			return ImportantItem(
				label, ok, gdrag.neuId, listOf(), java.util.List.copyOf(PetLoreResolver.loreFor(gdrag)),
				PetLoreResolver.displayNameFor(gdrag)
			)
		}

		private fun tierColorCode(tier: String?): String = when (tier?.uppercase(Locale.ROOT)) {
			"MYTHIC" -> "§d"
			"LEGENDARY" -> "§6"
			"EPIC" -> "§5"
			"RARE" -> "§9"
			"UNCOMMON" -> "§a"
			else -> "§f"
		}

		private fun terminator(all: List<InventoryDecoder.Stack>, spiritual: Boolean): ImportantItem {
			var best: InventoryDecoder.Stack? = null
			for (stack in all) {
				if (!baseId(stack).contains("TERMINATOR")) {
					continue
				}
				val mod = NbtAttrs.string(stack.extraAttributes, "modifier")
				val wanted = if (spiritual) "spiritual" else "hasty"
				if (mod != null && mod.equals(wanted, ignoreCase = true)) {
					best = prefer(best, stack)
				}
			}
			if (best == null) {
				return missingItem(if (spiritual) "Spiritual Duplex Terminator" else "Hasty Terminator", "TERMINATOR")
			}
			val ench = NbtAttrs.intMap(best.extraAttributes, "enchantments")
			val details = ArrayList<String>()
			val mod = NbtAttrs.string(best.extraAttributes, "modifier")
			if (mod != null) {
				details.add(prettyId(mod))
			}
			val cubism = enchLevel(ench, "cubism")
			val power = enchLevel(ench, "power")
			val rend = enchLevel(ench, "rend", "ultimate_rend")
			// Duplex is stored as ultimate_reiterate in item NBT.
			val duplex = enchLevel(ench, "ultimate_reiterate", "ultimate_duplex")
			val fatalTempo = enchLevel(ench, "ultimate_fatal_tempo", "fatal_tempo")
			if (cubism > 0) {
				details.add("Cubism $cubism")
			}
			if (power > 0) {
				details.add("Power $power")
			}
			if (fatalTempo > 0) {
				details.add("Fatal Tempo $fatalTempo")
			} else if (rend > 0) {
				details.add("Rend $rend")
			}
			if (duplex > 0) {
				details.add("Duplex $duplex")
			}
			val label = when {
				spiritual -> "Spiritual Duplex Terminator"
				fatalTempo > 0 -> "Hasty Fatal Tempo Terminator"
				rend > 0 -> "Hasty Rend Terminator"
				else -> "Hasty Terminator"
			}
			val ownedOk = !spiritual || (mod != null && mod.equals("spiritual", ignoreCase = true))
			return ownedItem(label, baseId(best), details, best, ownedOk)
		}

		private fun ragnarock(all: List<InventoryDecoder.Stack>): ImportantItem {
			var best: InventoryDecoder.Stack? = null
			for (stack in all) {
				if (baseId(stack).contains("RAGNAROCK")) {
					best = prefer(best, stack)
				}
			}
			if (best == null) {
				return missingItem("[Ragnarock]", "RAGNAROCK_AXE")
			}
			val ench = NbtAttrs.intMap(best.extraAttributes, "enchantments")
			val s = stars(best)
			val chimera = enchLevel(ench, "ultimate_chimera", "chimera")
			// Line: [Ragnarock] [N★] [Chim x]
			return ownedItem("[Ragnarock] [$s★] [Chim $chimera]", baseId(best), listOf(), best)
		}

		private fun enchLevel(ench: Map<String, Int>?, vararg keys: String?): Int {
			if (ench.isNullOrEmpty()) {
				return 0
			}
			var best = 0
			for (key in keys) {
				if (key.isNullOrBlank()) {
					continue
				}
				ench[key.lowercase(Locale.ROOT)]?.let { best = maxOf(best, it) }
			}
			return best
		}

		private fun sosFlare(all: List<InventoryDecoder.Stack>): ImportantItem {
			for (stack in all) {
				val id = baseId(stack)
				if (id == "SOS_FLARE" || id == "FLARE" || id.endsWith("_FLARE")) {
					return ownedItem("SOS Flare", id, listOf(prettyId(id)), stack)
				}
			}
			return missingItem("SOS Flare", "SOS_FLARE")
		}

		private fun missingItem(label: String, iconId: String): ImportantItem = ImportantItem(label, false, iconId, listOf(), listOf(), "")

		private fun ownedItem(
			label: String, iconId: String, details: List<String>, stack: InventoryDecoder.Stack, owned: Boolean = true
		): ImportantItem {
			var display = stack.displayName ?: ""
			if (display.isBlank()) {
				display = label
			}
			display = withStarsInName(ensureColoredName(display, iconId), stars(stack))
			return ImportantItem(label, owned, iconId, java.util.List.copyOf(details), stackLore(stack), display)
		}

		/** Append dungeon stars to the colored display name when missing. */
		@JvmStatic
		fun withStarsInName(displayName: String?, stars: Int): String {
			if (displayName.isNullOrBlank() || stars <= 0) {
				return displayName ?: ""
			}
			if (displayName.contains("✪") || displayName.contains("★")) {
				return displayName
			}
			return displayName.trim() + " " + "✪".repeat(minOf(10, stars))
		}

		/** If inventory name lost its § colour, restore from NEU displayname / tier. */
		@JvmStatic
		fun ensureColoredName(name: String?, itemId: String?): String {
			if (name != null && name.contains("§")) {
				return name
			}
			val plain = name ?: ""
			val neu = if (itemId.isNullOrBlank()) null else NeuRepoCache.get(itemId.uppercase(Locale.ROOT))
			if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive) {
				val neuName: String? = neu.get("displayname").asString
				if (neuName != null && neuName.contains("§")) {
					if (plain.isBlank()) {
						return neuName
					}
					// Keep live name (reforge etc.) but reuse NEU leading colour codes.
					val prefix = StringBuilder()
					var i = 0
					while (i + 1 < neuName.length && neuName[i] == '§') {
						prefix.append('§').append(neuName[i + 1])
						i += 2
					}
					if (prefix.isNotEmpty()) {
						return prefix.toString() + plain
					}
				}
			}
			if (neu != null && neu.has("tier") && neu.get("tier").isJsonPrimitive) {
				return tierColorCode(neu.get("tier").asString) + plain.ifBlank { prettyId(itemId) }
			}
			return plain
		}

		private fun scanMageArmor(all: List<InventoryDecoder.Stack>): List<ArmorPiece> = listOf(
			bestMageHelmet(all),
			bestArmor(all, "CHESTPLATE", true),
			bestArmor(all, "LEGGINGS", true),
			bestArmor(all, "BOOTS", true)
		)

		private fun scanArcherArmor(all: List<InventoryDecoder.Stack>): List<ArmorPiece> = listOf(
			bestWardenHelmet(all),
			bestArmor(all, "CHESTPLATE", false),
			bestArmor(all, "LEGGINGS", false),
			bestArmor(all, "BOOTS", false)
		)

		private fun bestMageHelmet(all: List<InventoryDecoder.Stack>): ArmorPiece {
			var best: InventoryDecoder.Stack? = null
			var bestRank = -1
			for (stack in all) {
				val id = baseId(stack)
				val goggles = id == "WITHER_GOGGLES" || id.contains("WITHER_GOGGLE")
				val helmet = id.contains("HELMET")
				val aurora = id.contains("AURORA")
				val hollow = id.contains("HOLLOW")
				val terror = id.contains("TERROR")
				if (!goggles && !(helmet && (aurora || hollow || terror))) {
					continue
				}
				val mageReforge = isMageReforge(NbtAttrs.string(stack.extraAttributes, "modifier"))
				// Hollow/Terror only count as mage with Loving/Necrotic; Aurora + goggles always ok.
				if (!goggles && !aurora && (hollow || terror) && !mageReforge) {
					continue
				}
				// Tier first so Infernal Loving Terror beats lower Aurora.
				var rank = kuudraTierRank(id) * 100 + stars(stack)
				if (mageReforge) {
					rank += 50
				}
				rank += when {
					goggles -> 30
					aurora -> 20
					hollow -> 10
					else -> 5
				}
				if (rank > bestRank) {
					bestRank = rank
					best = stack
				}
			}
			return if (best == null) missingArmor("HELMET", "Wither Goggles", "WITHER_GOGGLES") else ownedArmor(best, "HELMET")
		}

		private fun bestWardenHelmet(all: List<InventoryDecoder.Stack>): ArmorPiece {
			var best: InventoryDecoder.Stack? = null
			for (stack in all) {
				val id = baseId(stack)
				if (id.contains("WARDEN") && id.contains("HELMET")) {
					best = prefer(best, stack)
				}
			}
			return if (best == null) missingArmor("HELMET", "Warden Helmet", "WARDEN_HELMET") else ownedArmor(best, "HELMET")
		}

		private fun bestArmor(all: List<InventoryDecoder.Stack>, slot: String, mage: Boolean): ArmorPiece {
			var best: InventoryDecoder.Stack? = null
			var bestRank = -1
			for (stack in all) {
				val id = baseId(stack)
				if (!id.contains("_$slot")) {
					continue
				}
				val terror = id.contains("TERROR")
				val aurora = id.contains("AURORA")
				val hollow = id.contains("HOLLOW")
				val mageReforge = isMageReforge(NbtAttrs.string(stack.extraAttributes, "modifier"))
				// Mage: Aurora always; Hollow/Terror only with Loving or Necrotic. Archer: Terror only.
				val accepted = if (mage) aurora || ((hollow || terror) && mageReforge) else terror
				if (!accepted) {
					continue
				}
				var rank = kuudraTierRank(id) * 100 + stars(stack)
				if (mage) {
					if (mageReforge) {
						rank += 50
					}
					rank += when {
						aurora -> 20
						hollow -> 10
						else -> 5
					}
				}
				if (rank > bestRank) {
					bestRank = rank
					best = stack
				}
			}
			val slotLabel = when (slot) {
				"HELMET" -> "Helmet"
				"CHESTPLATE" -> "Chestplate"
				"LEGGINGS" -> "Leggings"
				"BOOTS" -> "Boots"
				else -> slot
			}
			if (best == null) {
				return missingArmor(slot, slotLabel, if (mage) "AURORA_$slot" else "TERROR_$slot")
			}
			return ownedArmor(best, slot)
		}

		private fun isMageReforge(mod: String?): Boolean {
			if (mod.isNullOrBlank()) {
				return false
			}
			val m = mod.lowercase(Locale.ROOT)
			return m.contains("necrotic") || m.contains("loving")
		}

		private fun missingArmor(slot: String, label: String, iconId: String): ArmorPiece =
			ArmorPiece(slot, label, iconId, false, listOf(), listOf(), null, 0, "", "")

		private fun ownedArmor(best: InventoryDecoder.Stack, slot: String): ArmorPiece {
			val details = armorDetails(best)
			val mod = NbtAttrs.string(best.extraAttributes, "modifier")
			val dyeItem = NbtAttrs.string(best.extraAttributes, "dye_item") ?: ""
			val id = baseId(best)
			val tier = kuudraTierName(id)
			val reforgePrefix = if (!mod.isNullOrBlank()) prettyId(mod) + " " else ""
			val label = when {
				id.contains("WITHER_GOGGLE") -> reforgePrefix + "Wither Goggles"
				id.contains("WARDEN") -> reforgePrefix + prettyId(id)
				else -> reforgePrefix + (if (tier.isBlank()) "" else "$tier ") + setName(id)
			}
			val starCount = stars(best)
			var display = best.displayName ?: ""
			if (display.isBlank()) {
				display = label.trim()
			}
			display = withStarsInName(ensureColoredName(display, id), starCount)
			return ArmorPiece(
				slot,
				label.trim(),
				id,
				true,
				java.util.List.copyOf(details),
				stackLore(best),
				best.dyeColor,
				maxOf(0, starCount),
				dyeItem,
				display
			)
		}

		private fun stackLore(stack: InventoryDecoder.Stack?): List<String> =
			if (stack == null || stack.lore.isEmpty()) listOf() else java.util.List.copyOf(stack.lore)

		private fun armorDetails(stack: InventoryDecoder.Stack): List<String> {
			val details = ArrayList<String>()
			val stars = stars(stack)
			if (stars > 0) {
				details.add("$stars★")
			}
			val mod = NbtAttrs.string(stack.extraAttributes, "modifier")
			if (!mod.isNullOrBlank()) {
				details.add(prettyId(mod))
			}
			val dyeItem = NbtAttrs.string(stack.extraAttributes, "dye_item")
			if (!dyeItem.isNullOrBlank()) {
				details.add(prettyId(dyeItem))
			}
			stack.dyeColor?.let { details.add(String.format(Locale.ROOT, "#%06X", it and 0xFFFFFF)) }
			val attrs = NbtAttrs.intMap(stack.extraAttributes, "attributes")
			attrs["legion"]?.takeIf { it > 0 }?.let { details.add("Legion $it") }
			attrs["mana_pool"]?.takeIf { it > 0 }?.let { details.add("Mana Pool $it") }
			attrs["mana_regeneration"]?.takeIf { it > 0 }?.let { details.add("Mana Regen $it") }
			return details
		}

		private fun kuudraTierRank(id: String): Int {
			val u = id.uppercase(Locale.ROOT)
			return when {
				u.startsWith("INFERNAL_") -> 4
				u.startsWith("FIERY_") -> 3
				u.startsWith("BURNING_") -> 2
				u.startsWith("HOT_") -> 1
				else -> 0
			}
		}

		private fun kuudraTierName(id: String): String = when (kuudraTierRank(id)) {
			4 -> "Infernal"
			3 -> "Fiery"
			2 -> "Burning"
			1 -> "Hot"
			else -> ""
		}

		private fun setName(id: String): String {
			var u = id.uppercase(Locale.ROOT)
			TIER_PREFIXES.firstOrNull { u.startsWith(it) }?.let { u = u.substring(it.length) }
			val cut = u.lastIndexOf('_')
			if (cut > 0) {
				u = u.substring(0, cut)
			}
			return prettyId(u)
		}

		private fun stars(stack: InventoryDecoder.Stack?): Int {
			if (stack == null) {
				return 0
			}
			val upgrade = NbtAttrs.intValue(stack.extraAttributes, "upgrade_level", 0)
			return if (upgrade > 0) upgrade else NbtAttrs.intValue(stack.extraAttributes, "dungeon_item_level", 0)
		}

		private fun prefer(a: InventoryDecoder.Stack?, b: InventoryDecoder.Stack): InventoryDecoder.Stack {
			if (a == null) {
				return b
			}
			val sa = stars(a) + kuudraTierRank(baseId(a)) * 10
			val sb = stars(b) + kuudraTierRank(baseId(b)) * 10
			return if (sb >= sa) b else a
		}

		private fun baseId(stack: InventoryDecoder.Stack?): String {
			if (stack == null) {
				return ""
			}
			val id = stack.id.uppercase(Locale.ROOT)
			val cut = id.indexOf(';')
			return if (cut >= 0) id.substring(0, cut) else id
		}

		private fun flatten(cats: Map<String, List<InventoryDecoder.Stack?>?>?): List<InventoryDecoder.Stack> {
			val out = ArrayList<InventoryDecoder.Stack>()
			for (list in cats?.values.orEmpty()) {
				for (stack in list.orEmpty()) {
					if (stack != null && stack.id.isNotBlank()) {
						out.add(stack)
					}
				}
			}
			return out
		}

		private fun prettyId(id: String?): String {
			if (id.isNullOrBlank()) {
				return ""
			}
			val sb = StringBuilder()
			for (part in id.replace('-', '_').split("_")) {
				if (part.isBlank()) {
					continue
				}
				if (sb.isNotEmpty()) {
					sb.append(' ')
				}
				sb.append(part[0].uppercaseChar())
				if (part.length > 1) {
					sb.append(part.substring(1).lowercase(Locale.ROOT))
				}
			}
			return sb.toString()
		}

		private fun str(el: JsonElement?): String {
			if (el == null || !el.isJsonPrimitive) {
				return ""
			}
			return try {
				el.asString
			} catch (ignored: RuntimeException) {
				""
			}
		}

		private fun num(el: JsonElement?): Double {
			if (el == null || !el.isJsonPrimitive) {
				return 0.0
			}
			return try {
				el.asDouble
			} catch (ignored: RuntimeException) {
				0.0
			}
		}
	}
}
