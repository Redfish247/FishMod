package fishmod.features.challenges;

import java.util.ArrayList;
import java.util.List;

/**
 * Curated catalog of named SkyBlock XP missions — concrete, specific milestones
 * (collection tiers, skill levels, slayer ranks, cata levels, pet milestones)
 * each of which corresponds to a known SkyBlock-XP reward.
 *
 * GapAnalyzer scans this catalog for missions the player hasn't completed,
 * scores each by closeness, and picks one sized to the tier.
 */
public class SkyblockMissions {

    public enum Type {
        COLLECTION, SKILL_LEVEL, SLAYER_LEVEL, CATA_LEVEL, PET_COUNT_AT_100,
        HOTM_LEVEL, MAGICAL_POWER, FAIRY_SOULS, CLASS_LEVEL, SB_LEVEL
    }

    public static class Mission {
        public final String id;
        public final Type   type;
        public final String paramStr;   // collection key / skill name / slayer name
        public final long   target;     // amount / level
        public final int    sbxp;       // reward (Hypixel SkyBlock XP)
        public final String label;      // human-readable
        public final boolean dailyOk, weeklyOk, monthlyOk;

        public Mission(String id, Type type, String paramStr, long target, int sbxp,
                       String label, boolean d, boolean w, boolean m) {
            this.id = id; this.type = type; this.paramStr = paramStr;
            this.target = target; this.sbxp = sbxp; this.label = label;
            this.dailyOk = d; this.weeklyOk = w; this.monthlyOk = m;
        }
    }

    public static final List<Mission> ALL = new ArrayList<>();

