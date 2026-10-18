/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.dungeons.EssenceShopData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Member {@code nether_island_player_data} + Crimson essence. */
public final class CrimsonSnapshot {
	public enum KuudraTier {
		NONE("none", "Basic"),
		HOT("hot", "Hot"),
		BURNING("burning", "Burning"),
		FIERY("fiery", "Fiery"),
		INFERNAL("infernal", "Infernal");

		private final String key;
		private final String label;

		KuudraTier(String key, String label) {
			this.key = key;
			this.label = label;
		}

		public String key() {
			return this.key;
		}

		public String label() {
			return this.label;
		}
	}

	public enum DojoChallenge {
		MOB_KB("mob_kb", "Force"),
		WALL_JUMP("wall_jump", "Stamina"),
		ARCHER("archer", "Swiftness"),
		SWORD_SWAP("sword_swap", "Discipline"),
		SNAKE("snake", "Mastery"),
		FIREBALL("fireball", "Tenacity"),
		LOCK_HEAD("lock_head", "Control");

		private final String key;
		private final String label;

		DojoChallenge(String key, String label) {
			this.key = key;
			this.label = label;
		}

		public String key() {
			return this.key;
		}

		public String label() {
			return this.label;
		}
	}

	public record KuudraTierStats(int completions, int highestWave) {
		public static KuudraTierStats empty() {
			return new KuudraTierStats(0, 0);
		}
	}

	public record DojoScore(int points, long timeMs) {
		public static DojoScore empty() {
			return new DojoScore(0, 0L);
		}
	}

	public record AbiphoneContact(
		String id,
		String name,
		boolean talkedTo,
		boolean completedQuest,
		boolean active,
		int incomingCalls,
		boolean dnd
	) {
	}

	/**
	 * Kuudra Follower Armor quest flags from {@code nether_island_player_data.quests}. Hypixel only
	 * writes these once set, so a missing key reads as not done.
	 */
	public record KuudraFollower(boolean loremaster, boolean cathedralBook, long relicClaimedMs, boolean leggings) {
		public static final KuudraFollower NONE = new KuudraFollower(false, false, 0L, false);

		static KuudraFollower from(JsonObject quests) {
			if (quests == null) {
				return NONE;
			}
			return new KuudraFollower(
				bool(quests.get("kuudra_loremaster")),
				bool(quests.get("found_kuudra_book")),
				Math.max(0L, (long) num(quests.get("last_kuudra_relic"))),
				bool(quests.get("found_kuudra_leggings"))
			);
		}
	}

	private static final String[] MINIBOSSES = {
		"BLADESOUL",
		"MAGE_OUTLAW",
		"BARBARIAN_DUKE_X",
		"ASHFANG",
		"MAGMA_BOSS"
	};

	private final String selectedFaction;
	private final double magesReputation;
	private final double magesReputationHighest;
	private final double barbariansReputation;
	private final double barbariansReputationHighest;
	private final long crimsonEssence;
	private final int matriarchPearls;
	private final long matriarchLastAttemptMs;
	private final Map<String, Boolean> minibosses;
	private final Map<KuudraTier, KuudraTierStats> kuudra;
	private final Map<DojoChallenge, DojoScore> dojo;
	private final List<String> lastMinibossesKilled;
	private final List<String> dailyQuests;
	private final int cavityNpcs;
	private final KuudraFollower kuudraFollower;
	private final List<AbiphoneContact> abiphoneContacts;
	private final int abiphoneActive;
	private final String abiphoneRingtone;
	private final int trioContactAddons;
	private final int operatorChipRepaired;
	private final int snakeBestScore;
	private final int tttLosses;
	private final int tttDraws;
	private final String abiphoneSort;
	private final CrimsonKuudraCard kuudraCard;
	private final DungeonSnapshot.EssenceShop crimsonShop;

