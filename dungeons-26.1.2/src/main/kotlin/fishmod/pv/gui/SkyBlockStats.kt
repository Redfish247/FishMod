/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import fishmod.pv.data.InventorySnapshot
import fishmod.pv.networth.InventoryDecoder
import java.util.Locale
import net.minecraft.network.chat.Component

object SkyBlockStats {
	private val STATS: Map<String, StatStyle> = mapOf(
		"health" to StatStyle("❤", "Health", 0xFFFF5555.toInt()),
		"defense" to StatStyle("❈", "Defense", 0xFF55FF55.toInt()),
		"speed" to StatStyle("✦", "Speed", 0xFFFFFFFF.toInt()),
		"walk_speed" to StatStyle("✦", "Speed", 0xFFFFFFFF.toInt()),
		"strength" to StatStyle("❁", "Strength", 0xFFFF5555.toInt()),
		"critical_damage" to StatStyle("☠", "Crit Damage", 0xFF5555FF.toInt()),
		"critical_chance" to StatStyle("☣", "Crit Chance", 0xFF5555FF.toInt()),
		"attack_speed" to StatStyle("⚔", "Attack Speed", 0xFFFFFF55.toInt()),
		"intelligence" to StatStyle("✎", "Intelligence", 0xFF55FFFF.toInt()),
		"ferocity" to StatStyle("⫽", "Ferocity", 0xFFFF5555.toInt()),
		"ability_damage" to StatStyle("๑", "Ability Damage", 0xFFFF5555.toInt()),
		"health_regen" to StatStyle("❣", "Health Regen", 0xFFFF5555.toInt()),
		"vitality" to StatStyle("♨", "Vitality", 0xFFFF5555.toInt()),
		"mending" to StatStyle("☄", "Mending", 0xFFFF5555.toInt()),
		"swing_range" to StatStyle("Ⓢ", "Swing Range", 0xFFFFAA00.toInt()),
		"magic_find" to StatStyle("✯", "Magic Find", 0xFF55FFFF.toInt()),
		"pet_luck" to StatStyle("♣", "Pet Luck", 0xFFFF55FF.toInt()),
		"true_defense" to StatStyle("❂", "True Defense", 0xFFFFFFFF.toInt()),
		"mining_fortune" to StatStyle("☘", "Mining Fortune", 0xFFFFAA00.toInt()),
		"farming_fortune" to StatStyle("☘", "Farming Fortune", 0xFFFFAA00.toInt()),
		"foraging_fortune" to StatStyle("☘", "Foraging Fortune", 0xFFFFAA00.toInt()),
		"timber" to StatStyle(SkyBlockSymbols.glyph(0xE02E), "Timber", 0xFFFFAA00.toInt()),
		"mining_speed" to StatStyle("⸕", "Mining Speed", 0xFFFFAA00.toInt()),
		"mining_spread" to StatStyle(SkyBlockSymbols.glyph(0xE016), "Mining Spread", 0xFFFFAA00.toInt(), true),
		"pristine" to StatStyle("✧", "Pristine", 0xFFAA00AA.toInt()),
		"sea_creature_chance" to StatStyle("α", "Sea Creature Chance", 0xFF00AAAA.toInt()),
		"fishing_speed" to StatStyle("☂", "Fishing Speed", 0xFF55FFFF.toInt())
	)

	private val POWER_COLORS: Map<String, Int> = mapOf(
		"silky" to 0xFF55FF55,
		"hurtful" to 0xFFFF5555,
		"sighted" to 0xFF55FFFF,
		"fortuitous" to 0xFFFFFF55,
		"bloody" to 0xFFFF5555,
		"shaded" to 0xFFAAAAAA,
		"forceful" to 0xFFFF5555,
		"demonic" to 0xFFFF5555,
		"pleasant" to 0xFF55FF55,
		"itchy" to 0xFFFFFF55,
		"adept" to 0xFF55FFFF,
		"sweet" to 0xFFFF55FF,
		"crumbly" to 0xFF00AA00,
		"frozen" to 0xFF55FFFF,
		"healthy" to 0xFFFF5555,
		"scorching" to 0xFFFFAA00,
		"sanguisuge" to 0xFFAA0000,
		"bubba" to 0xFFFF55FF,
		"slender" to 0xFFAAAAAA
	).mapValues { it.value.toInt() }

	@JvmStatic
	fun stat(id: String?): StatStyle =
		id?.let { STATS[it.lowercase(Locale.ROOT)] } ?: StatStyle("•", "Unknown", PvDraw.COLOR_TEXT)

	@JvmStatic
	fun byName(fullName: String?): StatStyle? {
		if (fullName.isNullOrBlank()) {
			return null
		}
		val trimmed = fullName.trim()
		return STATS.values.firstOrNull { it.fullName.equals(trimmed, ignoreCase = true) }
	}

	@JvmStatic
	fun tooltipLines(id: String?, formattedValue: String?): List<PvTooltip.Line> {
		val style = stat(id)
		val value = if (formattedValue.isNullOrBlank()) "-" else formattedValue
		return listOf(
			PvTooltip.Line.text(listOf(
				PvTooltip.Span.of(style.symbol + " ", style.color),
				PvTooltip.Span.bold(style.fullName, style.color)
			)),
			PvTooltip.Line.text(listOf(
				PvTooltip.Span.of(style.symbol + " ", style.color),
				PvTooltip.Span.of(value, style.color)
			))
		)
	}

	@JvmStatic
	fun powerColor(power: String?): Int {
		if (power.isNullOrBlank()) {
			return PvDraw.COLOR_MUTED
		}
		return POWER_COLORS[power.lowercase(Locale.ROOT)] ?: 0xFFFFAA00.toInt()
	}

	@JvmStatic
	fun powerName(power: String?): Component {
		if (power.isNullOrBlank()) {
			return PvDraw.styled("-", PvDraw.COLOR_MUTED, false)
		}
		return PvDraw.styled(InventoryDecoder.prettyWords(power), powerColor(power), true)
	}

	@JvmStatic
	fun loadoutName(name: String?): Component =
		PvDraw.styled(if (name.isNullOrBlank()) "Loadout" else name, 0xFFFFAA00.toInt(), true)

	@JvmStatic
	fun statChip(point: InventorySnapshot.StatPoint): Component {
		val style = stat(point.id)
		return Component.empty()
			.append(PvDraw.styled(style.symbol, style.color, false))
			.append(PvDraw.styled(point.value.toString(), style.color, false))
	}

	@JvmStatic
	@Suppress("UNUSED_PARAMETER")
	fun tuningLine(slot: Int?, stats: List<InventorySnapshot.StatPoint>?): Component = tuningStats(stats)

	@JvmStatic
	fun tuningStats(stats: List<InventorySnapshot.StatPoint>?): Component {
		val out = Component.empty()
		if (stats.isNullOrEmpty()) {
			out.append(PvDraw.styled("-", PvDraw.COLOR_MUTED, false))
			return out
		}
		stats.forEachIndexed { i, point ->
			if (i > 0) {
				out.append(PvDraw.styled(" ", PvDraw.COLOR_MUTED, false))
			}
			out.append(statChip(point))
		}
		return out
	}

	class StatStyle @JvmOverloads constructor(
		symbol: String?,
		fullName: String?,
		@get:JvmName("color") val color: Int,
		@get:JvmName("boldSymbol") val boldSymbol: Boolean = false
	) {
		@get:JvmName("symbol")
		val symbol: String = symbol ?: "•"

		@get:JvmName("fullName")
		val fullName: String = if (fullName.isNullOrBlank()) "Unknown" else fullName
	}
}
