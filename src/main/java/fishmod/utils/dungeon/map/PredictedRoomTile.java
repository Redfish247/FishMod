package fishmod.utils.dungeon.map;

import fishmod.utils.config.values.DungeonMapSettings;

import java.util.List;

/**
 * A room cell whose type isn't confirmed yet but {@link RoomSignatureDB} narrowed it to 2+
 * candidates from locally-observed data — anywhere from 2 up to all remaining room types (puzzle,
 * trap, miniboss, normal, ...) depending on how ambiguous the door pattern still is. Rendered as an
 * even split of however many candidates there are; collapses back to a plain {@link RoomTile} the
 * moment the real type is revealed or narrows to one candidate. Never persisted — recomputed each tick.
 */
public record PredictedRoomTile(GridPos pos, List<RoomType> candidates) implements Tile {
    @Override
    public RoomState state() {
        return RoomState.UNOPENED;
    }

    @Override
    public int color() {
        return colorFor(candidates.get(0));
    }

    public int colorAt(int index) {
        return colorFor(candidates.get(index));
    }

    private static int colorFor(RoomType type) {
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
