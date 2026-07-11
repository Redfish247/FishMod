package fishmod.utils.dungeon.map;

import fishmod.utils.Location;
import fishmod.utils.config.values.DungeonMapSettings;
import fishmod.utils.events.Events;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.network.packet.s2c.play.MapUpdateS2CPacket;

/**
 * Reads the dungeon's vanilla map item each tick and feeds {@link DungeonGrid}.
 *
 * <p>The client already reconstructs the full 128x128 {@code colors[]} buffer for us from the
 * server's incremental map updates (see {@code ClientWorld.getMapState}), so this class never
 * touches raw patches — it only needs the current dungeon map's {@link MapIdComponent}, which it
 * grabs off the first {@link MapUpdateS2CPacket} it sees each run (that packet is already
 * forwarded to every listener via {@code Events.ON_PACKET} in {@code ClientPlayNetworkHandlerMixin}'s
 * {@code onBundle} wrap, so no new mixin injection point is needed).
 *
 * <p>Calibration ties the map's own pixel coordinate space to the dungeon's physical grid: the
 * player's world position converts to an absolute grid cell via Hypixel's fixed 32-block room
 * spacing (dungeons are always instanced 8 blocks off a 32-block grid — a fixed fact about the
 * game, independent of anything mod-specific), and the player's on-map position (from their own
 * {@code MapDecoration}) anchors that grid cell to a pixel position. Room pixel size is measured
 * once from the entrance room's green streak (varies 16 vs 18px by floor).
 */
public class MapReader {
    private static MapIdComponent currentMapId;
    private static boolean calibrated = false;
    private static int mapRoomSize = 0;
    private static int refMapX, refMapZ;
    private static GridPos refGrid;

    public static void init() {
        Events.ON_PACKET.register(packet -> {
            if (Location.inDungeon() && packet instanceof MapUpdateS2CPacket mapPacket) {
                currentMapId = mapPacket.mapId();
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
        if (currentMapId != null) DungeonGrid.finalizeRunObservations();
        currentMapId = null;
        calibrated = false;
        mapRoomSize = 0;
        DungeonGrid.reset();
    }

    private static void tick() {
        if (!Location.inDungeon() || !DungeonMapSettings.enabled || currentMapId == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        MapState map = mc.world.getMapState(currentMapId);
        if (map == null) return;

        if (!calibrated) {
            tryCalibrate(mc, map);
            if (!calibrated) return;
        }
        sample(map, mc);
    }

    private static void tryCalibrate(MinecraftClient mc, MapState map) {
        int[] playerPixel = findPlayerPixel(mc, map);
        if (playerPixel == null) return;
        int roomSize = findEntranceStreakLength(map);
        if (roomSize < 6) return;

        refMapX = playerPixel[0];
        refMapZ = playerPixel[1];
        refGrid = GridPos.fromWorld(mc.player.getX(), mc.player.getZ());
        mapRoomSize = roomSize;
        calibrated = true;
    }

    private static int[] findPlayerPixel(MinecraftClient mc, MapState map) {
        String selfName = mc.player.getGameProfile().name();
        MapDecoration fallback = null;
        for (MapDecoration decoration : map.getDecorations()) {
            if (decoration.type().value() != MapDecorationTypes.PLAYER.value()) continue;
            if (decoration.name().isPresent() && decoration.name().get().getString().equals(selfName)) {
                return decorationToPixel(decoration);
            }
            if (fallback == null) fallback = decoration;
        }
        return fallback != null ? decorationToPixel(fallback) : null;
    }

    private static int[] decorationToPixel(MapDecoration decoration) {
        return new int[]{(decoration.x() >> 1) + 64, (decoration.z() >> 1) + 64};
    }

    /** Scans the whole buffer for the longest horizontal run of entrance-green pixels. */
    private static int findEntranceStreakLength(MapState map) {
        int best = 0;
        for (int z = 0; z < 128; z++) {
            int run = 0;
            for (int x = 0; x < 128; x++) {
                if (getColor(map, x, z) == RoomType.ENTRANCE.mapColor) {
                    run++;
                    if (run > best) best = run;
                } else {
                    run = 0;
                }
            }
        }
        return best;
    }

    private static int mapPixelOffset(int deltaGridUnits) {
        return deltaGridUnits * (mapRoomSize + 4) / 2;
    }

    private static void sample(MapState map, MinecraftClient mc) {
        GridPos playerGrid = GridPos.fromWorld(mc.player.getX(), mc.player.getZ());
        // Covers a full 6x6 room dungeon (12 grid units per axis) centered on wherever the player
        // currently is, regardless of which room that is.
        for (int dx = -13; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                GridPos pos = playerGrid.offset(dx, dz);
                int px = refMapX + mapPixelOffset(pos.x() - refGrid.x());
                int pz = refMapZ + mapPixelOffset(pos.z() - refGrid.z());

                if (pos.isRoomCell()) {
                    sampleRoom(map, pos, px + mapRoomSize / 2, pz + mapRoomSize / 2);
                } else if ((pos.x() & 1) != (pos.z() & 1)) { // straight connector, not a corner
                    sampleDoor(map, pos, px, pz);
                }
            }
        }
    }

    private static void sampleRoom(MapState map, GridPos pos, int px, int pz) {
        byte color = getColor(map, px, pz);
        if (color == 0) return;
        RoomType type = RoomType.fromMapColor(color);
        if (type == null) return;
        RoomState state = type == RoomType.UNKNOWN ? RoomState.UNOPENED : RoomState.DISCOVERED;
        DungeonGrid.updateRoom(pos, type, state);
    }

    private static void sampleDoor(MapState map, GridPos pos, int px, int pz) {
        byte color = getColor(map, px, pz);
        if (color == 0) return;
        // A connector inside a single merged multi-cell room shows that room's own type color
        // rather than a door palette color — check that before treating it as a real door.
        // UNKNOWN (gray, not-yet-identified) and ENTRANCE overlap with door colors, so they're
        // excluded here and left to the DoorType checks below instead.
        RoomType asRoomColor = RoomType.fromMapColor(color);
        if (asRoomColor != null && asRoomColor != RoomType.UNKNOWN && asRoomColor != RoomType.ENTRANCE) {
            DungeonGrid.markMerged(pos);
            return;
        }
        DoorType type = DoorType.fromMapColor(color);
        // Unmatched non-zero colors are treated as a plain closed door — wither-door pixels weren't
        // independently confirmed during porting, see DoorType's javadoc.
        if (type == null) type = DoorType.NORMAL_CLOSED;
        DungeonGrid.updateDoor(pos, type, RoomState.DISCOVERED);
    }

    private static byte getColor(MapState map, int x, int z) {
        if (x < 0 || z < 0 || x >= 128 || z >= 128) return 0;
        return map.colors[x + (z << 7)];
    }

    public static boolean isCalibrated() {
        return calibrated;
    }
}
