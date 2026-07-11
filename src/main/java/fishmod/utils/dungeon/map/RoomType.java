package fishmod.utils.dungeon.map;

/**
 * The type of a dungeon room, read off the "corner" map pixel of a room cell. Byte values are
 * literal Hypixel map-palette IDs, cross-confirmed against two independent open-source mods
 * (Skyblocker and Odin, github.com/odtheking/Odin) rather than computed from {@code MapColor}
 * constants, since Odin's IDs are hand-verified against a live dungeon map.
 */
public enum RoomType {
    ENTRANCE((byte) 30),
    FAIRY((byte) 82),
    NORMAL((byte) 63),
    RARE((byte) 63), // same color as NORMAL; only distinguished once the exact room design is known
    BLOOD((byte) 18),
    MINIBOSS((byte) 74),
    UNKNOWN((byte) 85), // room exists but its type hasn't been painted distinctly yet
    PUZZLE((byte) 66),
    TRAP((byte) 62);

    public final byte mapColor;

    RoomType(byte mapColor) {
        this.mapColor = mapColor;
    }

    public static RoomType fromMapColor(byte color) {
        for (RoomType type : values()) {
            if (type.mapColor == color) return type;
        }
        return null;
    }
}
