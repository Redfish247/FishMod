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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Event tabs: Bingo (resources + bingo profile) and Chocolate Factory ({@code events.easter}).
 */
public final class EventsSnapshot {
	public record Employee(String id, String name, int level) {
	}

	public record Rabbit(String id, String name, int count, String rarity) {
		public Rabbit {
			rarity = rarity == null || rarity.isBlank() ? "COMMON" : rarity.toUpperCase(Locale.ROOT);
		}
	}

	/** Years are SkyBlock years; 0 means the API didn't record one. */
	public record Chocobit(int id, int ownedYear, int expiryYear) {
		public boolean expired(int currentYear) {
			return expiryYear > 0 && expiryYear < currentYear;
		}

		public static int active(List<Chocobit> bits, int currentYear) {
			int n = 0;
			for (Chocobit bit : bits) {
				if (!bit.expired(currentYear)) {
					n++;
				}
			}
			return n;
		}
	}

	public record BingoGoal(String id, String name, String lore, long progress, long required, boolean community) {
	}

	public record BingoEvent(int key, long points, List<String> completedGoals) {
	}

	public record Chocolate(
		long chocolate,
		long totalChocolate,
		long chocolateSincePrestige,
		int chocolateLevel,
		int clickUpgrades,
		int multiplierUpgrades,
		int rabbitRarityUpgrades,
		int barnCapacityLevel,
		int timeTowerLevel,
		int timeTowerCharges,
		long timeTowerActivationMs,
		List<Employee> employees,
		int uniqueRabbits,
		int totalRabbitDuplicates,
		long breakfastEggs,
		long lunchEggs,
		long dinnerEggs,
		int hitmenSlots,
		long missedEggs,
		int cocoaFortuneUpgrades,
		long chocolateSpent,
		List<Rabbit> topRabbits,
		Map<String, Integer> rabbitCounts,
		String selectedFaction,
		int factionLevel,
		List<Chocobit> chocobits,
		int chocobitsFound,
		boolean chocobitsPresent
	) {
		public static Chocolate empty() {
			return new Chocolate(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(),
				Map.of(), "", 0, List.of(), 0, false);
		}

		public Set<String> ownedRabbits() {
			return rabbitCounts.keySet();
		}

		public ChocolateFactoryData.Prestige prestige() {
			return ChocolateFactoryData.prestige(chocolateLevel, chocolateSincePrestige);
		}

		public ChocolateFactoryData.Hitmen hitmen() {
			return ChocolateFactoryData.hitmen(hitmenSlots);
		}

		public boolean present() {
			return totalChocolate > 0 || chocolate > 0 || chocolateLevel > 0 || !employees.isEmpty();
		}
	}

	public record Bingo(
		boolean hasBingoProfile,
		String bingoProfileName,
		long bingoFirstJoinMs,
		int currentEventId,
		String currentEventName,
		String currentModifier,
		long currentStartMs,
		long currentEndMs,
		List<BingoGoal> currentGoals,
		List<BingoEvent> history,
		long totalPoints,
		int eventsPlayed
	) {
		public static Bingo empty() {
			return new Bingo(false, "", 0L, 0, "", "", 0L, 0L, List.of(), List.of(), 0L, 0);
		}

		public boolean present() {
			return hasBingoProfile || !currentGoals.isEmpty() || !history.isEmpty();
		}
	}

	private final Chocolate chocolate;
	private final Bingo bingo;

	private EventsSnapshot(Chocolate chocolate, Bingo bingo) {
		this.chocolate = chocolate == null ? Chocolate.empty() : chocolate;
		this.bingo = bingo == null ? Bingo.empty() : bingo;
	}

	public static EventsSnapshot empty() {
		return new EventsSnapshot(Chocolate.empty(), Bingo.empty());
	}

	public Chocolate chocolate() {
		return chocolate;
	}

	public Bingo bingo() {
		return bingo;
	}

	public EventsSnapshot withBingoResources(JsonObject resources) {
		Bingo merged = mergeBingoResources(this.bingo, resources);
		return new EventsSnapshot(this.chocolate, merged);
	}

