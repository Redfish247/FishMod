/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.BetterPV;
import fishmod.pv.data.AuctionSnapshot;
import fishmod.pv.data.BestiarySnapshot;
import fishmod.pv.data.CollectionSnapshot;
import fishmod.pv.data.CrimsonSnapshot;
import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.EventsSnapshot;
import fishmod.pv.data.FishingSnapshot;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.GardenSnapshot;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.Leveling;
import fishmod.pv.data.MiningSnapshot;
import fishmod.pv.data.MiscStatsSnapshot;
import fishmod.pv.data.PetSnapshot;
import fishmod.pv.data.PlayerStatsCalculator;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.data.ProfileSnapshot;
import fishmod.pv.data.RepoData;
import fishmod.pv.data.RiftSnapshot;
import fishmod.pv.data.SoftDataFailure;
import fishmod.pv.gui.ArmorStacks;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.NetworthBreakdown;
import fishmod.pv.networth.NetworthCalculator;
import fishmod.pv.weight.WeightBreakdown;
import fishmod.pv.weight.WeightCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/** Home / core profile parsing helpers. Constructs {@link ProfileFetcher.LoadedProfile} for core loads. */
final class ProfileHomeParser {
	private static final String[] HOME_SKILLS = {
		"combat", "foraging", "farming", "enchanting", "mining", "alchemy", "fishing", "carpentry", "taming", "hunting"
	};
	private static final String[][] HOME_SLAYERS = {
		{"zombie", "Revenant"},
		{"enderman", "Enderman"},
		{"spider", "Tarantula"},
		{"blaze", "Blaze"},
		{"wolf", "Sven"},
		{"vampire", "Vampire"}
	};

	private ProfileHomeParser() {
	}

	static ProfileFetcher.LoadedProfile parseHomeCore(String name, UUID uuid, JsonObject root) {
		return parseHomeCore(name, uuid, root, null);
	}

	/**
	 * Minimum valid Home snapshot only. No inventory UI, tab snapshots, museum, or networth.
	 * Fatal validation failures return {@code !ok()} so the loading egg stays up.
	 */
	static ProfileFetcher.LoadedProfile parseHomeCore(
		String name,
		UUID uuid,
		JsonObject root,
		String preferredProfileId
	) {
		try {
			// Home gear, stats and armor each decode the same inventory fields.
			return InventoryDecoder.withSharedDecode(
				() -> parseHomeCoreUnsafe(name, uuid, root, preferredProfileId)
			);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Core profile parse failed for {}", name, exception);
			return ProfileFetcher.failed(
				name,
				exception.getMessage() == null ? "Parse failed" : exception.getMessage()
			);
		}
	}

	static ProfileFetcher.LoadedProfile parseHomeCoreUnsafe(
		String name,
		UUID uuid,
		JsonObject root,
		String preferredProfileId
	) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		if (profiles == null || profiles.isEmpty()) {
			return ProfileFetcher.failed(name, "No SkyBlock profiles");
		}
		JsonObject best = ProfileCoopIndex.pickProfile(profiles, preferredProfileId);
		String cuteName = "Unknown";
		String undashed = HypixelApiClient.undashed(uuid);
		String profileId = null;
		if (best != null) {
			cuteName = best.has("cute_name") ? best.get("cute_name").getAsString() : cuteName;
			profileId = best.has("profile_id") ? best.get("profile_id").getAsString() : profileId;
		}
		List<ProfileFetcher.ProfileChoice> choices = ProfileCoopIndex.listProfileChoices(profiles, profileId, undashed);
		if (best == null) {
			return ProfileFetcher.failed(name, "No usable profile");
		}
		JsonObject members = best.has("members") && best.get("members").isJsonObject()
			? best.getAsJsonObject("members")
			: null;
		JsonObject member = ProfileMuseumLookup.findMember(members, undashed);
		if (member == null) {
			return ProfileFetcher.failed(name, "Member data missing");
		}

		ProfileCoopIndex.warmCoopMemberNames(choices, undashed, name, null);

		BetterPV.LOGGER.info("Parsing home core for {} ({})", name, cuteName);

		Map<String, Leveling.Progress> weightLevels = WeightCalculator.buildLevels(member);
		WeightBreakdown senither = WeightCalculator.senither(member, weightLevels);
		WeightBreakdown lily = WeightCalculator.lily(member, weightLevels);