	private CrimsonSnapshot(
		String selectedFaction,
		double magesReputation,
		double magesReputationHighest,
		double barbariansReputation,
		double barbariansReputationHighest,
		long crimsonEssence,
		int matriarchPearls,
		long matriarchLastAttemptMs,
		Map<String, Boolean> minibosses,
		Map<KuudraTier, KuudraTierStats> kuudra,
		Map<DojoChallenge, DojoScore> dojo,
		List<String> lastMinibossesKilled,
		List<String> dailyQuests,
		int cavityNpcs,
		KuudraFollower kuudraFollower,
		List<AbiphoneContact> abiphoneContacts,
		int abiphoneActive,
		String abiphoneRingtone,
		int trioContactAddons,
		int operatorChipRepaired,
		int snakeBestScore,
		int tttLosses,
		int tttDraws,
		String abiphoneSort,
		CrimsonKuudraCard kuudraCard,
		DungeonSnapshot.EssenceShop crimsonShop
	) {
		this.selectedFaction = selectedFaction == null ? "" : selectedFaction;
		this.magesReputation = magesReputation;
		this.magesReputationHighest = magesReputationHighest;
		this.barbariansReputation = barbariansReputation;
		this.barbariansReputationHighest = barbariansReputationHighest;
		this.crimsonEssence = Math.max(0L, crimsonEssence);
		this.matriarchPearls = Math.max(0, matriarchPearls);
		this.matriarchLastAttemptMs = Math.max(0L, matriarchLastAttemptMs);
		this.minibosses = Map.copyOf(minibosses == null ? Map.of() : minibosses);
		this.kuudra = Map.copyOf(kuudra == null ? Map.of() : kuudra);
		this.dojo = Map.copyOf(dojo == null ? Map.of() : dojo);
		this.lastMinibossesKilled = List.copyOf(lastMinibossesKilled == null ? List.of() : lastMinibossesKilled);
		this.dailyQuests = List.copyOf(dailyQuests == null ? List.of() : dailyQuests);
		this.cavityNpcs = Math.max(0, cavityNpcs);
		this.kuudraFollower = kuudraFollower == null ? KuudraFollower.NONE : kuudraFollower;
		this.abiphoneContacts = List.copyOf(abiphoneContacts == null ? List.of() : abiphoneContacts);
		this.abiphoneActive = Math.max(0, abiphoneActive);
		this.abiphoneRingtone = abiphoneRingtone == null ? "" : abiphoneRingtone;
		this.trioContactAddons = Math.max(0, trioContactAddons);
		this.operatorChipRepaired = Math.max(0, operatorChipRepaired);
		this.snakeBestScore = Math.max(0, snakeBestScore);
		this.tttLosses = Math.max(0, tttLosses);
		this.tttDraws = Math.max(0, tttDraws);
		this.abiphoneSort = abiphoneSort == null ? "" : abiphoneSort;
		this.kuudraCard = kuudraCard == null ? CrimsonKuudraCard.empty() : kuudraCard;
		this.crimsonShop = crimsonShop == null
			? DungeonSnapshot.EssenceShop.empty("crimson", "Crimson")
			: crimsonShop;
	}

	public static CrimsonSnapshot empty() {
		Map<String, Boolean> bosses = new LinkedHashMap<>();
		for (String id : MINIBOSSES) {
			bosses.put(id, false);
		}
		Map<KuudraTier, KuudraTierStats> kuudra = new LinkedHashMap<>();
		for (KuudraTier tier : KuudraTier.values()) {
			kuudra.put(tier, KuudraTierStats.empty());
		}
		Map<DojoChallenge, DojoScore> dojo = new LinkedHashMap<>();
		for (DojoChallenge challenge : DojoChallenge.values()) {
			dojo.put(challenge, DojoScore.empty());
		}
		return new CrimsonSnapshot(
			"", 0, 0, 0, 0, 0L, 0, 0L,
			bosses, kuudra, dojo, List.of(), List.of(), 0, KuudraFollower.NONE,
			List.of(), 0, "", 0, 0, 0, 0, 0, "",
			CrimsonKuudraCard.empty(),
			DungeonSnapshot.EssenceShop.empty("crimson", "Crimson")
		);
	}

