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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClassXpCalculator {
	/**
	 * Off-class share used by Adjectils / common RTCA sims ({@code selectedRate / 4}).
	 * UI tips often say 0.2×; matching Adjectils requires 0.25.
	 */
	private static final double OFF_CLASS_RATE = 0.25;
	/** Adjectils caps Derpy/Aura class XP at +50% even when Aura is +59%. */
	private static final double CLASS_MAYOR_CAP = 1.5;
	private static final int RTCA_MAX_RUNS = 200_000;

	public record FloorEstimate(String id, String label, boolean master, long xpPerRun, long runsNeeded, long completions) {
	}

	public record Result(
		String classId,
		String className,
		int currentLevel,
		float xpNeeded,
		int targetLevel,
		List<FloorEstimate> floors,
		String modsLabel
	) {
	}

	public record ClassRuns(String classId, String className, int currentLevel, long selectedRuns) {
	}

	public record AverageResult(
		int targetLevel,
		String floorLabel,
		long totalRuns,
		List<ClassRuns> classes,
		String modsLabel,
		boolean timedOut
	) {
	}

	private ClassXpCalculator() {
	}

	public static Result calculate(DungeonSnapshot data, ClassLevelQuery.Parsed query) {
		if (query == null || query.classAverage()) {
			return null;
		}
		return calculateForClass(data, query.classId(), query.displayName(), query.targetLevel());
	}

	/**
	 * Adjectils-style equal-runs planner: each run selects the class with the most XP still needed;
	 * that class gains full XP/run, others gain {@value #OFF_CLASS_RATE}. Stops when every class
	 * has reached the XP for {@code targetLevel} (Hypixel CA 50 requires all five at 50).
	 */
	public static AverageResult calculateAverage(DungeonSnapshot data, int targetLevel) {
		DungeonXpData.ensureLoaded();
		int target = Math.max(1, Math.min(50, targetLevel));
		String mods = modsLabel(data, null);
		float targetXp = CataXpMath.xpForLevel(target);

		if (data.classes().isEmpty()) {
			return new AverageResult(target, "-", 0L, List.of(), mods, false);
		}

		FloorPick floor = pickBestFloor(data);
		if (floor == null) {
			List<ClassRuns> empty = new ArrayList<>();
			for (DungeonSnapshot.ClassEntry clazz : data.classes()) {
				empty.add(new ClassRuns(clazz.id(), ClassLevelQuery.displayName(clazz.id()), clazz.level(), 0L));
			}
			return new AverageResult(target, "-", 0L, List.copyOf(empty), mods, false);
		}

		Map<String, Double> remaining = new LinkedHashMap<>();
		Map<String, Long> selected = new LinkedHashMap<>();
		Map<String, Double> rates = floor.ratesByClass();
		boolean alreadyDone = true;
		for (DungeonSnapshot.ClassEntry clazz : data.classes()) {
			double left = Math.max(0D, targetXp - clazz.xp());
			remaining.put(clazz.id(), left);
			selected.put(clazz.id(), 0L);
			if (left > 0D) {
				alreadyDone = false;
			}
		}

		if (alreadyDone) {
			List<ClassRuns> done = new ArrayList<>();
			for (DungeonSnapshot.ClassEntry clazz : data.classes()) {
				done.add(new ClassRuns(clazz.id(), ClassLevelQuery.displayName(clazz.id()), clazz.level(), 0L));
			}
			return new AverageResult(target, floor.label(), 0L, List.copyOf(done), mods, false);
		}

		long total = 0L;
		boolean timedOut = false;
		while (hasRemaining(remaining)) {
			if (total >= RTCA_MAX_RUNS) {
				timedOut = true;
				break;
			}
			total++;

			String neediest = null;
			double mostLeft = -1D;
			for (Map.Entry<String, Double> entry : remaining.entrySet()) {
				if (entry.getValue() > mostLeft) {
					mostLeft = entry.getValue();
					neediest = entry.getKey();
				}
			}
			if (neediest == null) {
				break;
			}

			for (Map.Entry<String, Double> entry : remaining.entrySet()) {
				double rate = rates.getOrDefault(entry.getKey(), floor.fallbackRate());
				double gain = entry.getKey().equals(neediest) ? rate : rate * OFF_CLASS_RATE;
				entry.setValue(entry.getValue() - gain);
			}
			selected.put(neediest, selected.getOrDefault(neediest, 0L) + 1L);
		}

		List<ClassRuns> out = new ArrayList<>();
		for (DungeonSnapshot.ClassEntry clazz : data.classes()) {
			out.add(new ClassRuns(
				clazz.id(),
				ClassLevelQuery.displayName(clazz.id()),
				clazz.level(),
				selected.getOrDefault(clazz.id(), 0L)
			));
		}
		return new AverageResult(target, floor.label(), total, List.copyOf(out), mods, timedOut);
	}

	private static boolean hasRemaining(Map<String, Double> remaining) {
		for (double left : remaining.values()) {
			if (left > 0D) {
				return true;
			}
		}
		return false;
	}

	private static Result calculateForClass(
		DungeonSnapshot data,
		String classId,
		String className,
		int targetLevel
	) {
		DungeonXpData.ensureLoaded();
		DungeonSnapshot.ClassEntry clazz = findClass(data, classId);
		int currentLevel = clazz == null ? 0 : clazz.level();
		float currentXp = clazz == null ? 0F : clazz.xp();
		int target = Math.max(1, Math.min(CataXpMath.maxLevel(), targetLevel));
		float needed = CataXpMath.xpNeeded(currentXp, target);
		String mods = modsLabel(data, classId);
		if (needed <= 0F) {
			return new Result(classId, className, currentLevel, 0F, target, List.of(), mods);
		}

		int cataLevel = data.cataLevel();
		long f7 = completions(data.normal(), "7");
		boolean masterUnlocked = f7 > 0L;
		List<FloorEstimate> estimates = new ArrayList<>();

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
			long xpPerRun = Math.max(1L, Math.round(classXpPerRun(
				floor.baseXp(),
				data.hecatombLevel(),
				data.scarfBonus(),
				data.catacombsGraduateBonus(),
				data.classEssenceBonus(classId),
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
		return new Result(
			classId,
			className,
			currentLevel,
			needed,
			target,
			List.copyOf(estimates),
			mods
		);
	}

	/**
	 * Class XP for an S+ run while playing that class (full rate), Adjectils-style:
	 * {@code base * (1 + hecClass + scarf + graduate + essence + (global-1)) * min(1.5, mayor)}.
	 * Expert Ring and Experienced Floor do not affect class XP in this model.
	 */
	public static double classXpPerRun(
		double baseXp,
		int hecatombLevel,
		double scarfBonus,
		double graduateBonus,
		double essenceBonus,
		double mayorXpFactor,
		double globalBoost
	) {
		double hec = 2.0 * DungeonXpData.hecatombBonus(hecatombLevel);
		double bonus = 1.0
			+ hec
			+ Math.max(0, scarfBonus)
			+ Math.max(0, graduateBonus)
			+ Math.max(0, essenceBonus)
			+ Math.max(0, globalBoost);
		double mayor = mayorXpFactor > 0 ? Math.min(CLASS_MAYOR_CAP, mayorXpFactor) : 1.0;
		return baseXp * bonus * mayor;
	}

	private record FloorPick(String label, Map<String, Double> ratesByClass, double fallbackRate) {
	}

	private static FloorPick pickBestFloor(DungeonSnapshot data) {
		int cataLevel = data.cataLevel();
		long f7 = completions(data.normal(), "7");
		boolean masterUnlocked = f7 > 0L;
		FloorPick best = null;
		double bestMean = -1;

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

			Map<String, Double> rates = new LinkedHashMap<>();
			double sum = 0;
			int n = 0;
			for (DungeonSnapshot.ClassEntry clazz : data.classes()) {
				double rate = Math.max(1.0, classXpPerRun(
					floor.baseXp(),
					data.hecatombLevel(),
					data.scarfBonus(),
					data.catacombsGraduateBonus(),
					data.classEssenceBonus(clazz.id()),
					data.mayorFactor(),
					0.0
				));
				rates.put(clazz.id(), rate);
				sum += rate;
				n++;
			}
			if (n == 0) {
				continue;
			}
			double mean = sum / n;
			if (mean > bestMean) {
				bestMean = mean;
				best = new FloorPick(floor.label(), rates, mean);
			}
		}
		return best;
	}

	private static String modsLabel(DungeonSnapshot data, String classId) {
		List<String> bits = new ArrayList<>();
		if (data.hecatombLevel() > 0) {
			bits.add("Hec " + data.hecatombLevel());
		}
		if (data.scarfBonus() > 0) {
			bits.add("Scarf +" + Math.round(data.scarfBonus() * 100) + "%");
		}
		if (data.catacombsGraduateBonus() > 0) {
			bits.add("Grad +" + Math.round(data.catacombsGraduateBonus() * 100) + "%");
		}
		if (classId != null) {
			double essence = data.classEssenceBonus(classId);
			if (essence > 0) {
				bits.add("Essence +" + Math.round(essence * 100) + "%");
			}
		}
		if (data.mayorFactor() > 1.0 && data.mayorName() != null && !data.mayorName().isBlank()) {
			bits.add(data.mayorName());
		} else if (data.mayorName() != null && !data.mayorName().isBlank()) {
			bits.add(data.mayorName() + " (no XP buff)");
		}
		return String.join(" · ", bits);
	}

	private static DungeonSnapshot.ClassEntry findClass(DungeonSnapshot data, String classId) {
		for (DungeonSnapshot.ClassEntry entry : data.classes()) {
			if (entry.id().equalsIgnoreCase(classId)) {
				return entry;
			}
		}
		return null;
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
