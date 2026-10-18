/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonObject;
import fishmod.pv.dungeons.CataXpMath;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.NbtAttrs;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.nbt.CompoundTag;

/** Derived Kuudra party-card stats (score, skills, gear checks, armor). */
public final class CrimsonKuudraCard {
	public record ImportantItem(
		String label,
		boolean owned,
		String iconId,
		List<String> details,
		List<String> lore,
		String displayName
	) {
		public ImportantItem {
			details = details == null ? List.of() : List.copyOf(details);
			lore = lore == null ? List.of() : List.copyOf(lore);
			displayName = displayName == null ? "" : displayName;
		}
	}

	public record ArmorPiece(
		String slot,
		String label,
		String iconId,
		boolean owned,
		List<String> details,
		List<String> lore,
		Integer dyeColor,
		int stars,
		String dyeItem,
		String displayName
	) {
		public ArmorPiece {
			details = details == null ? List.of() : List.copyOf(details);
			lore = lore == null ? List.of() : List.copyOf(lore);
			dyeItem = dyeItem == null ? "" : dyeItem;
			displayName = displayName == null ? "" : displayName;
			stars = Math.max(0, stars);
		}
	}

	private static final double[] TIER_WEIGHT = { 0.5, 1.0, 2.0, 4.0, 8.0 };

	private final double kuudraScore;
	private final int kuudraLevel;
	private final int magicalPower;
	private final String selectedPower;
	private final double powerIntelligence;
	private final double powerMagicFind;
	private final int cataLevel;
	private final int combatLevel;
	private final int foragingLevel;
	private final int skyBlockLevel;
	private final double vanquisherChancePct;
	private final List<String> vanquisherHover;
	private final List<ImportantItem> importantItems;
	private final List<ArmorPiece> mageArmor;
	private final List<ArmorPiece> archerArmor;
	private final KuudraGearChecks.Result gear;

	private CrimsonKuudraCard(
		double kuudraScore,
		int kuudraLevel,
		int magicalPower,
		String selectedPower,
		double powerIntelligence,
		double powerMagicFind,
		int cataLevel,
		int combatLevel,
		int foragingLevel,
		int skyBlockLevel,
		double vanquisherChancePct,
		List<String> vanquisherHover,
		List<ImportantItem> importantItems,
		List<ArmorPiece> mageArmor,
		List<ArmorPiece> archerArmor,
		KuudraGearChecks.Result gear
	) {
		this.kuudraScore = kuudraScore;
		this.kuudraLevel = kuudraLevel;
		this.magicalPower = magicalPower;
		this.selectedPower = selectedPower == null ? "" : selectedPower;
		this.powerIntelligence = powerIntelligence;
		this.powerMagicFind = powerMagicFind;
		this.cataLevel = cataLevel;
		this.combatLevel = combatLevel;
		this.foragingLevel = foragingLevel;
		this.skyBlockLevel = skyBlockLevel;
		this.vanquisherChancePct = vanquisherChancePct;
		this.vanquisherHover = List.copyOf(vanquisherHover == null ? List.of() : vanquisherHover);
		this.importantItems = List.copyOf(importantItems == null ? List.of() : importantItems);
		this.mageArmor = List.copyOf(mageArmor == null ? List.of() : mageArmor);
		this.archerArmor = List.copyOf(archerArmor == null ? List.of() : archerArmor);
		this.gear = gear == null ? KuudraGearChecks.Result.empty() : gear;
	}

	public static CrimsonKuudraCard empty() {
		return new CrimsonKuudraCard(
			0, 0, 0, "", 0, 0, 0, 0, 0, 0, 1.0 / 640.0 * 100.0,
			List.of("Base 1/640"),
			List.of(), List.of(), List.of(), KuudraGearChecks.Result.empty()
		);
	}