	public static CrimsonSnapshot fromMember(JsonObject member) {
		if (member == null) {
			return empty();
		}
		JsonObject nether = Leveling.obj(member.get("nether_island_player_data"));
		if (nether == null) {
			CrimsonSnapshot base = empty();
			return new CrimsonSnapshot(
				base.selectedFaction,
				base.magesReputation,
				base.magesReputationHighest,
				base.barbariansReputation,
				base.barbariansReputationHighest,
				readCrimsonEssence(member),
				base.matriarchPearls,
				base.matriarchLastAttemptMs,
				base.minibosses,
				base.kuudra,
				base.dojo,
				base.lastMinibossesKilled,
				base.dailyQuests,
				base.cavityNpcs,
				base.kuudraFollower,
				base.abiphoneContacts,
				base.abiphoneActive,
				base.abiphoneRingtone,
				base.trioContactAddons,
				0, 0, 0, 0, "",
				CrimsonKuudraCard.from(member, base.kuudra),
				EssenceShopData.crimson(member)
			);
		}

		String faction = str(nether.get("selected_faction"));
		double mage = num(nether.get("mages_reputation"));
		double mageHigh = Math.max(mage, num(nether.get("mages_reputation_highest")));
		double barb = num(nether.get("barbarians_reputation"));
		double barbHigh = Math.max(barb, num(nether.get("barbarians_reputation_highest")));

		JsonObject matriarch = Leveling.obj(nether.get("matriarch"));
		int pearls = matriarch == null ? 0 : (int) num(matriarch.get("pearls_collected"));
		long lastPearl = matriarch == null ? 0L : (long) num(matriarch.get("last_attempt"));

		JsonObject quests = Leveling.obj(nether.get("quests"));
		Map<String, Boolean> bosses = new LinkedHashMap<>();
		JsonObject minibossData = quests == null ? null : Leveling.obj(quests.get("miniboss_data"));
		for (String id : MINIBOSSES) {
			bosses.put(id, minibossData != null && bool(minibossData.get(id)));
		}
		KuudraFollower follower = KuudraFollower.from(quests);
		int cavity = 0;
		if (quests != null && quests.get("unlocked_cavity_npcs") instanceof JsonArray arr) {
			cavity = arr.size();
		}
		List<String> dailies = new ArrayList<>();
		JsonObject questData = quests == null ? null : Leveling.obj(quests.get("quest_data"));
		if (questData != null && questData.get("quest_list") instanceof JsonArray list) {
			for (JsonElement el : list) {
				if (el != null && el.isJsonPrimitive()) {
					dailies.add(el.getAsString());
				}
			}
		}

		JsonObject kuudraObj = Leveling.obj(nether.get("kuudra_completed_tiers"));
		Map<KuudraTier, KuudraTierStats> kuudra = new LinkedHashMap<>();
		for (KuudraTier tier : KuudraTier.values()) {
			int comps = kuudraObj == null ? 0 : (int) num(kuudraObj.get(tier.key()));
			int wave = kuudraObj == null ? 0 : (int) num(kuudraObj.get("highest_wave_" + tier.key()));
			kuudra.put(tier, new KuudraTierStats(Math.max(0, comps), Math.max(0, wave)));
		}

		JsonObject dojoObj = Leveling.obj(nether.get("dojo"));
		Map<DojoChallenge, DojoScore> dojo = new LinkedHashMap<>();
		for (DojoChallenge challenge : DojoChallenge.values()) {
			int points = dojoObj == null ? 0 : (int) num(dojoObj.get("dojo_points_" + challenge.key()));
			long time = dojoObj == null ? 0L : (long) num(dojoObj.get("dojo_time_" + challenge.key()));
			dojo.put(challenge, new DojoScore(Math.max(0, points), Math.max(0L, time)));
		}

		List<String> lastBosses = stringList(nether.get("last_minibosses_killed"));

		JsonObject abiphone = Leveling.obj(nether.get("abiphone"));
		List<String> active = abiphone == null ? List.of() : stringList(abiphone.get("active_contacts"));
		java.util.HashSet<String> activeSet = new java.util.HashSet<>();
		for (String id : active) {
			activeSet.add(id.toLowerCase(Locale.ROOT));
		}
		List<AbiphoneContact> contacts = new ArrayList<>();
		Map<String, AbiphoneContact> byNpc = new LinkedHashMap<>();
		JsonObject contactData = abiphone == null ? null : Leveling.obj(abiphone.get("contact_data"));
		if (contactData != null) {
			for (Map.Entry<String, JsonElement> entry : contactData.entrySet()) {
				String id = entry.getKey();
				JsonObject row = Leveling.obj(entry.getValue());
				boolean talked = row != null && bool(row.get("talked_to"));
				boolean done = row != null && bool(row.get("completed_quest"));
				int calls = row == null ? 0 : (int) num(row.get("incoming_calls_count"));
				boolean dnd = row != null && bool(row.get("dnd_enabled"));
				boolean isActive = activeSet.contains(id.toLowerCase(Locale.ROOT));
				String npcKey = AbiphoneNpcs.neuId(id);
				AbiphoneContact prev = byNpc.get(npcKey);
				if (prev == null) {
					String neuName = AbiphoneNpcs.displayName(id);
					byNpc.put(npcKey, new AbiphoneContact(
						id,
						neuName != null ? neuName : prettyContact(id),
						talked,
						done,
						isActive,
						Math.max(0, calls),
						dnd
					));
				} else {
					byNpc.put(npcKey, new AbiphoneContact(
						prev.id(),
						prev.name(),
						prev.talkedTo() || talked,
						prev.completedQuest() || done,
						prev.active() || isActive,
						Math.max(prev.incomingCalls(), Math.max(0, calls)),
						prev.dnd() || dnd
					));
				}
			}
			contacts.addAll(byNpc.values());
			contacts.sort(Comparator
				.comparing((AbiphoneContact c) -> !c.active())
				.thenComparing(AbiphoneContact::name, String.CASE_INSENSITIVE_ORDER));
		}
		String ringtone = abiphone == null ? "" : str(abiphone.get("selected_ringtone"));
		int trio = abiphone == null ? 0 : (int) num(abiphone.get("trio_contact_addons"));
		JsonObject chip = abiphone == null ? null : Leveling.obj(abiphone.get("operator_chip"));
		int chipRepaired = chip == null ? 0 : (int) num(chip.get("repaired_index"));
		JsonObject games = abiphone == null ? null : Leveling.obj(abiphone.get("games"));
		int snake = games == null ? 0 : (int) num(games.get("snake_best_score"));
		int tttL = games == null ? 0 : (int) num(games.get("tic_tac_toe_losses"));
		int tttD = games == null ? 0 : (int) num(games.get("tic_tac_toe_draws"));
		String sort = abiphone == null ? "" : str(abiphone.get("selected_sort"));

		return new CrimsonSnapshot(
			faction,
			mage,
			mageHigh,
			barb,
			barbHigh,
			readCrimsonEssence(member),
			pearls,
			lastPearl,
			bosses,
			kuudra,
			dojo,
			lastBosses,
			dailies,
			cavity,
			follower,
			contacts,
			active.size(),
			ringtone,
			trio,
			chipRepaired,
			snake,
			tttL,
			tttD,
			sort,
			CrimsonKuudraCard.from(member, kuudra),
			EssenceShopData.crimson(member)
		);
	}

