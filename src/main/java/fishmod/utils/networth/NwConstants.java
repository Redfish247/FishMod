package fishmod.utils.networth;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ported verbatim from SkyHelper-Networth constants (applicationWorth.js, misc.js, reforges.js,
 * prestiges.js). These drive the per-modifier multipliers and lookups used by the networth calc.
 */
public final class NwConstants {
    private NwConstants() {}

    // ---- APPLICATION_WORTH (applicationWorth.js) ----
    public static final double ENRICHMENT = 0.5;
    public static final double FARMING_FOR_DUMMIES = 0.5;
    public static final double OVERCLOCKER_3000 = 0.9;
    public static final double GEMSTONE_POWER_SCROLL = 0.5;
    public static final double WOOD_SINGULARITY = 0.5;
    public static final double ART_OF_WAR = 0.6;
    public static final double FUMING_POTATO_BOOK = 0.6;
    public static final double GEMSTONE_SLOTS = 0.6;
    public static final double RUNES = 0.6;
    public static final double TUNED_TRANSMISSION = 0.7;
    public static final double POCKET_SACK_IN_A_SACK = 0.7;
    public static final double ESSENCE = 0.75;
    public static final double SILEX = 0.75;
    public static final double ART_OF_PEACE = 0.8;
    public static final double DIVAN_POWDER_COATING = 0.8;
    public static final double ENCHANTMENT_UPGRADES = 0.8;
    public static final double JALAPENO_BOOK = 0.8;
    public static final double MANA_DISINTEGRATOR = 0.8;
    public static final double RECOMBOBULATOR = 0.8;
    public static final double THUNDER_IN_A_BOTTLE = 0.8;
    public static final double ENCHANTMENTS = 0.85;
    public static final double SHENS_AUCTION_PRICE = 0.85;
    public static final double BOOSTER = 0.8;
    public static final double DYE = 0.9;
    public static final double GEMSTONE_CHAMBERS = 0.9;
    public static final double ROD_PART = 1;
    public static final double DRILL_PART = 1;
    public static final double ETHERWARP = 1;
    public static final double MASTER_STAR = 1;
    public static final double GEMSTONE = 1;
    public static final double HOT_POTATO_BOOK = 1;
    public static final double NECRON_BLADE_SCROLL = 1;
    public static final double POLARVOID_BOOK = 1;
    public static final double PRESTIGE_ITEM = 1;
    public static final double REFORGE = 1;
    public static final double PET_CANDY = 0.65;
    public static final double SOULBOUND_PET_SKINS = 0.8;
    public static final double SOULBOUND_SKINS = 0.8;
    public static final double PET_ITEM = 1;

    // ---- ENCHANTMENTS_WORTH (applicationWorth.js) ----
    public static final Map<String, Double> ENCHANTMENTS_WORTH = new HashMap<>();
    static {
        ENCHANTMENTS_WORTH.put("COUNTER_STRIKE", 0.2);
        ENCHANTMENTS_WORTH.put("BIG_BRAIN", 0.35);
        ENCHANTMENTS_WORTH.put("ULTIMATE_INFERNO", 0.35);
        ENCHANTMENTS_WORTH.put("OVERLOAD", 0.35);
        ENCHANTMENTS_WORTH.put("ULTIMATE_SOUL_EATER", 0.35);
        ENCHANTMENTS_WORTH.put("ULTIMATE_FATAL_TEMPO", 0.65);
    }