	public static CrimsonKuudraCard from(
		JsonObject member,
		Map<CrimsonSnapshot.KuudraTier, CrimsonSnapshot.KuudraTierStats> kuudra
	) {
		if (member == null) {
			return empty();
		}
		double score = 0;
		if (kuudra != null) {
			CrimsonSnapshot.KuudraTier[] tiers = CrimsonSnapshot.KuudraTier.values();
			for (int i = 0; i < tiers.length; i++) {
				CrimsonSnapshot.KuudraTierStats stats = kuudra.get(tiers[i]);
				int comps = stats == null ? 0 : stats.completions();
				score += comps * TIER_WEIGHT[i];
			}
		}
		int level = (int) Math.floor(score / 100.0);

		JsonObject storage = Leveling.obj(member.get("accessory_bag_storage"));
		int mp = MagicalPowerCalculator.fromMember(member);
		if (mp <= 0) {
			mp = storage == null ? 0 : (int) num(storage.get("highest_magical_power"));
		}
		String power = storage == null ? "" : str(storage.get("selected_power"));
		Map<String, Double> powerStats = MaxwellPowers.statsFor(power, mp);
		double intel = powerStats.getOrDefault("intelligence", 0.0);
		double mf = powerStats.getOrDefault("magic_find", 0.0);

		float cataXp = Leveling.readCatacombsXp(member);
		int cata = CataXpMath.progress(cataXp).displayLevel();
		int combat = Leveling.skillLevel(member, "combat");
		int foraging = Leveling.skillLevel(member, "foraging");
		int sb = 0;
		JsonObject leveling = Leveling.obj(member.get("leveling"));
		if (leveling != null) {
			Float xp = Leveling.num(leveling.get("experience"));
			if (xp != null) {
				sb = (int) Math.floor(xp / 100F);
			}
		}

		Map<String, List<InventoryDecoder.Stack>> cats = InventoryDecoder.parseCategories(member, null);
		List<InventoryDecoder.Stack> all = flatten(cats);
		PetSnapshot pets = PetSnapshot.fromMember(member);

		Vanq vanq = vanquisherChance(member, pets, all);
		List<ImportantItem> items = scanImportant(all, pets, member);
		List<ArmorPiece> mage = scanMageArmor(all);
		List<ArmorPiece> archer = scanArcherArmor(all);

		return new CrimsonKuudraCard(
			score, level, mp, power, intel, mf, cata, combat, foraging, sb,
			vanq.pct, vanq.hover, items, mage, archer, KuudraGearChecks.fromMember(member, cats, pets)
		);
	}

	public double kuudraScore() { return this.kuudraScore; }
	public int kuudraLevel() { return this.kuudraLevel; }
	public int magicalPower() { return this.magicalPower; }
	public String selectedPower() { return this.selectedPower; }
	public String selectedPowerLabel() {
		if (this.selectedPower.isBlank()) {
			return "None";
		}
		String[] parts = this.selectedPower.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) sb.append(part.substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}
	public double powerIntelligence() { return this.powerIntelligence; }
	public double powerMagicFind() { return this.powerMagicFind; }
	public int cataLevel() { return this.cataLevel; }
	public int combatLevel() { return this.combatLevel; }
	public int foragingLevel() { return this.foragingLevel; }
	public int skyBlockLevel() { return this.skyBlockLevel; }
	public double vanquisherChancePct() { return this.vanquisherChancePct; }
	public List<String> vanquisherHover() { return this.vanquisherHover; }
	public List<ImportantItem> importantItems() { return this.importantItems; }
	public List<ArmorPiece> mageArmor() { return this.mageArmor; }
	public List<ArmorPiece> archerArmor() { return this.archerArmor; }
	public KuudraGearChecks.Result gear() { return this.gear; }

	public CrimsonKuudraCard withCombatStats(PlayerStatsSnapshot stats) {
		if (stats == null) {
			return this;
		}
		double intel = statValue(stats, "intelligence", this.powerIntelligence);
		double mf = statValue(stats, "magic_find", this.powerMagicFind);
		return new CrimsonKuudraCard(
			this.kuudraScore, this.kuudraLevel, this.magicalPower, this.selectedPower,
			intel, mf, this.cataLevel, this.combatLevel, this.foragingLevel, this.skyBlockLevel,
			this.vanquisherChancePct, this.vanquisherHover, this.importantItems, this.mageArmor, this.archerArmor,
			this.gear
		);
	}

	private static double statValue(PlayerStatsSnapshot stats, String id, double fallback) {
		for (PlayerStatsSnapshot.Entry entry : stats.entries()) {
			if (entry != null && id.equalsIgnoreCase(entry.id())) {
				OptionalDouble v = entry.value();
				if (v.isPresent()) {
					return v.getAsDouble();
				}
			}
		}
		return fallback;
	}

