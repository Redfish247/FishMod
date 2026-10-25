/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons;

import fishmod.pv.data.DungeonSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CataXpCalculator {
	public record FloorEstimate(String id, String label, boolean master, long xpPerRun, long runsNeeded, long completions) {
	}

	public record Result(float xpNeeded, int targetLevel, List<FloorEstimate> floors, String mayorLabel) {
	}

	private CataXpCalculator() {
	}

	public static Result calculate(DungeonSnapshot data, int targetLevel) {
		DungeonXpData.ensureLoaded();
		int target = Math.max(1, Math.min(CataXpMath.maxLevel(), targetLevel));
		float needed = CataXpMath.xpNeeded(data.cataXp(), target);
		List<FloorEstimate> estimates = new ArrayList<>();
		if (needed <= 0F) {
			return new Result(0F, target, List.of(), mayorDisplay(data));
		}

		int cataLevel = data.cataLevel();
		long f7 = completions(data.normal(), "7");
		boolean masterUnlocked = f7 > 0L;

		for (DungeonXpData.FloorDef floor : DungeonXpData.floors()) {
			if (cataLevel < floor.unlockCata()) {
				continue;
			}
			if (floor.master() && !masterUnlocked) {
				continue;
			}
			if (!chainUnlocked(data, floor)) {
				continue;
			}
			long comps = floor.master()
				? completions(data.master(), String.valueOf(floor.floor()))
				: completions(data.normal(), String.valueOf(floor.floor()));
			long xpPerRun = Math.max(1L, Math.round(cataXpPerRun(
				floor.baseXp(),
				comps,
				data.expertRing(),
				data.hecatombLevel(),
				data.mayorFactor(),
				0.0
			)));
			long runs = (long) Math.ceil(needed / (double) xpPerRun);
			estimates.add(new FloorEstimate(floor.id(), floor.label(), floor.master(), xpPerRun, runs, comps));
		}

		estimates.sort(Comparator
			.comparingLong(FloorEstimate::xpPerRun).reversed()
			.thenComparing(FloorEstimate::label));
		if (estimates.size() > 5) {
			estimates = new ArrayList<>(estimates.subList(0, 5));
		}
		return new Result(needed, target, List.copyOf(estimates), mayorDisplay(data));
	}

	/**
	 * SkyHelper-style Catacombs XP for an S+ run:
	 * {@code base * (1 + ring + hecatomb) * (1 + experienced?) * mayor * (1 + global)}.
	 * Experienced Floor Bonus (+50%) applies at 5+ completions on that floor.
	 */
	public static double cataXpPerRun(
		double baseXp,
		long completions,
		boolean expertRing,
		int hecatombLevel,
		double mayorXpFactor,
		double globalBoost
	) {
		double ring = expertRing ? DungeonXpData.expertRingBonus() : 0;
		double hec = DungeonXpData.hecatombBonus(hecatombLevel);
		double bonus = 1.0 + ring + hec;
		double experienced = DungeonXpData.isExperiencedFloor(completions)
			? (1.0 + DungeonXpData.experiencedFloorBonus())
			: 1.0;
		double mayor = mayorXpFactor > 0 ? mayorXpFactor : 1.0;
		return baseXp * bonus * experienced * mayor * (1.0 + globalBoost);
	}

	private static String mayorDisplay(DungeonSnapshot data) {
		String name = data.mayorName();
		if (name == null || name.isBlank()) {
			return "";
		}
		if (data.mayorFactor() <= 1.0) {
			return name + " (no XP buff)";
		}
		return name;
	}

	private static boolean chainUnlocked(DungeonSnapshot data, DungeonXpData.FloorDef floor) {
		long own = floor.master()
			? completions(data.master(), String.valueOf(floor.floor()))
			: completions(data.normal(), String.valueOf(floor.floor()));
		if (own > 0L) {
			return true;
		}
		if (floor.master()) {
			if (floor.floor() <= 1) {
				return completions(data.normal(), "7") > 0L;
			}
			return completions(data.master(), String.valueOf(floor.floor() - 1)) > 0L;
		}
		if (floor.floor() <= 1) {
			return true;
		}
		return completions(data.normal(), String.valueOf(floor.floor() - 1)) > 0L;
	}

	private static long completions(DungeonSnapshot.ModeStats mode, String id) {
		for (DungeonSnapshot.FloorEntry entry : mode.floors()) {
			if (id.equals(entry.id())) {
				return entry.completions();
			}
		}
		return 0L;
	}
}
