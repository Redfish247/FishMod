/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import java.util.Locale
import java.util.Optional
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor

/** Legacy 1.8 material / damage remaps used when building stacks from NEU or Hypixel defs. */
object LegacyMaterialMap {
	private val VALID_PATH = Regex("[a-z0-9/._-]+")
	private val LEGACY_DATA = Regex("\\d{1,5}")
	private val LEATHER_COLOR = Regex("color\\s*:\\s*(\\d+)", RegexOption.IGNORE_CASE)
	private val DYE_COLORS = arrayOf(
		"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	)

	@JvmField
	val COLOR_NAMES = arrayOf(
		"LIGHT_BLUE", "LIGHT_GRAY", "WHITE", "ORANGE", "MAGENTA", "YELLOW", "LIME", "PINK",
		"GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
	)

	@JvmField
	val COLOR_DAMAGE: Map<String, Int> = mapOf(
		"WHITE" to 0,
		"ORANGE" to 1,
		"MAGENTA" to 2,
		"LIGHT_BLUE" to 3,
		"YELLOW" to 4,
		"LIME" to 5,
		"PINK" to 6,
		"GRAY" to 7,
		"LIGHT_GRAY" to 8,
		"CYAN" to 9,
		"PURPLE" to 10,
		"BLUE" to 11,
		"BROWN" to 12,
		"GREEN" to 13,
		"RED" to 14,
		"BLACK" to 15
	)

	@JvmStatic
	fun isBanner(itemId: String?): Boolean {
		if (itemId.isNullOrBlank()) {
			return false
		}
		val id = itemId.lowercase(Locale.ROOT).replace("minecraft:", "")
		return id == "banner" || id.endsWith("_banner")
	}

	@JvmStatic
	fun isPlayerSkull(itemId: String?, damage: Int): Boolean {
		if (itemId == null) {
			return false
		}
		val id = itemId.lowercase(Locale.ROOT).replace("minecraft:", "")
		if (id == "player_head") {
			return true
		}
		return (id == "skull" || id == "skull_item") && damage == 3
	}

	@JvmStatic
	fun isLegacySkull(itemId: String?): Boolean {
		if (itemId == null) {
			return false
		}
		val id = itemId.lowercase(Locale.ROOT).replace("minecraft:", "")
		return id == "skull" || id == "skull_item" || id == "player_head"
	}

	@JvmStatic
	fun legacySkullByDamage(damage: Int): Item = when (damage) {
		0 -> Items.SKELETON_SKULL
		1 -> Items.WITHER_SKELETON_SKULL
		2 -> Items.ZOMBIE_HEAD
		4 -> Items.CREEPER_HEAD
		5 -> Items.DRAGON_HEAD
		else -> Items.PLAYER_HEAD
	}

	@JvmStatic
	fun isLeather(item: Item): Boolean =
		item === Items.LEATHER_HELMET ||
			item === Items.LEATHER_CHESTPLATE ||
			item === Items.LEATHER_LEGGINGS ||
			item === Items.LEATHER_BOOTS ||
			item === Items.LEATHER_HORSE_ARMOR

	@JvmStatic
	fun extractLeatherColor(nbt: String?): Int? {
		if (nbt.isNullOrBlank()) {
			return null
		}
		val match = LEATHER_COLOR.find(nbt) ?: return null
		return match.groupValues[1].toIntOrNull()
	}

	@JvmStatic
	fun applyLeatherColor(stack: ItemStack, nbt: String?) {
		val leather = extractLeatherColor(nbt)
		if (leather != null && isLeather(stack.item)) {
			stack.set(DataComponents.DYED_COLOR, DyedItemColor(leather))
		}
	}

	@JvmStatic
	fun resolveItemId(itemId: String?, damage: Int): Item {
		if (itemId.isNullOrBlank()) {
			return Items.PAPER
		}
		var normalized = itemId.lowercase(Locale.ROOT).replace(' ', '_')
		if (!normalized.contains(":")) {
			normalized = "minecraft:$normalized"
		}
		normalized = remapLegacy(normalized, damage)
		return try {
			BuiltInRegistries.ITEM.getOptional(Identifier.parse(normalized)).orElse(Items.PAPER)
		} catch (exception: Exception) {
			Items.PAPER
		}
	}

	@JvmStatic
	fun remapLegacy(id: String, damage: Int): String = when (id) {
		"minecraft:skull" -> "minecraft:player_head"
		"minecraft:dye", "minecraft:ink_sack" -> dyeByDamage(damage)
		"minecraft:potato_item" -> "minecraft:potato"
		"minecraft:carrot_item" -> "minecraft:carrot"
		"minecraft:melon" -> "minecraft:melon_slice"
		"minecraft:reeds" -> "minecraft:sugar_cane"
		"minecraft:nether_stalk" -> "minecraft:nether_wart"
		"minecraft:double_plant" -> when (damage) {
			0 -> "minecraft:sunflower"
			1 -> "minecraft:lilac"
			2 -> "minecraft:tall_grass"
			3 -> "minecraft:large_fern"
			4 -> "minecraft:rose_bush"
			5 -> "minecraft:peony"
			else -> "minecraft:sunflower"
		}
		"minecraft:red_flower" -> if (damage == 1) "minecraft:blue_orchid" else "minecraft:poppy"
		"minecraft:banner", "minecraft:white_banner" -> colorPrefixed("banner", damage)
		"minecraft:stained_glass_pane" -> colorPrefixed("stained_glass_pane", damage)
		"minecraft:stained_glass" -> colorPrefixed("stained_glass", damage)
		"minecraft:wool" -> colorPrefixed("wool", damage)
		"minecraft:carpet" -> colorPrefixed("carpet", damage)
		"minecraft:stained_hardened_clay", "minecraft:stained_terracotta" -> colorPrefixed("terracotta", damage)
		else -> id
	}

	@JvmStatic
	fun colorPrefixed(suffix: String, damage: Int): String {
		val idx = maxOf(0, minOf(DYE_COLORS.size - 1, damage))
		return "minecraft:" + DYE_COLORS[idx] + "_" + suffix
	}

	@JvmStatic
	fun dyeByDamage(damage: Int): String = when (damage) {
		1 -> "minecraft:red_dye"
		2 -> "minecraft:green_dye"
		3 -> "minecraft:cocoa_beans"
		4 -> "minecraft:lapis_lazuli"
		5 -> "minecraft:purple_dye"
		6 -> "minecraft:cyan_dye"
		7 -> "minecraft:light_gray_dye"
		8 -> "minecraft:gray_dye"
		9 -> "minecraft:pink_dye"
		10 -> "minecraft:lime_dye"
		11 -> "minecraft:yellow_dye"
		12 -> "minecraft:light_blue_dye"
		13 -> "minecraft:magenta_dye"
		14 -> "minecraft:orange_dye"
		15 -> "minecraft:bone_meal"
		else -> "minecraft:ink_sac"
	}

	/** Hypixel item def material: legacy Bukkit name plus damage value. */
	@JvmRecord
	data class Material(val name: String, val damage: Int)

	/**
	 * Parses a Hypixel item def material. Accepts `NAME` (damage from `durability`),
	 * Bukkit `NAME:data` and `minecraft:name`. Anything else is not a material:
	 * NEU's `TYPE;N` is a pet rarity / item level, not damage. Returns null when unusable.
	 */
	@JvmStatic
	fun parseMaterial(raw: String?, durability: Int): Material? {
		if (raw.isNullOrBlank()) {
			return null
		}
		var name = raw.trim().lowercase(Locale.ROOT).replace(' ', '_')
		if (name.startsWith("minecraft:")) {
			name = name.substring("minecraft:".length)
		}
		var damage = durability
		val colon = name.indexOf(':')
		if (colon >= 0) {
			val data = name.substring(colon + 1)
			if (!LEGACY_DATA.matches(data)) {
				return null
			}
			damage = data.toInt()
			name = name.substring(0, colon)
		}
		if (!VALID_PATH.matches(name)) {
			return null
		}
		return Material(name.uppercase(Locale.ROOT), damage)
	}

	/** Vanilla item lookup that never throws on bad ids. Accepts `name` or `minecraft:name`. */
	@JvmStatic
	fun vanillaItem(id: String?): Optional<Item> {
		if (id == null) {
			return Optional.empty()
		}
		var path = id.lowercase(Locale.ROOT)
		if (path.startsWith("minecraft:")) {
			path = path.substring("minecraft:".length)
		}
		if (!VALID_PATH.matches(path)) {
			return Optional.empty()
		}
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("minecraft", path))
	}