	public List<String> scoreHoverLines() {
		return List.of(
			"1 level = 100 Kuudra Score",
			"Score per completion:",
			"Basic 0.5, Hot 1, Burning 2, Fiery 4, Infernal 8"
		);
	}

	private record Vanq(double pct, List<String> hover) {
	}

	private static Vanq vanquisherChance(
		JsonObject member, PetSnapshot pets, List<InventoryDecoder.Stack> all
	) {
		double chance = 1.0 / 640.0;
		List<String> hover = new ArrayList<>();
		hover.add("Base 1/640");

		PetSnapshot.Entry kuudraPet = bestKuudraPet(pets);
		if (kuudraPet != null) {
			double mult = switch (kuudraPet.tier() == null ? "" : kuudraPet.tier().toUpperCase(Locale.ROOT)) {
				case "LEGENDARY", "MYTHIC", "EPIC" -> 1.20;
				case "RARE", "UNCOMMON" -> 1.15;
				default -> 1.10;
			};
			chance *= mult;
			hover.add(String.format(Locale.ROOT, "Kuudra Pet ×%.2f", mult));
		}

		if (hasWitherPiper(member)) {
			chance *= 1.10;
			hover.add("Wither Piper ×1.10");
		}
		if (endermanSlayerAtLeast(member, 9)) {
			chance *= 1.15;
			hover.add("Enderman IX ×1.15");
		}
		int tracking = maxAttribute(all, "tracking");
		if (tracking > 0) {
			double mult = 1.0 + Math.min(15, tracking) * 0.01;
			chance *= mult;
			hover.add(String.format(Locale.ROOT, "Tracking %d ×%.2f", tracking, mult));
		}
		int serendipity = maxAttribute(all, "crimson_serendipity");
		if (serendipity <= 0) {
			serendipity = maxAttribute(all, "serendipity");
		}
		if (serendipity > 0) {
			double mult = 1.0 + Math.min(10, serendipity) * 0.02;
			chance *= mult;
			hover.add(String.format(Locale.ROOT, "Crimson Serendipity ×%.2f", mult));
		}

		return new Vanq(chance * 100.0, hover);
	}

	private static PetSnapshot.Entry bestKuudraPet(PetSnapshot pets) {
		if (pets == null) {
			return null;
		}
		PetSnapshot.Entry best = null;
		for (PetSnapshot.Entry pet : pets.pets()) {
			if (pet == null || pet.type() == null) continue;
			if (!"KUUDRA".equalsIgnoreCase(pet.type())) continue;
			if (best == null || pet.level() > best.level()) {
				best = pet;
			}
		}
		return best;
	}

	private static boolean hasWitherPiper(JsonObject member) {
		JsonObject playerData = Leveling.obj(member.get("player_data"));
		JsonObject perks = playerData == null ? null : Leveling.obj(playerData.get("perks"));
		if (perks == null) {
			JsonObject dungeons = Leveling.obj(member.get("dungeons"));
			perks = dungeons == null ? null : Leveling.obj(dungeons.get("perks"));
		}
		if (perks == null) {
			return false;
		}
		for (String key : perks.keySet()) {
			if (key != null && key.toLowerCase(Locale.ROOT).contains("wither_piper")) {
				return num(perks.get(key)) > 0;
			}
		}
		return false;
	}

	private static boolean endermanSlayerAtLeast(JsonObject member, int level) {
		float xp = Leveling.readSlayerXp(member, "enderman");
		Leveling.Progress progress = Leveling.getLevel(RepoData.slayerXp("enderman"), xp, 9, true);
		return (int) Math.floor(progress.level()) >= level;
	}

	private static int maxAttribute(List<InventoryDecoder.Stack> stacks, String attr) {
		int best = 0;
		String key = attr.toLowerCase(Locale.ROOT);
		for (InventoryDecoder.Stack stack : stacks) {
			if (stack == null || stack.extraAttributes() == null) continue;
			CompoundTag attrs = NbtAttrs.compound(stack.extraAttributes(), "attributes");
			if (attrs == null) continue;
			for (String entry : attrs.keySet()) {
				if (entry != null && entry.toLowerCase(Locale.ROOT).equals(key)) {
					best = Math.max(best, NbtAttrs.intValue(attrs, entry, 0));
				}
			}
		}
		return best;
	}