	public EventsSnapshot withBingoHistory(JsonObject playerBingo) {
		Bingo merged = mergeBingoHistory(this.bingo, playerBingo);
		return new EventsSnapshot(this.chocolate, merged);
	}

	public static EventsSnapshot fromMember(
		JsonObject member,
		JsonObject profilesRoot,
		UUID uuid
	) {
		Chocolate chocolate = parseChocolate(member);
		Bingo bingo = parseBingoProfile(profilesRoot, uuid);
		return new EventsSnapshot(chocolate, bingo);
	}

	private static Chocolate parseChocolate(JsonObject member) {
		JsonObject events = Leveling.obj(member == null ? null : member.get("events"));
		JsonObject easter = events == null ? null : Leveling.obj(events.get("easter"));
		if (easter == null) {
			return Chocolate.empty();
		}
		long chocolate = longOf(easter, "chocolate");
		long total = longOf(easter, "total_chocolate");
		long sincePrestige = longOf(easter, "chocolate_since_prestige");
		int level = intOf(easter, "chocolate_level");
		int click = intOf(easter, "click_upgrades");
		int multi = intOf(easter, "chocolate_multiplier_upgrades");
		int rarity = intOf(easter, "rabbit_rarity_upgrades");
		int barn = intOf(easter, "rabbit_barn_capacity_level");

		JsonObject tower = Leveling.obj(easter.get("time_tower"));
		int towerLevel = tower == null ? 0 : intOf(tower, "level");
		int towerCharges = tower == null ? 0 : intOf(tower, "charges");
		long towerActive = tower == null ? 0L : longOf(tower, "activation_time");

		List<Employee> employees = new ArrayList<>();
		JsonObject emp = Leveling.obj(easter.get("employees"));
		if (emp != null) {
			for (var entry : emp.entrySet()) {
				String id = entry.getKey();
				if (id == null || !entry.getValue().isJsonPrimitive()) {
					continue;
				}
				int lvl = Math.max(0, (int) Math.round(num(entry.getValue())));
				employees.add(new Employee(id, ChocolateEmployees.displayName(id, prettyId(id)), lvl));
			}
			employees.sort(Comparator
				.comparingInt((Employee e) -> ChocolateEmployees.orderOf(e.id()))
				.thenComparing(Employee::name));
		}

		JsonObject rabbits = Leveling.obj(easter.get("rabbits"));
		int unique = 0;
		int duplicates = 0;
		List<Rabbit> top = new ArrayList<>();
		Map<String, Integer> owned = new HashMap<>();
		long breakfast = 0;
		long lunch = 0;
		long dinner = 0;
		String faction = "";
		int factionLevel = 0;
		if (rabbits != null) {
			JsonObject eggs = Leveling.obj(rabbits.get("collected_eggs"));
			if (eggs != null) {
				breakfast = longOf(eggs, "breakfast");
				lunch = longOf(eggs, "lunch");
				dinner = longOf(eggs, "dinner");
			}
			faction = str(rabbits, "selected_faction").toLowerCase(Locale.ROOT);
			factionLevel = intOf(rabbits, "faction_level");
			for (var entry : rabbits.entrySet()) {
				String id = entry.getKey();
				// Faction fields share the rabbits object with the rabbit counts.
				if (id == null || "collected_eggs".equals(id) || "collected_locations".equals(id)
					|| "selected_faction".equals(id) || "faction_level".equals(id)) {
					continue;
				}
				if (!entry.getValue().isJsonPrimitive()) {
					continue;
				}
				int count = Math.max(0, (int) Math.round(num(entry.getValue())));
				if (count <= 0) {
					continue;
				}
				unique++;
				duplicates += count;
				owned.merge(id.toLowerCase(Locale.ROOT), count, Integer::sum);
				top.add(new Rabbit(id, prettyId(id), count, HoppityRabbitsData.rarityOf(id)));
			}
			top.sort(Comparator.comparingInt(Rabbit::count).reversed().thenComparing(Rabbit::name));
			if (top.size() > 12) {
				top = new ArrayList<>(top.subList(0, 12));
			}
		}

		JsonObject hitmen = Leveling.obj(easter.get("rabbit_hitmen"));
		int hitmenSlots = hitmen == null ? 0 : intOf(hitmen, "rabbit_hitmen_slots");
		long missed = hitmen == null ? 0L : longOf(hitmen, "missed_uncollected_eggs");

		JsonObject shop = Leveling.obj(easter.get("shop"));
		int fortune = shop == null ? 0 : intOf(shop, "cocoa_fortune_upgrades");
		long spent = shop == null ? 0L : longOf(shop, "chocolate_spent");

		JsonObject bits = Leveling.obj(easter.get("chocobits"));
		int bitsFound = bits == null ? 0 : intOf(bits, "total_found");
		List<Chocobit> chocobits = parseChocobits(bits);

		return new Chocolate(
			chocolate, total, sincePrestige, level, click, multi, rarity, barn,
			towerLevel, towerCharges, towerActive,
			List.copyOf(employees), unique, duplicates,
			breakfast, lunch, dinner, hitmenSlots, missed, fortune, spent,
			List.copyOf(top), Map.copyOf(owned), faction, factionLevel,
			List.copyOf(chocobits), bitsFound, bits != null
		);
	}

