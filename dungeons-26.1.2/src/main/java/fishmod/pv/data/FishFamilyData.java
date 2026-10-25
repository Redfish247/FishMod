/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.List;
import java.util.Locale;

/**
 * Coral "Fish Family" catalog (Galatea Red House).
 * Discovered IDs come from {@code foraging.fish_family}; this list fills empty slots.
 */
public final class FishFamilyData {
	private static final List<String> ALL = List.of(
		"BAT_THE_FISH",
		"BERRY_THE_FISH",
		"CANDY_THE_FISH",
		"CENTURY_THE_FISH",
		"CLUCK_THE_FISH",
		"DIAMOND_THE_FISH",
		"DUST_THE_FISH",
		"EON_THE_FISH",
		"EXPERIMENT_THE_FISH",
		"FISH_THE_FISH",
		"FOSSIL_THE_FISH",
		"GABAGOOL_THE_FISH",
		"GIFT_THE_FISH",
		"GOLDOR_THE_FISH",
		"GORF_THE_FISH",
		"MAXOR_THE_FISH",
		"MOB_THE_FISH",
		"MYTH_THE_FISH",
		"NOPE_THE_FISH",
		"PARTY_THE_FISH",
		"PRICELESS_THE_FISH",
		"RABBIT_THE_FISH",
		"RIBBIT_THE_FISH",
		"ROCK_THE_FISH",
		"SHRIMP_THE_FISH",
		"SKELETON_THE_FISH",
		"SNOWFLAKE_THE_FISH",
		"STEW_THE_FISH",
		"STORM_THE_FISH",
		"SWAMP_THE_FISH",
		"TREE_THE_FISH",
		"WORM_THE_FISH",
		"ZOOP_THE_FISH"
	);

	private FishFamilyData() {
	}

	public static List<String> all() {
		return ALL;
	}

	public static String displayName(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		if ("SNOWFLAKE_THE_FISH".equalsIgnoreCase(id)) {
			return "Flake the Fish";
		}
		if ("GORF_THE_FISH".equalsIgnoreCase(id)) {
			return "Frog the Fish";
		}
		String[] parts = id.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank() || "THE".equalsIgnoreCase(part)) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return sb.toString();
	}
}