    // ---- misc.js ----
    public static final Map<String, Set<String>> BLOCKED_ENCHANTMENTS = new HashMap<>();
    static {
        BLOCKED_ENCHANTMENTS.put("BONE_BOOMERANG", new HashSet<>(Arrays.asList("OVERLOAD", "POWER", "ULTIMATE_SOUL_EATER")));
        BLOCKED_ENCHANTMENTS.put("DEATH_BOW", new HashSet<>(Arrays.asList("OVERLOAD", "POWER", "ULTIMATE_SOUL_EATER")));
        BLOCKED_ENCHANTMENTS.put("GARDENING_AXE", new HashSet<>(Arrays.asList("REPLENISH")));
        BLOCKED_ENCHANTMENTS.put("GARDENING_HOE", new HashSet<>(Arrays.asList("REPLENISH")));
        BLOCKED_ENCHANTMENTS.put("ADVANCED_GARDENING_AXE", new HashSet<>(Arrays.asList("REPLENISH")));
        BLOCKED_ENCHANTMENTS.put("ADVANCED_GARDENING_HOE", new HashSet<>(Arrays.asList("REPLENISH")));
    }

    /** IGNORED_ENCHANTMENTS: enchant name -> level to skip exactly. */
    public static final Map<String, Integer> IGNORED_ENCHANTMENTS = new HashMap<>();
    static { IGNORED_ENCHANTMENTS.put("SCAVENGER", 5); }

    public static final Set<String> STACKING_ENCHANTMENTS = new HashSet<>(Arrays.asList(
            "EXPERTISE", "COMPACT", "ABSORB", "CULTIVATING", "CHAMPION", "HECATOMB", "TOXOPHILITE"));

    public static final Set<String> IGNORE_SILEX = new HashSet<>(Arrays.asList("PROMISING_SPADE", "PROMISING_AXE"));

    public static final String[] MASTER_STARS = {
            "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"};

    public static final Set<String> ALLOWED_RECOMBOBULATED_CATEGORIES = new HashSet<>(Arrays.asList(
            "ACCESSORY", "NECKLACE", "GLOVES", "BRACELET", "BELT", "CLOAK", "VACUUM"));

    public static final Set<String> ALLOWED_RECOMBOBULATED_IDS = new HashSet<>(Arrays.asList(
            "DIVAN_HELMET", "DIVAN_CHESTPLATE", "DIVAN_LEGGINGS", "DIVAN_BOOTS",
            "FERMENTO_HELMET", "FERMENTO_CHESTPLATE", "FERMENTO_LEGGINGS", "FERMENTO_BOOTS",
            "SHADOW_ASSASSIN_CLOAK", "STARRED_SHADOW_ASSASSIN_CLOAK"));

    public static final List<String> ENRICHMENTS = Arrays.asList(
            "TALISMAN_ENRICHMENT_CRITICAL_CHANCE", "TALISMAN_ENRICHMENT_CRITICAL_DAMAGE",
            "TALISMAN_ENRICHMENT_DEFENSE", "TALISMAN_ENRICHMENT_HEALTH", "TALISMAN_ENRICHMENT_INTELLIGENCE",
            "TALISMAN_ENRICHMENT_MAGIC_FIND", "TALISMAN_ENRICHMENT_WALK_SPEED", "TALISMAN_ENRICHMENT_STRENGTH",
            "TALISMAN_ENRICHMENT_ATTACK_SPEED", "TALISMAN_ENRICHMENT_FEROCITY", "TALISMAN_ENRICHMENT_SEA_CREATURE_CHANCE");

    public static final Set<String> GEMSTONE_SLOT_TYPES = new HashSet<>(Arrays.asList(
            "COMBAT", "OFFENSIVE", "DEFENSIVE", "MINING", "UNIVERSAL", "CHISEL"));

    // ENCHANTMENT_UPGRADES (ItemEnchantments.js): enchant -> {upgradeItem, tier}
    public static final Map<String, int[]> ENCHANTMENT_UPGRADE_TIER = new HashMap<>();
    public static final Map<String, String> ENCHANTMENT_UPGRADE_ITEM = new HashMap<>();
    static {
        put("SCAVENGER", "GOLDEN_BOUNTY", 6);
        put("PESTERMINATOR", "PESTHUNTING_GUIDE", 6);
        put("LUCK_OF_THE_SEA", "GOLD_BOTTLE_CAP", 7);
        put("PISCARY", "TROUBLED_BUBBLE", 7);
        put("FRAIL", "SEVERED_PINCER", 7);
        put("SPIKED_HOOK", "OCTOPUS_TENDRIL", 7);
        put("CHARM", "CHAIN_END_TIMES", 6);
        put("VENOMOUS", "FATEFUL_STINGER", 7);
    }
    private static void put(String ench, String item, int tier) {
        ENCHANTMENT_UPGRADE_ITEM.put(ench, item);
        ENCHANTMENT_UPGRADE_TIER.put(ench, new int[]{tier});
    }

