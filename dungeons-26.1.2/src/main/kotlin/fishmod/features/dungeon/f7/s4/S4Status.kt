package fishmod.features.dungeon.f7.s4

/**
 * Where a tracked player stands relative to S4 (the 4th terminal section) as of the last update.
 * Anything short of solid evidence resolves to [POSSIBLE_MISSED] rather than a harder accusation —
 * Hypixel doesn't broadcast terminal *assignments*, only completions, so "did nothing this section"
 * is the strongest claim the available signals support.
 */
enum class S4Status {
    ACTIVE,
    CONTRIBUTED,
    CORE_EARLY,
    CORE_ON_TIME,
    CORE_LATE,
    POSSIBLE_MISSED,
    DEAD,
}
