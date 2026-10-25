/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

/**
 * Hypixel SkyBlock lore uses Private Use Area glyphs (U+E000+) from their resource-pack font.
 * Without that pack they render as missing boxes - map them to symbols that exist in vanilla fonts.
 * Mapping follows Hypixel/SkyHanni icon ids → classic SkyBlock chat symbols.
 */
public final class SkyBlockSymbols {
	private SkyBlockSymbols() {
	}

	/** Vanilla fallback for a Hypixel SkyBlock PUA stat/item glyph. */
	public static String glyph(int codePoint) {
		return map(codePoint);
	}

	/** Replace Hypixel custom-font codepoints in a §-legacy or plain string. */
	public static String replace(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		boolean hasPua = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= '\uE000' && c <= '\uF8FF') {
				hasPua = true;
				break;
			}
		}
		if (!hasPua) {
			return text;
		}
		// Gem slots reuse the same PUA as stats; prefer diamond/X inside Gemstones: [...] groups.
		String working = replaceGemstoneSlots(text);
		StringBuilder out = new StringBuilder(working.length());
		for (int i = 0; i < working.length(); ) {
			int cp = working.codePointAt(i);
			i += Character.charCount(cp);
			if (cp >= 0xE000 && cp <= 0xF8FF) {
				out.append(map(cp));
			} else {
				out.appendCodePoint(cp);
			}
		}
		return out.toString();
	}

	/**
	 * Inside {@code Gemstones: [..] [..]} lines, filled slots become ◆ and empty unlocked
	 * slots ({@code U+E054}) become ✖. Keeps § colour codes so Hypixel colours still apply.
	 */
	private static String replaceGemstoneSlots(String text) {
		if (!text.contains("Gemstone")) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length());
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '[') {
				int close = text.indexOf(']', i + 1);
				if (close > i) {
					out.append('[');
					out.append(mapGemSlotInner(text.substring(i + 1, close)));
					out.append(']');
					i = close + 1;
					continue;
				}
			}
			out.append(c);
			i++;
		}
		return out.toString();
	}

	private static String mapGemSlotInner(String inner) {
		StringBuilder out = new StringBuilder(inner.length());
		for (int i = 0; i < inner.length(); ) {
			int cp = inner.codePointAt(i);
			i += Character.charCount(cp);
			if (cp >= 0xE000 && cp <= 0xF8FF) {
				out.append(cp == 0xE054 ? "✖" : "◆");
			} else {
				out.appendCodePoint(cp);
			}
		}
		return out.toString();
	}

	private static String map(int codePoint) {
		return switch (codePoint) {
			// Combat / core stats (SkyHanni SkyblockStat)
			case 0xE010 -> "❤"; // Health
			case 0xE011 -> "❣"; // Health Regen
			case 0xE008 -> "❈"; // Defense
			case 0xE027 -> "❂"; // True Defense
			case 0xE00D -> "❁"; // Strength
			case 0xE02C -> "☣"; // Crit Chance
			case 0xE007 -> "☠"; // Crit Damage
			case 0xE001 -> "⚔"; // Attack Speed
			case 0xE00B -> "⫽"; // Ferocity
			case 0xE003 -> "✎"; // Intelligence
			case 0xE002 -> "๑"; // Ability Damage
			case 0xE028 -> "♥"; // Vitality
			case 0xE014 -> "☄"; // Mending
			case 0xE024 -> "Ⓢ"; // Swing Range
			case 0xE050 -> "❁"; // Damage (weapon)

			// Mining / foraging / farming
			case 0xE005 -> "Ⓟ"; // Breaking Power
			case 0xE015 -> "⸕"; // Mining Speed
			case 0xE016 -> "Ｙ"; // Mining Spread
			case 0xE02E -> "⚒"; // Timber
			case 0xE00F -> "❖"; // Gemstone Spread
			case 0xE01C -> "✧"; // Pristine
			case 0xE053 -> "☘"; // Mining Fortune
			case 0xE051 -> "☘"; // Farming Fortune
			case 0xE054 -> "☘"; // Foraging Fortune (empty gem slot handled above)
			case 0xE023 -> "∮"; // Sweep
			case 0xE019 -> "ൠ"; // Bonus Pest Chance
			case 0xE02B -> "✿"; // Overbloom
			case 0xE018 -> "ൠ"; // Pest (lore)
			case 0xE00E -> "⛽"; // Fuel
			case 0xE017 -> "▄"; // Overflow mana

			// Fishing
			case 0xE00C -> "☂"; // Fishing Speed
			case 0xE021 -> "α"; // Sea Creature Chance
			case 0xE009 -> "⚓"; // Double Hook
			case 0xE02A -> "♔"; // Trophy Chance
			case 0xE025 -> "☁"; // Treasure Chance

			// Misc
			case 0xE022 -> "✦"; // Speed
			case 0xE01A -> "✯"; // Magic Find
			case 0xE013 -> "♣"; // Pet Luck
			case 0xE012 -> "♨"; // Heat Resistance
			case 0xE006 -> "❄"; // Cold Resistance
			case 0xE01D -> "🐡"; // Respiration
			case 0xE01B -> "❂"; // Pressure Resistance
			case 0xE00A -> "☠"; // Fear
			case 0xE077 -> "◎"; // Tracking
			case 0xE02D -> "⤴"; // Pull
			case 0xE05B -> "☘"; // Hunter Fortune

			// Rift
			case 0xE020 -> "ф"; // Rift Time
			case 0xE01E -> "❁"; // Rift Damage
			case 0xE004 -> "⚡"; // Mana Regen
			case 0xE01F -> "❤"; // Hearts

			// Mob-type / name prefix icons in lore
			case 0xE085 -> "☠"; // Wither
			case 0xE081 -> "💀"; // Skeletal
			case 0xE078 -> "✦"; // Ender
			case 0xE07E -> "⚡"; // Mythological
			case 0xE068 -> "★"; // Named item / skin marker

			// Common slot / UI marks
			case 0xE000 -> "•";
			case 0xE071, 0xE074, 0xE07C, 0xE07D, 0xE084 -> "•";

			default -> "•";
		};
	}
}