	private static List<ImportantItem> scanImportant(
		List<InventoryDecoder.Stack> all, PetSnapshot pets, JsonObject member
	) {
		List<ImportantItem> out = new ArrayList<>();
		out.add(witherImpact(all));
		out.add(goldenDragon(pets, member));
		out.add(terminator(all, true));
		out.add(terminator(all, false));
		out.add(ragnarock(all));
		out.add(sosFlare(all));
		return out;
	}

	private static ImportantItem witherImpact(List<InventoryDecoder.Stack> all) {
		String[] ids = { "HYPERION", "SCYLLA", "ASTRAEA", "VALKYRIE", "NECRON_BLADE" };
		InventoryDecoder.Stack best = null;
		for (InventoryDecoder.Stack stack : all) {
			String id = baseId(stack);
			for (String needle : ids) {
				if (id.equals(needle) || id.endsWith("_" + needle) || id.startsWith(needle)) {
					best = prefer(best, stack);
					break;
				}
			}
		}
		if (best == null) {
			return missingItem("Wither Impact Weapon", "HYPERION");
		}
		List<String> details = new ArrayList<>();
		details.add(prettyId(baseId(best)));
		int s = stars(best);
		if (s > 0) details.add(s + "★");
		return ownedItem("Wither Impact Weapon", baseId(best), details, best);
	}

	private static ImportantItem goldenDragon(PetSnapshot pets, JsonObject member) {
		PetSnapshot.Entry gdrag = null;
		if (pets != null) {
			for (PetSnapshot.Entry pet : pets.pets()) {
				if (pet != null && "GOLDEN_DRAGON".equalsIgnoreCase(pet.type())) {
					if (gdrag == null || pet.level() > gdrag.level()) {
						gdrag = pet;
					}
				}
			}
		}
		if (gdrag == null) {
			return missingItem("[Lvl 200] Golden Dragon", "GOLDEN_DRAGON;4");
		}
		boolean ok = gdrag.level() >= 200;
		String label = "[Lvl " + gdrag.level() + "] Golden Dragon";
		String display = PetLoreResolver.displayNameFor(gdrag);
		List<String> lore = PetLoreResolver.loreFor(gdrag);
		return new ImportantItem(
			label, ok, gdrag.neuId(), List.of(), lore, display
		);
	}

	private static String tierColorCode(String tier) {
		if (tier == null) return "§f";
		return switch (tier.toUpperCase(Locale.ROOT)) {
			case "MYTHIC" -> "§d";
			case "LEGENDARY" -> "§6";
			case "EPIC" -> "§5";
			case "RARE" -> "§9";
			case "UNCOMMON" -> "§a";
			default -> "§f";
		};
	}

	private static ImportantItem terminator(List<InventoryDecoder.Stack> all, boolean spiritual) {
		InventoryDecoder.Stack best = null;
		for (InventoryDecoder.Stack stack : all) {
			String id = baseId(stack);
			if (!id.contains("TERMINATOR")) continue;
			String mod = NbtAttrs.string(stack.extraAttributes(), "modifier");
			boolean hasSpiritual = mod != null && mod.equalsIgnoreCase("spiritual");
			boolean hasHasty = mod != null && mod.equalsIgnoreCase("hasty");
			if (spiritual) {
				if (hasSpiritual) {
					best = prefer(best, stack);
				}
			} else if (hasHasty) {
				best = prefer(best, stack);
			}
		}
		if (best == null) {
			return missingItem(spiritual ? "Spiritual Duplex Terminator" : "Hasty Terminator", "TERMINATOR");
		}
		Map<String, Integer> ench = NbtAttrs.intMap(best.extraAttributes(), "enchantments");
		List<String> details = new ArrayList<>();
		String mod = NbtAttrs.string(best.extraAttributes(), "modifier");
		if (mod != null) details.add(prettyId(mod));
		int cubism = enchLevel(ench, "cubism");
		int power = enchLevel(ench, "power");
		int rend = enchLevel(ench, "rend", "ultimate_rend");
		// Duplex is stored as ultimate_reiterate in item NBT.
		int duplex = enchLevel(ench, "ultimate_reiterate", "ultimate_duplex");
		int fatalTempo = enchLevel(ench, "ultimate_fatal_tempo", "fatal_tempo");
		if (cubism > 0) details.add("Cubism " + cubism);
		if (power > 0) details.add("Power " + power);
		if (fatalTempo > 0) details.add("Fatal Tempo " + fatalTempo);
		else if (rend > 0) details.add("Rend " + rend);
		if (duplex > 0) details.add("Duplex " + duplex);
		String label;
		if (spiritual) {
			label = "Spiritual Duplex Terminator";
		} else if (fatalTempo > 0) {
			label = "Hasty Fatal Tempo Terminator";
		} else if (rend > 0) {
			label = "Hasty Rend Terminator";
		} else {
			label = "Hasty Terminator";
		}
		boolean ownedOk = spiritual
			? (mod != null && mod.equalsIgnoreCase("spiritual"))
			: true;
		return ownedItem(label, baseId(best), details, best, ownedOk);
	}

