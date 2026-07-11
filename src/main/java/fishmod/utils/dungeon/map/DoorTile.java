package fishmod.utils.dungeon.map;

import fishmod.utils.config.values.DungeonMapSettings;

/** A door/connector grid cell. Mutable: {@link DungeonGrid} upgrades its state/type in place as more is revealed. */
public class DoorTile implements Tile {
    private final GridPos pos;
    private DoorType type;
    private RoomState state = RoomState.UNDISCOVERED;

    DoorTile(GridPos pos, DoorType type, RoomState state) {
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

    public DoorType type() {
        return type;
    }

    void upgrade(DoorType newType, RoomState newState) {
        if (newType != null) type = newType;
        if (newState.ordinal() > state.ordinal()) state = newState;
    }

    @Override
    public int color() {
        if (state == RoomState.UNDISCOVERED || type == null) return 0;
        return switch (type) {
            case ENTRANCE -> DungeonMapSettings.entranceDoorColor;
            case BLOOD -> DungeonMapSettings.bloodDoorColor;
            case WITHER -> DungeonMapSettings.witherDoorColor;
            case NORMAL_OPEN, NORMAL_CLOSED -> DungeonMapSettings.normalDoorColor;
        };
    }
}
