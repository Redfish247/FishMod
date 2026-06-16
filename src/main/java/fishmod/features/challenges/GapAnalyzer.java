package fishmod.features.challenges;

import java.util.*;

/**
 * Pure gap-detection: scan a ProfileSnapshot, score every category for weakness,
 * and emit the highest-weakness Challenge sized to the requested Tier.
 *
 * Sizing philosophy:
 *   DAILY   ~ 30 minutes of focused play
 *   WEEKLY  ~ 3-5 hours
 *   MONTHLY ~ 20-40 hours
 */
public class GapAnalyzer {

    private static final long[] SKILL_XP_LEVEL_TARGETS = {
        // Cumulative XP to reach skill level 60 (combat-tier)
        50, 175, 375, 675, 1175, 1925, 2925, 4425, 6425, 9925,
        14925, 22425, 32425, 47425, 67425, 97425, 147425, 222425, 322425, 522425,
        822425, 1222425, 1722425, 2322425, 3022425, 3822425, 4722425, 5722425, 6822425, 8022425,
        9322425, 10722425, 12222425, 13822425, 15522425, 17322425, 19222425, 21222425, 23322425, 25522425,
        27822425, 30222425, 32722425, 35322425, 38072425, 40972425, 44072425, 47472425, 51172425, 55172425,
        59472425, 64072425, 68972425, 74172425, 79672425, 85472425, 91572425, 97972425, 104672425, 111672425
    };

    public static int skillLevel(long xp) {
        for (int i = SKILL_XP_LEVEL_TARGETS.length - 1; i >= 0; i--)
            if (xp >= SKILL_XP_LEVEL_TARGETS[i]) return i + 1;
        return 0;
    }

    /** Cumulative skill XP required to reach {@code level} (1..60). 0 for level &lt;= 0. */
    public static long xpToReach(int level) {
        if (level <= 0) return 0;
        int idx = Math.min(level, SKILL_XP_LEVEL_TARGETS.length) - 1;
        return SKILL_XP_LEVEL_TARGETS[idx];
    }

    /** Highest skill level represented by the XP table (used as the roadmap cap). */
    public static int maxSkillLevel() { return SKILL_XP_LEVEL_TARGETS.length; }

    // Only skills that can realistically be progressed via grinding.
    // Excluded: social (extremely slow, party-join based), carpentry (limited by recipes),
    // runecrafting (limited by enchanting table availability).
    private static final String[] SKILLS = {
        "combat","mining","farming","foraging","fishing","enchanting","alchemy","taming"
    };
    private static final String[] SLAYERS = {"zombie","spider","wolf","enderman","blaze","vampire"};

    static class Candidate {
        double weakness;       // 0..1
        ChallengeTemplate tpl;
        String paramStr;
        long   paramLong;
        int    paramInt;
        double baseline;
        double targetDelta;    // for delta-based templates
        double absoluteTarget; // for level-based templates (else 0)

        Candidate(double w, ChallengeTemplate t) { weakness = w; tpl = t; }
    }

