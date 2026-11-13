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
	@JvmStatic
	fun glyph(codePoint: Int): String = map(codePoint)

	@JvmStatic
	fun replace(text: String): String {
		if (text.isEmpty() || text.none { it in ''..'' }) {
			return text
		}
		return mapPua(replaceGemstoneSlots(text)) { map(it) }
	}

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
		0xE010 -> "❤"
		0xE011 -> "❣"
		0xE008 -> "❈"
		0xE027 -> "❂"
		0xE00D -> "❁"
		0xE02C -> "☣"
		0xE007 -> "☠"
		0xE001 -> "⚔"
		0xE00B -> "⫽"
		0xE003 -> "✎"
		0xE002 -> "๑"
		0xE028 -> "♥"
		0xE014 -> "☄"
		0xE024 -> "Ⓢ"
		0xE050 -> "❁"

		0xE005 -> "Ⓟ"
		0xE015 -> "⸕"
		0xE016 -> "Ｙ"
		0xE02E -> "⚒"
		0xE00F -> "❖"
		0xE01C -> "✧"
		0xE053 -> "☘"
		0xE051 -> "☘"
		0xE054 -> "☘"
		0xE023 -> "∮"
		0xE019 -> "ൠ"
		0xE02B -> "✿"
		0xE018 -> "ൠ"
		0xE00E -> "⛽"
		0xE017 -> "▄" // Overflow mana

		0xE00C -> "☂"
		0xE021 -> "α"
		0xE009 -> "⚓"
		0xE02A -> "♔"
		0xE025 -> "☁"

		0xE022 -> "✦"
		0xE01A -> "✯"
		0xE013 -> "♣"
		0xE012 -> "♨"
		0xE006 -> "❄"
		0xE01D -> "🐡"
		0xE01B -> "❂"
		0xE00A -> "☠"
		0xE077 -> "◎"
		0xE02D -> "⤴"
		0xE05B -> "☘"

		0xE020 -> "ф"
		0xE01E -> "❁"
		0xE004 -> "⚡"
		0xE01F -> "❤"

		// Mob-type / name prefix icons in lore
		0xE085 -> "☠"
		0xE081 -> "💀"
		0xE078 -> "✦"
		0xE07E -> "⚡"
		0xE068 -> "★"

		else -> "•"
	}
}
