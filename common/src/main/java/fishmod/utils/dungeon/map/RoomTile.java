package fishmod.utils.dungeon.map;

import fishmod.utils.config.values.DungeonMapSettings;

/** A room in the fixed 6x6 grid (pos in whole-tile coordinates, 0..5). Mutable: DungeonGrid upgrades it in place. */
public class RoomTile implements Tile {
    private final GridPos pos;
    private RoomType type;
    private RoomState state;
    /** Shared by every grid cell belonging to the same logical (possibly multi-cell) room. */
    int roomId = -1;
    /** Exact room design name from {@link RoomData}, once world-block scanning identifies it. Null until then. */
    private String name;
    /** Set once a scan has been attempted at this tile, so a failed/unloaded attempt isn't retried every tick. */
    boolean scanAttempted = false;

    RoomTile(GridPos pos, RoomType type, RoomState state) {
        this.pos = pos;
        this.type = type;
        this.state = state;
    }

    @Override
    public GridPos pos() {
        return pos;
    }

    @Override
    public RoomState state() {
        return state;
    }

    public RoomType type() {
        return type;
    }

    public String name() {
        return name;
    }

    void setName(String name) {
        if (this.name == null) this.name = name;
    }

    void upgrade(RoomType newType, RoomState newState) {
        if (newType != null && newType != RoomType.UNKNOWN) type = newType;
        if (newState != null && newState.ordinal() > state.ordinal()) state = newState;
    }

    /**
     * Always the room's TYPE color, regardless of clear/fail state — matching NoammAddons'
     * {@code Room.color}, which only special-cases UNOPENED (gray). An earlier version of this
     * method also swapped the fill to green on CLEARED/red on FAILED; that's wrong — clear/fail
     * state is meant to show on the room's text label instead, see DungeonMapHud's label color.
     */
    @Override
    public int color() {
        if (state == RoomState.UNDISCOVERED || type == null) return 0;
        if (state == RoomState.UNOPENED) return DungeonMapSettings.unopenedColor;
        return switch (type) {
            case PUZZLE -> DungeonMapSettings.puzzleColor;
            case TRAP -> DungeonMapSettings.trapColor;
            case MINIBOSS -> DungeonMapSettings.minibossColor;
            case FAIRY -> DungeonMapSettings.fairyColor;
            case BLOOD -> DungeonMapSettings.bloodColor;
            case ENTRANCE -> DungeonMapSettings.entranceColor;
            case NORMAL, RARE, UNKNOWN -> DungeonMapSettings.normalColor;
        };
    }
}
