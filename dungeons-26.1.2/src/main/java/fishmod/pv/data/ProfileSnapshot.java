/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import java.util.List;
import java.util.UUID;

public final class ProfileSnapshot {
	public record SkillEntry(
		String id,
		String name,
		int level,
		float progress,
		boolean maxed,
		String xpHover,
		List<PvTooltip.Line> hoverLines,
		float overflowLevel
	) {
		public SkillEntry {
			hoverLines = hoverLines == null || hoverLines.isEmpty()
				? List.of(PvTooltip.Line.of(xpHover == null ? "" : xpHover, PvDraw.COLOR_TEXT))
				: List.copyOf(hoverLines);
		}

		public SkillEntry(
			String id, String name, int level, float progress, boolean maxed, String xpHover, List<PvTooltip.Line> hoverLines
		) {
			this(id, name, level, progress, maxed, xpHover, hoverLines, maxed ? level : level + progress);
		}

		public SkillEntry(String id, String name, int level, float progress, boolean maxed, String xpHover) {
			this(id, name, level, progress, maxed, xpHover, List.of());
		}
	}

	public record SlayerEntry(
		String id,
		String name,
		int tier,
		float progress,
		boolean maxed,
		float xp,
		String xpHover,
		List<Integer> tierKills,
		List<PvTooltip.Line> hoverLines
	) {
		public SlayerEntry {
			tierKills = tierKills == null ? List.of() : List.copyOf(tierKills);
			hoverLines = hoverLines == null || hoverLines.isEmpty()
				? List.of(PvTooltip.Line.of(xpHover == null ? "" : xpHover, PvDraw.COLOR_TEXT))
				: List.copyOf(hoverLines);
			xp = Math.max(0F, xp);
		}

		public SlayerEntry(String id, String name, int tier, float progress, boolean maxed, String xpHover) {
			this(id, name, tier, progress, maxed, 0F, xpHover, List.of(), List.of());
		}

		public SlayerEntry(
			String id, String name, int tier, float progress, boolean maxed, String xpHover, List<Integer> tierKills
		) {
			this(id, name, tier, progress, maxed, 0F, xpHover, tierKills, List.of());
		}
	}

	/** Coop bank ledger row from {@code profiles[].banking.transactions[]}. */
	public record BankTransaction(String action, double amount, String initiatorName, long timestampMs) {
		public BankTransaction {
			action = action == null ? "" : action;
			initiatorName = initiatorName == null ? "" : initiatorName;
			amount = Math.max(0D, amount);
			timestampMs = Math.max(0L, timestampMs);
		}
	}

	/** Active {@code slayer.slayer_quest} row. {@code tier} is player-facing (T1+). */
	public record ActiveSlayerQuest(
		String typeId,
		String name,
		int tier,
		boolean spawned,
		boolean solo,
		float combatXp,
		String island
	) {
		public ActiveSlayerQuest {
			typeId = typeId == null ? "" : typeId;
			name = name == null ? "" : name;
			tier = Math.max(0, tier);
			combatXp = Math.max(0F, combatXp);
			island = island == null ? "" : island;
		}

		public boolean present() {
			return !name.isBlank() && tier > 0;
		}
	}

	/** {@code leveling.emblem_unlocks} + optional {@code leveling.selected_symbol}. */
	public record EmblemInfo(String selected, List<String> unlocked) {
		public EmblemInfo {
			selected = selected == null ? "" : selected;
			unlocked = List.copyOf(unlocked == null ? List.of() : unlocked);
		}

		public static EmblemInfo empty() {
			return new EmblemInfo("", List.of());
		}

		public boolean present() {
			return !unlocked.isEmpty() || !selected.isBlank();
		}
	}

	private static final String LOADING_HOVER = "Loading...";

	private final String playerName;
	private final UUID playerUuid;
	private final String profileName;
	private final int skyBlockLevel;
	private final int skyBlockXpIntoLevel;
	private final String weightText;
	private final String networthText;
	private final double purseCoins;
	private final double bankCoins;
	private final List<BankTransaction> bankTransactions;
	private final List<SkillEntry> skills;
	private final List<SlayerEntry> slayers;
	private final SkillEntry social;
	private final SkillEntry runecrafting;
	private final ActiveSlayerQuest activeSlayer;
	private final EmblemInfo emblems;
	private final fishmod.pv.slayer.SlayerMayorMods slayerMods;

