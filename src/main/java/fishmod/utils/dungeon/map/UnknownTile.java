package fishmod.utils.dungeon.map;

/** A room tile Hypixel hasn't painted on the map yet. Always fully transparent. */
public record UnknownTile(GridPos pos) implements Tile {
    @Override
    public RoomState state() {
        return RoomState.UNDISCOVERED;
    }

    @Override
    public int color() {
        return 0;
    }
}