    public static Challenge pickFor(Tier tier, ProfileSnapshot s) {
        if (s == null) return null;
        List<Candidate> cs = new ArrayList<>();

        // ── Skills: target a delta of XP scaled to tier ────────────────────────
        for (String sk : SKILLS) {
            long xp = s.skillXp.getOrDefault(sk, 0L);
            int lvl = skillLevel(xp);
            double w = 1.0 - Math.min(lvl, 60) / 60.0;
            if (w <= 0.05) continue;
            Candidate c = new Candidate(w, ChallengeTemplate.SKILL_XP_GAIN);
            c.paramStr = sk;
            c.paramLong = deltaXpForSkill(tier, lvl);
            c.baseline = xp;
            c.targetDelta = c.paramLong;
            cs.add(c);
        }

        // ── Pets: level N more pets to 100 (any pets) ──────────────────────────
        // Only generate if there are still pets under 100 to level.
        long underHundred = s.petLevels.values().stream().filter(v -> v < 100).count();
        if (underHundred > 0) {
            int n = switch (tier) { case DAILY -> 3; case WEEKLY -> 5; case MONTHLY -> 10; };
            n = (int) Math.min(n, underHundred);
            // Avg level of under-100 pets drives weakness — lots of low pets = highly suggestible.
            double avg = s.petLevels.values().stream().filter(v -> v < 100).mapToInt(Integer::intValue).average().orElse(50);
            double w = (1.0 - avg / 100.0) * 0.7; // dampen so it doesn't dominate
            Candidate c = new Candidate(w, ChallengeTemplate.PET_LEVEL_UP);
            c.paramInt = n;
            c.baseline = s.petsAt100;
            c.absoluteTarget = s.petsAt100 + n;
            cs.add(c);
        }

        // ── Named SkyBlock XP missions (collection tiers, skill caps, slayer ranks, cata, pets) ──
        for (SkyblockMissions.Mission m : SkyblockMissions.ALL) {
            if (!SkyblockMissions.okFor(m, tier)) continue;
            double cur = SkyblockMissions.currentValue(m, s);
            if (cur >= m.target) continue; // already done
            // Closeness — how close the player already is to completing it (we want missions
            // they can plausibly finish in this tier's window, so closer-to-done = higher score).
            double closeness = m.target > 0 ? cur / (double) m.target : 0;
            // Reward weight — bigger SB-XP missions get a small boost so they're not always
            // crowded out by trivial ones.
            double rewardBoost = Math.min(1.0, m.sbxp / 100.0);
            // Boosted base so structured SkyBlock-XP missions dominate generic skill/dungeon
            // candidates — these are the "what should I do next" milestones the player wants.
            double w = 0.8 + 0.6 * closeness + 0.3 * rewardBoost; // 0.8..1.7
            Candidate c = new Candidate(w, ChallengeTemplate.SB_MISSION);
            c.paramStr = m.id;
            c.baseline = cur;
            c.absoluteTarget = m.target;
            cs.add(c);
        }

        // ── Slayers: XP delta scaled to tier ───────────────────────────────────
        for (String sl : SLAYERS) {
            long xp = s.slayerXp.getOrDefault(sl, 0L);
            int slvl = slayerLevel(sl, xp);
            double w = 1.0 - Math.min(slvl, 9) / 9.0;
            if (w <= 0.05) continue;
            Candidate c = new Candidate(w, ChallengeTemplate.SLAYER_XP_GAIN);
            c.paramStr = sl;
            c.paramLong = deltaXpForSlayer(tier, slvl);
            c.baseline = xp;
            c.targetDelta = c.paramLong;
            cs.add(c);
        }

        // ── Dungeon runs ───────────────────────────────────────────────────────
        // Floor suggestions are gated by catacombs level so we don't ask a Cata 50
        // player to run F3 (trivial) or a Cata 20 player to run M7 (impossible).
        int cataLvl = fishmod.utils.HypixelApi.calcCataLevel(s.cataXp);

        // Normal floors: weight falls off as cata grows past ~35 and approaches 0 by Cata 45.
        // A high-cata player still might want F-runs for cheap completions, but the analyzer
        // strongly prefers M-floors past that point.
        for (int f = 1; f <= 7; f++) {
            long runs = s.floorCompletions.getOrDefault("F" + f, 0L);
            double progress = 1.0 - Math.min(runs, 200) / 200.0;
            double cataMult = Math.max(0.0, 1.0 - Math.max(0, cataLvl - 30) / 15.0); // 1.0 at <=30, 0 at >=45
            if (cataMult <= 0.01) continue;
            Candidate c = new Candidate(progress * cataMult * (0.6 + 0.05 * f), ChallengeTemplate.DUNGEON_RUNS);
            c.paramStr = "F" + f;
            c.paramInt = switch (tier) { case DAILY -> 5; case WEEKLY -> 25; case MONTHLY -> 100; };
            c.baseline = runs;
            c.absoluteTarget = runs + c.paramInt;
            cs.add(c);
        }

        // Master floors: rough cata requirement per floor. Weight rises with cata as they
        // become accessible, peaks once the player has comfortable headroom over the cap.
        int[] minCata = {0, 24, 27, 30, 33, 35, 37, 40}; // index 1..7
        for (int f = 1; f <= 7; f++) {
            long runs = s.floorCompletions.getOrDefault("M" + f, 0L);
            if (cataLvl < minCata[f] - 4) continue; // too far below the bar — would be miserable
            double progress = 1.0 - Math.min(runs, 200) / 200.0;
            double cataMult = Math.min(1.0, Math.max(0.0, (cataLvl - minCata[f] + 4) / 8.0));
            if (cataMult <= 0.01) continue;
            Candidate c = new Candidate(progress * cataMult * (0.75 + 0.05 * f), ChallengeTemplate.DUNGEON_RUNS);
            c.paramStr = "M" + f;
            // Master is slower: lower counts than equivalent-tier F goals.
            c.paramInt = switch (tier) { case DAILY -> 3; case WEEKLY -> 10; case MONTHLY -> 40; };
            c.baseline = runs;
            c.absoluteTarget = runs + c.paramInt;
            cs.add(c);
        }

        // ── Cata level (monthly only) ──────────────────────────────────────────
        if (tier == Tier.MONTHLY) {
            int curLvl = fishmod.utils.HypixelApi.calcCataLevel(s.cataXp);
            if (curLvl < 50) {
                Candidate c = new Candidate(1.0 - curLvl / 50.0, ChallengeTemplate.CATA_LEVEL);
                c.paramInt = Math.min(50, curLvl + 2);
                c.baseline = curLvl;
                c.absoluteTarget = c.paramInt;
                cs.add(c);
            }
        }

        if (cs.isEmpty()) return null;
        cs.sort((a, b) -> Double.compare(b.weakness, a.weakness));

        // Pick from a wider top-6 so variety stays high across rerolls.
        int topN = Math.min(6, cs.size());
        Candidate winner = cs.get(new Random().nextInt(topN));
        return materialize(winner, tier);
    }

