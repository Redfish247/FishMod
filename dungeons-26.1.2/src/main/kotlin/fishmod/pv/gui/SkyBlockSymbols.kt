/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

/**
 * Hypixel SkyBlock lore uses Private Use Area glyphs (U+E000+) from their resource-pack font.
 * Without that pack they render as missing boxes - map them to symbols that exist in vanilla fonts.
 * Mapping follows Hypixel/SkyHanni icon ids → classic SkyBlock chat symbols.
 */
object SkyBlockSymbols {
	/** Vanilla fallback for a Hypixel SkyBlock PUA stat/item glyph. */
	@JvmStatic
	fun glyph(codePoint: Int): String = map(codePoint)

	/** Replace Hypixel custom-font codepoints in a §-legacy or plain string. */
	@JvmStatic
	fun replace(text: String): String {
		if (text.isEmpty() || text.none { it in ''..'' }) {
			return text
		}
		// Gem slots reuse the same PUA as stats; prefer diamond/X inside Gemstones: [...] groups.
		return mapPua(replaceGemstoneSlots(text)) { map(it) }
	}

	/**
	 * Inside `Gemstones: [..] [..]` lines, filled slots become ◆ and empty unlocked
	 * slots (`U+E054`) become ✖. Keeps § colour codes so Hypixel colours still apply.
	 */
	private fun replaceGemstoneSlots(text: String): String {
		if (!text.contains("Gemstone")) {
			return text
		}
		val out = StringBuilder(text.length)
		var i = 0
		while (i < text.length) {
			val c = text[i]
			if (c == '[') {
				val close = text.indexOf(']', i + 1)
				if (close > i) {
					out.append('[')
					out.append(mapPua(text.substring(i + 1, close)) { if (it == 0xE054) "✖" else "◆" })
					out.append(']')
					i = close + 1
					continue
				}
			}
			out.append(c)
			i++
		}
		return out.toString()
	}

	private inline fun mapPua(text: String, mapper: (Int) -> String): String {
		val out = StringBuilder(text.length)
		var i = 0
		while (i < text.length) {
			val cp = text.codePointAt(i)
			i += Character.charCount(cp)
			if (cp in 0xE000..0xF8FF) {
				out.append(mapper(cp))
			} else {
				out.appendCodePoint(cp)
			}
		}
		return out.toString()
	}

	private fun map(codePoint: Int): String = when (codePoint) {
		// Combat / core stats (SkyHanni SkyblockStat)
		0xE010 -> "❤" // Health
		0xE011 -> "❣" // Health Regen
		0xE008 -> "❈" // Defense
		0xE027 -> "❂" // True Defense
		0xE00D -> "❁" // Strength
		0xE02C -> "☣" // Crit Chance
		0xE007 -> "☠" // Crit Damage
		0xE001 -> "⚔" // Attack Speed
		0xE00B -> "⫽" // Ferocity
		0xE003 -> "✎" // Intelligence
		0xE002 -> "๑" // Ability Damage
		0xE028 -> "♥" // Vitality
		0xE014 -> "☄" // Mending
		0xE024 -> "Ⓢ" // Swing Range
		0xE050 -> "❁" // Damage (weapon)

		// Mining / foraging / farming
		0xE005 -> "Ⓟ" // Breaking Power
		0xE015 -> "⸕" // Mining Speed
		0xE016 -> "Ｙ" // Mining Spread
		0xE02E -> "⚒" // Timber
		0xE00F -> "❖" // Gemstone Spread
		0xE01C -> "✧" // Pristine
		0xE053 -> "☘" // Mining Fortune
		0xE051 -> "☘" // Farming Fortune
		0xE054 -> "☘" // Foraging Fortune (empty gem slot handled above)
		0xE023 -> "∮" // Sweep
		0xE019 -> "ൠ" // Bonus Pest Chance
		0xE02B -> "✿" // Overbloom
		0xE018 -> "ൠ" // Pest (lore)
		0xE00E -> "⛽" // Fuel
		0xE017 -> "▄" // Overflow mana

		// Fishing
		0xE00C -> "☂" // Fishing Speed
		0xE021 -> "α" // Sea Creature Chance
		0xE009 -> "⚓" // Double Hook
		0xE02A -> "♔" // Trophy Chance
		0xE025 -> "☁" // Treasure Chance

		// Misc
		0xE022 -> "✦" // Speed
		0xE01A -> "✯" // Magic Find
		0xE013 -> "♣" // Pet Luck
		0xE012 -> "♨" // Heat Resistance
		0xE006 -> "❄" // Cold Resistance
		0xE01D -> "🐡" // Respiration
		0xE01B -> "❂" // Pressure Resistance
		0xE00A -> "☠" // Fear
		0xE077 -> "◎" // Tracking
		0xE02D -> "⤴" // Pull
		0xE05B -> "☘" // Hunter Fortune

		// Rift
		0xE020 -> "ф" // Rift Time
		0xE01E -> "❁" // Rift Damage
		0xE004 -> "⚡" // Mana Regen
		0xE01F -> "❤" // Hearts

		// Mob-type / name prefix icons in lore
		0xE085 -> "☠" // Wither
		0xE081 -> "💀" // Skeletal
		0xE078 -> "✦" // Ender
		0xE07E -> "⚡" // Mythological
		0xE068 -> "★" // Named item / skin marker

		// Common slot / UI marks (0xE000, 0xE071, 0xE074, 0xE07C, 0xE07D, 0xE084)
		else -> "•"
	}
}