    // MIDAS_SWORDS (MidasWeapon.js): id -> {maxBid, type}
    public static final Map<String, Object[]> MIDAS_SWORDS = new HashMap<>();
    static {
        MIDAS_SWORDS.put("MIDAS_SWORD", new Object[]{50_000_000L, "MIDAS_SWORD_50M"});
        MIDAS_SWORDS.put("STARRED_MIDAS_SWORD", new Object[]{250_000_000L, "STARRED_MIDAS_SWORD_250M"});
        MIDAS_SWORDS.put("MIDAS_STAFF", new Object[]{100_000_000L, "MIDAS_STAFF_100M"});
        MIDAS_SWORDS.put("STARRED_MIDAS_STAFF", new Object[]{500_000_000L, "STARRED_MIDAS_STAFF_500M"});
    }

    // REFORGES (reforges.js): reforge modifier -> reforge-stone item id
    public static final Map<String, String> REFORGES = new HashMap<>();
    static {
        REFORGES.put("stiff", "HARDENED_WOOD");
        REFORGES.put("trashy", "OVERFLOWING_TRASH_CAN");
        REFORGES.put("salty", "SALT_CUBE");
        REFORGES.put("aote_stone", "AOTE_STONE");
        REFORGES.put("blazing", "BLAZEN_SPHERE");
        REFORGES.put("waxed", "BLAZE_WAX");
        REFORGES.put("rooted", "BURROWING_SPORES");
        REFORGES.put("calcified", "CALCIFIED_HEART");
        REFORGES.put("candied", "CANDY_CORN");
        REFORGES.put("perfect", "DIAMOND_ATOM");
        REFORGES.put("fleet", "DIAMONITE");
        REFORGES.put("fabled", "DRAGON_CLAW");
        REFORGES.put("spiked", "DRAGON_SCALE");
        REFORGES.put("royal", "DWARVEN_TREASURE");
        REFORGES.put("warped", "ENDSTONE_GEODE");
        REFORGES.put("coldfusion", "ENTROPY_SUPPRESSOR");
        REFORGES.put("blooming", "FLOWERING_BOUQUET");
        REFORGES.put("fanged", "FULL_JAW_FANGING_KIT");
        REFORGES.put("jaded", "JADERALD");
        REFORGES.put("jerry_stone", "JERRY_STONE");
        REFORGES.put("magnetic", "LAPIS_CRYSTAL");
        REFORGES.put("earthy", "LARGE_WALNUT");
        REFORGES.put("groovy", "MANGROVE_GEM");
        REFORGES.put("fortified", "METEOR_SHARD");
        REFORGES.put("gilded", "MIDAS_JEWEL");
        REFORGES.put("cubic", "MOLTEN_CUBE");
        REFORGES.put("moonglade", "MOONGLADE_JEWEL");
        REFORGES.put("lunar", "MOONSTONE");
        REFORGES.put("necrotic", "NECROMANCER_BROOCH");
        REFORGES.put("fruitful", "ONYX");
        REFORGES.put("precise", "OPTICAL_LENS");
        REFORGES.put("mossy", "OVERGROWN_GRASS");
        REFORGES.put("pitchin", "PITCHIN_KOI");
        REFORGES.put("undead", "PREMIUM_FLESH");
        REFORGES.put("blood_soaked", "PRESUMED_GALLON_OF_RED_PAINT");
        REFORGES.put("mithraic", "PURE_MITHRIL");
        REFORGES.put("reinforced", "RARE_DIAMOND");
        REFORGES.put("ridiculous", "RED_NOSE");
        REFORGES.put("loving", "RED_SCARF");
        REFORGES.put("auspicious", "ROCK_GEMSTONE");
        REFORGES.put("treacherous", "RUSTY_ANCHOR");
        REFORGES.put("headstrong", "SALMON_OPAL");
        REFORGES.put("strengthened", "SEARING_STONE");
        REFORGES.put("glistening", "SHINY_PRISM");
        REFORGES.put("bustling", "SKYMART_BROCHURE");
        REFORGES.put("spiritual", "SPIRIT_DECOY");
        REFORGES.put("squeaky", "SQUEAKY_TOY");
        REFORGES.put("sunny", "SUNSTONE");
        REFORGES.put("suspicious", "SUSPICIOUS_VIAL");
        REFORGES.put("snowy", "TERRY_SNOWGLOBE");
        REFORGES.put("dimensional", "TITANIUM_TESSERACT");
        REFORGES.put("ambered", "AMBER_MATERIAL");
        REFORGES.put("beady", "BEADY_EYES");
        REFORGES.put("blessed", "BLESSED_FRUIT");
        REFORGES.put("bulky", "BULKY_STONE");
        REFORGES.put("buzzing", "CLIPPED_WINGS");
        REFORGES.put("erudite", "DAEDALUS_NOTES");
        REFORGES.put("submerged", "DEEP_SEA_ORB");
        REFORGES.put("renowned", "DRAGON_HORN");
        REFORGES.put("festive", "FROZEN_BAUBLE");
        REFORGES.put("giant", "GIANT_TOOTH");
        REFORGES.put("lustrous", "GLEAMING_CRYSTAL");
        REFORGES.put("bountiful", "GOLDEN_BALL");
        REFORGES.put("chomp", "KUUDRA_MANDIBLE");
        REFORGES.put("lucky", "LUCKY_DICE");
        REFORGES.put("mantid", "MANTID_CLAW");
        REFORGES.put("stellar", "PETRIFIED_STARFALL");
        REFORGES.put("scraped", "POCKET_ICEBERG");
        REFORGES.put("ancient", "PRECURSOR_GEAR");
        REFORGES.put("refined", "REFINED_AMBER");
        REFORGES.put("empowered", "SADAN_BROOCH");
        REFORGES.put("withered", "WITHER_BLOOD");
        REFORGES.put("glacial", "FRIGID_HUSK");
        REFORGES.put("heated", "HOT_STUFF");
        REFORGES.put("blood_shot", "SHRIVELED_CORNEA");
        REFORGES.put("dirty", "DIRT_BOTTLE");
        REFORGES.put("moil", "MOIL_LOG");
        REFORGES.put("toil", "TOIL_LOG");
        REFORGES.put("greater_spook", "BOO_STONE");
    }

    // PRESTIGES (prestiges.js): item id -> list of prestige item ids (in order)
    public static final Map<String, String[]> PRESTIGES = new HashMap<>();
    static {
        String[] families = {"CRIMSON", "TERROR", "FERVOR", "HOLLOW", "AURORA"};
        String[] pieces = {"CHESTPLATE", "HELMET", "LEGGINGS", "BOOTS"};
        // ordered prestige tiers (low -> high)
        String[] tiers = {"", "HOT_", "BURNING_", "FIERY_", "INFERNAL_"};
        for (String fam : families) {
            for (String piece : pieces) {
                // for each tier above base, list lower tiers in descending order
                for (int t = 1; t < tiers.length; t++) {
                    StringBuilder keyB = new StringBuilder().append(tiers[t]).append(fam).append("_").append(piece);
                    java.util.List<String> lowers = new java.util.ArrayList<>();
                    for (int j = t - 1; j >= 0; j--) {
                        lowers.add(tiers[j] + fam + "_" + piece);
                    }
                    PRESTIGES.put(keyB.toString(), lowers.toArray(new String[0]));
                }
            }
        }
    }
}
