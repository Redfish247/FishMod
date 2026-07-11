package fishmod.utils.dungeon.map;

/**
 * The type of a door/connector cell, read off its map pixel. Byte values cross-confirmed against
 * Odin (github.com/odtheking/Odin) — WITHER=119 in particular was unconfirmed guesswork in earlier
 * versions of this file; it's now a verified literal.
 */
public enum DoorType {
    NORMAL,
    WITHER,
    BLOOD,
    FAIRY;

    public static DoorType fromMapColor(byte color) {
        if (color == (byte) 119) return WITHER;
        if (color == RoomType.BLOOD.mapColor) return BLOOD;
        if (color == RoomType.FAIRY.mapColor) return FAIRY;
        return NORMAL;
    }
}