    private static Challenge materialize(Candidate c, Tier tier) {
        Challenge ch = new Challenge();
        ch.tier = tier;
        ch.template = c.tpl;
        ch.paramStr = c.paramStr;
        ch.paramLong = c.paramLong;
        ch.paramInt = c.paramInt;
        ch.baseline = c.baseline;
        ch.target = c.absoluteTarget > 0 ? c.absoluteTarget : (c.baseline + c.targetDelta);
        ch.current = c.baseline;
        long now = System.currentTimeMillis();
        ch.startedAtMs = now;
        ch.expiresAtMs = now + tier.durationMs;
        ch.lastTickMs = now;
        return ch;
    }

    private static long deltaXpForSkill(Tier t, int lvl) {
        // Roughly: target XP scales with current level so it stays meaningful
        long perTier = Math.max(50_000, SKILL_XP_LEVEL_TARGETS[Math.min(lvl, 59)] / 60);
        return switch (t) {
            case DAILY   -> perTier * 1;
            case WEEKLY  -> perTier * 8;
            case MONTHLY -> perTier * 30;
        };
    }

    private static long deltaXpForSlayer(Tier t, int lvl) {
        long base = lvl <= 4 ? 5_000 : lvl <= 6 ? 25_000 : 100_000;
        return switch (t) {
            case DAILY   -> base * 1;
            case WEEKLY  -> base * 6;
            case MONTHLY -> base * 25;
        };
    }

    public static int slayerLevel(String slayer, long xp) {
        long[] z = {5,15,200,1000,5000,20000,100000,400000,1000000};
        long[] standard = z;
        long[] vampire = {20,75,240,840,2400,4800,9600,16800,30000};
        long[] table = "vampire".equals(slayer) ? vampire : standard;
        for (int i = table.length - 1; i >= 0; i--) if (xp >= table[i]) return i + 1;
        return 0;
    }
}
