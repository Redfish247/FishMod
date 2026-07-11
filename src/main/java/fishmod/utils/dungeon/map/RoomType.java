package fishmod.utils.dungeon.map;

import net.minecraft.world.level.material.MapColor;

/**
 * The type of a dungeon room, read directly off Hypixel's own vanilla-map pixel colors
 * (the "center" pixel of a room-quadrant cell). Palette IDs come from Minecraft's own
 * {@link MapColor} constants, which Hypixel reuses to paint room types on the dungeon map.
 */
public enum RoomType {
    NORMAL(MapColor.COLOR_ORANGE.getPackedId(MapColor.Brightness.LOWEST)),
    PUZZLE(MapColor.COLOR_MAGENTA.getPackedId(MapColor.Brightness.HIGH)),
    TRAP(MapColor.COLOR_ORANGE.getPackedId(MapColor.Brightness.HIGH)),
    MINIBOSS(MapColor.COLOR_YELLOW.getPackedId(MapColor.Brightness.HIGH)),
    FAIRY(MapColor.COLOR_PINK.getPackedId(MapColor.Brightness.HIGH)),
    BLOOD(MapColor.FIRE.getPackedId(MapColor.Brightness.HIGH)),
    ENTRANCE(MapColor.PLANT.getPackedId(MapColor.Brightness.HIGH)),
    UNKNOWN(MapColor.COLOR_GRAY.getPackedId(MapColor.Brightness.NORMAL));

    public final byte mapColor;

    RoomType(int mapColor) {
        this.mapColor = (byte) mapColor;
    }

    public static RoomType fromMapColor(byte color) {
        for (RoomType type : values()) {
            if (type.mapColor == color) return type;
        }
        return null;
    }
}