	/** Prefer full profile INT / Magic Find once player stats are computed. */
	public CrimsonSnapshot withPlayerStats(PlayerStatsSnapshot stats) {
		if (stats == null || stats.isEmpty()) {
			return this;
		}
		return new CrimsonSnapshot(
			this.selectedFaction,
			this.magesReputation,
			this.magesReputationHighest,
			this.barbariansReputation,
			this.barbariansReputationHighest,
			this.crimsonEssence,
			this.matriarchPearls,
			this.matriarchLastAttemptMs,
			this.minibosses,
			this.kuudra,
			this.dojo,
			this.lastMinibossesKilled,
			this.dailyQuests,
			this.cavityNpcs,
			this.kuudraFollower,
			this.abiphoneContacts,
			this.abiphoneActive,
			this.abiphoneRingtone,
			this.trioContactAddons,
			this.operatorChipRepaired,
			this.snakeBestScore,
			this.tttLosses,
			this.tttDraws,
			this.abiphoneSort,
			this.kuudraCard.withCombatStats(stats),
			this.crimsonShop
		);
	}

	private static long readCrimsonEssence(JsonObject member) {
		JsonObject currencies = Leveling.obj(member.get("currencies"));
		JsonObject essence = currencies == null ? null : Leveling.obj(currencies.get("essence"));
		JsonObject crimson = essence == null ? null : Leveling.obj(essence.get("CRIMSON"));
		if (crimson != null) {
			return (long) num(crimson.get("current"));
		}
		return 0L;
	}

