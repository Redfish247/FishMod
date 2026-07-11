package fishmod.utils.dungeon.map;

/** A cell of the dungeon grid: either a room quadrant, a door/connector, or not yet revealed. */
public interface Tile {
    GridPos pos();

    RoomState state();

    /** ARGB color to draw for this cell, or {@code 0} (fully transparent) if it shouldn't be drawn. */
    int color();
}
