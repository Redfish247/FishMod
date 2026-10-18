/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parsed bestiary progress for one profile member. */
public final class BestiarySnapshot {
	public static final class FamilyProgress {
		private final BestiaryData.Family def;
		private final long kills;
		private final long deaths;
		private final int tier;
		private final int tiersMax;
		private final long intoTier;
		private final long nextNeed;
		private final boolean maxed;

		public FamilyProgress(
			BestiaryData.Family def,
			long kills,
			long deaths,
			int tier,
			int tiersMax,
			long intoTier,
			long nextNeed,
			boolean maxed
		) {
			this.def = def;
			this.kills = kills;
			this.deaths = deaths;
			this.tier = Math.max(0, tier);
			this.tiersMax = Math.max(0, tiersMax);
			this.intoTier = Math.max(0L, intoTier);
			this.nextNeed = Math.max(0L, nextNeed);
			this.maxed = maxed;
		}

		public BestiaryData.Family family() {
			return this.def;
		}

		public long kills() {
			return this.kills;
		}

		public long deaths() {
			return this.deaths;
		}

		public int tier() {
			return this.tier;
		}

		public int tiersMax() {
			return this.tiersMax;
		}

		public long intoTier() {
			return this.intoTier;
		}

		public long nextNeed() {
			return this.nextNeed;
		}

		public boolean maxed() {
			return this.maxed;
		}

		public float progress() {
			if (this.maxed || this.nextNeed <= 0L) {
				return 1F;
			}
			return Math.max(0F, Math.min(1F, this.intoTier / (float) this.nextNeed));
		}
	}

	public record CategoryProgress(
		BestiaryData.Category category,
		List<FamilyProgress> families,
		int unlockedTiers,
		int maxTiers,
		int familiesUnlocked,
		int familiesMaxed
	) {
	}

	private final Map<String, CategoryProgress> byCategory;
	private final int claimedMilestone;
	private final boolean maxKillsVisible;
	private final String lastKilledMob;
	private final int totalUnlockedTiers;
	private final int totalMaxTiers;

	private BestiarySnapshot(
		Map<String, CategoryProgress> byCategory,
		int claimedMilestone,
		boolean maxKillsVisible,
		String lastKilledMob,
		int totalUnlockedTiers,
		int totalMaxTiers
	) {
		this.byCategory = byCategory;
		this.claimedMilestone = claimedMilestone;
		this.maxKillsVisible = maxKillsVisible;
		this.lastKilledMob = lastKilledMob == null ? "" : lastKilledMob;
		this.totalUnlockedTiers = totalUnlockedTiers;
		this.totalMaxTiers = totalMaxTiers;
	}

	public static BestiarySnapshot empty() {
		return new BestiarySnapshot(Map.of(), 0, false, "", 0, 0);
	}

	public static BestiarySnapshot fromMember(JsonObject member) {
		BestiaryData.ensureLoaded();
		JsonObject bestiary = Leveling.obj(member == null ? null : member.get("bestiary"));
		JsonObject killsObj = bestiary == null ? null : Leveling.obj(bestiary.get("kills"));
		JsonObject deathsObj = bestiary == null ? null : Leveling.obj(bestiary.get("deaths"));
		JsonObject milestone = bestiary == null ? null : Leveling.obj(bestiary.get("milestone"));
		JsonObject misc = bestiary == null ? null : Leveling.obj(bestiary.get("miscellaneous"));

		Map<String, Long> kills = readCounts(killsObj);
		Map<String, Long> deaths = readCounts(deathsObj);
		String lastKilled = "";
		if (killsObj != null && killsObj.has("last_killed_mob") && killsObj.get("last_killed_mob").isJsonPrimitive()) {
			try {
				lastKilled = killsObj.get("last_killed_mob").getAsString();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			}
		}
		int claimed = 0;
		if (milestone != null && milestone.has("last_claimed_milestone")
			&& milestone.get("last_claimed_milestone").isJsonPrimitive()) {
			try {
				claimed = Math.max(0, milestone.get("last_claimed_milestone").getAsInt());
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			}
		}
		boolean maxVisible = misc != null && misc.has("max_kills_visible")
			&& misc.get("max_kills_visible").isJsonPrimitive()
			&& misc.get("max_kills_visible").getAsBoolean();

		Map<String, CategoryProgress> byCat = new LinkedHashMap<>();
		int totalUnlocked = 0;
		int totalMax = 0;
		for (BestiaryData.Category cat : BestiaryData.categories()) {
			List<FamilyProgress> families = new ArrayList<>();
			int unlockedTiers = 0;
			int maxTiers = 0;
			int famUnlocked = 0;
			int famMaxed = 0;
			for (BestiaryData.Family fam : cat.families()) {
				FamilyProgress fp = progressFor(fam, kills, deaths);
				families.add(fp);
				unlockedTiers += fp.tier();
				maxTiers += fp.tiersMax();
				if (fp.tier() > 0) {
					famUnlocked++;
				}
				if (fp.maxed()) {
					famMaxed++;
				}
			}
			totalUnlocked += unlockedTiers;
			totalMax += maxTiers;
			byCat.put(cat.id(), new CategoryProgress(cat, List.copyOf(families), unlockedTiers, maxTiers, famUnlocked, famMaxed));
		}
		return new BestiarySnapshot(Map.copyOf(byCat), claimed, maxVisible, lastKilled, totalUnlocked, totalMax);
	}