		List<ProfileSnapshot.SkillEntry> skills = buildHomeSkills(member);
		ProfileSnapshot.SkillEntry social = buildSocial(member);
		ProfileSnapshot.SkillEntry runecrafting = buildRunecrafting(member);
		List<ProfileSnapshot.SlayerEntry> slayers = buildHomeSlayers(member);
		ProfileSnapshot.ActiveSlayerQuest activeSlayer = parseActiveSlayerQuest(member);

		int sbLevel = 0;
		int sbXp = 0;
		JsonObject leveling = Leveling.obj(member.get("leveling"));
		if (leveling != null) {
			Float experience = Leveling.num(leveling.get("experience"));
			if (experience != null) {
				sbLevel = (int) Math.floor(experience / 100F);
				sbXp = Math.round(experience % 100F);
			}
		}
		ProfileSnapshot.EmblemInfo emblems = parseEmblems(leveling);

		double purseCoins = NetworthCalculator.purse(member);
		double bankCoins = NetworthCalculator.bank(best, member);
		List<ProfileSnapshot.BankTransaction> bankTransactions = parseBankTransactions(best);

		ProfileSnapshot snapshot = new ProfileSnapshot(
			name,
			uuid,
			cuteName,
			sbLevel,
			sbXp,
			FormatUtil.weight(senither.total()),
			"...",
			purseCoins,
			bankCoins,
			bankTransactions,
			skills,
			slayers,
			social,
			runecrafting,
			activeSlayer,
			emblems
		);

		Map<String, List<InventoryDecoder.Stack>> homeGear = InventoryDecoder.parseHomeGear(member);
		PlayerStatsSnapshot playerStats = PlayerStatsCalculator.fromMember(member, homeGear);
		ItemStack[] armor = ArmorStacks.fromMember(member);

		MiscStatsSnapshot misc;
		try {
			misc = MiscStatsSnapshot.from(best, member);
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Misc stats parse failed for {}", name, exception);
			misc = MiscStatsSnapshot.empty();
		}

