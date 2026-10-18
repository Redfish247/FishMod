/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.weight;

import java.util.Locale;

public final class WeightStages {
	private WeightStages() {
	}

	public static String senitherStage(double total) {
		if (total >= 30_000) {
			return "No Life";
		}
		if (total >= 15_000) {
			return "End Game";
		}
		if (total >= 10_000) {
			return "Early End";
		}
		if (total >= 7_000) {
			return "Late Game";
		}
		if (total >= 2_000) {
			return "Mid Game";
		}
		return "Early Game";
	}

	public static String lilyRank(double total) {
		if (total >= 50_000) {
			return "Prestigious";
		}
		if (total >= 44_500) {
			return "Grandmaster";
		}
		if (total >= 37_000) {
			return "Diamond";
		}
		if (total >= 24_500) {
			return "Platinum";
		}
		if (total >= 17_900) {
			return "Gold";
		}
		if (total >= 13_425) {
			return "Silver";
		}
		return "Bronze";
	}

	/** ARGB colour for a Senither stage or Lily rank label. */
	public static int colorFor(String stageOrRank) {
		if (stageOrRank == null || stageOrRank.isBlank()) {
			return 0xFF9A9AAC;
		}
		return switch (stageOrRank.toLowerCase(Locale.ROOT)) {
			// Lily - named after the colour/metal
			case "bronze" -> 0xFFCD7F32;
			case "silver" -> 0xFFC0C0C0;
			case "gold" -> 0xFFFFAA00;
			case "platinum" -> 0xFFE5E4E2;
			case "diamond" -> 0xFF55FFFF;
			case "grandmaster" -> 0xFFFF55FF;
			case "prestigious" -> 0xFFFF5555;
			// Senither - progression ladder (grey → green → blue → purple → gold → red)
			case "early game" -> 0xFFAAAAAA;
			case "mid game" -> 0xFF55FF55;
			case "late game" -> 0xFF5555FF;
			case "early end" -> 0xFFAA00AA;
			case "end game" -> 0xFFFFAA00;
			case "no life" -> 0xFFFF5555;
			default -> 0xFFFFAA00;
		};
	}
}
