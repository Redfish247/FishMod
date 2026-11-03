/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import java.util.Locale

/** Shared § / & formatting helpers for item name and rarity parsing. */
object ItemTextFormat {
	private val FORMAT_CODE = Regex("§([0-9a-fk-or])", RegexOption.IGNORE_CASE)
	private val SECTION_ANY = Regex("§.")
	private val AMP_CODE = Regex("&[0-9a-fk-or]")

	@JvmStatic
	fun stripFormatting(text: String?): String {
		if (text.isNullOrEmpty()) {
			return ""
		}
		return text.replace(SECTION_ANY, "").replace(AMP_CODE, "")
	}

	@JvmStatic
	fun prettyId(id: String?): String {
		if (id.isNullOrBlank()) {
			return "Unknown"
		}
		return id.lowercase(Locale.ROOT).split("_")
			.filter { it.isNotEmpty() }
			.joinToString(" ") { it[0].uppercaseChar() + it.substring(1) }
	}

	@JvmStatic
	fun tierFromFormattingPrefix(text: String?): String {
		if (text.isNullOrBlank()) {
			return ""
		}
		for (code in FORMAT_CODE.findAll(text)) {
			val tier = when (code.groupValues[1][0].lowercaseChar()) {
				'a' -> "UNCOMMON"
				'9' -> "RARE"
				'5' -> "EPIC"
				'6' -> "LEGENDARY"
				'd' -> "MYTHIC"
				'b' -> "DIVINE"
				'c' -> "SPECIAL"
				'4' -> "ULTIMATE"
				'f', '7' -> "COMMON"
				else -> ""
			}
			if (tier.isNotBlank()) {
				return tier
			}
		}
		return ""
	}

	@JvmStatic
	fun tierColorPrefix(tier: String?): String {
		if (tier == null) {
			return "§f"
		}
		return when (tier.uppercase(Locale.ROOT)) {
			"COMMON" -> "§f"
			"UNCOMMON" -> "§a"
			"RARE" -> "§9"
			"EPIC" -> "§5"
			"LEGENDARY" -> "§6"
			"MYTHIC" -> "§d"
			"DIVINE" -> "§b"
			"SPECIAL", "VERY_SPECIAL" -> "§c"
			"ULTIMATE" -> "§4"
			else -> "§f"
		}
	}
}