	@JvmStatic
	fun resolveMaterial(material: String?, skyblockId: String?, durability: Int): Item {
		val parsed = parseMaterial(material, durability)
		if (parsed != null) {
			val upper = parsed.name
			val damage = parsed.damage
			// Legacy coloured blocks used material + durability (meta).
			val remapped = when (upper) {
				"STAINED_GLASS_PANE" -> colorPrefixed("stained_glass_pane", damage)
				"STAINED_GLASS" -> colorPrefixed("stained_glass", damage)
				"WOOL" -> colorPrefixed("wool", damage)
				"CARPET" -> colorPrefixed("carpet", damage)
				"STAINED_CLAY", "STAINED_HARDENED_CLAY", "HARDENED_CLAY" -> colorPrefixed("terracotta", damage)
				"CONCRETE" -> colorPrefixed("concrete", damage)
				"CONCRETE_POWDER" -> colorPrefixed("concrete_powder", damage)
				"INK_SACK", "DYE" -> dyeByDamage(damage)
				else -> null
			}
			if (remapped != null) {
				val colored = vanillaItem(remapped)
				if (colored.isPresent) {
					return colored.get()
				}
			}
			legacyMaterial(upper)?.let { return it }
			val item = vanillaItem(upper)
			if (item.isPresent) {
				return item.get()
			}
		}
		// Modern Hypixel ids like RED_STAINED_GLASS_PANE. Drop NEU's ;N / -N suffix first.
		val upperId = NeuItemResolver.canonicalId(skyblockId)
		for (color in COLOR_NAMES) {
			if (upperId.startsWith(color + "_")) {
				val direct = vanillaItem(upperId)
				if (direct.isPresent) {
					return direct.get()
				}
				break
			}
		}
		return guessFromId(skyblockId)
	}

