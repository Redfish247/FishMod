/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import fishmod.pv.price.HypixelItemsCache
import java.util.Base64
import java.util.Locale
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Builds fallback stacks from Hypixel items API definitions when NEU misses. */
object HypixelItemResolver {
	private val WHITESPACE = Regex("\\s+")

	@JvmStatic
	fun buildFromHypixel(key: String?): ItemStack {
		val def = HypixelItemsCache.get(NeuItemResolver.canonicalId(key)) ?: HypixelItemsCache.get(key)
		if (def != null && def.has("item_model") && def.get("item_model").isJsonPrimitive) {
			ItemStackCache.rememberItemModel(key, def.get("item_model").asString)
		}
		val material = if (def != null && def.has("material") && def.get("material").isJsonPrimitive) {
			def.get("material").asString
		} else {
			null
		}
		var durability = 0
		if (def != null && def.has("durability") && def.get("durability").isJsonPrimitive) {
			durability = intOrZero(def.get("durability").asJsonPrimitive)
		} else if (def != null && def.has("damage") && def.get("damage").isJsonPrimitive) {
			durability = intOrZero(def.get("damage").asJsonPrimitive)
		}
		var skinValue = extractHypixelSkinValue(def)
		if (isBrokenHypixelPlaceholderSkin(skinValue)) {
			skinValue = null
		}
		val parsed = LegacyMaterialMap.parseMaterial(material, durability)
		val skullMaterial = parsed != null &&
			(parsed.name == "SKULL_ITEM" || parsed.name == "SKULL" || parsed.name == "PLAYER_HEAD")
		if (skinValue != null) {
			val skull = ItemStack(Items.PLAYER_HEAD)
			// Hypixel item skins often carry invalid signatures; unsigned Value still loads the texture URL.
			SkullTextureApplier.applyValue(skull, skinValue, null)
			return if (SkullTextureApplier.isTexturedPlayerHead(skull)) skull else ItemStack(Items.PAPER)
		}
		if (skullMaterial) {
			// Bare SKULL_ITEM without skin renders as Steve - prefer paper so callers can fall back.
			return ItemStack(Items.PAPER)
		}
		return ItemStack(LegacyMaterialMap.resolveMaterial(material, key, durability))
	}

	private fun intOrZero(value: JsonPrimitive): Int = try {
		if (value.isNumber) value.asInt else value.asString.trim().toInt()
	} catch (ignored: NumberFormatException) {
		0
	}

	@JvmStatic
	fun extractHypixelSkinValue(def: JsonObject?): String? {
		if (def == null || !def.has("skin")) {
			return null
		}
		val skin = def.get("skin")
		if (skin.isJsonPrimitive) {
			return skin.asString?.takeUnless { it.isBlank() }
		}
		if (skin.isJsonObject && skin.asJsonObject.has("value") && skin.asJsonObject.get("value").isJsonPrimitive) {
			return skin.asJsonObject.get("value").asString?.takeUnless { it.isBlank() }
		}
		return null
	}

	@JvmStatic
	fun extractHypixelSkinSignature(def: JsonObject?): String? {
		if (def == null || !def.has("skin") || !def.get("skin").isJsonObject) {
			return null
		}
		val skin = def.getAsJsonObject("skin")
		if (skin.has("signature") && skin.get("signature").isJsonPrimitive) {
			return skin.get("signature").asString?.takeUnless { it.isBlank() }
		}
		return null
	}

	/**
	 * Hypixel's items API reuses one DiscordApp placeholder skin for potion/talisman bags (and similar).
	 * Those heads never look like the real menu icons.
	 */
	@JvmStatic
	fun isBrokenHypixelPlaceholderSkin(skinValue: String?): Boolean {
		if (skinValue.isNullOrBlank()) {
			return false
		}
		return try {
			val json = String(Base64.getDecoder().decode(SkullTextureApplier.padBase64(skinValue.replace(WHITESPACE, ""))))
			json.contains("24bbfd9d84f42456cd02a4baa5cd054bced0ddb2d1c8321c83e5d667cd85575a") || json.contains("DiscordApp")
		} catch (ignored: Exception) {
			false
		}
	}

	@JvmStatic
	fun resolveItemModel(skyblockId: String?): String? {
		val neu = NeuItemResolver.neuItem(skyblockId)
		if (neu != null && neu.has("nbttag") && neu.get("nbttag").isJsonPrimitive) {
			NeuItemResolver.extractItemModel(neu.get("nbttag").asString)?.let { return it }
		}
		val def = HypixelItemsCache.get(NeuItemResolver.canonicalId(skyblockId)) ?: HypixelItemsCache.get(skyblockId)
		if (def != null && def.has("item_model") && def.get("item_model").isJsonPrimitive) {
			return def.get("item_model").asString
		}
		return null
	}

	@JvmStatic
	fun hypixelDisplayName(skyblockId: String?): String? {
		val def = hypixelDef(skyblockId)
		if (def != null && def.has("name") && def.get("name").isJsonPrimitive) {
			val name: String? = def.get("name").asString
			if (!name.isNullOrBlank()) {
				return name
			}
		}
		return null
	}

	@JvmStatic
	fun hypixelTier(skyblockId: String?): String {
		if (skyblockId.isNullOrBlank()) {
			return ""
		}
		val hypixel = HypixelItemsCache.get(skyblockId.uppercase(Locale.ROOT))
			?: HypixelItemsCache.get(NeuItemResolver.canonicalId(skyblockId))
		if (hypixel != null && hypixel.has("tier") && hypixel.get("tier").isJsonPrimitive) {
			return SkyBlockItemFactory.normalizeTier(hypixel.get("tier").asString)
		}
		return ""
	}

	@JvmStatic
	fun hypixelDef(skyblockId: String?): JsonObject? {
		val def = HypixelItemsCache.get(NeuItemResolver.canonicalId(skyblockId))
		if (def == null && skyblockId != null) {
			return HypixelItemsCache.get(skyblockId.uppercase(Locale.ROOT))
		}
		return def
	}
}