	public String selectedFaction() {
		return this.selectedFaction;
	}

	public String factionLabel() {
		if (this.selectedFaction.isBlank()) {
			return "None";
		}
		String lower = this.selectedFaction.toLowerCase(Locale.ROOT);
		if (lower.startsWith("mage")) {
			return "MAGE";
		}
		if (lower.startsWith("barb")) {
			return "BARBARIAN";
		}
		return prettyContact(this.selectedFaction);
	}

	/** Faction colour for the selected faction label, or muted when none. */
	public int factionColor(int mageColor, int barbColor, int noneColor) {
		String lower = this.selectedFaction.toLowerCase(Locale.ROOT);
		if (lower.startsWith("mage")) {
			return mageColor;
		}
		if (lower.startsWith("barb")) {
			return barbColor;
		}
		return noneColor;
	}

	public double magesReputation() {
		return this.magesReputation;
	}

	public double magesReputationHighest() {
		return this.magesReputationHighest;
	}

	public double barbariansReputation() {
		return this.barbariansReputation;
	}

	public double barbariansReputationHighest() {
		return this.barbariansReputationHighest;
	}

	public long crimsonEssence() {
		return this.crimsonEssence;
	}

	public int matriarchPearls() {
		return this.matriarchPearls;
	}

	public long matriarchLastAttemptMs() {
		return this.matriarchLastAttemptMs;
	}

	public Map<String, Boolean> minibosses() {
		return this.minibosses;
	}

	public int minibossesKilled() {
		int n = 0;
		for (Boolean v : this.minibosses.values()) {
			if (Boolean.TRUE.equals(v)) {
				n++;
			}
		}
		return n;
	}

	public KuudraTierStats kuudra(KuudraTier tier) {
		return this.kuudra.getOrDefault(tier, KuudraTierStats.empty());
	}

	public int kuudraTotalCompletions() {
		int n = 0;
		for (KuudraTierStats stats : this.kuudra.values()) {
			n += stats.completions();
		}
		return n;
	}

	/** Highest wave on the highest Kuudra tier that has at least one clear. */
	public int kuudraHighestClearedWave() {
		KuudraTier[] tiers = KuudraTier.values();
		for (int i = tiers.length - 1; i >= 0; i--) {
			KuudraTierStats stats = kuudra(tiers[i]);
			if (stats.completions() > 0) {
				return stats.highestWave();
			}
		}
		return 0;
	}

	public DojoScore dojo(DojoChallenge challenge) {
		return this.dojo.getOrDefault(challenge, DojoScore.empty());
	}

	public int dojoTotalPoints() {
		int n = 0;
		for (DojoScore score : this.dojo.values()) {
			n += score.points();
		}
		return n;
	}

	public List<String> lastMinibossesKilled() {
		return this.lastMinibossesKilled;
	}

	public List<String> dailyQuests() {
		return this.dailyQuests;
	}

	public int cavityNpcs() {
		return this.cavityNpcs;
	}

	public boolean kuudraLoremaster() {
		return this.kuudraFollower.loremaster();
	}

	public KuudraFollower kuudraFollower() {
		return this.kuudraFollower;
	}

	public List<AbiphoneContact> abiphoneContacts() {
		return this.abiphoneContacts;
	}

