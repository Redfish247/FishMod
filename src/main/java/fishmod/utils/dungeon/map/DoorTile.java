package fishmod.utils.dungeon.map;

import fishmod.utils.config.values.DungeonMapSettings;

/** A door between two adjacent room tiles. Mutable: DungeonGrid upgrades it in place. */
public class DoorTile {
    private DoorType type;
    /** Never regresses true -> false, matching every other state in this package. */
    private boolean opened = false;

    DoorTile(DoorType type) {
        this.type = type;
    }

    public DoorType type() {
        return type;
    }

    public boolean opened() {
        return opened;
    }

    void upgrade(DoorType newType) {
        if (newType != null) type = newType;
    }

    void markOpened() {
        opened = true;
    }

    public int color() {
        if (type == null) return 0;
        return switch (type) {
            case WITHER -> opened ? DungeonMapSettings.witherDoorOpenColor : DungeonMapSettings.witherDoorColor;
            case BLOOD -> DungeonMapSettings.bloodDoorColor;
            case FAIRY -> DungeonMapSettings.fairyColor;
            case NORMAL -> DungeonMapSettings.normalDoorColor;
        };
    }
}
