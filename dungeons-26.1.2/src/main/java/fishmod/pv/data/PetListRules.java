/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Which pets the Pets page shows. Networth and other pages still see every pet. */
public final class PetListRules {
	public static final List<String> WISP_LADDER = List.of("DROPLET_WISP", "FROST_WISP", "GLACIAL_WISP", "SUBZERO_WISP");
	private static final Set<String> HIDDEN = Set.of("BINGO");

	private PetListRules() {
	}

	public static List<PetSnapshot.Entry> visibleOwned(List<PetSnapshot.Entry> pets) {
		List<PetSnapshot.Entry> out = new ArrayList<>();
		PetSnapshot.Entry bestWisp = null;
		for (PetSnapshot.Entry pet : pets) {
			String type = key(pet.type());
			if (HIDDEN.contains(type)) {
				continue;
			}
			if (wispRank(type) >= 0) {
				if (bestWisp == null || WISP_ORDER.compare(pet, bestWisp) > 0) {
					bestWisp = pet;
				}
				continue;
			}
			out.add(pet);
		}
		if (bestWisp != null) {
			out.add(bestWisp);
		}
		return out;
	}

	/** Whether a pet type the player doesn't own should appear as a "missing" slot. */
	public static boolean showAsMissing(String type, Set<String> ownedTypes) {
		String key = key(type);
		if (HIDDEN.contains(key)) {
			return false;
		}
		int rank = wispRank(key);
		if (rank < 0) {
			return true;
		}
		for (String wisp : WISP_LADDER) {
			if (ownedTypes.contains(wisp)) {
				return false;
			}
		}
		return rank == 0;
	}

	public static int wispRank(String type) {
		return WISP_LADDER.indexOf(key(type));
	}

	// Highest wisp type, then rarity, then XP; uuid keeps duplicates deterministic.
	private static final Comparator<PetSnapshot.Entry> WISP_ORDER = Comparator
		.comparingInt((PetSnapshot.Entry p) -> wispRank(p.type()))
		.thenComparingInt(PetSnapshot.Entry::tierIndex)
		.thenComparingDouble(PetSnapshot.Entry::exp)
		.thenComparing(PetSnapshot.Entry::uuid, Comparator.reverseOrder());

	private static String key(String type) {
		return type == null ? "" : type.toUpperCase(Locale.ROOT);
	}
}