	public int abiphoneActive() {
		return this.abiphoneActive;
	}

	public String abiphoneRingtone() {
		return this.abiphoneRingtone;
	}

	public int trioContactAddons() {
		return this.trioContactAddons;
	}

	public int operatorChipRepaired() {
		return this.operatorChipRepaired;
	}

	public int snakeBestScore() {
		return this.snakeBestScore;
	}

	public int tttLosses() {
		return this.tttLosses;
	}

	public int tttDraws() {
		return this.tttDraws;
	}

	public String abiphoneSort() {
		return this.abiphoneSort;
	}

	public int abiphoneQuestsDone() {
		int n = 0;
		for (AbiphoneContact c : this.abiphoneContacts) {
			if (c.completedQuest()) n++;
		}
		return n;
	}

	public int abiphoneDndCount() {
		int n = 0;
		for (AbiphoneContact c : this.abiphoneContacts) {
			if (c.dnd()) n++;
		}
		return n;
	}

	public CrimsonKuudraCard kuudraCard() {
		return this.kuudraCard;
	}

	public DungeonSnapshot.EssenceShop crimsonShop() {
		return this.crimsonShop;
	}

	public static String dojoRank(int points) {
		if (points >= 1000) return "S";
		if (points >= 800) return "A";
		if (points >= 600) return "B";
		if (points >= 400) return "C";
		if (points >= 200) return "D";
		return "F";
	}

	public static int dojoRankColor(String rank) {
		return switch (rank == null ? "" : rank) {
			case "S" -> 0xFFFFAA00;
			case "A" -> 0xFF55FF55;
			case "B" -> 0xFF55FFFF;
			case "C" -> 0xFFFFFF55;
			case "D" -> 0xFFAAAAAA;
			default -> 0xFFFF5555;
		};
	}

	public static String prettyMiniboss(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		return switch (id.toUpperCase(Locale.ROOT)) {
			case "BLADESOUL" -> "Bladesoul";
			case "MAGE_OUTLAW" -> "Mage Outlaw";
			case "BARBARIAN_DUKE_X" -> "Barbarian Duke X";
			case "ASHFANG" -> "Ashfang";
			case "MAGMA_BOSS" -> "Magma Boss";
			default -> prettyContact(id);
		};
	}

	public static String prettyDaily(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String raw = id;
		if (raw.startsWith("crimson_isle_")) {
			raw = raw.substring("crimson_isle_".length());
		}
		// Strip trailing quality letter like _c / _a / _s
		if (raw.length() > 2 && raw.charAt(raw.length() - 2) == '_' && Character.isLetter(raw.charAt(raw.length() - 1))) {
			raw = raw.substring(0, raw.length() - 2);
		}
		return prettyContact(raw);
	}

	private static String prettyContact(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String[] parts = id.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
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

	private static List<String> stringList(JsonElement el) {
		List<String> out = new ArrayList<>();
		if (!(el instanceof JsonArray arr)) {
			return out;
		}
		for (JsonElement item : arr) {
			if (item != null && item.isJsonPrimitive()) {
				String s = item.getAsString();
				if (s != null && !s.isBlank()) {
					out.add(s);
				}
			}
		}
		return out;
	}

	private static String str(JsonElement el) {
		if (el == null || !el.isJsonPrimitive()) {
			return "";
		}
		try {
			return el.getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static double num(JsonElement el) {
		if (el == null || !el.isJsonPrimitive()) {
			return 0;
		}
		try {
			return el.getAsDouble();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return 0;
		}
	}

	private static boolean bool(JsonElement el) {
		if (el == null || el.isJsonNull()) {
			return false;
		}
		if (el.isJsonPrimitive()) {
			try {
				if (el.getAsJsonPrimitive().isBoolean()) {
					return el.getAsBoolean();
				}
				if (el.getAsJsonPrimitive().isNumber()) {
					return el.getAsInt() != 0;
				}
				String s = el.getAsString();
				return "true".equalsIgnoreCase(s) || "1".equals(s);
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				return false;
			}
		}
		return false;
	}
}
