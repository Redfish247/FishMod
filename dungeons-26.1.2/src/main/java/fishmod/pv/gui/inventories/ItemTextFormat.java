/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared § / & formatting helpers for item name and rarity parsing. */
final class ItemTextFormat {
	private ItemTextFormat() {
	}

	static String stripFormatting(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		return text.replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "");
	}

	static String prettyId(String id) {
		if (id == null || id.isBlank()) {
			return "Unknown";
		}
		String[] parts = id.toLowerCase(Locale.ROOT).split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return out.toString();
	}

	static String tierFromFormattingPrefix(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		Matcher code = Pattern.compile("§([0-9a-fk-or])", Pattern.CASE_INSENSITIVE).matcher(text);
		while (code.find()) {
			String tier = switch (Character.toLowerCase(code.group(1).charAt(0))) {
				case 'a' -> "UNCOMMON";
				case '9' -> "RARE";
				case '5' -> "EPIC";
				case '6' -> "LEGENDARY";
				case 'd' -> "MYTHIC";
				case 'b' -> "DIVINE";
				case 'c' -> "SPECIAL";
				case '4' -> "ULTIMATE";
				case 'f', '7' -> "COMMON";
				default -> "";
			};
			if (!tier.isBlank()) {
				return tier;
			}
		}
		return "";
	}

	static String tierColorPrefix(String tier) {
		if (tier == null) {
			return "§f";
		}
		return switch (tier.toUpperCase(Locale.ROOT)) {
			case "COMMON" -> "§f";
			case "UNCOMMON" -> "§a";
			case "RARE" -> "§9";
			case "EPIC" -> "§5";
			case "LEGENDARY" -> "§6";
			case "MYTHIC" -> "§d";
			case "DIVINE" -> "§b";
			case "SPECIAL", "VERY_SPECIAL" -> "§c";
			case "ULTIMATE" -> "§4";
			default -> "§f";
		};
	}
}
