/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonObject;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.util.LegacyChatFormatting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;

/** Formats Hypixel rank-coloured display names for tooltips. */
public final class HypixelRanks {
	private HypixelRanks() {
	}

	public static List<PvTooltip.Span> nameSpans(String name, JsonObject player) {
		String safeName = name == null || name.isBlank() ? "?" : name;
		if (player == null) {
			return List.of(PvTooltip.Span.bold(safeName, PvDraw.COLOR_TEXT));
		}
		List<PvTooltip.Span> prefix = prefixSpans(player);
		List<PvTooltip.Span> out = new ArrayList<>(prefix.size() + 1);
		out.addAll(prefix);
		int nameColor = prefix.isEmpty() ? color(ChatFormatting.GRAY) : prefix.getFirst().color();
		out.add(PvTooltip.Span.bold(safeName, nameColor));
		return out;
	}

	/** Name colour that goes with the rank bracket, grey for no rank. Same rule as {@link #nameSpans}. */
	public static int nameColor(JsonObject player) {
		List<PvTooltip.Span> prefix = prefixSpans(player);
		return prefix.isEmpty() ? color(ChatFormatting.GRAY) : prefix.getFirst().color();
	}

	/** Rank bracket spans only (no player name), e.g. {@code [MVP+] }. Empty for default/none. */
	public static List<PvTooltip.Span> prefixSpans(JsonObject player) {
		if (player == null) {
			return List.of();
		}
		String rank = effectiveRank(player);
		int plusColor = namedColor(player, "rankPlusColor", ChatFormatting.RED);
		int monthlyPlus = namedColor(player, "monthlyRankColor", ChatFormatting.RED);
		return switch (rank) {
			case "VIP" -> prefixOnly(ChatFormatting.GREEN, "VIP", "", color(ChatFormatting.GREEN));
			case "VIP_PLUS" -> prefixOnly(ChatFormatting.GREEN, "VIP", "+", color(ChatFormatting.GOLD));
			case "MVP" -> prefixOnly(ChatFormatting.AQUA, "MVP", "", color(ChatFormatting.AQUA));
			case "MVP_PLUS" -> prefixOnly(ChatFormatting.AQUA, "MVP", "+", plusColor);
			case "SUPERSTAR", "MVP_PLUS_PLUS" -> prefixOnly(ChatFormatting.GOLD, "MVP", "++", monthlyPlus);
			case "YOUTUBER" -> List.of(
				PvTooltip.Span.of("[", color(ChatFormatting.RED)),
				PvTooltip.Span.of("YOUTUBE", color(ChatFormatting.WHITE)),
				PvTooltip.Span.of("] ", color(ChatFormatting.RED))
			);
			case "ADMIN" -> prefixOnly(ChatFormatting.RED, "ADMIN", "", color(ChatFormatting.RED));
			case "GAME_MASTER", "MODERATOR" -> prefixOnly(ChatFormatting.DARK_GREEN, "GM", "", color(ChatFormatting.DARK_GREEN));
			case "HELPER" -> prefixOnly(ChatFormatting.BLUE, "HELPER", "", color(ChatFormatting.BLUE));
			default -> List.of();
		};
	}

	private static List<PvTooltip.Span> prefixOnly(
		ChatFormatting bracketFmt,
		String tag,
		String plus,
		int plusRgb
	) {
		int bracket = color(bracketFmt);
		List<PvTooltip.Span> spans = new ArrayList<>(4);
		spans.add(PvTooltip.Span.of("[", bracket));
		spans.add(PvTooltip.Span.of(tag, bracket));
		if (plus != null && !plus.isEmpty()) {
			spans.add(PvTooltip.Span.of(plus, plusRgb));
		}
		spans.add(PvTooltip.Span.of("] ", bracket));
		return spans;
	}

	private static String effectiveRank(JsonObject player) {
		String rank = text(player, "rank", "NONE");
		String monthly = text(player, "monthlyPackageRank", "NONE");
		String packageRank = text(player, "newPackageRank", "NONE");
		if (!"YOUTUBER".equals(rank) && !"NONE".equals(monthly)) {
			return monthly;
		}
		if ("NONE".equals(rank) || "NORMAL".equals(rank)) {
			return packageRank;
		}
		return rank;
	}

	private static int namedColor(JsonObject player, String key, ChatFormatting fallback) {
		String raw = text(player, key, LegacyChatFormatting.name(fallback));
		ChatFormatting formatting = LegacyChatFormatting.byName(raw);
		if (formatting == null || !LegacyChatFormatting.isColor(formatting)) {
			return color(fallback);
		}
		return color(formatting);
	}

	private static int color(ChatFormatting formatting) {
		Integer rgb = LegacyChatFormatting.rgb(formatting);
		return rgb == null ? PvDraw.COLOR_TEXT : 0xFF000000 | rgb;
	}

	private static String text(JsonObject obj, String key, String fallback) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			String value = obj.get(key).getAsString();
			return value == null || value.isBlank() ? fallback : value.toUpperCase(Locale.ROOT);
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return fallback;
		}
	}
}
