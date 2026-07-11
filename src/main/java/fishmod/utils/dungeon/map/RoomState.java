package fishmod.utils.dungeon.map;

/**
 * How much is known about a room cell, from least to most certain. Declaration order doubles as
 * priority: {@link DungeonGrid} merges only ever upgrade a cell's state (by ordinal), never regress
 * it, so a transient bad pixel read can't erase an already-confirmed state.
 *
 * <p>Room state comes from the "center" map pixel (distinct from the "corner" pixel that gives
 * {@link RoomType}) — a scheme cross-confirmed against Odin's {@code MapCheckmark}
 * (github.com/odtheking/Odin): WHITE/GREEN/RED/QUESTION_MARK checkmark colors painted once a room
 * enters clear/fail/uncertain state, separate from its type color.
 */
public enum RoomState {
    UNDISCOVERED,
    UNOPENED,     // corner color known but is RoomType.UNKNOWN (gray) — not yet distinctly typed
    DISCOVERED,   // type known, center color still matches the type color (not cleared/failed yet)
    UNCERTAIN,    // center pixel is the "question mark" checkmark color
    PARTIAL,      // center pixel is the "white" checkmark color
    CLEARED,      // center pixel is the "green" checkmark color
    FAILED;       // center pixel is the "red" checkmark color

    /**
     * Resolves the room-cell state from its center pixel, given its already-known corner (type)
     * color. Returns {@code null} if the center pixel doesn't match any recognized checkmark and
     * isn't equal to the corner color either (an unpainted/ambiguous read — caller should skip
     * updating state entirely rather than guessing).
     */
    public static RoomState fromCenterColor(byte center, byte corner) {
        if (center == corner) return DISCOVERED;
        return switch (center) {
            case 34 -> PARTIAL;
            case 30 -> CLEARED;
            case 18 -> FAILED;
            case 119 -> UNCERTAIN;
            default -> null;
        };
    }
}
