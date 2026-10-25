/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Catacombs / class / run stats for the Dungeons tab. */
public final class DungeonSnapshot {
	public record ClassEntry(
		String id,
		String name,
		int level,
		float xp,
		float progress,
		boolean maxed,
		boolean selected,
		String xpHover
	) {
	}

	public record FloorEntry(
		String id,
		String label,
		long completions,
		long milestoneCompletions,
		long mobsKilled,
		long bestScore,
		long mostMobsKilled,
		long fastestMs,
		long fastestSMs,
		long fastestSPlusMs,
		double mostHealing,
		String mostDamageClass,
		double mostDamage
	) {
		public static FloorEntry empty(String id, String label) {
			return new FloorEntry(id, label, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0D, null, 0D);
		}
	}

	public record ModeStats(long totalRuns, List<FloorEntry> floors) {
		public static ModeStats empty() {
			return new ModeStats(0L, List.of());
		}
	}

	public record EssencePerk(String id, String name, int level, int maxLevel) {
		public boolean maxed() {
			return level >= maxLevel && maxLevel > 0;
		}
	}

	public record EssenceShop(String id, String name, long balance, String iconId, List<EssencePerk> perks) {
		public static EssenceShop empty(String id, String name) {
			String icon = switch (id == null ? "" : id) {
				case "wither" -> "ESSENCE_WITHER";
				case "undead" -> "ESSENCE_UNDEAD";
				case "gold" -> "ESSENCE_GOLD";
				case "diamond" -> "ESSENCE_DIAMOND";
				case "crimson" -> "ESSENCE_CRIMSON";
				case "forest" -> "ESSENCE_FOREST";
				case "spider" -> "ESSENCE_SPIDER";
				case "dragon" -> "ESSENCE_DRAGON";
				case "ice" -> "ESSENCE_ICE";
				case "fossil" -> "ESSENCE_FOSSIL";
				case "safari" -> "ESSENCE_SAFARI";
				default -> "ESSENCE_WITHER";
			};
			return new EssenceShop(id, name, 0L, icon, List.of());
		}
	}

	private final int cataLevel;
	private final float cataXp;
	private final float cataProgress;
	private final boolean cataMaxed;
	private final String cataHover;
	private final long secrets;
	private final double secretsPerRun;
	private final float classAverage;
	private final float classAverageProgress;
	private final boolean classAverageMaxed;
	private final String classAverageHover;
	private final List<ClassEntry> classes;
	private final ModeStats normal;
	private final ModeStats master;
	private final boolean expertRing;
	private final int hecatombLevel;
	private final double scarfBonus;
	private final double catacombsGraduateBonus;
	private final Map<String, Double> classEssenceBonuses;
	private final double mayorFactor;
	private final String mayorName;
	private final EssenceShop witherShop;
	private final EssenceShop undeadShop;
	private final EssenceShop iceShop;
	private final EssenceShop spiderShop;
	private final EssenceShop dragonShop;
	private final int dailyRuns;
	private final int journalsUnlocked;
	private final HubRace hubRace;
	private final DungeonChestHistory chestHistory;

	public record HubRace(String selectedRace, String selectedSetting, Boolean runback) {
		public HubRace {
			selectedRace = selectedRace == null ? "" : selectedRace;
			selectedSetting = selectedSetting == null ? "" : selectedSetting;
		}

		public static HubRace empty() {
			return new HubRace("", "", null);
		}

		public boolean present() {
			return !selectedRace.isBlank() || !selectedSetting.isBlank() || runback != null;
		}
	}

	public DungeonSnapshot(
		int cataLevel,
		float cataXp,
		float cataProgress,
		boolean cataMaxed,
		String cataHover,
		long secrets,
		double secretsPerRun,
		float classAverage,
		float classAverageProgress,
		boolean classAverageMaxed,
		String classAverageHover,
		List<ClassEntry> classes,
		ModeStats normal,
		ModeStats master,
		boolean expertRing,
		int hecatombLevel,
		double scarfBonus,
		double catacombsGraduateBonus,
		Map<String, Double> classEssenceBonuses,
		double mayorFactor,
		String mayorName,
		EssenceShop witherShop,
		EssenceShop undeadShop,
		EssenceShop iceShop,
		EssenceShop spiderShop,
		EssenceShop dragonShop,
		int dailyRuns,
		int journalsUnlocked,
		HubRace hubRace,
		DungeonChestHistory chestHistory
	) {
		this.cataLevel = cataLevel;
		this.cataXp = cataXp;
		this.cataProgress = cataProgress;
		this.cataMaxed = cataMaxed;
		this.cataHover = cataHover;
		this.secrets = secrets;
		this.secretsPerRun = secretsPerRun;
		this.classAverage = classAverage;
		this.classAverageProgress = classAverageProgress;
		this.classAverageMaxed = classAverageMaxed;
		this.classAverageHover = classAverageHover;
		this.classes = classes == null ? List.of() : List.copyOf(classes);
		this.normal = normal == null ? ModeStats.empty() : normal;
		this.master = master == null ? ModeStats.empty() : master;
		this.expertRing = expertRing;
		this.hecatombLevel = hecatombLevel;
		this.scarfBonus = scarfBonus;
		this.catacombsGraduateBonus = Math.max(0, catacombsGraduateBonus);
		this.classEssenceBonuses = classEssenceBonuses == null
			? Map.of()
			: Collections.unmodifiableMap(Map.copyOf(classEssenceBonuses));
		this.mayorFactor = mayorFactor;
		this.mayorName = mayorName == null ? "" : mayorName;
		this.witherShop = witherShop == null ? EssenceShop.empty("wither", "Wither") : witherShop;
		this.undeadShop = undeadShop == null ? EssenceShop.empty("undead", "Undead") : undeadShop;
		this.iceShop = iceShop == null ? EssenceShop.empty("ice", "Ice") : iceShop;
		this.spiderShop = spiderShop == null ? EssenceShop.empty("spider", "Spider") : spiderShop;
		this.dragonShop = dragonShop == null ? EssenceShop.empty("dragon", "Dragon") : dragonShop;
		this.dailyRuns = Math.max(0, dailyRuns);
		this.journalsUnlocked = Math.max(0, journalsUnlocked);
		this.hubRace = hubRace == null ? HubRace.empty() : hubRace;
		this.chestHistory = chestHistory == null ? DungeonChestHistory.empty() : chestHistory;
	}