	private static ImportantItem ragnarock(List<InventoryDecoder.Stack> all) {
		InventoryDecoder.Stack best = null;
		for (InventoryDecoder.Stack stack : all) {
			String id = baseId(stack);
			if (id.contains("RAGNAROCK") || id.equals("RAGNAROCK_AXE")) {
				best = prefer(best, stack);
			}
		}
		if (best == null) {
			return missingItem("[Ragnarock]", "RAGNAROCK_AXE");
		}
		Map<String, Integer> ench = NbtAttrs.intMap(best.extraAttributes(), "enchantments");
		int s = stars(best);
		int chimera = enchLevel(ench, "ultimate_chimera", "chimera");
		// Line: [Ragnarock] [N★] [Chim x]
		String label = "[Ragnarock] [" + s + "★] [Chim " + chimera + "]";
		return ownedItem(label, baseId(best), List.of(), best);
	}

	private static int enchLevel(Map<String, Integer> ench, String... keys) {
		if (ench == null || ench.isEmpty() || keys == null) {
			return 0;
		}
		int best = 0;
		for (String key : keys) {
			if (key == null || key.isBlank()) {
				continue;
			}
			Integer value = ench.get(key.toLowerCase(Locale.ROOT));
			if (value != null) {
				best = Math.max(best, value);
			}
		}
		return best;
	}

	private static ImportantItem sosFlare(List<InventoryDecoder.Stack> all) {
		for (InventoryDecoder.Stack stack : all) {
			String id = baseId(stack);
			if (id.equals("SOS_FLARE") || id.equals("FLARE") || id.endsWith("_FLARE")) {
				return ownedItem("SOS Flare", id, List.of(prettyId(id)), stack);
			}
		}
		return missingItem("SOS Flare", "SOS_FLARE");
	}

	private static ImportantItem missingItem(String label, String iconId) {
		return new ImportantItem(label, false, iconId, List.of(), List.of(), "");
	}

	private static ImportantItem ownedItem(
		String label, String iconId, List<String> details, InventoryDecoder.Stack stack
	) {
		return ownedItem(label, iconId, details, stack, true);
	}

	private static ImportantItem ownedItem(
		String label, String iconId, List<String> details, InventoryDecoder.Stack stack, boolean owned
	) {
		String display = stack.displayName() == null ? "" : stack.displayName();
		if (display.isBlank()) {
			display = label;
		}
		display = ensureColoredName(display, iconId);
		display = withStarsInName(display, stars(stack));
		return new ImportantItem(label, owned, iconId, details, stackLore(stack), display);
	}

	/** Append dungeon stars to the colored display name when missing. */
	static String withStarsInName(String displayName, int stars) {
		if (displayName == null || displayName.isBlank() || stars <= 0) {
			return displayName == null ? "" : displayName;
		}
		if (displayName.contains("✪") || displayName.contains("★")) {
			return displayName;
		}
		return displayName.trim() + " " + "✪".repeat(Math.min(10, stars));
	}

