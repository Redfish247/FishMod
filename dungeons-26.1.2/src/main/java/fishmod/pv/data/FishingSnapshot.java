/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Member-side fishing: Overview + Trophy Fish / Frogs. */
public final class FishingSnapshot {
	public record TierCounts(int bronze, int silver, int gold, int diamond) {
		public static TierCounts zero() {
			return new TierCounts(0, 0, 0, 0);
		}

		public int total() {
			return Math.max(0, bronze) + Math.max(0, silver) + Math.max(0, gold) + Math.max(0, diamond);
		}

		public int of(TrophyFishData.Tier tier) {
			if (tier == null) {
				return 0;
			}
			return switch (tier) {
				case BRONZE -> Math.max(0, bronze);
				case SILVER -> Math.max(0, silver);
				case GOLD -> Math.max(0, gold);
				case DIAMOND -> Math.max(0, diamond);
			};
		}

		public boolean discovered(TrophyFishData.Tier tier) {
			return of(tier) > 0;
		}

		public int discoveredTier() {
			int n = 0;
			for (TrophyFishData.Tier tier : TrophyFishData.Tier.values()) {
				if (discovered(tier)) {
					n++;
				}
			}
			return n;
		}

		/** Diamond=4 … Bronze=1, none=0 - for sort / completion. */
		public int bestRank() {
			TrophyFishData.Tier best = bestDiscovered();
			if (best == null) {
				return 0;
			}
			return switch (best) {
				case DIAMOND -> 4;
				case GOLD -> 3;
				case SILVER -> 2;
				case BRONZE -> 1;
			};
		}

		public TrophyFishData.Tier bestDiscovered() {
			for (TrophyFishData.Tier tier : TrophyFishData.Tier.topToBottom()) {
				if (discovered(tier)) {
					return tier;
				}
			}
			return null;
		}
	}

	public record TrophyRow(String id, String name, TierCounts counts, boolean countsExact) {
		public int totalCaught() {
			return counts == null ? 0 : counts.total();
		}

		public boolean anyDiscovered() {
			return counts != null && counts.total() > 0;
		}
	}

	private final int fishingLevel;
	private final float fishingFill;
	private final boolean fishingMaxed;
	private final String fishingHover;

	private final long itemsFishedTotal;
	private final long itemsFishedNormal;
	private final long itemsFishedTreasure;
	private final long itemsFishedLargeTreasure;
	private final long itemsFishedTrophyFish;
	private final long itemsFishedTrophyFrog;
	private final long itemsFishedOutstanding;
	private final long seaCreatureKills;
	private final long festivalSharksKilled;
	/** -1 when the profile has no Dolphin milestone field. */
	private final long dolphinMilestone;

	private final long trophyFishTotal;
	private final String trophyFishLastCaught;
	private final List<TrophyRow> trophyFish;
	private final List<TrophyRow> trophyFrogs;
	private final long peltCount;

	private FishingSnapshot(
		int fishingLevel, float fishingFill, boolean fishingMaxed, String fishingHover,
		long itemsFishedTotal, long itemsFishedNormal, long itemsFishedTreasure, long itemsFishedLargeTreasure,
		long itemsFishedTrophyFish, long itemsFishedTrophyFrog, long itemsFishedOutstanding,
		long seaCreatureKills, long festivalSharksKilled, long dolphinMilestone,
		long trophyFishTotal, String trophyFishLastCaught,
		List<TrophyRow> trophyFish, List<TrophyRow> trophyFrogs,
		long peltCount
	) {
		this.fishingLevel = Math.max(0, fishingLevel);
		this.fishingFill = Math.max(0f, Math.min(1f, fishingFill));
		this.fishingMaxed = fishingMaxed;
		this.fishingHover = fishingHover == null ? "" : fishingHover;
		this.itemsFishedTotal = Math.max(0L, itemsFishedTotal);
		this.itemsFishedNormal = Math.max(0L, itemsFishedNormal);
		this.itemsFishedTreasure = Math.max(0L, itemsFishedTreasure);
		this.itemsFishedLargeTreasure = Math.max(0L, itemsFishedLargeTreasure);
		this.itemsFishedTrophyFish = Math.max(0L, itemsFishedTrophyFish);
		this.itemsFishedTrophyFrog = Math.max(0L, itemsFishedTrophyFrog);
		this.itemsFishedOutstanding = Math.max(0L, itemsFishedOutstanding);
		this.seaCreatureKills = Math.max(0L, seaCreatureKills);
		this.festivalSharksKilled = Math.max(0L, festivalSharksKilled);
		this.dolphinMilestone = Math.max(-1L, dolphinMilestone);
		this.trophyFishTotal = Math.max(0L, trophyFishTotal);
		this.trophyFishLastCaught = trophyFishLastCaught == null ? "" : trophyFishLastCaught;
		this.trophyFish = List.copyOf(trophyFish == null ? List.of() : trophyFish);
		this.trophyFrogs = List.copyOf(trophyFrogs == null ? List.of() : trophyFrogs);
		this.peltCount = Math.max(0L, peltCount);
	}

