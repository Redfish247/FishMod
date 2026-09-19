package fishmod.features.dungeon.f7.s4

/** Player's status relative to S4; ambiguous evidence resolves to [POSSIBLE_MISSED] rather than a harder accusation, since Hypixel never broadcasts terminal assignments. */
enum class S4Status {
    ACTIVE,
    CONTRIBUTED,
    CORE_EARLY,
    CORE_ON_TIME,
    CORE_LATE,
    POSSIBLE_MISSED,
    DEAD,
}
