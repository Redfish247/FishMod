/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.pv.gui.SkyBlockSymbols;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Uncached gzip/NBT inventory decode and text flattening. */
final class NbtInventoryCodec {
	private NbtInventoryCodec() {
	}

	static List<InventoryDecoder.Stack> decodeKeepingEmptyUncached(String encoded, int minSlots) throws IOException {
		byte[] bytes = Base64.getDecoder().decode(encoded);
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
			CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
			if (root == null) {
				return List.of();
			}
			Tag itemsTag = root.get("i");
			if (!(itemsTag instanceof ListTag items)) {
				InventoryDecoder.Stack bare = fromBareAttributes(root);
				return bare == null ? List.of() : List.of(bare);
			}
			List<InventoryDecoder.Stack> out = new ArrayList<>();
			int size = Math.max(minSlots, items.size());
			for (int i = 0; i < size; i++) {
				if (i >= items.size()) {
					out.add(null);
					continue;
				}
				Tag child = items.get(i);
				if (!(child instanceof CompoundTag compound) || compound.isEmpty()) {
					out.add(null);
					continue;
				}
				out.add(fromSlot(compound));
			}
			return out;
		}
	}

	static List<InventoryDecoder.Stack> decodeUncached(String encoded) throws IOException {
		byte[] bytes = Base64.getDecoder().decode(encoded);
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
			CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
			if (root == null) {
				return List.of();
			}
			Tag itemsTag = root.get("i");
			if (!(itemsTag instanceof ListTag items)) {
				InventoryDecoder.Stack bare = fromBareAttributes(root);
				return bare == null ? List.of() : List.of(bare);
			}
			List<InventoryDecoder.Stack> out = new ArrayList<>();
			for (int i = 0; i < items.size(); i++) {
				Tag child = items.get(i);
				if (!(child instanceof CompoundTag compound) || compound.isEmpty()) {
					continue;
				}
				InventoryDecoder.Stack stack = fromSlot(compound);
				if (stack != null) {
					out.add(stack);
				}
			}
			return out;
		}
	}

	// Farming and hunting toolkit slots store the item's ExtraAttributes as the root, with no "i" list,
	// display name or lore.
	static InventoryDecoder.Stack fromBareAttributes(CompoundTag root) {
		String id = string(root, "id");
		if (id == null || id.isBlank()) {
			return null;
		}
		return new InventoryDecoder.Stack(id, 1, root, List.of(), false);
	}

	static InventoryDecoder.Stack fromSlot(CompoundTag slot) {
		CompoundTag tag = compound(slot.get("tag"));
		if (tag == null) {
			tag = slot;
		}
		CompoundTag attrs = compound(tag.get("ExtraAttributes"));
		if (attrs == null) {
			attrs = compound(tag.get("extra_attributes"));
		}
		if (attrs == null) {
			return null;
		}
		String id = string(attrs, "id");
		if (id == null) {
			id = string(attrs, "ID");
		}
		if (id == null || id.isBlank()) {
			return null;
		}
		int count = Math.max(1, intOr(slot, "Count", intOr(slot, "count", 1)));
		CompoundTag display = compound(tag.get("display"));
		List<String> lore = new ArrayList<>();
		String displayName = null;
		Integer dyeColor = null;
		if (display != null) {
			displayName = cleanJsonText(tagText(display.get("Name")));
			Tag loreTag = display.get("Lore");
			if (loreTag instanceof ListTag loreList) {
				for (Tag line : loreList) {
					// Preserve blank lore entries as tooltip spacers.
					lore.add(cleanJsonText(tagText(line)));
				}
			}
			if (display.contains("color")) {
				int color = intOr(display, "color", Integer.MIN_VALUE);
				if (color != Integer.MIN_VALUE) {
					dyeColor = color & 0xFFFFFF;
				}
			}
		}
		String skullValue = null;
		String skullSignature = null;
		CompoundTag skullOwner = compound(tag.get("SkullOwner"));
		if (skullOwner == null) {
			skullOwner = compound(tag.get("skull_owner"));
		}
		if (skullOwner != null) {
			CompoundTag props = compound(skullOwner.get("Properties"));
			if (props == null) {
				props = compound(skullOwner.get("properties"));
			}
			Tag textures = props == null ? null : props.get("textures");
			if (textures instanceof ListTag list && !list.isEmpty()) {
				CompoundTag first = compound(list.get(0));
				if (first != null) {
					skullValue = string(first, "Value");
					if (skullValue == null) {
						skullValue = string(first, "value");
					}
					skullSignature = string(first, "Signature");
					if (skullSignature == null) {
						skullSignature = string(first, "signature");
					}
				}
			}
		}
		boolean soulbound = attrs.contains("donated_museum")
			|| lore.stream().anyMatch(line -> line.contains("Soulbound"));
		return new InventoryDecoder.Stack(id, count, attrs, lore, soulbound, displayName, dyeColor, skullValue, skullSignature);
	}

	static String tagText(Tag tag) {
		if (tag == null) {
			return "";
		}
		if (tag instanceof net.minecraft.nbt.StringTag stringTag) {
			try {
				return stringTag.value();
			} catch (Throwable ignored) {
				try {
					return stringTag.toString().replaceAll("^\"|\"$", "");
				} catch (Throwable ignored2) {
				}
			}
		}
		String raw = tag.toString();
		if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
			return unescapeSnbtString(raw.substring(1, raw.length() - 1));
		}
		return raw;
	}

	static String unescapeSnbtString(String value) {
		return value
			.replace("\\\"", "\"")
			.replace("\\n", "\n")
			.replace("\\u00a7", "§")
			.replace("\\u00A7", "§");
	}

	/** Flatten Hypixel JSON text components / quoted SNBT into a §-legacy string. */
	static String cleanJsonText(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String line = raw.trim();
		if (line.startsWith("\"") && line.endsWith("\"") && line.length() >= 2) {
			line = unescapeSnbtString(line.substring(1, line.length() - 1));
		}
		if ((line.startsWith("{") || line.startsWith("[")) && line.contains("text")) {
			try {
				JsonElement element = JsonParser.parseString(line);
				String flattened = flattenJsonText(element, new JsonStyle());
				if (flattened != null && !flattened.isEmpty()) {
					return SkyBlockSymbols.replace(flattened);
				}
			} catch (Exception ignored) {
				// Fall through to the legacy quote scraper below.
			}
			// Fallback: scrape text nodes only (loses obfuscated / bold).
			StringBuilder out = new StringBuilder();
			int idx = 0;
			while (true) {
				int textIdx = line.indexOf("\"text\"", idx);
				if (textIdx < 0) {
					break;
				}
				int colon = line.indexOf(':', textIdx);
				int firstQuote = line.indexOf('"', colon + 1);
				int secondQuote = firstQuote >= 0 ? line.indexOf('"', firstQuote + 1) : -1;
				while (secondQuote > firstQuote && line.charAt(secondQuote - 1) == '\\') {
					secondQuote = line.indexOf('"', secondQuote + 1);
				}
				if (firstQuote >= 0 && secondQuote > firstQuote) {
					out.append(unescapeSnbtString(line.substring(firstQuote + 1, secondQuote)));
				}
				idx = secondQuote > 0 ? secondQuote + 1 : textIdx + 6;
			}
			if (!out.isEmpty()) {
				return SkyBlockSymbols.replace(out.toString());
			}
		}
		return SkyBlockSymbols.replace(line.replace("\\u00a7", "§").replace("\\u00A7", "§"));
	}

	/**
	 * Converts Hypixel / Minecraft JSON text into §-legacy, preserving obfuscated (§k)
	 * used for recombobulator rarity markers.
	 */
	static String flattenJsonText(JsonElement element, JsonStyle parent) {
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonPrimitive()) {
			return element.getAsString();
		}
		if (element.isJsonArray()) {
			StringBuilder out = new StringBuilder();
			for (JsonElement child : element.getAsJsonArray()) {
				out.append(flattenJsonText(child, parent));
			}
			return out.toString();
		}
		if (!element.isJsonObject()) {
			return "";
		}
		JsonObject obj = element.getAsJsonObject();
		JsonStyle style = parent.child(obj);
		StringBuilder out = new StringBuilder();
		if (obj.has("text") && obj.get("text").isJsonPrimitive()) {
			String text = obj.get("text").getAsString();
			if (text != null && !text.isEmpty()) {
				out.append(style.toLegacyPrefix());
				out.append(text);
			}
		}
		if (obj.has("extra") && obj.get("extra").isJsonArray()) {
			for (JsonElement child : obj.getAsJsonArray("extra")) {
				out.append(flattenJsonText(child, style));
			}
		}
		return out.toString();
	}

	static final class JsonStyle {
		private final char color;
		private final boolean bold;
		private final boolean italic;
		private final boolean underlined;
		private final boolean strikethrough;
		private final boolean obfuscated;

		JsonStyle() {
			this(' ', false, false, false, false, false);
		}

		private JsonStyle(
			char color,
			boolean bold,
			boolean italic,
			boolean underlined,
			boolean strikethrough,
			boolean obfuscated
		) {
			this.color = color;
			this.bold = bold;
			this.italic = italic;
			this.underlined = underlined;
			this.strikethrough = strikethrough;
			this.obfuscated = obfuscated;
		}

		JsonStyle child(JsonObject obj) {
			char nextColor = this.color;
			if (obj.has("color") && obj.get("color").isJsonPrimitive()) {
				nextColor = namedColorCode(obj.get("color").getAsString());
			}
			return new JsonStyle(
				nextColor,
				boolOr(obj, "bold", this.bold),
				boolOr(obj, "italic", this.italic),
				boolOr(obj, "underlined", this.underlined),
				boolOr(obj, "strikethrough", this.strikethrough),
				boolOr(obj, "obfuscated", this.obfuscated)
			);
		}

		String toLegacyPrefix() {
			StringBuilder out = new StringBuilder();
			if (this.color != ' ') {
				out.append('§').append(this.color);
			} else {
				out.append("§r");
			}
			if (this.bold) {
				out.append("§l");
			}
			if (this.italic) {
				out.append("§o");
			}
			if (this.underlined) {
				out.append("§n");
			}
			if (this.strikethrough) {
				out.append("§m");
			}
			if (this.obfuscated) {
				out.append("§k");
			}
			return out.toString();
		}

		private static boolean boolOr(JsonObject obj, String key, boolean fallback) {
			if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
				try {
					return obj.get(key).getAsBoolean();
				} catch (Exception ignored) {
					return fallback;
				}
			}
			return fallback;
		}

		private static char namedColorCode(String name) {
			if (name == null || name.isBlank()) {
				return 'f';
			}
			String key = name.trim().toLowerCase(Locale.ROOT);
			if (key.startsWith("#") && key.length() == 7) {
				return nearestLegacyColor(key);
			}
			return switch (key) {
				case "black" -> '0';
				case "dark_blue" -> '1';
				case "dark_green" -> '2';
				case "dark_aqua" -> '3';
				case "dark_red" -> '4';
				case "dark_purple" -> '5';
				case "gold" -> '6';
				case "gray", "grey" -> '7';
				case "dark_gray", "dark_grey" -> '8';
				case "blue" -> '9';
				case "green" -> 'a';
				case "aqua" -> 'b';
				case "red" -> 'c';
				case "light_purple", "pink" -> 'd';
				case "yellow" -> 'e';
				case "white" -> 'f';
				default -> 'f';
			};
		}

		private static char nearestLegacyColor(String hex) {
			try {
				int rgb = Integer.parseInt(hex.substring(1), 16);
				int r = (rgb >> 16) & 0xFF;
				int g = (rgb >> 8) & 0xFF;
				int b = rgb & 0xFF;
				char best = 'f';
				int bestDist = Integer.MAX_VALUE;
				int[][] palette = {
					{'0', 0, 0, 0}, {'1', 0, 0, 170}, {'2', 0, 170, 0}, {'3', 0, 170, 170},
					{'4', 170, 0, 0}, {'5', 170, 0, 170}, {'6', 255, 170, 0}, {'7', 170, 170, 170},
					{'8', 85, 85, 85}, {'9', 85, 85, 255}, {'a', 85, 255, 85}, {'b', 85, 255, 255},
					{'c', 255, 85, 85}, {'d', 255, 85, 255}, {'e', 255, 255, 85}, {'f', 255, 255, 255}
				};
				for (int[] entry : palette) {
					int dr = r - entry[1];
					int dg = g - entry[2];
					int db = b - entry[3];
					int dist = dr * dr + dg * dg + db * db;
					if (dist < bestDist) {
						bestDist = dist;
						best = (char) entry[0];
					}
				}
				return best;
			} catch (Exception ignored) {
				return 'f';
			}
		}
	}

	static CompoundTag compound(Tag tag) {
		return tag instanceof CompoundTag c ? c : null;
	}

	static String string(CompoundTag tag, String key) {
		if (tag == null || !tag.contains(key)) {
			return null;
		}
		try {
			return tag.getStringOr(key, null);
		} catch (Throwable ignored) {
			Tag value = tag.get(key);
			return value == null ? null : value.toString().replaceAll("^\"|\"$", "");
		}
	}

	static int intOr(CompoundTag tag, String key, int fallback) {
		if (tag == null || !tag.contains(key)) {
			return fallback;
		}
		try {
			return tag.getIntOr(key, fallback);
		} catch (Throwable ignored) {
			try {
				return tag.getByteOr(key, (byte) fallback);
			} catch (Throwable ignored2) {
				return fallback;
			}
		}
	}
}
