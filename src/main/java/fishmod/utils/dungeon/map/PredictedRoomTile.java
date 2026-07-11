package fishmod.utils.dungeon.map;

import fishmod.utils.config.values.DungeonMapSettings;

/**
 * A room cell whose type isn't confirmed yet but {@link RoomSignatureDB} narrowed it to 2+
 * candidates from locally-observed data. Rendered as a split-color blend of the top 2 candidates
 * by observation count; collapses back to a plain {@link RoomTile} the moment the real type is
 * revealed or narrows to one candidate. Never persisted — recomputed each tick.
 */
public record PredictedRoomTile(GridPos pos, RoomType primary, RoomType secondary) implements Tile {
    @Override
    public RoomState state() {
        return RoomState.UNOPENED;
    }

    @Override
    public int color() {
        return primaryColor();
    }

    public int primaryColor() {
        return colorFor(primary);
    }

    public int secondaryColor() {
        return colorFor(secondary);
    }

    private static int colorFor(RoomType type) {
        return switch (type) {
            case PUZZLE -> DungeonMapSettings.puzzleColor;
            case TRAP -> DungeonMapSettings.trapColor;
            case MINIBOSS -> DungeonMapSettings.minibossColor;
            case FAIRY -> DungeonMapSettings.fairyColor;
            case BLOOD -> DungeonMapSettings.bloodColor;
            case ENTRANCE -> DungeonMapSettings.entranceColor;
            case NORMAL, UNKNOWN -> DungeonMapSettings.normalColor;
        };
    }
}