	public ProfileSnapshot(
		String playerName,
		UUID playerUuid,
		String profileName,
		int skyBlockLevel,
		int skyBlockXpIntoLevel,
		String weightText,
		String networthText,
		List<SkillEntry> skills,
		List<SlayerEntry> slayers,
		SkillEntry social
	) {
		this(
			playerName, playerUuid, profileName, skyBlockLevel, skyBlockXpIntoLevel,
			weightText, networthText, 0D, 0D, List.of(), skills, slayers, social,
			null, null, EmblemInfo.empty()
		);
	}

	public ProfileSnapshot(
		String playerName,
		UUID playerUuid,
		String profileName,
		int skyBlockLevel,
		int skyBlockXpIntoLevel,
		String weightText,
		String networthText,
		double purseCoins,
		double bankCoins,
		List<SkillEntry> skills,
		List<SlayerEntry> slayers,
		SkillEntry social
	) {
		this(
			playerName, playerUuid, profileName, skyBlockLevel, skyBlockXpIntoLevel,
			weightText, networthText, purseCoins, bankCoins, List.of(), skills, slayers, social,
			null, null, EmblemInfo.empty()
		);
	}

	public ProfileSnapshot(
		String playerName,
		UUID playerUuid,
		String profileName,
		int skyBlockLevel,
		int skyBlockXpIntoLevel,
		String weightText,
		String networthText,
		double purseCoins,
		double bankCoins,
		List<BankTransaction> bankTransactions,
		List<SkillEntry> skills,
		List<SlayerEntry> slayers,
		SkillEntry social
	) {
		this(
			playerName, playerUuid, profileName, skyBlockLevel, skyBlockXpIntoLevel,
			weightText, networthText, purseCoins, bankCoins, bankTransactions, skills, slayers, social,
			null, null, EmblemInfo.empty()
		);
	}

	public ProfileSnapshot(
		String playerName,
		UUID playerUuid,
		String profileName,
		int skyBlockLevel,
		int skyBlockXpIntoLevel,
		String weightText,
		String networthText,
		double purseCoins,
		double bankCoins,
		List<BankTransaction> bankTransactions,
		List<SkillEntry> skills,
		List<SlayerEntry> slayers,
		SkillEntry social,
		SkillEntry runecrafting,
		ActiveSlayerQuest activeSlayer,
		EmblemInfo emblems
	) {
		this(
			playerName, playerUuid, profileName, skyBlockLevel, skyBlockXpIntoLevel,
			weightText, networthText, purseCoins, bankCoins, bankTransactions,
			skills, slayers, social, runecrafting, activeSlayer, emblems,
			fishmod.pv.slayer.SlayerMayorMods.none()
		);
	}

	public ProfileSnapshot(
		String playerName,
		UUID playerUuid,
		String profileName,
		int skyBlockLevel,
		int skyBlockXpIntoLevel,
		String weightText,
		String networthText,
		double purseCoins,
		double bankCoins,
		List<BankTransaction> bankTransactions,
		List<SkillEntry> skills,
		List<SlayerEntry> slayers,
		SkillEntry social,
		SkillEntry runecrafting,
		ActiveSlayerQuest activeSlayer,
		EmblemInfo emblems,
		fishmod.pv.slayer.SlayerMayorMods slayerMods
	) {
		this.playerName = playerName;
		this.playerUuid = playerUuid;
		this.profileName = profileName;
		this.skyBlockLevel = skyBlockLevel;
		this.skyBlockXpIntoLevel = Math.max(0, Math.min(100, skyBlockXpIntoLevel));
		this.weightText = weightText;
		this.networthText = networthText;
		this.purseCoins = Math.max(0D, purseCoins);
		this.bankCoins = Math.max(0D, bankCoins);
		this.bankTransactions = List.copyOf(bankTransactions == null ? List.of() : bankTransactions);
		this.skills = List.copyOf(skills);
		this.slayers = List.copyOf(slayers);
		this.social = social == null ? entry("social", "Social") : social;
		this.runecrafting = runecrafting == null ? entry("runecrafting", "Runecrafting") : runecrafting;
		this.activeSlayer = activeSlayer != null && activeSlayer.present() ? activeSlayer : null;
		this.emblems = emblems == null ? EmblemInfo.empty() : emblems;
		this.slayerMods = slayerMods == null ? fishmod.pv.slayer.SlayerMayorMods.none() : slayerMods;
	}

	public String playerName() {
		return this.playerName;
	}

	public UUID playerUuid() {
		return this.playerUuid;
	}

	public String profileName() {
		return this.profileName;
	}

	public int skyBlockLevel() {
		return this.skyBlockLevel;
	}

