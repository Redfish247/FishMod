/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.Locale;
import java.util.Map;

/**
 * SkyCrypt-style labels for {@code player_stats.kills} / {@code deaths} keys.
 * Strips trailing level suffixes ({@code kuudra_follower_500}) then maps known ids.
 */
public final class MobNames {
	private static final Map<String, String> NAMES = Map.ofEntries(
		Map.entry("pond_squid", "Squid"),
		Map.entry("unburried_zombie", "Crypt Ghoul"),
		Map.entry("unburied_zombie", "Crypt Ghoul"),
		Map.entry("zealot_enderman", "Zealot"),
		Map.entry("invisible_creeper", "Sneaky Creeper"),
		Map.entry("generator_ghast", "Minion Ghast"),
		Map.entry("generator_magma_cube", "Minion Magma Cube"),
		Map.entry("generator_slime", "Minion Slime"),
		Map.entry("brood_mother_spider", "Brood Mother"),
		Map.entry("obsidian_wither", "Obsidian Defender"),
		Map.entry("sadan_statue", "Terracotta"),
		Map.entry("diamond_guy", "Angry Archaeologist"),
		Map.entry("tentaclees", "Fels"),
		Map.entry("master_diamond_guy", "Master Angry Archaeologist"),
		Map.entry("master_sadan_statue", "Master Terracotta"),
		Map.entry("master_tentaclees", "Master Fels"),
		Map.entry("maxor", "Necron"),
		Map.entry("pig_rider", "Taurus")
	);

	private MobNames() {
	}

	public static String pretty(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		key = key.replaceAll("§[0-9a-fk-or]", "");
		key = key.replaceAll("_\\d+$", "");
		String mapped = NAMES.get(key);
		if (mapped != null) {
			return mapped;
		}
		return titleCase(key);
	}

	private static String titleCase(String key) {
		String[] parts = key.split("_+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1));
			}
		}
		return out.toString();
	}
}