	public static FishingSnapshot empty() {
		return new FishingSnapshot(
			0, 0f, false, "",
			0, 0, 0, 0, 0, 0, 0,
			0, 0, -1L,
			0, "", List.of(), List.of(),
			0L
		);
	}

	public static FishingSnapshot fromMember(JsonObject member) {
		if (member == null) {
			return empty();
		}
		TrophyFishData.ensureLoaded();
		TrophySkulls.ensureLoaded();

		double fishingXp = Leveling.readSkillXpDouble(member, "fishing");
		int fishingCap = Leveling.skillCap("fishing", member);
		Leveling.Progress fishing = Leveling.getLevel(Leveling.skillTable("fishing"), fishingXp, fishingCap, false);

		JsonObject stats = Leveling.obj(member.get("player_stats"));
		JsonObject fished = Leveling.obj(stats == null ? null : stats.get("items_fished"));
		JsonObject leveling = Leveling.obj(member.get("leveling"));
		JsonObject pets = Leveling.obj(stats == null ? null : stats.get("pets"));
		JsonObject milestones = Leveling.obj(pets == null ? null : pets.get("milestone"));
		long dolphin = milestones != null && milestones.has("sea_creatures_killed")
			? longOf(milestones, "sea_creatures_killed") : -1L;

		JsonObject trophyRoot = Leveling.obj(member.get("trophy_fish"));
		long totalCaught = longOf(trophyRoot, "total_caught");
		String last = str(trophyRoot, "last_caught");
		List<TrophyRow> fishRows = parseTrophyFish(trophyRoot);

		JsonObject frogRoot = Leveling.obj(member.get("trophy_frog"));
		if (frogRoot == null) {
			frogRoot = Leveling.obj(member.get("trophy_frogs"));
		}
		List<TrophyRow> frogRows = frogRoot != null
			? parseTrophyFrogsExact(frogRoot)
			: parseTrophyFrogsFromTasks(leveling == null ? null : leveling.get("completed_tasks"));

		JsonObject quests = Leveling.obj(member.get("quests"));
		JsonObject trapper = Leveling.obj(quests == null ? null : quests.get("trapper_quest"));

		return new FishingSnapshot(
			(int) Math.floor(fishing.level()), fishing.fill(), fishing.maxed(), fishing.skillHoverText("Fishing"),
			longOf(fished, "total"),
			longOf(fished, "normal"),
			longOf(fished, "treasure"),
			longOf(fished, "large_treasure"),
			longOf(fished, "trophy_fish"),
			longOf(fished, "trophy_frog"),
			longOf(fished, "outstanding"),
			longOf(stats, "sea_creature_kills"),
			longOf(leveling, "fishing_festival_sharks_killed"),
			dolphin,
			totalCaught, last, fishRows, frogRows,
			longOf(trapper, "pelt_count")
		);
	}