	public int skyBlockXpIntoLevel() {
		return this.skyBlockXpIntoLevel;
	}

	public float skyBlockProgress() {
		return this.skyBlockXpIntoLevel / 100.0f;
	}

	public String weightText() {
		return this.weightText;
	}

	public String networthText() {
		return this.networthText;
	}

	public double purseCoins() {
		return this.purseCoins;
	}

	public double bankCoins() {
		return this.bankCoins;
	}

	public List<BankTransaction> bankTransactions() {
		return this.bankTransactions;
	}

	public List<SkillEntry> skills() {
		return this.skills;
	}

	public List<SlayerEntry> slayers() {
		return this.slayers;
	}

	/** False while {@link #skills()} still holds the loading placeholders. */
	public boolean skillsLoaded() {
		for (SkillEntry skill : this.skills) {
			if (!LOADING_HOVER.equals(skill.xpHover())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Average over every entry in {@link #skills()}. Runecrafting and Social are cosmetic and live outside
	 * that list, so they never count; unlevelled skills count as 0 like the in-game Skill Avg.
	 */
	public double skillAverage(boolean withProgress) {
		if (this.skills.isEmpty()) {
			return 0D;
		}
		double sum = 0D;
		for (SkillEntry skill : this.skills) {
			sum += skill.level();
			if (withProgress && !skill.maxed()) {
				sum += Math.max(0F, Math.min(1F, skill.progress()));
			}
		}
		return sum / this.skills.size();
	}

	/** Average of uncapped levels with progress, using SkyHanni's overflow curve past the skill cap. */
	public double overflowSkillAverage() {
		if (this.skills.isEmpty()) {
			return 0D;
		}
		double sum = 0D;
		for (SkillEntry skill : this.skills) {
			sum += Math.max(skill.level(), skill.overflowLevel());
		}
		return sum / this.skills.size();
	}

	public SkillEntry social() {
		return this.social;
	}

	public SkillEntry runecrafting() {
		return this.runecrafting;
	}

	public ActiveSlayerQuest activeSlayer() {
		return this.activeSlayer;
	}

	public EmblemInfo emblems() {
		return this.emblems;
	}

	public fishmod.pv.slayer.SlayerMayorMods slayerMods() {
		return this.slayerMods;
	}

	public ProfileSnapshot withWeightText(String weightText) {
		return new ProfileSnapshot(
			this.playerName, this.playerUuid, this.profileName, this.skyBlockLevel, this.skyBlockXpIntoLevel,
			weightText, this.networthText, this.purseCoins, this.bankCoins, this.bankTransactions,
			this.skills, this.slayers, this.social, this.runecrafting, this.activeSlayer, this.emblems,
			this.slayerMods
		);
	}

	public ProfileSnapshot withNetworthText(String networthText) {
		return new ProfileSnapshot(
			this.playerName, this.playerUuid, this.profileName, this.skyBlockLevel, this.skyBlockXpIntoLevel,
			this.weightText, networthText, this.purseCoins, this.bankCoins, this.bankTransactions,
			this.skills, this.slayers, this.social, this.runecrafting, this.activeSlayer, this.emblems,
			this.slayerMods
		);
	}

	public static ProfileSnapshot loading(String name) {
		String player = name == null || name.isBlank() ? "Player" : name.trim();
		List<SkillEntry> skills = List.of(
			entry("combat", "Combat"), entry("foraging", "Foraging"),
			entry("farming", "Farming"), entry("enchanting", "Enchanting"),
			entry("mining", "Mining"), entry("alchemy", "Alchemy"),
			entry("fishing", "Fishing"), entry("carpentry", "Carpentry"),
			entry("taming", "Taming"), entry("hunting", "Hunting")
		);
		List<SlayerEntry> slayers = List.of(
			slayer("zombie", "Revenant"), slayer("enderman", "Enderman"),
			slayer("spider", "Tarantula"), slayer("blaze", "Blaze"),
			slayer("wolf", "Sven"), slayer("vampire", "Vampire")
		);
		return new ProfileSnapshot(
			player, null, "...", 0, 0, "...", "-", 0D, 0D, List.of(), skills, slayers, entry("social", "Social"),
			entry("runecrafting", "Runecrafting"), null, EmblemInfo.empty()
		);
	}

	private static SkillEntry entry(String id, String name) {
		return new SkillEntry(id, name, 0, 0F, false, LOADING_HOVER);
	}

	private static SlayerEntry slayer(String id, String name) {
		return new SlayerEntry(id, name, 0, 0F, false, "Loading...", List.of());
	}
}