		return new ProfileFetcher.LoadedProfile(
			snapshot,
			DungeonSnapshot.empty(),
			InventorySnapshot.empty(),
			PetSnapshot.empty(),
			AuctionSnapshot.empty(),
			CollectionSnapshot.empty(),
			GardenSnapshot.empty(),
			MiningSnapshot.empty(),
			ForagingSnapshot.empty(),
			FishingSnapshot.empty(),
			CrimsonSnapshot.empty().withPlayerStats(playerStats),
			RiftSnapshot.empty(),
			BestiarySnapshot.empty(),
			EventsSnapshot.empty(),
			misc,
			null,
			profileId,
			root,
			choices,
			senither,
			lily,
			NetworthBreakdown.empty("Loading networth"),
			NetworthBreakdown.empty("Loading networth"),
			NetworthBreakdown.empty("Loading networth"),
			NetworthBreakdown.empty("Loading networth"),
			armor,
			playerStats,
			null
		);
	}

	static List<ProfileSnapshot.SkillEntry> buildHomeSkills(JsonObject member) {
		List<ProfileSnapshot.SkillEntry> skills = new ArrayList<>();
		for (String skill : HOME_SKILLS) {
			int cap = Leveling.skillCap(skill, member);
			Leveling.Progress progress = Leveling.getLevel(
				Leveling.skillTable(skill), Leveling.readSkillXpDouble(member, skill), cap, false
			);
			skills.add(new ProfileSnapshot.SkillEntry(
				skill,
				ProfileParseSupport.title(skill),
				progress.cappedLevel(),
				progress.fill(),
				progress.maxed(),
				progress.skillHover(ProfileParseSupport.title(skill)),
				progress.skillHoverLines(ProfileParseSupport.title(skill)),
				progress.overflowLevel()
			));
		}
		return skills;
	}

	static ProfileSnapshot.SkillEntry buildSocial(JsonObject member) {
		float socialXp = (float) Leveling.readSkillXpDouble(member, "social");
		int socialCap = Leveling.skillCap("social", member);
		Leveling.Progress socialProgress = Leveling.getLevel(Leveling.skillTable("social"), socialXp, socialCap, false);
		return new ProfileSnapshot.SkillEntry(
			"social",
			"Social",
			socialProgress.cappedLevel(),
			socialProgress.fill(),
			socialProgress.maxed(),
			socialProgress.skillHover("Social"),
			socialProgress.skillHoverLines("Social")
		);
	}

	static ProfileSnapshot.SkillEntry buildRunecrafting(JsonObject member) {
		float xp = (float) Leveling.readSkillXpDouble(member, "runecrafting");
		int cap = Leveling.skillCap("runecrafting", member);
		Leveling.Progress progress = Leveling.getLevel(Leveling.skillTable("runecrafting"), xp, cap, false);
		return new ProfileSnapshot.SkillEntry(
			"runecrafting",
			"Runecrafting",
			progress.cappedLevel(),
			progress.fill(),
			progress.maxed(),
			progress.skillHover("Runecrafting"),
			progress.skillHoverLines("Runecrafting")
		);
	}

	static ProfileSnapshot.ActiveSlayerQuest parseActiveSlayerQuest(JsonObject member) {
		JsonObject slayer = Leveling.obj(member == null ? null : member.get("slayer"));
		JsonObject quest = slayer == null ? null : Leveling.obj(slayer.get("slayer_quest"));
		if (quest == null) {
			return null;
		}
		String type = ProfileParseSupport.str(quest.get("type"));
		if (type.isBlank()) {
			return null;
		}
		Float tierRaw = Leveling.num(quest.get("tier"));
		if (tierRaw == null) {
			return null;
		}
		int tier = Math.max(0, Math.round(tierRaw)) + 1;
		boolean spawned = Leveling.num(quest.get("spawn_timestamp")) != null;
		boolean solo = ProfileParseSupport.bool(quest, "solo");
		Float combat = Leveling.num(quest.get("combat_xp"));
		String island = InventoryDecoder.prettyWords(ProfileParseSupport.str(quest.get("last_killed_mob_island")));
		return new ProfileSnapshot.ActiveSlayerQuest(
			type.toLowerCase(Locale.ROOT),
			slayerDisplayName(type),
			tier,
			spawned,
			solo,
			combat == null ? 0F : Math.max(0F, combat),
			island
		);
	}

	static String slayerDisplayName(String type) {
		if (type == null || type.isBlank()) {
			return "";
		}
		String key = type.trim().toLowerCase(Locale.ROOT);
		for (String[] pair : HOME_SLAYERS) {
			if (pair[0].equals(key)) {
				return pair[1];
			}
		}
		return ProfileParseSupport.title(key);
	}

	static ProfileSnapshot.EmblemInfo parseEmblems(JsonObject leveling) {
		if (leveling == null) {
			return ProfileSnapshot.EmblemInfo.empty();
		}
		java.util.LinkedHashSet<String> unlocked = new java.util.LinkedHashSet<>();
		addEmblemIds(unlocked, leveling.get("emblem_unlocks"));
		addEmblemIds(unlocked, leveling.get("unlocked_emblems"));
		addEmblemIds(unlocked, leveling.get("emblems"));
		if (leveling.has("completed_tasks") && leveling.get("completed_tasks").isJsonArray()) {
			for (JsonElement el : leveling.getAsJsonArray("completed_tasks")) {
				String id = emblemId(el);
				if (id.isBlank()) {
					continue;
				}
				String upper = id.toUpperCase(Locale.ROOT);
				if (upper.contains("EMBLEM") || upper.contains("SYMBOL")) {
					unlocked.add(id);
				}
			}
		}
		String selected = ProfileParseSupport.str(leveling.get("selected_symbol"));
		if (selected.isBlank()) {
			selected = ProfileParseSupport.str(leveling.get("selected_emblem"));
		}
		if (!selected.isBlank()) {
			unlocked.add(selected);
		}
		if (unlocked.isEmpty() && selected.isBlank()) {
			return ProfileSnapshot.EmblemInfo.empty();
		}
		return new ProfileSnapshot.EmblemInfo(selected, List.copyOf(unlocked));
	}

	static void addEmblemIds(java.util.Set<String> out, JsonElement raw) {
		if (raw == null || raw.isJsonNull()) {
			return;
		}
		if (raw.isJsonArray()) {
			for (JsonElement el : raw.getAsJsonArray()) {
				String id = emblemId(el);
				if (!id.isBlank()) {
					out.add(id);
				}
			}
			return;
		}
		if (raw.isJsonObject()) {
			for (var entry : raw.getAsJsonObject().entrySet()) {
				String id = emblemId(entry.getValue());
				if (id.isBlank()) {
					id = entry.getKey() == null ? "" : entry.getKey().trim();
				}
				if (!id.isBlank()) {
					out.add(id);
				}
			}
		}
	}

	static String emblemId(JsonElement el) {
		if (el == null || el.isJsonNull()) {
			return "";
		}
		if (el.isJsonPrimitive()) {
			return ProfileParseSupport.str(el);
		}
		if (!el.isJsonObject()) {
			return "";
		}
		JsonObject obj = el.getAsJsonObject();
		String id = ProfileParseSupport.str(obj.get("id"));
		if (id.isBlank()) {
			id = ProfileParseSupport.str(obj.get("emblem"));
		}
		if (id.isBlank()) {
			id = ProfileParseSupport.str(obj.get("symbol"));
		}
		if (id.isBlank()) {
			id = ProfileParseSupport.str(obj.get("name"));
		}
		return id;
	}

	static List<ProfileSnapshot.SlayerEntry> buildHomeSlayers(JsonObject member) {
		List<ProfileSnapshot.SlayerEntry> slayers = new ArrayList<>();
		for (String[] pair : HOME_SLAYERS) {
			float xp = Leveling.readSlayerXp(member, pair[0]);
			JsonArray slayerTable = RepoData.slayerXp(pair[0]);
			int slayerCap = slayerTable == null || slayerTable.isEmpty() ? 9 : slayerTable.size();
			Leveling.Progress progress = Leveling.getLevel(slayerTable, xp, slayerCap, true);
			int[] kills = Leveling.readSlayerBossKills(member, pair[0]);
			List<Integer> killList = new ArrayList<>(kills.length);
			for (int kill : kills) {
				killList.add(kill);
			}
			slayers.add(new ProfileSnapshot.SlayerEntry(
				pair[0],
				pair[1],
				(int) Math.floor(progress.level()),
				progress.fill(),
				progress.maxed(),
				(float) progress.totalXp(),
				progress.slayerHoverWithKills(pair[1], pair[0], kills),
				killList,
				progress.slayerHoverLinesWithKills(pair[1], pair[0], kills)
			));
		}
		return slayers;
	}

	static List<ProfileSnapshot.BankTransaction> parseBankTransactions(JsonObject profileRoot) {
		if (profileRoot == null) {
			return List.of();
		}
		JsonObject banking = Leveling.obj(profileRoot.get("banking"));
		if (banking == null || !(banking.get("transactions") instanceof JsonArray arr) || arr.isEmpty()) {
			return List.of();
		}
		List<ProfileSnapshot.BankTransaction> out = new ArrayList<>(arr.size());
		for (JsonElement el : arr) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject tx = el.getAsJsonObject();
			String action = tx.has("action") && tx.get("action").isJsonPrimitive()
				? tx.get("action").getAsString()
				: "";
			double amount = 0D;
			if (tx.has("amount") && tx.get("amount").isJsonPrimitive()) {
				try {
					amount = tx.get("amount").getAsDouble();
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					amount = 0D;
				}
			}
			String initiator = "";
			if (tx.has("initiator_name") && tx.get("initiator_name").isJsonPrimitive()) {
				initiator = tx.get("initiator_name").getAsString();
				if (initiator != null) {
					initiator = initiator.replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "").trim();
				} else {
					initiator = "";
				}
			}
			long ts = 0L;
			if (tx.has("timestamp") && tx.get("timestamp").isJsonPrimitive()) {
				try {
					ts = tx.get("timestamp").getAsLong();
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					ts = 0L;
				}
			}
			if (action.isBlank() && amount <= 0D && ts <= 0L) {
				continue;
			}
			out.add(new ProfileSnapshot.BankTransaction(action, amount, initiator, ts));
		}
		out.sort((a, b) -> Long.compare(b.timestampMs(), a.timestampMs()));
		if (out.size() > 24) {
			return List.copyOf(out.subList(0, 24));
		}
		return List.copyOf(out);
	}
}
