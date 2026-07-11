package fishmod.utils.dungeon.map;

import net.minecraft.block.MapColor;

/**
 * The type of a dungeon room, read directly off Hypixel's own vanilla-map pixel colors
 * (the "center" pixel of a room-quadrant cell). Palette IDs come from Minecraft's own
 * {@link MapColor} constants, which Hypixel reuses to paint room types on the dungeon map.
 *
 * <p>Yarn renames Mojang's {@code COLOR_*}/{@code FIRE}/{@code PLANT} constants: confirmed via
 * {@code javap} against the 1.21.11 merged jar that {@code COLOR_ORANGE -> ORANGE},
 * {@code COLOR_MAGENTA -> MAGENTA}, {@code COLOR_YELLOW -> YELLOW}, {@code COLOR_PINK -> PINK},
 * {@code FIRE -> BRIGHT_RED}, {@code PLANT -> DARK_GREEN}, {@code COLOR_GRAY -> GRAY} — the
 * declaration order (and therefore packed id) of the palette is unchanged, only the field names
 * moved. {@code getPackedId(Brightness)} was likewise renamed to {@code getRenderColorByte(Brightness)}
 * but computes the identical {@code (id << 2) | brightness.id} packed byte.
 */
public enum RoomType {
    NORMAL(MapColor.ORANGE.getRenderColorByte(MapColor.Brightness.LOWEST)),
    PUZZLE(MapColor.MAGENTA.getRenderColorByte(MapColor.Brightness.HIGH)),
    TRAP(MapColor.ORANGE.getRenderColorByte(MapColor.Brightness.HIGH)),
    MINIBOSS(MapColor.YELLOW.getRenderColorByte(MapColor.Brightness.HIGH)),
    FAIRY(MapColor.PINK.getRenderColorByte(MapColor.Brightness.HIGH)),
    BLOOD(MapColor.BRIGHT_RED.getRenderColorByte(MapColor.Brightness.HIGH)),
    ENTRANCE(MapColor.DARK_GREEN.getRenderColorByte(MapColor.Brightness.HIGH)),
    UNKNOWN(MapColor.GRAY.getRenderColorByte(MapColor.Brightness.NORMAL));

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
