package fishmod.features.dungeon.map;

import fishmod.utils.dungeon.map.MapReader;

/** Entry point for the dungeon map feature — wires up map-reading; rendering is in {@link DungeonMapHud}. */
public class DungeonMapFeature {
    public static void init() {
        MapReader.init();
    }
}
