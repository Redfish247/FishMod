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

/** Trophy frog catalog (Lotus Atoll / Ribery order). */
public final class TrophyFrogData {
	private static final List<TrophyFishData.Def> DEFS = List.of(
		def("COMMON_FROG"),
		def("EXPLODING_FROG"),
		def("LEAP_FROG"),
		def("WETLANDS_FROG"),
		def("REALITY_HOPPER"),
		def("CAVE_FROG"),
		def("SEA_FROG"),
		def("TREE_FROG"),
		def("HIGHLANDS_FROG"),
		def("BULLFROG"),
		def("BLESSED_FROG")
	);

	private TrophyFrogData() {
	}

	public static List<TrophyFishData.Def> all() {
		return DEFS;
	}

	private static TrophyFishData.Def def(String id) {
		return new TrophyFishData.Def(id, pretty(id));
	}

	private static String pretty(String id) {
		if ("REALITY_HOPPER".equalsIgnoreCase(id)) {
			return "Reality Hopper";
		}
		if ("BULLFROG".equalsIgnoreCase(id)) {
			return "Bullfrog";
		}
		String[] parts = id.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
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