	private static List<TrophyRow> parseTrophyFish(JsonObject root) {
		List<TrophyRow> out = new ArrayList<>();
		for (TrophyFishData.Def def : TrophyFishData.all()) {
			String key = def.id().toLowerCase(Locale.ROOT);
			int bronze = (int) longOf(root, key + "_bronze");
			int silver = (int) longOf(root, key + "_silver");
			int gold = (int) longOf(root, key + "_gold");
			int diamond = (int) longOf(root, key + "_diamond");
			out.add(new TrophyRow(def.id(), def.name(), new TierCounts(bronze, silver, gold, diamond), true));
		}
		return out;
	}

	private static List<TrophyRow> parseTrophyFrogsExact(JsonObject root) {
		List<TrophyRow> out = new ArrayList<>();
		for (TrophyFishData.Def def : TrophyFrogData.all()) {
			String key = def.id().toLowerCase(Locale.ROOT);
			int bronze = (int) longOf(root, key + "_bronze");
			int silver = (int) longOf(root, key + "_silver");
			int gold = (int) longOf(root, key + "_gold");
			int diamond = (int) longOf(root, key + "_diamond");
			out.add(new TrophyRow(def.id(), def.name(), new TierCounts(bronze, silver, gold, diamond), true));
		}
		return out;
	}

	/** Discovery-only fallback when Hypixel has no {@code trophy_frog} object yet. */
	private static List<TrophyRow> parseTrophyFrogsFromTasks(JsonElement completedTasks) {
		Set<String> tasks = new HashSet<>();
		if (completedTasks != null && completedTasks.isJsonArray()) {
			for (JsonElement el : completedTasks.getAsJsonArray()) {
				if (el != null && el.isJsonPrimitive()) {
					try {
						tasks.add(el.getAsString().toUpperCase(Locale.ROOT));
					} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					}
				}
			}
		}
		List<TrophyRow> out = new ArrayList<>();
		for (TrophyFishData.Def def : TrophyFrogData.all()) {
			int bronze = tasks.contains("TROPHY_" + def.id() + "_BRONZE") ? 1 : 0;
			int silver = tasks.contains("TROPHY_" + def.id() + "_SILVER") ? 1 : 0;
			int gold = tasks.contains("TROPHY_" + def.id() + "_GOLD") ? 1 : 0;
			int diamond = tasks.contains("TROPHY_" + def.id() + "_DIAMOND") ? 1 : 0;
			out.add(new TrophyRow(def.id(), def.name(), new TierCounts(bronze, silver, gold, diamond), false));
		}
		return out;
	}

	private static long longOf(JsonObject obj, String key) {
		if (obj == null) {
			return 0L;
		}
		return Leveling.longAmount(obj.get(key));
	}

	private static String str(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			return obj.get(key).getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	public int fishingLevel() { return fishingLevel; }
	public float fishingFill() { return fishingFill; }
	public boolean fishingMaxed() { return fishingMaxed; }
	public String fishingHover() { return fishingHover; }
	public long itemsFishedTotal() { return itemsFishedTotal; }
	public long itemsFishedNormal() { return itemsFishedNormal; }
	public long itemsFishedTreasure() { return itemsFishedTreasure; }
	public long itemsFishedLargeTreasure() { return itemsFishedLargeTreasure; }
	public long itemsFishedTrophyFish() { return itemsFishedTrophyFish; }
	public long itemsFishedTrophyFrog() { return itemsFishedTrophyFrog; }
	public long itemsFishedOutstanding() { return itemsFishedOutstanding; }
	public long seaCreatureKills() { return seaCreatureKills; }
	public long festivalSharksKilled() { return festivalSharksKilled; }
	public long dolphinMilestone() { return dolphinMilestone; }
	public long trophyFishTotal() { return trophyFishTotal; }
	public String trophyFishLastCaught() { return trophyFishLastCaught; }
	public List<TrophyRow> trophyFish() { return trophyFish; }
	public List<TrophyRow> trophyFrogs() { return trophyFrogs; }
	public long peltCount() { return peltCount; }
}
