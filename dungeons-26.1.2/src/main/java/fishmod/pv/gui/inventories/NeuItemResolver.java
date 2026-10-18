/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.neu.NeuRepoCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Resolves SkyBlock ids against NEU repo defs and builds base stacks from NEU fields. */
final class NeuItemResolver {
	/** Hypixel sack rune ids: {@code RUNE_ZAP_1} → NEU {@code ZAP_RUNE;1}. */
	private static final Pattern SACK_RUNE_ID = Pattern.compile(
		"^RUNE_(.+)_(\\d+)$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern ITEM_MODEL = Pattern.compile(
		"ItemModel\\s*:\\s*\"([^\"]+)\"",
		Pattern.CASE_INSENSITIVE
	);
	/** Rarity word in NEU lore tails. */
	static final Pattern RARITY_WORD = Pattern.compile(
		"(?i)\\b(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|VERY[_ ]SPECIAL|SPECIAL|ULTIMATE)\\b"
	);

	private NeuItemResolver() {
	}

	static JsonObject neuItem(String skyblockId) {
		for (String candidate : candidates(skyblockId)) {
			JsonObject item = NeuRepoCache.get(candidate);
			if (item != null) {
				return item;
			}
		}
		return null;
	}

	/**
	 * NEU internal names often differ from Hypixel ids
	 * (e.g. {@code RED_STAINED_GLASS_PANE} → {@code STAINED_GLASS_PANE-14}, pets {@code TYPE;4},
	 * sack runes {@code RUNE_ZAP_1} → {@code ZAP_RUNE;1}).
	 */
	static List<String> candidates(String skyblockId) {
		List<String> out = new ArrayList<>();
		if (skyblockId == null || skyblockId.isBlank()) {
			return out;
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		addCandidate(out, key);
		if (key.contains(":")) {
			addCandidate(out, key.replace(':', '-'));
		}
		if ("RUBY_VEILSHROOM".equals(key)) {
			addCandidate(out, "VEILSHROOM");
		}
		Matcher rune = SACK_RUNE_ID.matcher(key);
		if (rune.matches()) {
			addCandidate(out, rune.group(1) + "_RUNE;" + rune.group(2));
		}
		// Cofl / AH pet tags: PET_JELLYFISH → NEU JELLYFISH;3 (not PET_ITEM_*).
		if (key.startsWith("PET_") && !key.startsWith("PET_ITEM_")) {
			String type = key.substring(4);
			if (!type.isBlank()) {
				for (int i : new int[] { 3, 4, 5, 2, 1, 0 }) {
					addCandidate(out, type + ";" + i);
				}
				addCandidate(out, type);
			}
		}
		// COLOUR_STAINED_GLASS_PANE / COLOUR_WOOL / etc. → legacy NEU damage form
		for (String color : LegacyMaterialMap.COLOR_NAMES) {
			String prefix = color + "_";
			if (!key.startsWith(prefix)) {
				continue;
			}
			String rest = key.substring(prefix.length());
			Integer damage = LegacyMaterialMap.COLOR_DAMAGE.get(color);
			if (damage == null) {
				break;
			}
			String neuBase = switch (rest) {
				case "STAINED_GLASS_PANE" -> "STAINED_GLASS_PANE";
				case "STAINED_GLASS" -> "STAINED_GLASS";
				case "WOOL" -> "WOOL";
				case "CARPET" -> "CARPET";
				case "TERRACOTTA", "STAINED_HARDENED_CLAY", "STAINED_CLAY" -> "STAINED_CLAY";
				case "CONCRETE" -> "CONCRETE";
				case "CONCRETE_POWDER" -> "CONCRETE_POWDER";
				default -> null;
			};
			if (neuBase != null) {
				addCandidate(out, neuBase + "-" + damage);
				if (damage == 0) {
					addCandidate(out, neuBase);
				}
			}
			break;
		}
		return out;
	}

	private static void addCandidate(List<String> out, String key) {
		if (key != null && !key.isBlank() && !out.contains(key)) {
			out.add(key);
		}
	}

	static String canonicalId(String skyblockId) {
		if (skyblockId == null) {
			return "";
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		int semi = key.indexOf(';');
		return semi >= 0 ? key.substring(0, semi) : key.replaceAll("-\\d+$", "");
	}

	static ItemStack buildFromNeu(String key) {
		JsonObject item = NeuRepoCache.get(key);
		if (item == null) {
			// Kick off async fetch; next frame/open may resolve.
			NeuRepoCache.prefetch(List.of(key));
			return null;
		}
		String itemId = item.has("itemid") && item.get("itemid").isJsonPrimitive()
			? item.get("itemid").getAsString()
			: "";
		int damage = item.has("damage") && item.get("damage").isJsonPrimitive()
			? item.get("damage").getAsInt()
			: 0;
		String nbt = item.has("nbttag") && item.get("nbttag").isJsonPrimitive()
			? item.get("nbttag").getAsString()
			: null;
		String model = extract(ITEM_MODEL, nbt);
		ItemStackCache.rememberItemModel(key, model);
		return buildFromNeuFields(itemId, damage, nbt);
	}

	static ItemStack buildFromNeuFields(String itemId, int damage, String nbt) {
		if (LegacyMaterialMap.isPlayerSkull(itemId, damage)) {
			ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
			SkullTextureApplier.applyFromNbt(skull, nbt);
			// Never cache / return a bare Steve head - let Hypixel fallback or callers skip.
			return SkullTextureApplier.isTexturedPlayerHead(skull) ? skull : null;
		}
		if (LegacyMaterialMap.isLegacySkull(itemId)) {
			return new ItemStack(LegacyMaterialMap.legacySkullByDamage(damage));
		}

		String model = extract(ITEM_MODEL, nbt);
		String resolveId;
		if (LegacyMaterialMap.isBanner(itemId) || LegacyMaterialMap.isBanner(model)) {
			// NEU often sets ItemModel white_banner while damage carries the real colour (e.g. Totem).
			resolveId = "minecraft:banner";
		} else if (model != null && model.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
			resolveId = model;
		} else {
			resolveId = itemId;
		}
		Item item = LegacyMaterialMap.resolveItemId(resolveId, damage);
		ItemStack stack = new ItemStack(item == null ? Items.PAPER : item);
		LegacyMaterialMap.applyLeatherColor(stack, nbt);
		return stack;
	}

	static String extractItemModel(String nbt) {
		return extract(ITEM_MODEL, nbt);
	}

	private static String extract(Pattern pattern, String nbt) {
		if (nbt == null || nbt.isBlank()) {
			return null;
		}
		Matcher matcher = pattern.matcher(nbt);
		return matcher.find() ? matcher.group(1) : null;
	}

	/** Newer NEU items often omit {@code tier}; rarity lives in the last lore line / name colour. */
	static String tierFromNeuItem(JsonObject neu) {
		if (neu.has("lore") && neu.get("lore").isJsonArray()) {
			JsonArray lore = neu.getAsJsonArray("lore");
			for (int i = lore.size() - 1; i >= 0; i--) {
				JsonElement el = lore.get(i);
				if (!el.isJsonPrimitive()) {
					continue;
				}
				String plain = ItemTextFormat.stripFormatting(el.getAsString());
				if (plain.isBlank()) {
					continue;
				}
				Matcher m = RARITY_WORD.matcher(plain);
				if (m.find()) {
					return SkyBlockItemFactory.normalizeTier(m.group(1));
				}
			}
		}
		if (neu.has("displayname") && neu.get("displayname").isJsonPrimitive()) {
			return ItemTextFormat.tierFromFormattingPrefix(neu.get("displayname").getAsString());
		}
		return "";
	}
}
