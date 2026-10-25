/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.networth.InventoryDecoder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class SkyBlockStats {
	private static final Map<String, StatStyle> STATS = Map.ofEntries(
		Map.entry("health", new StatStyle("❤", "Health", 0xFFFF5555)),
		Map.entry("defense", new StatStyle("❈", "Defense", 0xFF55FF55)),
		Map.entry("speed", new StatStyle("✦", "Speed", 0xFFFFFFFF)),
		Map.entry("walk_speed", new StatStyle("✦", "Speed", 0xFFFFFFFF)),
		Map.entry("strength", new StatStyle("❁", "Strength", 0xFFFF5555)),
		Map.entry("critical_damage", new StatStyle("☠", "Crit Damage", 0xFF5555FF)),
		Map.entry("critical_chance", new StatStyle("☣", "Crit Chance", 0xFF5555FF)),
		Map.entry("attack_speed", new StatStyle("⚔", "Attack Speed", 0xFFFFFF55)),
		Map.entry("intelligence", new StatStyle("✎", "Intelligence", 0xFF55FFFF)),
		Map.entry("ferocity", new StatStyle("⫽", "Ferocity", 0xFFFF5555)),
		Map.entry("ability_damage", new StatStyle("๑", "Ability Damage", 0xFFFF5555)),
		Map.entry("health_regen", new StatStyle("❣", "Health Regen", 0xFFFF5555)),
		Map.entry("vitality", new StatStyle("♨", "Vitality", 0xFFFF5555)),
		Map.entry("mending", new StatStyle("☄", "Mending", 0xFFFF5555)),
		Map.entry("swing_range", new StatStyle("Ⓢ", "Swing Range", 0xFFFFAA00)),
		Map.entry("magic_find", new StatStyle("✯", "Magic Find", 0xFF55FFFF)),
		Map.entry("pet_luck", new StatStyle("♣", "Pet Luck", 0xFFFF55FF)),
		Map.entry("true_defense", new StatStyle("❂", "True Defense", 0xFFFFFFFF)),
		Map.entry("mining_fortune", new StatStyle("☘", "Mining Fortune", 0xFFFFAA00)),
		Map.entry("farming_fortune", new StatStyle("☘", "Farming Fortune", 0xFFFFAA00)),
		Map.entry("foraging_fortune", new StatStyle("☘", "Foraging Fortune", 0xFFFFAA00)),
		Map.entry("timber", new StatStyle(SkyBlockSymbols.glyph(0xE02E), "Timber", 0xFFFFAA00)),
		Map.entry("mining_speed", new StatStyle("⸕", "Mining Speed", 0xFFFFAA00)),
		Map.entry("mining_spread", new StatStyle(SkyBlockSymbols.glyph(0xE016), "Mining Spread", 0xFFFFAA00, true)),
		Map.entry("pristine", new StatStyle("✧", "Pristine", 0xFFAA00AA)),
		Map.entry("sea_creature_chance", new StatStyle("α", "Sea Creature Chance", 0xFF00AAAA)),
		Map.entry("fishing_speed", new StatStyle("☂", "Fishing Speed", 0xFF55FFFF))
	);

	/** Rough Hypixel/Maxwell power name colours. */
	private static final Map<String, Integer> POWER_COLORS = Map.ofEntries(
		Map.entry("silky", 0xFF55FF55),
		Map.entry("hurtful", 0xFFFF5555),
		Map.entry("sighted", 0xFF55FFFF),
		Map.entry("fortuitous", 0xFFFFFF55),
		Map.entry("bloody", 0xFFFF5555),
		Map.entry("shaded", 0xFFAAAAAA),
		Map.entry("forceful", 0xFFFF5555),
		Map.entry("demonic", 0xFFFF5555),
		Map.entry("pleasant", 0xFF55FF55),
		Map.entry("itchy", 0xFFFFFF55),
		Map.entry("adept", 0xFF55FFFF),
		Map.entry("sweet", 0xFFFF55FF),
		Map.entry("crumbly", 0xFF00AA00),
		Map.entry("frozen", 0xFF55FFFF),
		Map.entry("healthy", 0xFFFF5555),
		Map.entry("scorching", 0xFFFFAA00),
		Map.entry("sanguisuge", 0xFFAA0000),
		Map.entry("bubba", 0xFFFF55FF),
		Map.entry("slender", 0xFFAAAAAA)
	);

	private SkyBlockStats() {
	}

	public static StatStyle stat(String id) {
		if (id == null) {
			return new StatStyle("•", "Unknown", PvDraw.COLOR_TEXT);
		}
		return STATS.getOrDefault(id.toLowerCase(Locale.ROOT), new StatStyle("•", "Unknown", PvDraw.COLOR_TEXT));
	}

	/** Style for a display name like "Mining Fortune", or null when it isn't a known stat. */
	public static StatStyle byName(String fullName) {
		if (fullName == null || fullName.isBlank()) {
			return null;
		}
		for (StatStyle style : STATS.values()) {
			if (style.fullName().equalsIgnoreCase(fullName.trim())) {
				return style;
			}
		}
		return null;
	}

	/** Hover tip: coloured full name, then coloured symbol + full number. */
	public static List<PvTooltip.Line> tooltipLines(String id, String formattedValue) {
		StatStyle style = stat(id);
		String value = formattedValue == null || formattedValue.isBlank() ? "-" : formattedValue;
		return List.of(
			PvTooltip.Line.text(List.of(
				PvTooltip.Span.of(style.symbol() + " ", style.color()),
				PvTooltip.Span.bold(style.fullName(), style.color())
			)),
			PvTooltip.Line.text(List.of(
				PvTooltip.Span.of(style.symbol() + " ", style.color()),
				PvTooltip.Span.of(value, style.color())
			))
		);
	}

	public static int powerColor(String power) {
		if (power == null || power.isBlank()) {
			return PvDraw.COLOR_MUTED;
		}
		return POWER_COLORS.getOrDefault(power.toLowerCase(Locale.ROOT), 0xFFFFAA00);
	}

	public static Component powerName(String power) {
		if (power == null || power.isBlank()) {
			return PvDraw.styled("-", PvDraw.COLOR_MUTED, false);
		}
		return PvDraw.styled(InventoryDecoder.prettyWords(power), powerColor(power), true);
	}

	public static Component loadoutName(String name) {
		return PvDraw.styled(name == null || name.isBlank() ? "Loadout" : name, 0xFFFFAA00, true);
	}

	public static Component statChip(InventorySnapshot.StatPoint point) {
		StatStyle style = stat(point.id());
		MutableComponent out = Component.empty();
		out.append(PvDraw.styled(style.symbol(), style.color(), false));
		out.append(PvDraw.styled(String.valueOf(point.value()), style.color(), false));
		return out;
	}

	public static Component tuningLine(Integer slot, java.util.List<InventorySnapshot.StatPoint> stats) {
		return tuningStats(stats);
	}

	public static Component tuningStats(java.util.List<InventorySnapshot.StatPoint> stats) {
		MutableComponent out = Component.empty();
		if (stats == null || stats.isEmpty()) {
			out.append(PvDraw.styled("-", PvDraw.COLOR_MUTED, false));
			return out;
		}
		boolean first = true;
		for (InventorySnapshot.StatPoint point : stats) {
			if (!first) {
				out.append(PvDraw.styled(" ", PvDraw.COLOR_MUTED, false));
			}
			first = false;
			out.append(statChip(point));
		}
		return out;
	}

	public record StatStyle(String symbol, String fullName, int color, boolean boldSymbol) {
		public StatStyle(String symbol, String fullName, int color) {
			this(symbol, fullName, color, false);
		}

		public StatStyle {
			symbol = symbol == null ? "•" : symbol;
			fullName = fullName == null || fullName.isBlank() ? "Unknown" : fullName;
		}
	}
}
