/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fishmod.pv.price.HypixelItemsCache;
import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Builds fallback stacks from Hypixel items API definitions when NEU misses. */
final class HypixelItemResolver {
	private HypixelItemResolver() {
	}

	static ItemStack buildFromHypixel(String key) {
		JsonObject def = HypixelItemsCache.get(NeuItemResolver.canonicalId(key));
		if (def == null) {
			def = HypixelItemsCache.get(key);
		}
		if (def != null && def.has("item_model") && def.get("item_model").isJsonPrimitive()) {
			ItemStackCache.rememberItemModel(key, def.get("item_model").getAsString());
		}
		String material = def != null && def.has("material") && def.get("material").isJsonPrimitive()
			? def.get("material").getAsString()
			: null;
		int durability = 0;
		if (def != null && def.has("durability") && def.get("durability").isJsonPrimitive()) {
			durability = intOrZero(def.get("durability").getAsJsonPrimitive());
		} else if (def != null && def.has("damage") && def.get("damage").isJsonPrimitive()) {
			durability = intOrZero(def.get("damage").getAsJsonPrimitive());
		}
		String skinValue = extractHypixelSkinValue(def);
		String skinSignature = extractHypixelSkinSignature(def);
		if (isBrokenHypixelPlaceholderSkin(skinValue)) {
			skinValue = null;
			skinSignature = null;
		}
		LegacyMaterialMap.Material parsed = LegacyMaterialMap.parseMaterial(material, durability);
		boolean skullMaterial = parsed != null && (
			parsed.name().equals("SKULL_ITEM")
				|| parsed.name().equals("SKULL")
				|| parsed.name().equals("PLAYER_HEAD")
		);
		if (skinValue != null) {
			ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
			// Hypixel item skins often carry invalid signatures; unsigned Value still loads the texture URL.
			SkullTextureApplier.applyValue(skull, skinValue, null);
			return SkullTextureApplier.isTexturedPlayerHead(skull) ? skull : new ItemStack(Items.PAPER);
		}
		if (skullMaterial) {
			// Bare SKULL_ITEM without skin renders as Steve - prefer paper so callers can fall back.
			return new ItemStack(Items.PAPER);
		}
		Item item = LegacyMaterialMap.resolveMaterial(material, key, durability);
		return new ItemStack(item);
	}

	private static int intOrZero(JsonPrimitive value) {
		try {
			return value.isNumber() ? value.getAsInt() : Integer.parseInt(value.getAsString().trim());
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	static String extractHypixelSkinValue(JsonObject def) {
		if (def == null || !def.has("skin")) {
			return null;
		}
		var skin = def.get("skin");
		if (skin.isJsonPrimitive()) {
			String value = skin.getAsString();
			return value == null || value.isBlank() ? null : value;
		}
		if (skin.isJsonObject() && skin.getAsJsonObject().has("value")
			&& skin.getAsJsonObject().get("value").isJsonPrimitive()) {
			String value = skin.getAsJsonObject().get("value").getAsString();
			return value == null || value.isBlank() ? null : value;
		}
		return null;
	}

	static String extractHypixelSkinSignature(JsonObject def) {
		if (def == null || !def.has("skin") || !def.get("skin").isJsonObject()) {
			return null;
		}
		var skin = def.getAsJsonObject("skin");
		if (skin.has("signature") && skin.get("signature").isJsonPrimitive()) {
			String signature = skin.get("signature").getAsString();
			return signature == null || signature.isBlank() ? null : signature;
		}
		return null;
	}

	/**
	 * Hypixel's items API reuses one DiscordApp placeholder skin for potion/talisman bags (and similar).
	 * Those heads never look like the real menu icons.
	 */
	static boolean isBrokenHypixelPlaceholderSkin(String skinValue) {
		if (skinValue == null || skinValue.isBlank()) {
			return false;
		}
		try {
			String json = new String(java.util.Base64.getDecoder().decode(
				SkullTextureApplier.padBase64(skinValue.replaceAll("\\s+", ""))
			));
			return json.contains("24bbfd9d84f42456cd02a4baa5cd054bced0ddb2d1c8321c83e5d667cd85575a")
				|| json.contains("DiscordApp");
		} catch (Exception ignored) {
			return false;
		}
	}

	static String resolveItemModel(String skyblockId) {
		JsonObject neu = NeuItemResolver.neuItem(skyblockId);
		if (neu != null && neu.has("nbttag") && neu.get("nbttag").isJsonPrimitive()) {
			String model = NeuItemResolver.extractItemModel(neu.get("nbttag").getAsString());
			if (model != null) {
				return model;
			}
		}
		JsonObject def = HypixelItemsCache.get(NeuItemResolver.canonicalId(skyblockId));
		if (def == null) {
			def = HypixelItemsCache.get(skyblockId);
		}
		if (def != null && def.has("item_model") && def.get("item_model").isJsonPrimitive()) {
			return def.get("item_model").getAsString();
		}
		return null;
	}

	static String hypixelDisplayName(String skyblockId) {
		JsonObject def = HypixelItemsCache.get(NeuItemResolver.canonicalId(skyblockId));
		if (def == null && skyblockId != null) {
			def = HypixelItemsCache.get(skyblockId.toUpperCase(Locale.ROOT));
		}
		if (def != null && def.has("name") && def.get("name").isJsonPrimitive()) {
			String name = def.get("name").getAsString();
			if (name != null && !name.isBlank()) {
				return name;
			}
		}
		return null;
	}

	static String hypixelTier(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return "";
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		JsonObject hypixel = HypixelItemsCache.get(key);
		if (hypixel == null) {
			hypixel = HypixelItemsCache.get(NeuItemResolver.canonicalId(skyblockId));
		}
		if (hypixel != null && hypixel.has("tier") && hypixel.get("tier").isJsonPrimitive()) {
			return SkyBlockItemFactory.normalizeTier(hypixel.get("tier").getAsString());
		}
		return "";
	}

	static JsonObject hypixelDef(String skyblockId) {
		JsonObject def = HypixelItemsCache.get(NeuItemResolver.canonicalId(skyblockId));
		if (def == null && skyblockId != null) {
			def = HypixelItemsCache.get(skyblockId.toUpperCase(Locale.ROOT));
		}
		return def;
	}
}