	/** Soonest expiry first; bits without a recorded expiry go last. */
	static List<Chocobit> parseChocobits(JsonObject bits) {
		List<Chocobit> out = new ArrayList<>();
		if (bits == null || !bits.has("owned") || !bits.get("owned").isJsonArray()) {
			return out;
		}
		for (JsonElement el : bits.getAsJsonArray("owned")) {
			JsonObject bit = Leveling.obj(el);
			int id = bit == null ? 0 : intOf(bit, "id");
			if (id <= 0) {
				continue;
			}
			out.add(new Chocobit(id, Math.max(0, intOf(bit, "owned_year")), Math.max(0, intOf(bit, "expiry_year"))));
		}
		out.sort(Comparator
			.comparingInt((Chocobit b) -> b.expiryYear() > 0 ? b.expiryYear() : Integer.MAX_VALUE)
			.thenComparingInt(Chocobit::id));
		return out;
	}

	private static Bingo parseBingoProfile(JsonObject profilesRoot, UUID uuid) {
		if (profilesRoot == null || uuid == null) {
			return Bingo.empty();
		}
		JsonArray profiles = profilesRoot.has("profiles") && profilesRoot.get("profiles").isJsonArray()
			? profilesRoot.getAsJsonArray("profiles")
			: null;
		if (profiles == null) {
			return Bingo.empty();
		}
		String undashed = uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
		for (JsonElement el : profiles) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject profile = el.getAsJsonObject();
			String mode = profile.has("game_mode") && profile.get("game_mode").isJsonPrimitive()
				? profile.get("game_mode").getAsString()
				: "";
			if (!"bingo".equalsIgnoreCase(mode)) {
				continue;
			}
			String cute = profile.has("cute_name") && profile.get("cute_name").isJsonPrimitive()
				? profile.get("cute_name").getAsString()
				: "Bingo";
			JsonObject members = Leveling.obj(profile.get("members"));
			if (members == null) {
				return new Bingo(true, cute, 0L, 0, "", "", 0L, 0L, List.of(), List.of(), 0L, 0);
			}
			JsonObject member = null;
			for (var entry : members.entrySet()) {
				String key = entry.getKey() == null ? "" : entry.getKey().replace("-", "").toLowerCase(Locale.ROOT);
				if (key.equals(undashed) && entry.getValue().isJsonObject()) {
					member = entry.getValue().getAsJsonObject();
					break;
				}
			}
			if (member == null) {
				for (var entry : members.entrySet()) {
					if (entry.getValue().isJsonObject()) {
						member = entry.getValue().getAsJsonObject();
						break;
					}
				}
			}
			long join = 0L;
			JsonObject profileMeta = member == null ? null : Leveling.obj(member.get("profile"));
			if (profileMeta != null) {
				join = longOf(profileMeta, "first_join");
			}
			return new Bingo(true, cute, join, 0, "", "", 0L, 0L, List.of(), List.of(), 0L, 0);
		}
		return Bingo.empty();
	}

	private static Bingo mergeBingoResources(Bingo base, JsonObject resources) {
		Bingo b = base == null ? Bingo.empty() : base;
		if (resources == null) {
			return b;
		}
		int id = intOf(resources, "id");
		String name = str(resources, "name");
		String modifier = str(resources, "modifier");
		long start = longOf(resources, "start");
		long end = longOf(resources, "end");
		List<BingoGoal> goals = new ArrayList<>();
		if (resources.has("goals") && resources.get("goals").isJsonArray()) {
			for (JsonElement el : resources.getAsJsonArray("goals")) {
				if (el == null || !el.isJsonObject()) {
					continue;
				}
				JsonObject g = el.getAsJsonObject();
				String gid = str(g, "id");
				String gname = str(g, "name");
				String lore = str(g, "lore");
				long progress = longOf(g, "progress");
				long required = 0L;
				if (g.has("requiredAmount") && g.get("requiredAmount").isJsonPrimitive()) {
					required = longOf(g, "requiredAmount");
				} else if (g.has("tiers") && g.get("tiers").isJsonArray()) {
					JsonArray tiers = g.getAsJsonArray("tiers");
					if (!tiers.isEmpty() && tiers.get(tiers.size() - 1).isJsonPrimitive()) {
						required = Math.round(num(tiers.get(tiers.size() - 1)));
					}
				}
				boolean community = g.has("tiers") && g.get("tiers").isJsonArray();
				goals.add(new BingoGoal(gid, gname.isBlank() ? prettyId(gid) : strip(gname), strip(lore), progress, required, community));
			}
		}
		return new Bingo(
			b.hasBingoProfile(), b.bingoProfileName(), b.bingoFirstJoinMs(),
			id, name, modifier, start, end, List.copyOf(goals),
			b.history(), b.totalPoints(), b.eventsPlayed()
		);
	}

	private static Bingo mergeBingoHistory(Bingo base, JsonObject playerBingo) {
		Bingo b = base == null ? Bingo.empty() : base;
		if (playerBingo == null || !playerBingo.has("events") || !playerBingo.get("events").isJsonArray()) {
			return b;
		}
		List<BingoEvent> history = new ArrayList<>();
		long points = 0L;
		for (JsonElement el : playerBingo.getAsJsonArray("events")) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject ev = el.getAsJsonObject();
			int key = intOf(ev, "key");
			long pts = longOf(ev, "points");
			points += pts;
			List<String> goals = new ArrayList<>();
			if (ev.has("completed_goals") && ev.get("completed_goals").isJsonArray()) {
				for (JsonElement g : ev.getAsJsonArray("completed_goals")) {
					if (g != null && g.isJsonPrimitive()) {
						goals.add(g.getAsString());
					} else if (g != null && g.isJsonArray()) {
						for (JsonElement nested : g.getAsJsonArray()) {
							if (nested != null && nested.isJsonPrimitive()) {
								goals.add(nested.getAsString());
							}
						}
					}
				}
			}
			history.add(new BingoEvent(key, pts, List.copyOf(goals)));
		}
		history.sort(Comparator.comparingInt(BingoEvent::key).reversed());
		return new Bingo(
			b.hasBingoProfile(), b.bingoProfileName(), b.bingoFirstJoinMs(),
			b.currentEventId(), b.currentEventName(), b.currentModifier(),
			b.currentStartMs(), b.currentEndMs(), b.currentGoals(),
			List.copyOf(history), points, history.size()
		);
	}

	private static String str(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			return obj.get(key).getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static int intOf(JsonObject obj, String key) {
		return (int) Math.max(0, Math.round(num(obj == null ? null : obj.get(key))));
	}

	private static long longOf(JsonObject obj, String key) {
		return Math.max(0L, Math.round(num(obj == null ? null : obj.get(key))));
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

	private static String prettyId(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String[] parts = id.toLowerCase(Locale.ROOT).split("[_\\-]+");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1));
			}
		}
		return out.toString();
	}

	private static String strip(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replaceAll("§[0-9a-fk-or]", "").trim();
	}
}
