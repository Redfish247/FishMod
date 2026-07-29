package fishmod.utils.dungeon.map;

import fishmod.utils.Location;
import fishmod.utils.events.Events;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import net.minecraft.network.packet.s2c.play.MapUpdateS2CPacket;

/**
 * Reads the dungeon's vanilla map item each tick purely to calibrate a world-position bridge for
 * {@link fishmod.features.dungeon.DungeonWaypoints}: which fixed 32-block grid tile a world X/Z
 * position falls in. No room/door type tracking or HUD rendering — this only locates the entrance's
 * green streak in the map's pixel data once, to anchor {@link #worldToGridPos}.
 */
public class MapReader {
    private static final byte ENTRANCE_MAP_COLOR = (byte) 30;

    private static MapIdComponent currentMapId;
    private static boolean calibrated = false;
    private static int roomGap = 20;
    private static int entranceTileX = 0;
    private static int entranceTileZ = 0;

    // World-position bridge — captured once, from the player's position the first tick the dungeon
    // map is seen (reliably at/near the entrance, since that's where a run starts). Hypixel dungeons
    // are always instanced 8 blocks off a fixed 32-block grid.
    private static boolean worldAnchored = false;
    private static int worldOriginX;
    private static int worldOriginZ;

    public static void init() {
        Events.ON_PACKET.register(packet -> {
            if (Location.inDungeon() && packet instanceof MapUpdateS2CPacket mapPacket) {
                MapIdComponent newId = mapPacket.mapId();
                // Each dungeon run gets its own fresh map item ID. Location alone doesn't reliably
                // signal "new run" — going from one run straight into another via the dungeon hub
                // can stay at Location.DUNGEON the whole time, so ON_LOCATION_CHANGE never fires
                // between runs. A changed map ID is a direct, run-specific signal instead.
                if (currentMapId != null && !currentMapId.equals(newId)) reset();
                currentMapId = newId;
            }
            return false;
        });
        Events.ON_SERVER_TICK.register(() -> {
            tick();
            return false;
        });
        Events.ON_LOCATION_CHANGE.register(newLocation -> {
            if (newLocation != Location.DUNGEON) reset();
            return false;
        });
    }

    private static void reset() {
        currentMapId = null;
        calibrated = false;
        roomGap = 20;
        worldAnchored = false;
    }

    private static void tick() {
        if (!Location.inDungeon() || currentMapId == null || calibrated) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        if (!worldAnchored) {
            int px = (int) Math.floor(mc.player.getX() + 8.5);
            int pz = (int) Math.floor(mc.player.getZ() + 8.5);
            worldOriginX = px - Math.floorMod(px, 32);
            worldOriginZ = pz - Math.floorMod(pz, 32);
            worldAnchored = true;
        }

        MapState map = mc.world.getMapState(currentMapId);
        if (map == null || map.colors == null || map.colors.length < 128 * 128) return;

        tryCalibrate(map.colors);
    }

    /** Finds the entrance's green streak and derives the grid's pixel origin from its position modulo the room-grid period. */
    private static boolean tryCalibrate(byte[] colors) {
        for (int index = 0; index < colors.length; index++) {
            if (colors[index] != ENTRANCE_MAP_COLOR) continue;

            int end = index;
            while (end < colors.length && colors[end] == colors[index]) end++;

            int length = end - index;
            if (length != 16 && length != 18) continue;

            int roomSize = length;
            roomGap = roomSize + 4;
            int pixelStartX = (index % 128) % roomGap;
            int pixelStartY = (index / 128) % roomGap;
            if (pixelStartX == 0) pixelStartX = 22;
            if (pixelStartY == 0) pixelStartY = 22;

            entranceTileX = ((index % 128) - pixelStartX) / roomGap;
            entranceTileZ = ((index / 128) - pixelStartY) / roomGap;

            calibrated = true;
            return true;
        }
        return false;
    }

    public static boolean isCalibrated() {
        return calibrated;
    }

    // --- World-position bridge, exposed for DungeonWaypoints (see that class for usage). Only valid
    // once isCalibrated() is true (worldOriginX/Z are captured before calibration, but entranceTileX/Z
    // — needed to convert an arbitrary tile index — only come from tryCalibrate). ---

    /** World X of grid tile column tileX's northwest corner. */
    public static int tileWorldOriginX(int tileX) {
        return worldOriginX + (tileX - entranceTileX) * 32;
    }

    /** World Z of grid tile row tileZ's northwest corner. */
    public static int tileWorldOriginZ(int tileZ) {
        return worldOriginZ + (tileZ - entranceTileZ) * 32;
    }

    /** Which GridPos tile (0..5 range in normal play) a world X/Z position falls in. */
    public static GridPos worldToGridPos(double worldX, double worldZ) {
        int tileX = entranceTileX + Math.floorDiv((int) Math.floor(worldX) - worldOriginX, 32);
        int tileZ = entranceTileZ + Math.floorDiv((int) Math.floor(worldZ) - worldOriginZ, 32);
        return new GridPos(tileX, tileZ);
    }
}
