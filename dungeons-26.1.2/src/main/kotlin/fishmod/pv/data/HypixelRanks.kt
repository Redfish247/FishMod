/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.util.LegacyChatFormatting
import java.util.Locale
import net.minecraft.ChatFormatting

object HypixelRanks {
	@JvmStatic
	fun nameSpans(name: String?, player: JsonObject?): List<PvTooltip.Span> {
		val safeName = if (name.isNullOrBlank()) "?" else name
		if (player == null) {
			return listOf(PvTooltip.Span.bold(safeName, PvDraw.COLOR_TEXT))
		}
		val prefix = prefixSpans(player)
		val out = ArrayList<PvTooltip.Span>(prefix.size + 1)
		out.addAll(prefix)
		val nameColor = if (prefix.isEmpty()) color(ChatFormatting.GRAY) else prefix.first().color
		out.add(PvTooltip.Span.bold(safeName, nameColor))
		return out
	}

	@JvmStatic
	fun nameColor(player: JsonObject?): Int {
		val prefix = prefixSpans(player)
		return if (prefix.isEmpty()) color(ChatFormatting.GRAY) else prefix.first().color
	}

	@JvmStatic
	fun prefixSpans(player: JsonObject?): List<PvTooltip.Span> {
		if (player == null) {
			return listOf()
		}
		val rank = effectiveRank(player)
		val plusColor = namedColor(player, "rankPlusColor", ChatFormatting.RED)
		val monthlyPlus = namedColor(player, "monthlyRankColor", ChatFormatting.RED)
		return when (rank) {
			"VIP" -> prefixOnly(ChatFormatting.GREEN, "VIP", "", color(ChatFormatting.GREEN))
			"VIP_PLUS" -> prefixOnly(ChatFormatting.GREEN, "VIP", "+", color(ChatFormatting.GOLD))
			"MVP" -> prefixOnly(ChatFormatting.AQUA, "MVP", "", color(ChatFormatting.AQUA))
			"MVP_PLUS" -> prefixOnly(ChatFormatting.AQUA, "MVP", "+", plusColor)
			"SUPERSTAR", "MVP_PLUS_PLUS" -> prefixOnly(ChatFormatting.GOLD, "MVP", "++", monthlyPlus)
			"YOUTUBER" -> listOf(
				PvTooltip.Span.of("[", color(ChatFormatting.RED)),
				PvTooltip.Span.of("YOUTUBE", color(ChatFormatting.WHITE)),
				PvTooltip.Span.of("] ", color(ChatFormatting.RED))
			)
			"ADMIN" -> prefixOnly(ChatFormatting.RED, "ADMIN", "", color(ChatFormatting.RED))
			"GAME_MASTER", "MODERATOR" -> prefixOnly(ChatFormatting.DARK_GREEN, "GM", "", color(ChatFormatting.DARK_GREEN))
			"HELPER" -> prefixOnly(ChatFormatting.BLUE, "HELPER", "", color(ChatFormatting.BLUE))
			else -> listOf()
		}
	}

	private fun prefixOnly(bracketFmt: ChatFormatting, tag: String, plus: String?, plusRgb: Int): List<PvTooltip.Span> {
		val bracket = color(bracketFmt)
		val spans = ArrayList<PvTooltip.Span>(4)
		spans.add(PvTooltip.Span.of("[", bracket))
		spans.add(PvTooltip.Span.of(tag, bracket))
		if (!plus.isNullOrEmpty()) {
			spans.add(PvTooltip.Span.of(plus, plusRgb))
		}
		spans.add(PvTooltip.Span.of("] ", bracket))
		return spans
	}

	private fun effectiveRank(player: JsonObject): String {
		val rank = text(player, "rank", "NONE")
		val monthly = text(player, "monthlyPackageRank", "NONE")
		val packageRank = text(player, "newPackageRank", "NONE")
		if (rank != "YOUTUBER" && monthly != "NONE") {
			return monthly
		}
		if (rank == "NONE" || rank == "NORMAL") {
			return packageRank
		}
		return rank
	}

	private fun namedColor(player: JsonObject, key: String, fallback: ChatFormatting): Int {
		val raw = text(player, key, LegacyChatFormatting.name(fallback))
		val formatting = LegacyChatFormatting.byName(raw)
		if (formatting == null || !LegacyChatFormatting.isColor(formatting)) {
			return color(fallback)
		}
		return color(formatting)
	}

	private fun color(formatting: ChatFormatting): Int {
		val rgb = LegacyChatFormatting.rgb(formatting)
		return if (rgb == null) PvDraw.COLOR_TEXT else (0xFF000000.toInt() or rgb)
	}

	private fun text(obj: JsonObject?, key: String?, fallback: String): String {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull) {
			return fallback
		}
		return try {
			val value: String? = obj.get(key).asString
			if (value.isNullOrBlank()) fallback else value.uppercase(Locale.ROOT)
		} catch (ignored: IllegalStateException) {
			fallback
		} catch (ignored: ClassCastException) {
			fallback
		} catch (ignored: NumberFormatException) {
			fallback
		} catch (ignored: UnsupportedOperationException) {
			fallback
		}
	}
}
