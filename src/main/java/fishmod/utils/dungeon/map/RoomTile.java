package fishmod.utils.dungeon.map;

import fishmod.utils.config.values.DungeonMapSettings;

/** A room-quadrant grid cell. Mutable: {@link DungeonGrid} upgrades its state/type in place as more is revealed. */
public class RoomTile implements Tile {
    private final GridPos pos;
    private RoomType type;
    private RoomState state;
    /** Shared by every grid cell belonging to the same logical (possibly multi-cell) room. */
    int roomId = -1;

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

    void upgrade(RoomType newType, RoomState newState) {
        if (newType != null && newType != RoomType.UNKNOWN) type = newType;
        if (newState.ordinal() > state.ordinal()) state = newState;
    }

    @Override
    public int color() {
        if (state == RoomState.UNDISCOVERED || type == null) return 0;
        if (state == RoomState.UNOPENED) return DungeonMapSettings.unopenedColor;
        int color = switch (type) {
            case PUZZLE -> DungeonMapSettings.puzzleColor;
            case TRAP -> DungeonMapSettings.trapColor;
            case MINIBOSS -> DungeonMapSettings.minibossColor;
            case FAIRY -> DungeonMapSettings.fairyColor;
            case BLOOD -> DungeonMapSettings.bloodColor;
            case ENTRANCE -> DungeonMapSettings.entranceColor;
            case NORMAL, UNKNOWN -> DungeonMapSettings.normalColor;
        };
        return state == RoomState.CLEARED ? DungeonMapSettings.clearedColor : color;
    }
}