	private fun legacyMaterial(material: String): Item? = when (material.uppercase(Locale.ROOT)) {
		"SKULL_ITEM", "SKULL" -> Items.PLAYER_HEAD
		"INK_SACK" -> Items.INK_SAC
		"RAW_FISH" -> Items.COD
		"COOKED_FISH" -> Items.COOKED_COD
		"WATCH" -> Items.CLOCK
		"EMPTY_MAP" -> Items.MAP
		"BOOK_AND_QUILL" -> Items.WRITABLE_BOOK
		"FIREBALL" -> Items.FIRE_CHARGE
		"SPECKLED_MELON" -> Items.GLISTERING_MELON_SLICE
		"SULPHUR" -> Items.GUNPOWDER
		"NETHER_STALK" -> Items.NETHER_WART
		"WATER_LILY" -> Items.LILY_PAD
		"CARROT_ITEM" -> Items.CARROT
		"POTATO_ITEM" -> Items.POTATO
		"GRILLED_PORK" -> Items.COOKED_PORKCHOP
		"PORK" -> Items.PORKCHOP
		"EXP_BOTTLE" -> Items.EXPERIENCE_BOTTLE
		"FIREWORK" -> Items.FIREWORK_ROCKET
		"RED_ROSE" -> Items.POPPY
		"YELLOW_FLOWER" -> Items.DANDELION
		"WEB" -> Items.COBWEB
		"LEATHER_HELMET" -> Items.LEATHER_HELMET
		"LEATHER_CHESTPLATE" -> Items.LEATHER_CHESTPLATE
		"LEATHER_LEGGINGS" -> Items.LEATHER_LEGGINGS
		"LEATHER_BOOTS" -> Items.LEATHER_BOOTS
		else -> null
	}

	@JvmStatic
	fun guessFromId(id: String?): Item {
		if (id == null) {
			return Items.PAPER
		}
		val upper = id.uppercase(Locale.ROOT)
		return when {
			upper.contains("SWORD") -> Items.DIAMOND_SWORD
			upper.contains("BOW") -> Items.BOW
			upper.contains("PICKAXE") -> Items.DIAMOND_PICKAXE
			upper.contains("AXE") -> Items.DIAMOND_AXE
			upper.contains("HOE") -> Items.DIAMOND_HOE
			upper.contains("SHOVEL") -> Items.DIAMOND_SHOVEL
			upper.contains("HELMET") || upper.contains("HOOD") -> Items.DIAMOND_HELMET
			upper.contains("CHESTPLATE") || upper.contains("TUNIC") -> Items.DIAMOND_CHESTPLATE
			upper.contains("LEGGINGS") || upper.contains("PANTS") -> Items.DIAMOND_LEGGINGS
			upper.contains("BOOTS") -> Items.DIAMOND_BOOTS
			upper.contains("ARROW") -> Items.ARROW
			upper.contains("POTION") -> Items.POTION
			upper.contains("FISH") || upper.contains("SEA") -> Items.COD
			upper.contains("SACK") -> Items.BUNDLE
			else -> Items.PAPER
		}
	}
}
