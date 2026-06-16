package fishmod.features.optimizer;

import fishmod.features.challenges.GapAnalyzer;
import fishmod.features.challenges.ProfileSnapshot;
import fishmod.features.challenges.SkyblockMissions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure analysis over a {@link ProfileSnapshot}. Produces:
 *  - a per-skill progression roadmap (level + progress to next level), and
 *  - a ranked "what to do next" suggestion list.
 *
 * The suggestion engine reuses the curated {@link SkyblockMissions} catalog: for each mission
 * family (same type + param, e.g. "Combat 25/50/60") it finds the *nearest uncompleted*
 * milestone, then ranks those by how close the player already is (quick wins float up) blended
 * with the SkyBlock-XP reward. No new domain data is invented — every suggestion is a real,
 * already-curated SkyBlock milestone read live from the player's profile.
 */
public class OptimizerAnalyzer {

    /** Grindable skills with the standard XP table (matches GapAnalyzer). */
    public static final String[] SKILLS = {
        "combat", "mining", "farming", "foraging", "fishing", "enchanting", "alchemy", "taming"
    };

    // Category accents (ARGB) for suggestion rows.
    public static final int C_SKILL   = 0xFF24B6B0; // teal
    public static final int C_COMBAT  = 0xFFE0604E; // red-orange (slayers)
    public static final int C_DUNGEON = 0xFFB07CE0; // purple (cata / classes)
    public static final int C_COLLECT = 0xFF5FBF6A; // green (collections)
    public static final int C_ECON    = 0xFF63C7E0; // aqua (MP / fairy / SB level)
    public static final int C_OTHER   = 0xFFC8A85A; // gold (pets / hotm)

    public static class SkillRow {
        public String name;
        public int    level;
        public long   xp;
        public boolean maxed;
        public double frac;  // 0..1 progress within the current level
    }

    public static class Suggestion {
        public String  label;
        public double  cur, target;
        public boolean levelLike;  // true => render "cur / target" as integers; false => big-number format
        public int     sbxp;       // SkyBlock XP reward
        public double  score;      // ranking key
        public int     color;
    }

    /** One row per grindable skill, sorted weakest-first so the roadmap highlights what to push. */
    public static List<SkillRow> skillRoadmap(ProfileSnapshot s) {
        List<SkillRow> out = new ArrayList<>();
        int cap = GapAnalyzer.maxSkillLevel(); // 60 (table end)
        for (String sk : SKILLS) {
            long xp = s.skillXp.getOrDefault(sk, 0L);
            int lvl = GapAnalyzer.skillLevel(xp);
            SkillRow r = new SkillRow();
            r.name = sk;
            r.xp = xp;
            r.level = Math.min(lvl, cap);
            if (lvl >= cap) {
                r.maxed = true;
                r.frac = 1.0;
            } else {
                long prev = GapAnalyzer.xpToReach(lvl);
                long next = GapAnalyzer.xpToReach(lvl + 1);
                long span = Math.max(1, next - prev);
                r.frac = Math.max(0.0, Math.min(1.0, (double) (xp - prev) / span));
            }
            out.add(r);
        }
        out.sort(Comparator.comparingInt((SkillRow a) -> a.level).thenComparing(a -> a.name));
        return out;
    }

    /** Ranked next-step suggestions, highest priority first, capped at {@code max}. */
    public static List<Suggestion> suggestions(ProfileSnapshot s, int max) {
        // Collapse each mission family to its nearest uncompleted milestone.
        Map<String, SkyblockMissions.Mission> nearest = new LinkedHashMap<>();
        Map<String, Double> nearestCur = new HashMap<>();
        for (SkyblockMissions.Mission m : SkyblockMissions.ALL) {
            double cur = SkyblockMissions.currentValue(m, s);
            if (cur >= m.target) continue; // already done
            String fam = m.type + "|" + m.paramStr;
            SkyblockMissions.Mission held = nearest.get(fam);
            if (held == null || m.target < held.target) {
                nearest.put(fam, m);
                nearestCur.put(fam, cur);
            }
        }

        List<Suggestion> out = new ArrayList<>();
        for (Map.Entry<String, SkyblockMissions.Mission> e : nearest.entrySet()) {
            SkyblockMissions.Mission m = e.getValue();
            double cur = nearestCur.getOrDefault(e.getKey(), 0.0);
            double closeness = m.target > 0 ? Math.min(1.0, cur / m.target) : 0.0;
            Suggestion sg = new Suggestion();
            sg.label = m.label;
            sg.cur = cur;
            sg.target = m.target;
            sg.sbxp = m.sbxp;
            sg.levelLike = m.target <= 2000; // levels / MP / fairy souls / pet counts
            sg.color = colorFor(m.type);
            // Quick wins (near complete) dominate, with a reward nudge so big milestones still surface.
            sg.score = 0.72 * closeness + 0.28 * Math.min(1.0, m.sbxp / 250.0);
            out.add(sg);
        }
        out.sort((a, b) -> Double.compare(b.score, a.score));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    private static int colorFor(SkyblockMissions.Type t) {
        return switch (t) {
            case SKILL_LEVEL                 -> C_SKILL;
            case SLAYER_LEVEL                -> C_COMBAT;
            case CATA_LEVEL, CLASS_LEVEL     -> C_DUNGEON;
            case COLLECTION                  -> C_COLLECT;
            case MAGICAL_POWER, FAIRY_SOULS, SB_LEVEL -> C_ECON;
            case PET_COUNT_AT_100, HOTM_LEVEL -> C_OTHER;
        };
    }
}