    static {
        // ── Collections (top tier targets — common picks shown by SB XP rewards) ─
        // d/w/m eligibility roughly by grindiness.
        col("WHEAT",        250_000,   25, "Wheat collection to MAX (250k)",     false, true,  true);
        col("CARROT_ITEM",  100_000,   25, "Carrot collection to MAX (100k)",    false, true,  true);
        col("POTATO_ITEM",  100_000,   25, "Potato collection to MAX (100k)",    false, true,  true);
        col("PUMPKIN",      100_000,   25, "Pumpkin collection to MAX (100k)",   false, true,  true);
        col("MELON",        100_000,   25, "Melon collection to MAX (100k)",     false, true,  true);
        col("MUSHROOM_COLLECTION", 50_000, 25, "Mushroom collection to MAX (50k)", false, true, true);
        col("CACTUS",        50_000,   25, "Cactus collection to MAX (50k)",     false, true,  true);
        col("SUGAR_CANE",    50_000,   25, "Sugar Cane collection to MAX (50k)", false, true,  true);
        col("NETHER_STALK",  50_000,   25, "Nether Wart collection to MAX (50k)", false, true, true);
        col("COBBLESTONE",   25_000,   25, "Cobblestone collection to MAX (25k)", true, true,  true);
        col("OBSIDIAN",      50_000,   25, "Obsidian collection to MAX (50k)",   false, true, true);
        col("COAL",          50_000,   25, "Coal collection to MAX (50k)",       true,  true, true);
        col("IRON_INGOT",    100_000,  25, "Iron collection to MAX (100k)",      false, true, true);
        col("GOLD_INGOT",    50_000,   25, "Gold collection to MAX (50k)",       false, true, true);
        col("DIAMOND",       25_000,   25, "Diamond collection to MAX (25k)",    false, true, true);
        col("EMERALD",       25_000,   25, "Emerald collection to MAX (25k)",    false, true, true);
        col("MITHRIL_ORE",   75_000,   25, "Mithril collection to MAX (75k)",    false, true, true);
        col("LOG",           20_000,   25, "Oak collection to MAX (20k)",        true,  true, true);
        col("LOG:1",         20_000,   25, "Spruce collection to MAX (20k)",     true,  true, true);
        col("LOG:2",         20_000,   25, "Birch collection to MAX (20k)",      true,  true, true);
        col("LOG:3",         20_000,   25, "Jungle collection to MAX (20k)",     true,  true, true);
        col("RAW_FISH",      10_000,   25, "Fish collection to MAX (10k)",       false, true, true);
        col("INK_SACK:4",    25_000,   25, "Lapis collection to MAX (25k)",      false, true, true);
        col("REDSTONE",      50_000,   25, "Redstone collection to MAX (50k)",   false, true, true);
        col("ROTTEN_FLESH",  25_000,   25, "Rotten Flesh collection to MAX (25k)", false, true, true);
        col("BONE",          25_000,   25, "Bone collection to MAX (25k)",       false, true, true);
        col("STRING",        25_000,   25, "String collection to MAX (25k)",     false, true, true);
        col("SPIDER_EYE",    25_000,   25, "Spider Eye collection to MAX (25k)", false, true, true);
        col("ENDER_PEARL",   10_000,   25, "Ender Pearl collection to MAX (10k)", false, true, true);
        col("GHAST_TEAR",    5_000,    25, "Ghast Tear collection to MAX (5k)",  false, true, true);
        col("SLIME_BALL",    25_000,   25, "Slimeball collection to MAX (25k)",  false, true, true);

        // ── Skill milestones ─────────────────────────────────────────────────
        skill("combat",    25, 50,  "Combat 25",   false, true,  true);
        skill("combat",    50, 100, "Combat 50",   false, false, true);
        skill("combat",    60, 250, "Combat 60",   false, false, true);
        skill("mining",    25, 50,  "Mining 25",   false, true,  true);
        skill("mining",    50, 100, "Mining 50",   false, false, true);
        skill("mining",    60, 250, "Mining 60",   false, false, true);
        skill("farming",   25, 50,  "Farming 25",  false, true,  true);
        skill("farming",   50, 100, "Farming 50",  false, false, true);
        skill("farming",   60, 250, "Farming 60",  false, false, true);
        skill("foraging",  25, 50,  "Foraging 25", false, true,  true);
        skill("foraging",  50, 100, "Foraging 50", false, false, true);
        skill("fishing",   25, 50,  "Fishing 25",  false, true,  true);
        skill("fishing",   50, 100, "Fishing 50",  false, false, true);
        skill("enchanting",25, 50,  "Enchanting 25", false, true, true);
        skill("enchanting",50, 100, "Enchanting 50", false, false, true);
        skill("alchemy",   25, 50,  "Alchemy 25",  false, true,  true);
        skill("alchemy",   50, 100, "Alchemy 50",  false, false, true);
        skill("taming",    25, 50,  "Taming 25",   false, true,  true);
        skill("taming",    50, 100, "Taming 50",   false, false, true);

        // ── Slayer levels ────────────────────────────────────────────────────
        for (String sl : new String[]{"zombie","spider","wolf","enderman","blaze"}) {
            slayer(sl, 5, 25,  "Slayer 5 — " + sl, true,  true, true);
            slayer(sl, 7, 75,  "Slayer 7 — " + sl, false, true, true);
            slayer(sl, 8, 125, "Slayer 8 — " + sl, false, false, true);
            slayer(sl, 9, 250, "Slayer 9 — " + sl, false, false, true);
        }

        // ── Catacombs levels (each grants SB XP) ─────────────────────────────
        cata(20, 25,  "Catacombs 20", false, true,  true);
        cata(30, 50,  "Catacombs 30", false, true,  true);
        cata(35, 75,  "Catacombs 35", false, false, true);
        cata(40, 125, "Catacombs 40", false, false, true);
        cata(45, 175, "Catacombs 45", false, false, true);
        cata(50, 250, "Catacombs 50", false, false, true);

        // ── Pet milestones (pets at level 100 grant SB XP) ───────────────────
        petCount(1,  25,  "1st pet to level 100",  true,  true, true);
        petCount(3,  50,  "3 pets at level 100",   false, true, true);
        petCount(5,  100, "5 pets at level 100",   false, true, true);
        petCount(10, 250, "10 pets at level 100",  false, false, true);
        petCount(20, 500, "20 pets at level 100",  false, false, true);

        // ── Heart of the Mountain ────────────────────────────────────────────
        hotm(2,  25,  "Heart of the Mountain Tier 2", true,  true,  true);
        hotm(3,  50,  "Heart of the Mountain Tier 3", false, true,  true);
        hotm(4,  75,  "Heart of the Mountain Tier 4", false, true,  true);
        hotm(5,  100, "Heart of the Mountain Tier 5", false, false, true);
        hotm(6,  150, "Heart of the Mountain Tier 6", false, false, true);
        hotm(7,  200, "Heart of the Mountain Tier 7", false, false, true);
        hotm(8,  300, "Heart of the Mountain Tier 8", false, false, true);
        hotm(9,  400, "Heart of the Mountain Tier 9", false, false, true);
        hotm(10, 500, "Heart of the Mountain Tier 10", false, false, true);

        // ── Magical Power ────────────────────────────────────────────────────
        mp(50,   25,  "50 Magical Power",   true,  true,  true);
        mp(100,  50,  "100 Magical Power",  false, true,  true);
        mp(150,  75,  "150 Magical Power",  false, true,  true);
        mp(200,  125, "200 Magical Power",  false, false, true);
        mp(300,  175, "300 Magical Power",  false, false, true);
        mp(400,  250, "400 Magical Power",  false, false, true);
        mp(500,  350, "500 Magical Power",  false, false, true);
        mp(600,  500, "600 Magical Power",  false, false, true);

        // ── Fairy souls (each cluster of 5 grants a stat boost + SB XP at milestones) ──
        fs(20,  25,  "Collect 20 Fairy Souls",  true,  true,  true);
        fs(50,  50,  "Collect 50 Fairy Souls",  false, true,  true);
        fs(100, 75,  "Collect 100 Fairy Souls", false, true,  true);
        fs(150, 100, "Collect 150 Fairy Souls", false, false, true);
        fs(200, 150, "Collect 200 Fairy Souls", false, false, true);
        fs(226, 250, "Collect all 226 Fairy Souls", false, false, true);

        // ── Dungeon class levels ─────────────────────────────────────────────
        for (String cls : new String[]{"healer","mage","berserk","archer","tank"}) {
            classLvl(cls, 15, 25,  cls + " class lvl 15", true,  true,  true);
            classLvl(cls, 25, 50,  cls + " class lvl 25", false, true,  true);
            classLvl(cls, 35, 100, cls + " class lvl 35", false, false, true);
            classLvl(cls, 50, 250, cls + " class lvl 50", false, false, true);
        }

        // ── SkyBlock level milestones ────────────────────────────────────────
        sbLvl(50,  25,  "SkyBlock level 50",  true,  true,  true);
        sbLvl(100, 50,  "SkyBlock level 100", false, true,  true);
        sbLvl(150, 75,  "SkyBlock level 150", false, true,  true);
        sbLvl(200, 100, "SkyBlock level 200", false, false, true);
        sbLvl(250, 150, "SkyBlock level 250", false, false, true);
        sbLvl(300, 200, "SkyBlock level 300", false, false, true);
        sbLvl(400, 350, "SkyBlock level 400", false, false, true);

        // ── Intermediate collection tiers (the early grind that gives quick wins) ──
        // Smaller targets than the MAX entries — daily-friendly bumps.
        colMid("WHEAT",       50_000,   10, "Wheat to ~50% (50k)",         true, true, true);
        colMid("CARROT_ITEM", 25_000,   10, "Carrot to ~50% (25k)",        true, true, true);
        colMid("POTATO_ITEM", 25_000,   10, "Potato to ~50% (25k)",        true, true, true);
        colMid("COBBLESTONE", 5_000,    10, "Cobble to 5k",                true, true, true);
        colMid("COAL",        10_000,   10, "Coal to 10k",                 true, true, true);
        colMid("IRON_INGOT",  25_000,   10, "Iron to 25k",                 true, true, true);
        colMid("GOLD_INGOT",  10_000,   10, "Gold to 10k",                 true, true, true);
        colMid("MITHRIL_ORE", 15_000,   10, "Mithril to 15k",              true, true, true);
        colMid("LOG",         5_000,    10, "Oak to 5k",                   true, true, true);
        colMid("INK_SACK:3",  5_000,    10, "Cocoa to 5k",                 true, true, true);
    }

