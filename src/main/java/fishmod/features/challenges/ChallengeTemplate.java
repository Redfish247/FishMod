package fishmod.features.challenges;

/**
 * Kinds of challenges. Each carries enough parameter slots for any template.
 * A Challenge captures baseline & target, then completion is computed by comparing
 * the current ProfileSnapshot field against (baseline + delta).
 */
public enum ChallengeTemplate {
    /** Level paramInt pets to 100 (any pets — count across the whole pet list). */
    PET_LEVEL_UP,
    /** Named SkyBlock XP mission from {@link SkyblockMissions}; paramStr = mission id. */
    SB_MISSION,
    /** Gain a delta of XP in a specific skill (skill name in paramStr). */
    SKILL_XP_GAIN,
    /** Gain a delta of XP in a specific slayer (zombie/spider/wolf/enderman/blaze/vampire). */
    SLAYER_XP_GAIN,
    /** Complete N runs on a specific dungeon floor (paramStr = "F1".."M7"). */
    DUNGEON_RUNS,
    /** Reach catacombs level paramInt. */
    CATA_LEVEL,
    /** Bump a collection (paramStr = collection name) to tier paramInt. */
    COLLECTION_TIER;

    public String describe(String paramStr, long paramLong, int paramInt) {
        return switch (this) {
            case PET_LEVEL_UP     -> "Level §e" + paramInt + " §7pets to §a100";
            case SB_MISSION       -> {
                SkyblockMissions.Mission m = lookupMission(paramStr);
                yield m == null ? paramStr : "§f" + m.label + " §7(§e+" + m.sbxp + " §7SB XP)";
            }
            case SKILL_XP_GAIN    -> "Gain §e" + fmt(paramLong) + " §7XP in §a" + paramStr;
            case SLAYER_XP_GAIN   -> "Gain §e" + fmt(paramLong) + " §7" + paramStr + " §7slayer XP";
            case DUNGEON_RUNS     -> "Complete §e" + paramInt + " §7runs of §a" + paramStr;
            case CATA_LEVEL       -> "Reach Catacombs level §a" + paramInt;
            case COLLECTION_TIER  -> "Raise §a" + paramStr + " §7collection to tier §e" + paramInt;
        };
    }

    private static SkyblockMissions.Mission lookupMission(String id) {
        for (SkyblockMissions.Mission m : SkyblockMissions.ALL) if (m.id.equals(id)) return m;
        return null;
    }

    private static String fmt(long n) {
        if (n >= 1_000_000_000L) return String.format("%.1fB", n / 1e9);
        if (n >= 1_000_000L)     return String.format("%.1fM", n / 1e6);
        if (n >= 1_000L)         return String.format("%.0fk", n / 1e3);
        return Long.toString(n);
    }
}