	/** If inventory name lost its § colour, restore from NEU displayname / tier. */
	static String ensureColoredName(String name, String itemId) {
		if (name != null && name.contains("§")) {
			return name;
		}
		String plain = name == null ? "" : name;
		JsonObject neu = itemId == null || itemId.isBlank() ? null : NeuRepoCache.get(itemId.toUpperCase(Locale.ROOT));
		if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive()) {
			String neuName = neu.get("displayname").getAsString();
			if (neuName != null && neuName.contains("§")) {
				if (plain.isBlank()) {
					return neuName;
				}
				// Keep live name (reforge etc.) but reuse NEU leading colour codes.
				StringBuilder prefix = new StringBuilder();
				for (int i = 0; i + 1 < neuName.length(); i++) {
					if (neuName.charAt(i) == '§') {
						prefix.append('§').append(neuName.charAt(i + 1));
						i++;
					} else {
						break;
					}
				}
				if (!prefix.isEmpty()) {
					return prefix + plain;
				}
			}
		}
		if (neu != null && neu.has("tier") && neu.get("tier").isJsonPrimitive()) {
			return tierColorCode(neu.get("tier").getAsString()) + (plain.isBlank() ? prettyId(itemId) : plain);
		}
		return plain;
	}

	private static List<ArmorPiece> scanMageArmor(List<InventoryDecoder.Stack> all) {
		List<ArmorPiece> out = new ArrayList<>();
		out.add(bestMageHelmet(all));
		out.add(bestArmor(all, "CHESTPLATE", true));
		out.add(bestArmor(all, "LEGGINGS", true));
		out.add(bestArmor(all, "BOOTS", true));
		return out;
	}

	private static List<ArmorPiece> scanArcherArmor(List<InventoryDecoder.Stack> all) {
		List<ArmorPiece> out = new ArrayList<>();
		out.add(bestWardenHelmet(all));
		out.add(bestArmor(all, "CHESTPLATE", false));
		out.add(bestArmor(all, "LEGGINGS", false));
		out.add(bestArmor(all, "BOOTS", false));
		return out;
	}

	private static ArmorPiece bestMageHelmet(List<InventoryDecoder.Stack> all) {
		InventoryDecoder.Stack best = null;
		int bestRank = -1;
		for (InventoryDecoder.Stack stack : all) {
			String id = baseId(stack);
			boolean goggles = id.equals("WITHER_GOGGLES") || id.contains("WITHER_GOGGLE");
			boolean helmet = id.contains("HELMET");
			boolean aurora = id.contains("AURORA");
			boolean hollow = id.contains("HOLLOW");
			boolean terror = id.contains("TERROR");
			if (!goggles && !(helmet && (aurora || hollow || terror))) {
				continue;
			}
			String mod = NbtAttrs.string(stack.extraAttributes(), "modifier");
			boolean mageReforge = isMageReforge(mod);
			// Hollow/Terror only count as mage with Loving/Necrotic; Aurora + goggles always ok.
			if (!goggles && !aurora && (hollow || terror) && !mageReforge) {
				continue;
			}
			// Tier first so Infernal Loving Terror beats lower Aurora.
			int rank = kuudraTierRank(id) * 100 + stars(stack);
			if (mageReforge) rank += 50;
			if (goggles) rank += 30;
			else if (aurora) rank += 20;
			else if (hollow) rank += 10;
			else rank += 5;
			if (rank > bestRank) {
				bestRank = rank;
				best = stack;
			}
		}
		if (best == null) {
			return missingArmor("HELMET", "Wither Goggles", "WITHER_GOGGLES");
		}
		return ownedArmor(best, "HELMET");
	}

	private static ArmorPiece bestWardenHelmet(List<InventoryDecoder.Stack> all) {
		InventoryDecoder.Stack best = null;
		for (InventoryDecoder.Stack stack : all) {
			String id = baseId(stack);
			if (id.contains("WARDEN") && id.contains("HELMET")) {
				best = prefer(best, stack);
			}
		}
		if (best == null) {
			return missingArmor("HELMET", "Warden Helmet", "WARDEN_HELMET");
		}
		return ownedArmor(best, "HELMET");
	}

	private static ArmorPiece bestArmor(List<InventoryDecoder.Stack> all, String slot, boolean mage) {
		InventoryDecoder.Stack best = null;
		int bestRank = -1;
		for (InventoryDecoder.Stack stack : all) {
			String id = baseId(stack);
			if (!id.endsWith("_" + slot) && !id.contains("_" + slot)) continue;
			boolean terror = id.contains("TERROR");
			boolean aurora = id.contains("AURORA");
			boolean hollow = id.contains("HOLLOW");
			String mod = NbtAttrs.string(stack.extraAttributes(), "modifier");
			boolean mageReforge = isMageReforge(mod);
			if (mage) {
				// Aurora always; Hollow/Terror only with Loving or Necrotic.
				if (aurora) {
					// ok
				} else if ((hollow || terror) && mageReforge) {
					// ok
				} else {
					continue;
				}
			} else if (!terror) {
				continue;
			}
			int rank = kuudraTierRank(id) * 100 + stars(stack);
			if (mage) {
				if (mageReforge) rank += 50;
				if (aurora) rank += 20;
				else if (hollow) rank += 10;
				else rank += 5;
			}
			if (rank > bestRank) {
				bestRank = rank;
				best = stack;
			}
		}
		String slotLabel = switch (slot) {
			case "HELMET" -> "Helmet";
			case "CHESTPLATE" -> "Chestplate";
			case "LEGGINGS" -> "Leggings";
			case "BOOTS" -> "Boots";
			default -> slot;
		};
		if (best == null) {
			String fallback = mage ? "AURORA_" + slot : "TERROR_" + slot;
			return missingArmor(slot, slotLabel, fallback);
		}
		return ownedArmor(best, slot);
	}

	private static boolean isMageReforge(String mod) {
		if (mod == null || mod.isBlank()) {
			return false;
		}
		String m = mod.toLowerCase(Locale.ROOT);
		return m.contains("necrotic") || m.contains("loving");
	}

	private static ArmorPiece missingArmor(String slot, String label, String iconId) {
		return new ArmorPiece(slot, label, iconId, false, List.of(), List.of(), null, 0, "", "");
	}

	private static ArmorPiece ownedArmor(InventoryDecoder.Stack best, String slot) {
		List<String> details = armorDetails(best);
		String mod = NbtAttrs.string(best.extraAttributes(), "modifier");
		String dyeItem = NbtAttrs.string(best.extraAttributes(), "dye_item");
		if (dyeItem == null) dyeItem = "";
		String tier = kuudraTierName(baseId(best));
		String set = setName(baseId(best));
		String label;
		if (baseId(best).contains("WITHER_GOGGLE")) {
			label = (mod != null && !mod.isBlank() ? prettyId(mod) + " " : "") + "Wither Goggles";
		} else if (baseId(best).contains("WARDEN")) {
			label = (mod != null && !mod.isBlank() ? prettyId(mod) + " " : "") + prettyId(baseId(best));
		} else {
			label = (mod != null && !mod.isBlank() ? prettyId(mod) + " " : "")
				+ (tier.isBlank() ? "" : tier + " ") + set;
		}
		int starCount = stars(best);
		String display = best.displayName() == null ? "" : best.displayName();
		if (display.isBlank()) {
			display = label.trim();
		}
		display = ensureColoredName(display, baseId(best));
		display = withStarsInName(display, starCount);
		return new ArmorPiece(
			slot,
			label.trim(),
			baseId(best),
			true,
			details,
			stackLore(best),
			best.dyeColor(),
			starCount,
			dyeItem,
			display
		);
	}

	private static List<String> stackLore(InventoryDecoder.Stack stack) {
		if (stack == null || stack.lore() == null || stack.lore().isEmpty()) {
			return List.of();
		}
		return List.copyOf(stack.lore());
	}

	private static List<String> armorDetails(InventoryDecoder.Stack stack) {
		List<String> details = new ArrayList<>();
		int stars = stars(stack);
		if (stars > 0) details.add(stars + "★");
		String mod = NbtAttrs.string(stack.extraAttributes(), "modifier");
		if (mod != null && !mod.isBlank()) details.add(prettyId(mod));
		String dyeItem = NbtAttrs.string(stack.extraAttributes(), "dye_item");
		if (dyeItem != null && !dyeItem.isBlank()) details.add(prettyId(dyeItem));
		if (stack.dyeColor() != null) {
			details.add(String.format(Locale.ROOT, "#%06X", stack.dyeColor() & 0xFFFFFF));
		}
		Map<String, Integer> attrs = NbtAttrs.intMap(stack.extraAttributes(), "attributes");
		Integer legion = attrs.get("legion");
		if (legion != null && legion > 0) details.add("Legion " + legion);
		Integer manaPool = attrs.get("mana_pool");
		if (manaPool != null && manaPool > 0) details.add("Mana Pool " + manaPool);
		Integer manaRegeneration = attrs.get("mana_regeneration");
		if (manaRegeneration != null && manaRegeneration > 0) details.add("Mana Regen " + manaRegeneration);
		return details;
	}

	private static int kuudraTierRank(String id) {
		String u = id.toUpperCase(Locale.ROOT);
		if (u.startsWith("INFERNAL_")) return 4;
		if (u.startsWith("FIERY_")) return 3;
		if (u.startsWith("BURNING_")) return 2;
		if (u.startsWith("HOT_")) return 1;
		return 0;
	}

	private static String kuudraTierName(String id) {
		return switch (kuudraTierRank(id)) {
			case 4 -> "Infernal";
			case 3 -> "Fiery";
			case 2 -> "Burning";
			case 1 -> "Hot";
			default -> "";
		};
	}

	private static String setName(String id) {
		String u = id.toUpperCase(Locale.ROOT);
		for (String prefix : List.of("INFERNAL_", "FIERY_", "BURNING_", "HOT_")) {
			if (u.startsWith(prefix)) {
				u = u.substring(prefix.length());
				break;
			}
		}
		int cut = u.lastIndexOf('_');
		if (cut > 0) {
			u = u.substring(0, cut);
		}
		return prettyId(u);
	}

	private static int stars(InventoryDecoder.Stack stack) {
		if (stack == null || stack.extraAttributes() == null) return 0;
		int upgrade = NbtAttrs.intValue(stack.extraAttributes(), "upgrade_level", 0);
		if (upgrade > 0) return upgrade;
		return NbtAttrs.intValue(stack.extraAttributes(), "dungeon_item_level", 0);
	}

	private static InventoryDecoder.Stack prefer(InventoryDecoder.Stack a, InventoryDecoder.Stack b) {
		if (a == null) return b;
		if (b == null) return a;
		int sa = stars(a) + kuudraTierRank(baseId(a)) * 10;
		int sb = stars(b) + kuudraTierRank(baseId(b)) * 10;
		return sb >= sa ? b : a;
	}

	private static String baseId(InventoryDecoder.Stack stack) {
		if (stack == null || stack.id() == null) return "";
		String id = stack.id().toUpperCase(Locale.ROOT);
		int cut = id.indexOf(';');
		return cut >= 0 ? id.substring(0, cut) : id;
	}

	private static long personalBank(JsonObject member) {
		JsonObject profile = Leveling.obj(member.get("profile"));
		if (profile != null) {
			Float bal = Leveling.num(profile.get("bank_account"));
			if (bal != null) return Math.round(bal);
		}
		return 0L;
	}

	private static List<InventoryDecoder.Stack> flatten(Map<String, List<InventoryDecoder.Stack>> cats) {
		List<InventoryDecoder.Stack> out = new ArrayList<>();
		if (cats == null) return out;
		for (List<InventoryDecoder.Stack> list : cats.values()) {
			if (list == null) continue;
			for (InventoryDecoder.Stack stack : list) {
				if (stack != null && stack.id() != null && !stack.id().isBlank()) {
					out.add(stack);
				}
			}
		}
		return out;
	}

	private static String prettyId(String id) {
		if (id == null || id.isBlank()) return "";
		String[] parts = id.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) sb.append(part.substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}

	private static String str(com.google.gson.JsonElement el) {
		if (el == null || !el.isJsonPrimitive()) return "";
		try { return el.getAsString(); } catch (IllegalStateException | ClassCastException | UnsupportedOperationException ignored) { return ""; }
	}

	private static double num(com.google.gson.JsonElement el) {
		if (el == null || !el.isJsonPrimitive()) return 0;
		try { return el.getAsDouble(); } catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) { return 0; }
	}
}