    private static void col(String key, long target, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("COL_" + key, Type.COLLECTION, key, target, xp, label, d, w, m));
    }
    private static void skill(String name, int lvl, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("SK_" + name + "_" + lvl, Type.SKILL_LEVEL, name, lvl, xp, label, d, w, m));
    }
    private static void slayer(String name, int lvl, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("SL_" + name + "_" + lvl, Type.SLAYER_LEVEL, name, lvl, xp, label, d, w, m));
    }
    private static void cata(int lvl, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("CATA_" + lvl, Type.CATA_LEVEL, "", lvl, xp, label, d, w, m));
    }
    private static void petCount(int n, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("PET_" + n, Type.PET_COUNT_AT_100, "", n, xp, label, d, w, m));
    }
    private static void hotm(int lvl, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("HOTM_" + lvl, Type.HOTM_LEVEL, "", lvl, xp, label, d, w, m));
    }
    private static void mp(int v, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("MP_" + v, Type.MAGICAL_POWER, "", v, xp, label, d, w, m));
    }
    private static void fs(int v, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("FS_" + v, Type.FAIRY_SOULS, "", v, xp, label, d, w, m));
    }
    private static void classLvl(String cls, int lvl, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("CLS_" + cls + "_" + lvl, Type.CLASS_LEVEL, cls, lvl, xp, label, d, w, m));
    }
    private static void sbLvl(int lvl, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("SB_" + lvl, Type.SB_LEVEL, "", lvl, xp, label, d, w, m));
    }
    private static void colMid(String key, long target, int xp, String label, boolean d, boolean w, boolean m) {
        ALL.add(new Mission("COLMID_" + key + "_" + target, Type.COLLECTION, key, target, xp, label, d, w, m));
    }

    /** Reads the player's current value for this mission. */
    public static double currentValue(Mission m, ProfileSnapshot s) {
        return switch (m.type) {
            case COLLECTION       -> s.collections.getOrDefault(m.paramStr, 0L);
            case SKILL_LEVEL      -> GapAnalyzer.skillLevel(s.skillXp.getOrDefault(m.paramStr, 0L));
            case SLAYER_LEVEL     -> GapAnalyzer.slayerLevel(m.paramStr, s.slayerXp.getOrDefault(m.paramStr, 0L));
            case CATA_LEVEL       -> fishmod.utils.HypixelApi.calcCataLevel(s.cataXp);
            case PET_COUNT_AT_100 -> s.petsAt100;
            case HOTM_LEVEL       -> s.hotmLevel;
            case MAGICAL_POWER    -> s.magicalPower;
            case FAIRY_SOULS      -> s.fairySouls;
            case CLASS_LEVEL      -> s.classLevels.getOrDefault(m.paramStr, 0);
            case SB_LEVEL         -> s.sbLevel;
        };
    }

    public static boolean okFor(Mission m, Tier t) {
        return switch (t) { case DAILY -> m.dailyOk; case WEEKLY -> m.weeklyOk; case MONTHLY -> m.monthlyOk; };
    }
}