	public CategoryProgress category(String id) {
		if (id == null) {
			return null;
		}
		return byCategory.get(id);
	}

	public List<CategoryProgress> categories() {
		List<CategoryProgress> out = new ArrayList<>();
		for (BestiaryData.Category cat : BestiaryData.categories()) {
			CategoryProgress progress = byCategory.get(cat.id());
			if (progress != null) {
				out.add(progress);
			} else {
				out.add(new CategoryProgress(cat, List.of(), 0, 0, 0, 0));
			}
		}
		return out;
	}

	public int claimedMilestone() {
		return claimedMilestone;
	}

	/** In-game Bestiary milestone = unique family tiers / 10 (not last claimed reward). */
	public int milestone() {
		return totalUnlockedTiers / 10;
	}

	public int milestoneProgress() {
		return totalUnlockedTiers % 10;
	}

	public boolean maxKillsVisible() {
		return maxKillsVisible;
	}

	public String lastKilledMob() {
		return lastKilledMob;
	}

	public int totalUnlockedTiers() {
		return totalUnlockedTiers;
	}

	public int totalMaxTiers() {
		return totalMaxTiers;
	}

	private static FamilyProgress progressFor(
		BestiaryData.Family fam,
		Map<String, Long> kills,
		Map<String, Long> deaths
	) {
		long killSum = 0L;
		long deathSum = 0L;
		for (String mobId : fam.mobIds()) {
			killSum += kills.getOrDefault(mobId, 0L);
			deathSum += deaths.getOrDefault(mobId, 0L);
		}
		return progressFor(fam, killSum, deathSum, BestiaryData.bracket(fam.bracketType(), fam.bracket()));
	}

	/** {@code killSum} stays the real total; only tier math is clamped to the family cap. */
	static FamilyProgress progressFor(BestiaryData.Family fam, long killSum, long deathSum, List<Integer> ladder) {
		long tierKills = fam.cap() > 0 ? Math.min(killSum, fam.cap()) : killSum;
		// NEU brackets are cumulative kill thresholds. Family cap = kills for that family's max tier.
		int tiersMax = tiersMaxFor(ladder, fam.cap());
		int tier = 0;
		for (int i = 0; i < tiersMax; i++) {
			if (tierKills >= ladder.get(i)) {
				tier = i + 1;
			} else {
				break;
			}
		}
		boolean maxed = tiersMax > 0 && tier >= tiersMax;
		long into = 0L;
		long next = 0L;
		if (!maxed && tier < ladder.size()) {
			long prev = tier > 0 ? ladder.get(tier - 1) : 0L;
			long nextThresh = ladder.get(tier);
			into = Math.max(0L, tierKills - prev);
			next = Math.max(0L, nextThresh - prev);
		}
		return new FamilyProgress(fam, killSum, deathSum, tier, tiersMax, into, next, maxed);
	}

	/** Highest ladder index whose cumulative threshold is within the family kill cap. */
	private static int tiersMaxFor(List<Integer> ladder, int cap) {
		if (ladder == null || ladder.isEmpty()) {
			return 0;
		}
		if (cap <= 0) {
			return ladder.size();
		}
		int max = 0;
		for (int i = 0; i < ladder.size(); i++) {
			if (ladder.get(i) <= cap) {
				max = i + 1;
			} else {
				break;
			}
		}
		return max;
	}

	private static Map<String, Long> readCounts(JsonObject obj) {
		Map<String, Long> out = new HashMap<>();
		if (obj == null) {
			return out;
		}
		for (var entry : obj.entrySet()) {
			String key = entry.getKey();
			if (key == null || key.isBlank() || "last_killed_mob".equals(key)) {
				continue;
			}
			if (!entry.getValue().isJsonPrimitive()) {
				continue;
			}
			try {
				long v = entry.getValue().getAsLong();
				if (v > 0L) {
					out.put(key.toLowerCase(Locale.ROOT), v);
				}
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			}
		}
		return out;
	}
}
