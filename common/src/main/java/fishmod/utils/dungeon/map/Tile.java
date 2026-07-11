package fishmod.utils.dungeon.map;

/** A room cell of the fixed 6x6 dungeon grid: either a resolved room or not yet revealed. */
public interface Tile {
    GridPos pos();

    RoomState state();

    /** ARGB color to draw for this cell, or {@code 0} (fully transparent) if it shouldn't be drawn. */
    int color();
}
