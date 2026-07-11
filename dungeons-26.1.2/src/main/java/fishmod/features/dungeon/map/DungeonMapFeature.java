package fishmod.features.dungeon.map;

import fishmod.features.dungeon.DungeonScore;
import fishmod.utils.dungeon.map.DungeonGrid;
import fishmod.utils.dungeon.map.MapReader;

/** Entry point for the dungeon map feature — wires up map-reading; rendering is in {@link DungeonMapHud}. */
public class DungeonMapFeature {
    public static void init() {
        // :common's DungeonGrid can't call DungeonScore directly (that class needs this module's
        // own Minecraft-API code), so it takes the puzzle count through this supplier instead.
        DungeonGrid.puzzleCountSupplier = DungeonScore::getPuzzleCount;
        MapReader.init();
    }
}
