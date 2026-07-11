package fishmod.utils.dungeon.map;

import net.minecraft.block.MapColor;

/**
 * The type of a dungeon door/connector cell, read off Hypixel's vanilla-map pixel color.
 * Only entrance and blood doors have well-evidenced palette IDs (matching the constants
 * Skyblocker's DungeonMapUtils hardcodes); wither-door vs. normal-door pixel colors were not
 * independently confirmed during porting research, so WITHER falls back to being detected via
 * {@link DungeonGrid}'s wither-door heuristic (adjacent to a known BLOOD room) rather than a
 * dedicated map color — verify against a live F5+ run and tighten this if a distinct color shows up.
 */
public enum DoorType {
    NORMAL_CLOSED(MapColor.BLACK.getRenderColorByte(MapColor.Brightness.LOWEST)),
    NORMAL_OPEN((byte) 34),  // MapColor.WHITE (Yarn's renamed SNOW).getRenderColorByte(HIGH)
    ENTRANCE((byte) 30),     // MapColor.DARK_GREEN (Yarn's renamed PLANT).getRenderColorByte(HIGH)
    BLOOD((byte) 18),        // MapColor.BRIGHT_RED (Yarn's renamed FIRE).getRenderColorByte(HIGH)
    WITHER((byte) 0);        // sentinel — resolved heuristically, see class javadoc

    public final byte mapColor;

    DoorType(byte mapColor) {
        this.mapColor = mapColor;
    }

    public static DoorType fromMapColor(byte color) {
        for (DoorType type : values()) {
            if (type != WITHER && type.mapColor == color) return type;
        }
        return null;
    }

    public boolean isOpen() {
        return this == NORMAL_OPEN || this == ENTRANCE;
    }
}
