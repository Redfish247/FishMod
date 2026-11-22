/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import com.google.gson.JsonObject
import fishmod.pv.neu.NeuRepoCache
import java.util.Locale
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object NeuItemResolver {
	private val SACK_RUNE_ID = Regex("^RUNE_(.+)_(\\d+)$", RegexOption.IGNORE_CASE)
	private val ITEM_MODEL = Regex("ItemModel\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
	private val DAMAGE_SUFFIX = Regex("-\\d+$")

	@JvmField
	val RARITY_WORD = Regex("(?i)\\b(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|VERY[_ ]SPECIAL|SPECIAL|ULTIMATE)\\b")

	@JvmStatic
	fun neuItem(skyblockId: String?): JsonObject? {
		for (candidate in candidates(skyblockId)) {
			NeuRepoCache.get(candidate)?.let { return it }
		}
		return null
	}

	@JvmStatic
	fun candidates(skyblockId: String?): List<String> {
		val out = ArrayList<String>()
		if (skyblockId.isNullOrBlank()) {
			return out
		}
		val key = skyblockId.uppercase(Locale.ROOT)
		addCandidate(out, key)
		if (key.contains(":")) {
			addCandidate(out, key.replace(':', '-'))
		}
		if ("RUBY_VEILSHROOM" == key) {
			addCandidate(out, "VEILSHROOM")
		}
		SACK_RUNE_ID.matchEntire(key)?.let { rune ->
			addCandidate(out, rune.groupValues[1] + "_RUNE;" + rune.groupValues[2])
		}
		if (key.startsWith("PET_") && !key.startsWith("PET_ITEM_")) {
			val type = key.substring(4)
			if (type.isNotBlank()) {
				for (i in intArrayOf(3, 4, 5, 2, 1, 0)) {
					addCandidate(out, "$type;$i")
				}
				addCandidate(out, type)
			}
		}
		for (color in LegacyMaterialMap.COLOR_NAMES) {
			val prefix = color + "_"
			if (!key.startsWith(prefix)) {
				continue
			}
			val rest = key.substring(prefix.length)
			val damage = LegacyMaterialMap.COLOR_DAMAGE[color] ?: break
			val neuBase = when (rest) {
				"STAINED_GLASS_PANE" -> "STAINED_GLASS_PANE"
				"STAINED_GLASS" -> "STAINED_GLASS"
				"WOOL" -> "WOOL"
				"CARPET" -> "CARPET"
				"TERRACOTTA", "STAINED_HARDENED_CLAY", "STAINED_CLAY" -> "STAINED_CLAY"
				"CONCRETE" -> "CONCRETE"
				"CONCRETE_POWDER" -> "CONCRETE_POWDER"
				else -> null
			}
			if (neuBase != null) {
				addCandidate(out, "$neuBase-$damage")
				if (damage == 0) {
					addCandidate(out, neuBase)
				}
			}
			break
		}
		return out
	}

	private fun addCandidate(out: MutableList<String>, key: String?) {
		if (!key.isNullOrBlank() && !out.contains(key)) {
			out.add(key)
		}
	}

	@JvmStatic
	fun canonicalId(skyblockId: String?): String {
		if (skyblockId == null) {
			return ""
		}
		val key = skyblockId.uppercase(Locale.ROOT)
		val semi = key.indexOf(';')
		return if (semi >= 0) key.substring(0, semi) else key.replace(DAMAGE_SUFFIX, "")
	}

	@JvmStatic
	fun buildFromNeu(key: String): ItemStack? {
		val item = NeuRepoCache.get(key)
		if (item == null) {
			NeuRepoCache.prefetch(listOf(key))
			return null
		}
		val itemId = if (item.has("itemid") && item.get("itemid").isJsonPrimitive) item.get("itemid").asString else ""
		val damage = if (item.has("damage") && item.get("damage").isJsonPrimitive) item.get("damage").asInt else 0
		val nbt = if (item.has("nbttag") && item.get("nbttag").isJsonPrimitive) item.get("nbttag").asString else null
		ItemStackCache.rememberItemModel(key, extract(ITEM_MODEL, nbt))
		return buildFromNeuFields(itemId, damage, nbt)
	}

	@JvmStatic
	fun buildFromNeuFields(itemId: String?, damage: Int, nbt: String?): ItemStack? {
		if (LegacyMaterialMap.isPlayerSkull(itemId, damage)) {
			val skull = ItemStack(Items.PLAYER_HEAD)
			SkullTextureApplier.applyFromNbt(skull, nbt)
			// Never cache / return a bare Steve head - let Hypixel fallback or callers skip.
			return if (SkullTextureApplier.isTexturedPlayerHead(skull)) skull else null
		}
		if (LegacyMaterialMap.isLegacySkull(itemId)) {
			return ItemStack(LegacyMaterialMap.legacySkullByDamage(damage))
		}

		val model = extract(ITEM_MODEL, nbt)
		val resolveId = if (LegacyMaterialMap.isBanner(itemId) || LegacyMaterialMap.isBanner(model)) {
			"minecraft:banner"
		} else if (model != null && model.lowercase(Locale.ROOT).startsWith("minecraft:")) {
			model
		} else {
			itemId
		}
		val stack = ItemStack(LegacyMaterialMap.resolveItemId(resolveId, damage))
		LegacyMaterialMap.applyLeatherColor(stack, nbt)
		return stack
	}

	@JvmStatic
	fun extractItemModel(nbt: String?): String? = extract(ITEM_MODEL, nbt)

	private fun extract(pattern: Regex, nbt: String?): String? {
		if (nbt.isNullOrBlank()) {
			return null
		}
		return pattern.find(nbt)?.groupValues?.get(1)
	}

	@JvmStatic
	fun tierFromNeuItem(neu: JsonObject): String {
		if (neu.has("lore") && neu.get("lore").isJsonArray) {
			val lore = neu.getAsJsonArray("lore")
			for (i in lore.size() - 1 downTo 0) {
				val el = lore.get(i)
				if (!el.isJsonPrimitive) {
					continue
				}
				val plain = ItemTextFormat.stripFormatting(el.asString)
				if (plain.isBlank()) {
					continue
				}
				val m = RARITY_WORD.find(plain)
				if (m != null) {
					return SkyBlockItemFactory.normalizeTier(m.groupValues[1])
				}
			}
		}
		if (neu.has("displayname") && neu.get("displayname").isJsonPrimitive) {
			return ItemTextFormat.tierFromFormattingPrefix(neu.get("displayname").asString)
		}
		return ""
	}
}
