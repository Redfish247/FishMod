package fishmod.utils.dungeon.map;

/**
 * How much is known about a grid cell, from least to most certain. Declaration order doubles as
 * priority: {@link DungeonGrid} merges only ever upgrade a cell's state (by ordinal), never regress
 * it, so a later "undiscovered" read of the same pixel can't erase an already-confirmed room.
 */
public enum RoomState {
    UNDISCOVERED,
    UNOPENED,
    DISCOVERED,
    CLEARED,
    FAILED
}