	public int cataLevel() {
		return this.cataLevel;
	}

	public float cataXp() {
		return this.cataXp;
	}

	public float cataProgress() {
		return this.cataProgress;
	}

	public boolean cataMaxed() {
		return this.cataMaxed;
	}

	public String cataHover() {
		return this.cataHover;
	}

	public long secrets() {
		return this.secrets;
	}

	public double secretsPerRun() {
		return this.secretsPerRun;
	}

	public float classAverage() {
		return this.classAverage;
	}

	public float classAverageProgress() {
		return this.classAverageProgress;
	}

	public boolean classAverageMaxed() {
		return this.classAverageMaxed;
	}

	public String classAverageHover() {
		return this.classAverageHover;
	}

	public List<ClassEntry> classes() {
		return this.classes;
	}

	public ModeStats mode(boolean masterMode) {
		return masterMode ? this.master : this.normal;
	}

	public ModeStats normal() {
		return this.normal;
	}

	public ModeStats master() {
		return this.master;
	}

	public boolean expertRing() {
		return this.expertRing;
	}

	public int hecatombLevel() {
		return this.hecatombLevel;
	}

	public double scarfBonus() {
		return this.scarfBonus;
	}

	/** Catacombs Graduate attribute bonus (0-0.20). */
	public double catacombsGraduateBonus() {
		return this.catacombsGraduateBonus;
	}

	public double classEssenceBonus(String classId) {
		if (classId == null || this.classEssenceBonuses.isEmpty()) {
			return 0;
		}
		Double value = this.classEssenceBonuses.get(classId.toLowerCase(java.util.Locale.ROOT));
		return value == null ? 0 : value;
	}

	public double mayorFactor() {
		return this.mayorFactor;
	}

	public String mayorName() {
		return this.mayorName;
	}

	public EssenceShop witherShop() {
		return this.witherShop;
	}

	public EssenceShop undeadShop() {
		return this.undeadShop;
	}

	public EssenceShop iceShop() {
		return this.iceShop;
	}

	public EssenceShop spiderShop() {
		return this.spiderShop;
	}

	public EssenceShop dragonShop() {
		return this.dragonShop;
	}

	public int dailyRuns() {
		return this.dailyRuns;
	}

	public int journalsUnlocked() {
		return this.journalsUnlocked;
	}

	public HubRace hubRace() {
		return this.hubRace;
	}

	public DungeonChestHistory chestHistory() {
		return this.chestHistory;
	}

	public static HubRace parseHubRace(com.google.gson.JsonElement raw) {
		com.google.gson.JsonObject root = raw != null && raw.isJsonObject() ? raw.getAsJsonObject() : null;
		if (root == null || root.entrySet().isEmpty()) {
			return HubRace.empty();
		}
		String race = "";
		if (root.has("selected_race") && root.get("selected_race").isJsonPrimitive()) {
			try {
				race = root.get("selected_race").getAsString();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				race = "";
			}
		}
		String setting = "";
		if (root.has("selected_setting") && root.get("selected_setting").isJsonPrimitive()) {
			try {
				setting = root.get("selected_setting").getAsString();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				setting = "";
			}
		}
		Boolean runback = null;
		if (root.has("runback") && root.get("runback").isJsonPrimitive()) {
			try {
				runback = root.get("runback").getAsBoolean();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				runback = null;
			}
		}
		if ((race == null || race.isBlank()) && (setting == null || setting.isBlank()) && runback == null) {
			return HubRace.empty();
		}
		return new HubRace(
			fishmod.pv.networth.InventoryDecoder.prettyWords(race),
			fishmod.pv.networth.InventoryDecoder.prettyWords(setting),
			runback
		);
	}

	public static DungeonSnapshot empty() {
		return new DungeonSnapshot(
			0, 0F, 0F, false, "Loading...",
			0L, 0D,
			0F, 0F, false, "Loading...",
			List.of(),
			ModeStats.empty(),
			ModeStats.empty(),
			false, 0, 0, 0, Map.of(), 1.0, "",
			EssenceShop.empty("wither", "Wither"),
			EssenceShop.empty("undead", "Undead"),
			EssenceShop.empty("ice", "Ice"),
			EssenceShop.empty("spider", "Spider"),
			EssenceShop.empty("dragon", "Dragon"),
			0, 0,
			HubRace.empty(),
			DungeonChestHistory.empty()
		);
	}
}
