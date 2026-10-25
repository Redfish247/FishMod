/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonObject;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.NbtAttrs;
import fishmod.pv.price.HypixelItemsCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Kuudra gear checklists. Hard requirements ({@link CheckState#MISSING}) are kept apart from recommended
 * upgrades ({@link CheckState#UPGRADE}) so a usable item is never reported as missing just because it lacks
 * an optimisation. Armour sets are summarised per slot using the best matching piece for that set's rules,
 * and every set searches all owned pieces on its own.
 */
public final class KuudraGearChecks {
	public enum Status { COMPLETE, INCOMPLETE, MISSING, UNKNOWN }

	public enum CheckState { OK, UPGRADE, MISSING, WARN, INFO, UNKNOWN }

	/** {@code upgradeTo} is the recommended level or tier when the value is below it. */
	public record Check(String label, String value, CheckState state, String upgradeTo, boolean highlight) {
		static Check of(String label, String value, CheckState state) {
			return new Check(label, value, state, null, false);
		}
	}

	/** {@code value} is set for slot style rows ("Chestplate: Infernal Terror"), null for item rows. */
	public record Entry(
		String name, String value, String iconId, Status status, List<Check> checks, String source,
		InventoryDecoder.Stack stack, PetSnapshot.Entry pet
	) {
	}

	public record Group(
		String title, String iconId, Status status, int owned, List<Entry> entries, List<Check> checks,
		boolean informational
	) {
	}

	public record Result(List<Group> items, List<Group> armour) {
		public static Result empty() {
			return new Result(List.of(), List.of());
		}
	}

	public record Owned(InventoryDecoder.Stack stack, String source) {
	}

	/** {@code abilityId} is null when the profile has no HOTM data, blank when nothing is selected. */
	public record Inputs(
		List<Owned> items,
		boolean inventoryKnown,
		List<PetSnapshot.Entry> pets,
		boolean petsKnown,
		List<InventoryDecoder.WardrobePreset> presets,
		String abilityId,
		Function<String, String> itemName,
		Function<InventoryDecoder.Stack, String> category,
		Function<String, String> abilityName
	) {
	}

	// Item sources Kuudra gear can be pulled from, in dedup priority order.
	private static final String[][] SOURCES = {
		{ "armor", "Armour" }, { "equipment", "Equipment" }, { "inventory", "Inventory" },
		{ "enderchest", "Ender Chest" }, { "storage", "Backpack" }, { "wardrobe", "Wardrobe" },
		{ "personal_vault", "Personal Vault" }
	};
	private static final Set<String> WITHER_BLADES = Set.of("HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE", "NECRON_BLADE");
	private static final Set<String> KUUDRA_SETS = Set.of("CRIMSON", "AURORA", "TERROR", "HOLLOW", "FERVOR");
	private static final Set<String> RCM_REFORGES = Set.of("necrotic", "loving");
	private static final List<String> SLOTS = List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");
	private static final List<String> BODY_SLOTS = List.of("CHESTPLATE", "LEGGINGS", "BOOTS");
	private static final List<String> RARITIES = List.of(
		"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL");
	// Max levels from the NEU enchanted book items (e.g. CUBISM;6, PROSECUTE;6, TABASCO;3).
	private static final int MAX_CUBISM = 6;
	private static final int MAX_SMOLDERING = 5;
	private static final int MAX_POWER = 7;
	private static final int MAX_GIANT_KILLER = 7;
	private static final int MAX_PROSECUTE = 6;
	private static final int MAX_TABASCO = 3;
	private static final int MAX_ULTIMATE = 5;
	private static final int MAX_CANDY = 10;
	private static final String[] DUPLEX = { "ultimate_reiterate", "ultimate_duplex" };
	private static final Check API_OFF = Check.of("Inventory API", "Off", CheckState.UNKNOWN);
	public static final String MISSING_TEXT = "Not found";

	private KuudraGearChecks() {
	}

	public static Result fromMember(
		JsonObject member, Map<String, List<InventoryDecoder.Stack>> categories, PetSnapshot pets
	) {
		if (member == null) {
			return Result.empty();
		}
		boolean petsKnown = member.has("pets_data") || member.has("pets");
		return compute(new Inputs(
			collect(categories),
			InventoryDecoder.hasInventoryApi(member),
			pets == null ? List.of() : pets.pets(),
			petsKnown,
			InventoryDecoder.wardrobePresets(member),
			selectedAbility(member),
			KuudraGearChecks::itemName,
			KuudraGearChecks::category,
			MiningHotmData::displayName
		));
	}

	public static String previewTitle(Entry entry) {
		if (entry.pet() != null) {
			return PetLoreResolver.displayNameFor(entry.pet());
		}
		if (entry.stack() == null) {
			return entry.name();
		}
		String named = CrimsonKuudraCard.ensureColoredName(entry.stack().displayName(), entry.iconId());
		return CrimsonKuudraCard.withStarsInName(named.isBlank() ? entry.name() : named, stars(entry.stack()));
	}

	public static List<String> previewLore(Entry entry) {
		if (entry.pet() != null) {
			return PetLoreResolver.loreFor(entry.pet());
		}
		return entry.stack() == null || entry.stack().lore() == null ? List.of() : entry.stack().lore();
	}

	/** Flattens the accessible item sources, keeping one copy per item uuid. */
	static List<Owned> collect(Map<String, List<InventoryDecoder.Stack>> categories) {
		List<Owned> out = new ArrayList<>();
		if (categories == null) {
			return out;
		}
		Set<String> seen = new HashSet<>();
		for (String[] source : SOURCES) {
			for (InventoryDecoder.Stack stack : categories.getOrDefault(source[0], List.of())) {
				if (stack == null || stack.id() == null || stack.id().isBlank()) {
					continue;
				}
				String uuid = NbtAttrs.string(stack.extraAttributes(), "uuid");
				if (uuid != null && !uuid.isBlank() && !seen.add(uuid)) {
					continue;
				}
				out.add(new Owned(stack, source[1]));
			}
		}
		return out;
	}

	static Result compute(Inputs in) {
		List<Group> items = new ArrayList<>();
		items.add(witherBlades(in));
		items.add(goldenDragons(in));
		addTerminators(in, items);
		items.add(ragnarock(in));
		items.add(sosFlare(in));
		items.add(rendBonemerang(in));
		items.add(swarmAots(in));
		items.add(lastBreath(in));
		items.add(stun(in));

		List<Group> armour = new ArrayList<>();
		armour.add(dpsTerror(in));
		armour.add(rcm(in));
		armour.add(hollow(in));
		armour.add(reaper(in));
		armour.add(tuxedo(in));
		armour.add(racingHelmet(in));
		armour.add(crownOfAvarice(in));
		return new Result(List.copyOf(items), List.copyOf(armour));
	}

	private static Group witherBlades(Inputs in) {
		Numbering numbering = new Numbering();
		List<Entry> entries = new ArrayList<>();
		for (Owned owned : matching(in, WITHER_BLADES::contains)) {
			Map<String, Integer> ench = enchants(owned.stack());
			String ultimate = ultimateKey(ench);
			List<Check> checks = new ArrayList<>();
			checks.add(ultimate == null
				? new Check("Ultimate", "None", CheckState.UPGRADE, null, false)
				: ultimateCheck(ultimate, ench.get(ultimate)));
			checks.add(starsCheck(owned.stack()));
			checks.add(reforgeCheck(owned.stack()));
			entries.add(entry(in, owned, numbering, checks));
		}
		return itemGroup(in, "Wither Blades", "HYPERION", entries, List.of());
	}

	private static Group goldenDragons(Inputs in) {
		if (!in.petsKnown()) {
			return new Group("Golden Dragon", "GOLDEN_DRAGON;4", Status.UNKNOWN, 0, List.of(),
				List.of(Check.of("Pets", "Not in API", CheckState.UNKNOWN)), false);
		}
		List<Entry> entries = new ArrayList<>();
		int n = 0;
		for (PetSnapshot.Entry pet : in.pets()) {
			if (pet == null || !"GOLDEN_DRAGON".equalsIgnoreCase(pet.type())) {
				continue;
			}
			n++;
			List<Check> checks = new ArrayList<>();
			boolean maxed = pet.level() >= pet.maxLevel();
			checks.add(new Check("Level", String.valueOf(pet.level()), maxed ? CheckState.OK : CheckState.UPGRADE,
				maxed ? null : String.valueOf(pet.maxLevel()), false));
			checks.add(Check.of("Pet item", pet.hasHeldItem() ? in.itemName().apply(pet.heldItem()) : "None",
				CheckState.INFO));
			checks.add(Check.of("Candies", pet.candyUsed() + " / " + MAX_CANDY, CheckState.INFO));
			entries.add(new Entry("Golden Dragon (" + n + ")", null, pet.neuId(), statusOf(checks), checks, "Pets",
				null, pet));
		}
		return new Group("Golden Dragon", "GOLDEN_DRAGON;4", groupStatus(entries), entries.size(), entries,
			List.of(), false);
	}

	private static void addTerminators(Inputs in, List<Group> out) {
		Numbering duplexN = new Numbering();
		Numbering rendN = new Numbering();
		Numbering otherN = new Numbering();
		List<Entry> duplex = new ArrayList<>();
		List<Entry> rend = new ArrayList<>();
		List<Entry> other = new ArrayList<>();
		for (Owned owned : matching(in, "TERMINATOR"::equals)) {
			Map<String, Integer> ench = enchants(owned.stack());
			int duplexLvl = level(ench, DUPLEX);
			int rendLvl = level(ench, "ultimate_rend");
			List<Check> checks = new ArrayList<>();
			if (duplexLvl > 0) {
				checks.add(enchCheck("Duplex", duplexLvl, MAX_ULTIMATE, CheckState.MISSING));
				checks.add(starsCheck(owned.stack()));
				int cubism = level(ench, "cubism");
				Check cubismCheck = enchCheck("Cubism", cubism, MAX_CUBISM, CheckState.UPGRADE);
				checks.add(new Check(cubismCheck.label(), cubismCheck.value(), cubismCheck.state(),
					cubismCheck.upgradeTo(), cubism >= MAX_CUBISM));
				checks.add(enchCheck("Smoldering", level(ench, "smoldering"), MAX_SMOLDERING, CheckState.UPGRADE));
				checks.add(enchCheck("Power", level(ench, "power"), MAX_POWER, CheckState.UPGRADE));
				checks.add(piercingWarning(level(ench, "piercing")));
				checks.add(reforgeCheck(owned.stack()));
				duplex.add(entry(in, owned, duplexN, checks, "Duplex Terminator"));
			} else if (rendLvl > 0) {
				checks.add(enchCheck("Rend", rendLvl, MAX_ULTIMATE, CheckState.MISSING));
				checks.add(enchCheck("Cubism", level(ench, "cubism"), MAX_CUBISM, CheckState.UPGRADE));
				checks.add(enchCheck("Power", level(ench, "power"), MAX_POWER, CheckState.UPGRADE));
				checks.add(enchCheck("Smoldering", level(ench, "smoldering"), MAX_SMOLDERING, CheckState.UPGRADE));
				checks.add(starsCheck(owned.stack()));
				checks.add(reforgeCheck(owned.stack()));
				rend.add(entry(in, owned, rendN, checks, "Rend Terminator"));
			} else {
				String ultimate = ultimateKey(ench);
				int ultLvl = ultimate == null ? 0 : ench.get(ultimate);
				checks.add(Check.of("Ultimate",
					ultimate == null ? "None" : ultimateName(ultimate) + " " + roman(ultLvl), CheckState.INFO));
				int power = level(ench, "power");
				int cubism = level(ench, "cubism");
				checks.add(Check.of("Power", power > 0 ? roman(power) : "None", CheckState.INFO));
				checks.add(Check.of("Cubism", cubism > 0 ? roman(cubism) : "None", CheckState.INFO));
				checks.add(starsCheck(owned.stack()));
				checks.add(reforgeCheck(owned.stack()));
				other.add(entry(in, owned, otherN, checks, "Terminator"));
			}
		}
		out.add(itemGroup(in, "Duplex Terminator", "TERMINATOR", duplex, List.of()));
		out.add(itemGroup(in, "Rend Terminator", "TERMINATOR", rend, List.of()));
		if (!other.isEmpty()) {
			out.add(new Group("Other Terminators", "TERMINATOR", Status.COMPLETE, other.size(), other, List.of(), true));
		}
	}

	private static Group ragnarock(Inputs in) {
		Numbering numbering = new Numbering();
		List<Entry> entries = new ArrayList<>();
		for (Owned owned : matching(in, "RAGNAROCK_AXE"::equals)) {
			Map<String, Integer> ench = enchants(owned.stack());
			List<Check> checks = new ArrayList<>();
			checks.add(enchCheck("Chimera", level(ench, "ultimate_chimera"), MAX_ULTIMATE, CheckState.MISSING));
			checks.add(starsCheck(owned.stack()));
			checks.add(reforgeCheck(owned.stack()));
			entries.add(entry(in, owned, numbering, checks));
		}
		return itemGroup(in, "Ragnarock", "RAGNAROCK_AXE", entries, List.of());
	}

	private static Group sosFlare(Inputs in) {
		List<Entry> entries = new ArrayList<>();
		int total = 0;
		for (Owned owned : matching(in, "SOS_FLARE"::equals)) {
			int count = Math.max(1, owned.stack().count());
			total += count;
			List<Check> checks = count > 1 ? List.of(Check.of("Count", String.valueOf(count), CheckState.INFO)) : List.of();
			entries.add(new Entry(in.itemName().apply("SOS_FLARE"), null, "SOS_FLARE", Status.COMPLETE, checks,
				owned.source(), owned.stack(), null));
		}
		Group group = itemGroup(in, "SOS Flare", "SOS_FLARE", entries, List.of());
		return new Group(group.title(), group.iconId(), group.status(), total, entries, group.checks(), false);
	}

	/** Copies without Rend are only listed when no Rend copy exists, so the reason is visible. */
	private static Group rendBonemerang(Inputs in) {
		Numbering numbering = new Numbering();
		List<Entry> withRend = new ArrayList<>();
		List<Entry> without = new ArrayList<>();
		for (Owned owned : matching(in, "BONE_BOOMERANG"::equals)) {
			Map<String, Integer> ench = enchants(owned.stack());
			int rendLvl = level(ench, "ultimate_rend");
			int piercing = level(ench, "piercing");
			List<Check> checks = new ArrayList<>();
			checks.add(enchCheck("Rend", rendLvl, MAX_ULTIMATE, CheckState.MISSING));
			checks.add(enchCheck("Cubism", level(ench, "cubism"), MAX_CUBISM, CheckState.UPGRADE));
			checks.add(enchCheck("Power", level(ench, "power"), MAX_POWER, CheckState.UPGRADE));
			checks.add(enchCheck("Smoldering", level(ench, "smoldering"), MAX_SMOLDERING, CheckState.UPGRADE));
			checks.add(enchCheck("Tabasco", level(ench, "tabasco"), MAX_TABASCO, CheckState.UPGRADE));
			checks.add(Check.of("Piercing", piercing > 0 ? roman(piercing) : "None", CheckState.INFO));
			checks.add(starsCheck(owned.stack()));
			(rendLvl > 0 ? withRend : without).add(entry(in, owned, numbering, checks, "Bonemerang"));
		}
		return itemGroup(in, "Rend Bonemerang", "BONE_BOOMERANG", withRend.isEmpty() ? without : withRend, List.of());
	}

	private static Group swarmAots(Inputs in) {
		Numbering numbering = new Numbering();
		List<Entry> withSwarm = new ArrayList<>();
		List<Entry> without = new ArrayList<>();
		for (Owned owned : matching(in, "AXE_OF_THE_SHREDDED"::equals)) {
			Map<String, Integer> ench = enchants(owned.stack());
			int swarm = level(ench, "ultimate_swarm");
			List<Check> checks = new ArrayList<>();
			checks.add(enchCheck("Swarm", swarm, MAX_ULTIMATE, CheckState.MISSING));
			checks.add(enchCheck("Cubism", level(ench, "cubism"), MAX_CUBISM, CheckState.UPGRADE));
			checks.add(enchCheck("Giant Killer", level(ench, "giant_killer"), MAX_GIANT_KILLER, CheckState.UPGRADE));
			checks.add(enchCheck("Prosecute", level(ench, "prosecute"), MAX_PROSECUTE, CheckState.UPGRADE));
			checks.add(enchCheck("Smoldering", level(ench, "smoldering"), MAX_SMOLDERING, CheckState.UPGRADE));
			checks.add(enchCheck("Tabasco", level(ench, "tabasco"), MAX_TABASCO, CheckState.UPGRADE));
			checks.add(starsCheck(owned.stack()));
			(swarm > 0 ? withSwarm : without).add(entry(in, owned, numbering, checks, "AOTS"));
		}
		return itemGroup(in, "Swarm AOTS", "AXE_OF_THE_SHREDDED", withSwarm.isEmpty() ? without : withSwarm, List.of());
	}

	private static Group lastBreath(Inputs in) {
		Numbering numbering = new Numbering();
		List<Entry> entries = new ArrayList<>();
		for (Owned owned : matching(in, "LAST_BREATH"::equals)) {
			int duplex = level(enchants(owned.stack()), DUPLEX);
			Check check = duplex == 1
				? Check.of("Duplex", "I", CheckState.OK)
				: Check.of("Duplex", duplex > 1 ? roman(duplex) + ", needs I" : "None, needs I", CheckState.MISSING);
			entries.add(entry(in, owned, numbering, List.of(check, starsCheck(owned.stack()))));
		}
		return itemGroup(in, "Last Breath (Duplex I)", "LAST_BREATH", entries, List.of());
	}

	/** One representative tool plus the selected HOTM ability; owning many pickaxes adds nothing. */
	private static Group stun(Inputs in) {
		Owned tool = null;
		int toolScore = Integer.MIN_VALUE;
		for (Owned owned : in.items()) {
			String category = in.category().apply(owned.stack());
			if (!"PICKAXE".equals(category) && !"DRILL".equals(category)) {
				continue;
			}
			int score = toolScore(owned.stack());
			if (score > toolScore) {
				tool = owned;
				toolScore = score;
			}
		}
		Entry toolRow;
		if (!in.inventoryKnown()) {
			toolRow = slotRow("Suitable pickaxe/drill", "Unknown", "DIAMOND_PICKAXE", Status.UNKNOWN, List.of(), null);
		} else if (tool == null) {
			toolRow = slotRow("Suitable pickaxe/drill", MISSING_TEXT, "DIAMOND_PICKAXE", Status.MISSING, List.of(), null);
		} else {
			String id = baseId(tool.stack());
			toolRow = slotRow("Suitable pickaxe/drill", "Owned", id, Status.COMPLETE,
				List.of(Check.of("Tool", in.itemName().apply(id), CheckState.INFO)), tool);
		}
		Entry abilityRow;
		if (in.abilityId() == null) {
			abilityRow = slotRow("Selected HOTM ability", "Unknown", "", Status.UNKNOWN, List.of(), null);
		} else if (isPickobulus(in.abilityId())) {
			abilityRow = slotRow("Selected HOTM ability", in.abilityName().apply(in.abilityId()), "", Status.COMPLETE,
				List.of(), null);
		} else {
			String name = in.abilityId().isBlank() ? "None" : in.abilityName().apply(in.abilityId());
			abilityRow = slotRow("Selected HOTM ability", name, "", Status.MISSING,
				List.of(Check.of("Required", "Pickobulus", CheckState.MISSING)), null);
		}
		List<Entry> rows = List.of(toolRow, abilityRow);
		return new Group("Stun", "DIVAN_DRILL", slotGroupStatus(rows), tool == null ? 0 : 1, rows, List.of(), false);
	}

	private static Group dpsTerror(Inputs in) {
		if (!in.inventoryKnown()) {
			return unknownArmour("DPS Terror", "INFERNAL_TERROR_CHESTPLATE");
		}
		List<Entry> rows = new ArrayList<>();
		rows.add(helmetRow(in, "WARDEN_HELMET"));
		for (String slot : BODY_SLOTS) {
			Owned best = best(in, o -> {
				String id = baseId(o.stack());
				return "TERROR".equals(kuudraSet(id)) && slot.equals(slotOf(id)) && !RCM_REFORGES.contains(reforge(o.stack()));
			}, o -> ("ancient".equals(reforge(o.stack())) ? 1000 : 0) + tierRank(baseId(o.stack())) * 20 + stars(o.stack()));
			if (best == null) {
				rows.add(slotRow(pretty(slot), MISSING_TEXT, "INFERNAL_TERROR_" + slot, Status.MISSING, List.of(), null));
				continue;
			}
			List<Check> checks = new ArrayList<>();
			checks.add(tierCheck(baseId(best.stack())));
			String reforge = reforge(best.stack());
			checks.add("ancient".equals(reforge)
				? Check.of("Reforge", "Ancient", CheckState.OK)
				: new Check("Reforge", reforge.isEmpty() ? "None" : pretty(reforge), CheckState.UPGRADE, "Ancient", false));
			checks.add(starsCheck(best.stack()));
			rows.add(pieceRow(pretty(slot), best, checks));
		}
		return armourGroup("DPS Terror", "INFERNAL_TERROR_CHESTPLATE", rows, false);
	}

	/**
	 * Necrotic or Loving Kuudra-set pieces of any set and tier, Terror preferred. Wither Goggles also count in
	 * the helmet slot. Ancient Terror never qualifies, so DPS and RCM can't claim the same piece.
	 */
	private static Group rcm(Inputs in) {
		if (!in.inventoryKnown()) {
			return unknownArmour("RCM", "INFERNAL_TERROR_HELMET");
		}
		List<Entry> rows = new ArrayList<>();
		for (String slot : SLOTS) {
			Owned best = best(in, o -> {
				String id = baseId(o.stack());
				if (!RCM_REFORGES.contains(reforge(o.stack())) || !slot.equals(slotOf(id))) {
					return false;
				}
				return KUUDRA_SETS.contains(kuudraSet(id)) || "WITHER_GOGGLES".equals(id);
			}, o -> {
				String id = baseId(o.stack());
				return ("TERROR".equals(kuudraSet(id)) ? 1000 : 0) + tierRank(id) * 20 + stars(o.stack());
			});
			if (best == null) {
				rows.add(slotRow(pretty(slot), MISSING_TEXT, "INFERNAL_TERROR_" + slot, Status.MISSING, List.of(), null));
				continue;
			}
			String id = baseId(best.stack());
			List<Check> checks = new ArrayList<>();
			if (KUUDRA_SETS.contains(kuudraSet(id))) {
				checks.add(tierCheck(id));
			}
			checks.add(Check.of("Reforge", pretty(reforge(best.stack())), CheckState.OK));
			checks.add(starsCheck(best.stack()));
			rows.add(pieceRow(pretty(slot), best, checks));
		}
		return armourGroup("RCM", "INFERNAL_TERROR_HELMET", rows, false);
	}

	private static Group hollow(Inputs in) {
		if (!in.inventoryKnown()) {
			return unknownArmour("Hollow", "INFERNAL_HOLLOW_HELMET");
		}
		List<Entry> rows = new ArrayList<>();
		for (String slot : SLOTS) {
			Owned best = best(in, o -> {
				String id = baseId(o.stack());
				return "HOLLOW".equals(kuudraSet(id)) && slot.equals(slotOf(id));
			}, o -> tierRank(baseId(o.stack())) * 20 + stars(o.stack()));
			if (best == null) {
				rows.add(slotRow(pretty(slot), MISSING_TEXT, "INFERNAL_HOLLOW_" + slot, Status.MISSING, List.of(), null));
				continue;
			}
			rows.add(pieceRow(pretty(slot), best, List.of(tierCheck(baseId(best.stack())), reforgeCheck(best.stack()),
				starsCheck(best.stack()))));
		}
		return armourGroup("Hollow", "INFERNAL_HOLLOW_HELMET", rows, false);
	}

	private static Group reaper(Inputs in) {
		if (!in.inventoryKnown()) {
			return unknownArmour("Reaper", "REAPER_MASK");
		}
		List<Entry> rows = new ArrayList<>();
		String[] ids = { "REAPER_MASK", "REAPER_CHESTPLATE", "REAPER_LEGGINGS", "REAPER_BOOTS" };
		for (int i = 0; i < ids.length; i++) {
			String id = ids[i];
			Owned best = best(in, o -> id.equals(baseId(o.stack())), o -> stars(o.stack()));
			rows.add(best == null
				? slotRow(pretty(SLOTS.get(i)), MISSING_TEXT, id, Status.MISSING, List.of(), null)
				: pieceRow(pretty(SLOTS.get(i)), best, List.of(reforgeCheck(best.stack()))));
		}
		long owned = rows.stream().filter(r -> r.stack() != null).count();
		Status status = owned == rows.size() ? Status.COMPLETE : owned == 0 ? Status.MISSING : Status.INCOMPLETE;
		return new Group("Reaper", "REAPER_MASK", status, (int) owned, rows, List.of(), true);
	}

	private static Group tuxedo(Inputs in) {
		if (!in.inventoryKnown()) {
			return unknownArmour("Tuxedo", "ELEGANT_TUXEDO_CHESTPLATE");
		}
		List<Entry> rows = new ArrayList<>();
		rows.add(helmetRow(in, "WARDEN_HELMET"));
		for (String slot : BODY_SLOTS) {
			Owned best = best(in, o -> {
				String id = baseId(o.stack());
				return tuxedoRank(id) > 0 && slot.equals(slotOf(id));
			}, o -> tuxedoRank(baseId(o.stack())) * 20 + stars(o.stack()));
			if (best == null) {
				rows.add(slotRow(pretty(slot), MISSING_TEXT, "ELEGANT_TUXEDO_" + slot, Status.MISSING, List.of(), null));
				continue;
			}
			int rank = tuxedoRank(baseId(best.stack()));
			String variant = rank == 3 ? "Elegant" : rank == 2 ? "Fancy" : "Cheap";
			List<Check> checks = List.of(
				new Check("Variant", variant, rank == 3 ? CheckState.OK : CheckState.UPGRADE, rank == 3 ? null : "Elegant", false),
				reforgeCheck(best.stack()));
			rows.add(pieceRow(pretty(slot), best, checks));
		}
		return armourGroup("Tuxedo", "ELEGANT_TUXEDO_CHESTPLATE", rows, false);
	}

	private static Group racingHelmet(Inputs in) {
		if (!in.inventoryKnown()) {
			return unknownArmour("Racing Helmet", "RACING_HELMET");
		}
		return armourGroup("Racing Helmet", "RACING_HELMET", List.of(helmetRow(in, "RACING_HELMET")), false);
	}

	/** Warns per wardrobe set that wears Crown of Avarice with Terror pieces. Owning both alone is fine. */
	private static Group crownOfAvarice(Inputs in) {
		if (!in.inventoryKnown()) {
			return new Group("Crown of Avarice", "CROWN_OF_AVARICE", Status.UNKNOWN, 0, List.of(), List.of(API_OFF), false);
		}
		if (in.presets().isEmpty()) {
			return new Group("Crown of Avarice", "CROWN_OF_AVARICE", Status.UNKNOWN, 0, List.of(),
				List.of(Check.of("Wardrobe sets", "Not in API", CheckState.UNKNOWN)), false);
		}
		List<Check> checks = new ArrayList<>();
		List<Entry> entries = new ArrayList<>();
		for (InventoryDecoder.WardrobePreset preset : in.presets()) {
			List<InventoryDecoder.Stack> pieces = preset.pieces();
			InventoryDecoder.Stack helmet = pieces.isEmpty() ? null : pieces.get(0);
			if (helmet == null || !"CROWN_OF_AVARICE".equals(baseId(helmet))) {
				continue;
			}
			boolean terror = false;
			for (int i = 1; i < pieces.size(); i++) {
				if (pieces.get(i) != null && "TERROR".equals(kuudraSet(baseId(pieces.get(i))))) {
					terror = true;
				}
			}
			if (terror) {
				String where = "Wardrobe slot " + preset.slot();
				entries.add(new Entry(where, "Crown of Avarice in Terror set", "CROWN_OF_AVARICE", Status.INCOMPLETE,
					List.of(Check.of("", "Warden Helmet preferred", CheckState.WARN)), where, helmet, null));
			}
		}
		if (entries.isEmpty()) {
			checks.add(Check.of("Terror wardrobe sets", "No Crown", CheckState.OK));
		}
		Status status = entries.isEmpty() ? Status.COMPLETE : Status.INCOMPLETE;
		return new Group("Crown of Avarice", "CROWN_OF_AVARICE", status, entries.size(), entries, checks, false);
	}

	private static Entry helmetRow(Inputs in, String id) {
		Owned best = best(in, o -> id.equals(baseId(o.stack())), o -> stars(o.stack()));
		if (best == null) {
			return slotRow("Helmet", MISSING_TEXT, id, Status.MISSING, List.of(), null);
		}
		return pieceRow("Helmet", best, List.of(reforgeCheck(best.stack()), starsCheck(best.stack())));
	}

	private static Entry pieceRow(String slotLabel, Owned piece, List<Check> checks) {
		String id = baseId(piece.stack());
		String value = KUUDRA_SETS.contains(kuudraSet(id))
			? tierName(tierRank(id)) + " " + pretty(kuudraSet(id))
			: pretty(id);
		if (tuxedoRank(id) > 0) {
			value = pretty(id.substring(0, id.indexOf("_TUXEDO_"))) + " Tuxedo";
		}
		return new Entry(slotLabel, value, id, statusOf(checks), List.copyOf(checks), piece.source(), piece.stack(), null);
	}

	private static Entry slotRow(String name, String value, String iconId, Status status, List<Check> checks, Owned owned) {
		return new Entry(name, value, iconId, status, checks, owned == null ? null : owned.source(),
			owned == null ? null : owned.stack(), null);
	}

	private static Group unknownArmour(String title, String iconId) {
		return new Group(title, iconId, Status.UNKNOWN, 0, List.of(), List.of(API_OFF), false);
	}

	private static Group armourGroup(String title, String iconId, List<Entry> rows, boolean informational) {
		int owned = (int) rows.stream().filter(r -> r.stack() != null).count();
		return new Group(title, iconId, slotGroupStatus(rows), owned, rows, List.of(), informational);
	}

	/** Complete when every row is satisfied, missing when nothing is owned, otherwise incomplete. */
	private static Status slotGroupStatus(List<Entry> rows) {
		if (rows.stream().anyMatch(r -> r.status() == Status.UNKNOWN)) {
			return Status.UNKNOWN;
		}
		if (rows.stream().allMatch(r -> r.status() == Status.COMPLETE)) {
			return Status.COMPLETE;
		}
		return rows.stream().allMatch(r -> r.status() == Status.MISSING) ? Status.MISSING : Status.INCOMPLETE;
	}

	private static Group itemGroup(Inputs in, String title, String iconId, List<Entry> entries, List<Check> checks) {
		if (!in.inventoryKnown()) {
			return new Group(title, iconId, Status.UNKNOWN, 0, List.of(), List.of(API_OFF), false);
		}
		return new Group(title, iconId, groupStatus(entries), entries.size(), entries, checks, false);
	}

	/** Owned items are never "missing" as a group: the best copy decides between complete and incomplete. */
	private static Status groupStatus(List<Entry> entries) {
		if (entries.isEmpty()) {
			return Status.MISSING;
		}
		return entries.stream().anyMatch(e -> e.status() == Status.COMPLETE) ? Status.COMPLETE : Status.INCOMPLETE;
	}

	static Status statusOf(List<Check> checks) {
		if (checks.stream().anyMatch(c -> c.state() == CheckState.MISSING)) {
			return Status.MISSING;
		}
		if (checks.stream().anyMatch(c -> c.state() == CheckState.UPGRADE)) {
			return Status.INCOMPLETE;
		}
		return Status.COMPLETE;
	}

	private static Check tierCheck(String id) {
		int rank = tierRank(id);
		return new Check("Tier", tierName(rank), rank >= 4 ? CheckState.OK : CheckState.UPGRADE,
			rank >= 4 ? null : "Infernal", false);
	}

	private static Owned best(Inputs in, Predicate<Owned> accept, ToIntFunction<Owned> score) {
		Owned best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Owned owned : in.items()) {
			if (!accept.test(owned)) {
				continue;
			}
			int s = score.applyAsInt(owned);
			if (s > bestScore) {
				best = owned;
				bestScore = s;
			}
		}
		return best;
	}

	private static List<Owned> matching(Inputs in, Predicate<String> idMatches) {
		List<Owned> out = new ArrayList<>();
		for (Owned owned : in.items()) {
			if (idMatches.test(baseId(owned.stack()))) {
				out.add(owned);
			}
		}
		return out;
	}

	/** Higher rarity first; Dungeonbreaker and Pickonimbus sort last since they're limited-use tools. */
	static int toolScore(InventoryDecoder.Stack stack) {
		String id = baseId(stack);
		int score = rarityRank(stack) * 10;
		if (id.contains("DUNGEONBREAKER") || id.startsWith("PICKONIMBUS")) {
			score -= 1000;
		}
		return score;
	}

	private static int rarityRank(InventoryDecoder.Stack stack) {
		List<String> lore = stack.lore();
		if (lore == null) {
			return 0;
		}
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = lore.get(i) == null ? "" : lore.get(i).replaceAll("\u00a7.", "").trim().toUpperCase(Locale.ROOT);
			if (line.isEmpty()) {
				continue;
			}
			for (int r = RARITIES.size() - 1; r >= 0; r--) {
				if (line.startsWith(RARITIES.get(r))) {
					return r + 1;
				}
			}
			return 0;
		}
		return 0;
	}

	private static Entry entry(Inputs in, Owned owned, Numbering numbering, List<Check> checks) {
		return entry(in, owned, numbering, checks, in.itemName().apply(baseId(owned.stack())));
	}

	private static Entry entry(Inputs in, Owned owned, Numbering numbering, List<Check> checks, String name) {
		return new Entry(name + " (" + numbering.next(name) + ")", null, baseId(owned.stack()), statusOf(checks),
			List.copyOf(checks), owned.source(), owned.stack(), null);
	}

	private static final class Numbering {
		private final Map<String, Integer> counts = new HashMap<>();

		int next(String key) {
			return this.counts.merge(key, 1, Integer::sum);
		}
	}

	/** {@code whenAbsent} is MISSING for hard requirements, UPGRADE for recommended enchants. */
	private static Check enchCheck(String label, int level, int max, CheckState whenAbsent) {
		if (level <= 0) {
			return new Check(label, "None", whenAbsent, whenAbsent == CheckState.UPGRADE ? roman(max) : null, false);
		}
		return level < max
			? new Check(label, roman(level), CheckState.UPGRADE, roman(max), false)
			: Check.of(label, roman(level), CheckState.OK);
	}

	private static Check ultimateCheck(String key, Integer level) {
		int lvl = level == null ? 0 : level;
		Integer max = ULTIMATE_MAX.get(key);
		boolean below = max != null && lvl < max;
		return new Check("Ultimate", ultimateName(key) + " " + roman(lvl), below ? CheckState.UPGRADE : CheckState.OK,
			below ? roman(max) : null, false);
	}

	private static Check piercingWarning(int level) {
		return level > 0
			? Check.of("Piercing", roman(level), CheckState.WARN)
			: Check.of("Piercing", "None", CheckState.OK);
	}

	private static Check starsCheck(InventoryDecoder.Stack stack) {
		int stars = stars(stack);
		return Check.of("Stars", stars > 0 ? String.valueOf(stars) : "None", CheckState.INFO);
	}

	private static Check reforgeCheck(InventoryDecoder.Stack stack) {
		String reforge = reforge(stack);
		return Check.of("Reforge", reforge.isEmpty() ? "None" : pretty(reforge), CheckState.INFO);
	}

	private static final Map<String, Integer> ULTIMATE_MAX = Map.ofEntries(
		Map.entry("ultimate_reiterate", 5), Map.entry("ultimate_duplex", 5), Map.entry("ultimate_rend", 5),
		Map.entry("ultimate_chimera", 5), Map.entry("ultimate_swarm", 5), Map.entry("ultimate_wise", 5),
		Map.entry("ultimate_soul_eater", 5), Map.entry("ultimate_fatal_tempo", 5), Map.entry("ultimate_inferno", 5),
		Map.entry("ultimate_combo", 5), Map.entry("ultimate_one_for_all", 1)
	);

	static String ultimateKey(Map<String, Integer> ench) {
		for (Map.Entry<String, Integer> e : ench.entrySet()) {
			if (e.getKey().startsWith("ultimate_") && e.getValue() != null && e.getValue() > 0) {
				return e.getKey();
			}
		}
		return null;
	}

	static String ultimateName(String key) {
		String core = key.startsWith("ultimate_") ? key.substring("ultimate_".length()) : key;
		return switch (core) {
			case "reiterate", "duplex" -> "Duplex";
			case "wise" -> "Ultimate Wise";
			default -> pretty(core);
		};
	}

	static boolean isPickobulus(String abilityId) {
		return "pickaxe_toss".equalsIgnoreCase(abilityId) || "pickobulus".equalsIgnoreCase(abilityId);
	}

	static String selectedAbility(JsonObject member) {
		JsonObject tree = Leveling.obj(member.get("skill_tree"));
		JsonObject selected = Leveling.obj(tree == null ? null : tree.get("selected_ability"));
		if (selected != null && selected.has("mining") && selected.get("mining").isJsonPrimitive()) {
			return selected.get("mining").getAsString();
		}
		JsonObject core = Leveling.obj(member.get("mining_core"));
		if (core != null && core.has("selected_pickaxe_ability") && core.get("selected_pickaxe_ability").isJsonPrimitive()) {
			return core.get("selected_pickaxe_ability").getAsString();
		}
		return tree != null || core != null ? "" : null;
	}

	static String category(InventoryDecoder.Stack stack) {
		JsonObject def = HypixelItemsCache.get(baseId(stack));
		if (def != null && def.has("category") && def.get("category").isJsonPrimitive()) {
			return def.get("category").getAsString().toUpperCase(Locale.ROOT);
		}
		List<String> lore = stack.lore();
		if (lore != null) {
			for (int i = lore.size() - 1; i >= 0; i--) {
				String line = lore.get(i) == null ? "" : lore.get(i).replaceAll("\u00a7.", "").trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.contains(" DRILL")) {
					return "DRILL";
				}
				return line.contains(" PICKAXE") ? "PICKAXE" : "";
			}
		}
		return "";
	}

	static String itemName(String id) {
		JsonObject def = HypixelItemsCache.get(id);
		if (def != null && def.has("name") && def.get("name").isJsonPrimitive()) {
			return def.get("name").getAsString();
		}
		JsonObject neu = NeuRepoCache.get(id);
		if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive()) {
			return neu.get("displayname").getAsString().replaceAll("\u00a7.", "");
		}
		return pretty(id);
	}

	private static Map<String, Integer> enchants(InventoryDecoder.Stack stack) {
		return NbtAttrs.intMap(stack.extraAttributes(), "enchantments");
	}

	private static int level(Map<String, Integer> ench, String... keys) {
		int best = 0;
		for (String key : keys) {
			Integer value = ench.get(key);
			if (value != null) {
				best = Math.max(best, value);
			}
		}
		return best;
	}

	static int stars(InventoryDecoder.Stack stack) {
		int upgrade = NbtAttrs.intValue(stack.extraAttributes(), "upgrade_level", 0);
		return upgrade > 0 ? upgrade : NbtAttrs.intValue(stack.extraAttributes(), "dungeon_item_level", 0);
	}

	private static String reforge(InventoryDecoder.Stack stack) {
		String mod = NbtAttrs.string(stack.extraAttributes(), "modifier");
		return mod == null ? "" : mod.toLowerCase(Locale.ROOT);
	}

	static String baseId(InventoryDecoder.Stack stack) {
		if (stack == null || stack.id() == null) {
			return "";
		}
		String id = stack.id().toUpperCase(Locale.ROOT);
		int cut = id.indexOf(';');
		return cut >= 0 ? id.substring(0, cut) : id;
	}

	static String slotOf(String id) {
		if ("REAPER_MASK".equals(id) || "WITHER_GOGGLES".equals(id)) {
			return "HELMET";
		}
		for (String slot : SLOTS) {
			if (id.endsWith("_" + slot)) {
				return slot;
			}
		}
		return "";
	}

	static int tierRank(String id) {
		if (id.startsWith("INFERNAL_")) return 4;
		if (id.startsWith("FIERY_")) return 3;
		if (id.startsWith("BURNING_")) return 2;
		if (id.startsWith("HOT_")) return 1;
		return 0;
	}

	private static String tierName(int rank) {
		return switch (rank) {
			case 4 -> "Infernal";
			case 3 -> "Fiery";
			case 2 -> "Burning";
			case 1 -> "Hot";
			default -> "Base";
		};
	}

	/** Kuudra armour set name (e.g. {@code TERROR}) without tier prefix or slot suffix. */
	static String kuudraSet(String id) {
		String slot = slotOf(id);
		if (slot.isEmpty() || !id.endsWith("_" + slot)) {
			return "";
		}
		String core = id.substring(0, id.length() - slot.length() - 1);
		for (String prefix : List.of("INFERNAL_", "FIERY_", "BURNING_", "HOT_")) {
			if (core.startsWith(prefix)) {
				core = core.substring(prefix.length());
				break;
			}
		}
		return KUUDRA_SETS.contains(core) ? core : "";
	}

	private static int tuxedoRank(String id) {
		if (!BODY_SLOTS.contains(slotOf(id)) || !id.contains("_TUXEDO_")) {
			return 0;
		}
		if (id.startsWith("ELEGANT_")) return 3;
		if (id.startsWith("FANCY_")) return 2;
		if (id.startsWith("CHEAP_")) return 1;
		return 0;
	}

	static String roman(int n) {
		String[] numerals = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
		return n >= 0 && n < numerals.length ? numerals[n] : String.valueOf(n);
	}

	private static String pretty(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (String part : raw.toLowerCase(Locale.ROOT).split("_")) {
			if (part.isEmpty()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}
}
