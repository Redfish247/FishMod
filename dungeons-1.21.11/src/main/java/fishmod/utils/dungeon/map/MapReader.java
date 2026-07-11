package fishmod.utils.dungeon.map;

import fishmod.utils.Location;
import fishmod.utils.config.values.DungeonMapSettings;
import fishmod.utils.events.Events;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapState;
import net.minecraft.network.packet.s2c.play.MapUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the dungeon's vanilla map item each tick and feeds {@link DungeonGrid}.
 *
 * <p>Calibration and the room/door TYPE grid are read entirely from the map's own pixel data —
 * no player world position or map decoration is needed for that part. Door OPENED state and
 * player markers are separate, additive concerns that do need a world-position bridge (see
 * {@link #worldOriginX}) or map decorations respectively; they're captured here but kept
 * architecturally separate from the pixel-native detection so a bad world-position read can't
 * corrupt room/door type detection the way it did before this file's Odin-based redesign.
 *
 * <p>Door detection and calibration ported from Odin (github.com/odtheking/Odin); door
 * opened-state + wither/blood inference ported from NoammAddons
 * (github.com/Noamm9/NoammAddons, 1.21.11-legacy branch) — both open-source Hypixel Skyblock mods
 * with independently confirmed-working dungeon maps.
 */
public class MapReader {
    private static final int GRID_SIZE = 6;
    private static final int ROOM_SPACING = 4;
    private static final int DOOR_CHECK_Y = 69;

    private static MapIdComponent currentMapId;
    private static boolean calibrated = false;
    private static int roomSize = 16;
    private static int roomGap = 20;
    private static int pixelStartX = 5;
    private static int pixelStartY = 5;
    private static int entranceTileX = 0;
    private static int entranceTileZ = 0;

    // World-position bridge for door opened-state checks only — captured once, from the player's
    // position the first tick the dungeon map is seen (reliably at/near the entrance, since that's
    // where a run starts). Hypixel dungeons are always instanced 8 blocks off a fixed 32-block grid.
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
        if (currentMapId != null) DungeonGrid.finalizeRunObservations();
        currentMapId = null;
        calibrated = false;
        roomSize = 16;
        roomGap = 20;
        pixelStartX = 5;
        pixelStartY = 5;
        worldAnchored = false;
        DungeonGrid.reset();
    }

    private static void tick() {
        if (!Location.inDungeon() || !DungeonMapSettings.enabled || currentMapId == null) return;
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

        if (!calibrated) {
            if (!tryCalibrate(map.colors)) return;
        }
        sample(map.colors);
        updateDoorOpenedState(mc);
        updatePlayerMarkers(mc, map);
        identifyOneRoom(mc);
    }

    /** Finds the entrance's green streak and derives the grid's pixel origin from its position modulo the room-grid period. */
    private static boolean tryCalibrate(byte[] colors) {
        for (int index = 0; index < colors.length; index++) {
            if (colors[index] != RoomType.ENTRANCE.mapColor) continue;

            int end = index;
            while (end < colors.length && colors[end] == colors[index]) end++;

            int length = end - index;
            if (length != 16 && length != 18) continue;

            roomSize = length;
            roomGap = roomSize + ROOM_SPACING;
            pixelStartX = (index % 128) % roomGap;
            pixelStartY = (index / 128) % roomGap;
            if (pixelStartX == 0) pixelStartX = 22;
            if (pixelStartY == 0) pixelStartY = 22;

            entranceTileX = ((index % 128) - pixelStartX) / roomGap;
            entranceTileZ = ((index / 128) - pixelStartY) / roomGap;

            calibrated = true;
            return true;
        }
        return false;
    }

    private static void sample(byte[] colors) {
        int halfRoom = roomSize / 2;
        int connectionGap = roomSize + ROOM_SPACING / 2;

        RoomType[] types = new RoomType[GRID_SIZE * GRID_SIZE];

        // Pass 1: sample every room corner+center first. Connector checks below need to compare a
        // tile against its EAST/SOUTH neighbor's type, which — in a single combined pass — hasn't
        // been sampled yet at that point in iteration order, so thisType == neighborType could
        // never be true and merging silently never fired. Splitting into two passes fixes that.
        for (int tileZ = 0; tileZ < GRID_SIZE; tileZ++) {
            for (int tileX = 0; tileX < GRID_SIZE; tileX++) {
                int originX = pixelStartX + tileX * roomGap;
                int originZ = pixelStartY + tileZ * roomGap;
                GridPos pos = new GridPos(tileX, tileZ);

                byte corner = getColor(colors, originX, originZ);
                if (corner == 0) continue;
                RoomType type = RoomType.fromMapColor(corner);
                if (type == null) continue;

                types[tileZ * GRID_SIZE + tileX] = type;
                byte center = getColor(colors, originX + halfRoom, originZ + halfRoom);
                RoomState state = type == RoomType.UNKNOWN
                        ? RoomState.UNOPENED
                        : RoomState.fromCenterColor(center, corner);
                if (state != null) DungeonGrid.updateRoom(pos, type, state);
            }
        }

        // Pass 2: now that every tile's type is known, check connectors/doors/merges.
        for (int tileZ = 0; tileZ < GRID_SIZE; tileZ++) {
            for (int tileX = 0; tileX < GRID_SIZE; tileX++) {
                int originX = pixelStartX + tileX * roomGap;
                int originZ = pixelStartY + tileZ * roomGap;
                GridPos pos = new GridPos(tileX, tileZ);

                if (tileX < GRID_SIZE - 1) {
                    sampleConnector(colors, pos, true, originX, originZ, connectionGap, halfRoom, types, tileX, tileZ);
                }
                if (tileZ < GRID_SIZE - 1) {
                    sampleConnector(colors, pos, false, originX, originZ, connectionGap, halfRoom, types, tileX, tileZ);
                }
            }
        }
    }

    /**
     * Checks the connector between (tileX,tileZ) and its east (horizontal) or south (!horizontal)
     * neighbor. If the gap's row/column-aligned pixel is non-empty, the two cells are actually one
     * merged room (no door); otherwise, a non-empty pixel at the gap's midpoint is a real door.
     */
    private static void sampleConnector(byte[] colors, GridPos pos, boolean horizontal,
                                         int originX, int originZ, int connectionGap, int halfRoom,
                                         RoomType[] types, int tileX, int tileZ) {
        int gapAlignedX = horizontal ? originX + connectionGap : originX;
        int gapAlignedZ = horizontal ? originZ : originZ + connectionGap;
        byte gapAligned = getColor(colors, gapAlignedX, gapAlignedZ);

        GridPos neighbor = horizontal ? pos.offset(1, 0) : pos.offset(0, 1);
        int neighborIndex = horizontal ? tileZ * GRID_SIZE + (tileX + 1) : (tileZ + 1) * GRID_SIZE + tileX;
        RoomType thisType = types[tileZ * GRID_SIZE + tileX];
        RoomType neighborType = types[neighborIndex];

        if (gapAligned != 0) {
            if (thisType != null && thisType == neighborType) {
                DungeonGrid.markConnected(pos, neighbor);
            }
            return;
        }

        int doorX = horizontal ? originX + connectionGap : originX + halfRoom;
        int doorZ = horizontal ? originZ + halfRoom : originZ + connectionGap;
        byte doorColor = getColor(colors, doorX, doorZ);
        // A blank (unpainted) connector with no door pixel does NOT mean "merged" — it just as
        // often means "not revealed yet". An earlier version of this method treated blank+same-type
        // as an automatic merge, which incorrectly fused separate same-type rooms together whenever
        // the corridor between them hadn't been revealed. Only the positive signal above (the
        // gap-aligned pixel actually showing the room's own color once Hypixel paints it) is a real
        // merge — this matches Odin's own algorithm, which never merges on blank alone.
        if (doorColor == 0) return;

        DungeonGrid.updateDoor(new DoorKey(pos, horizontal), DoorType.fromMapColor(doorColor));
    }

    /**
     * Checks each known door's actual world block; if it's air, marks it opened. Blood doors also
     * infer "opened" once any known Blood room has left UNOPENED state (you can't be past an
     * UNOPENED blood room without having gone through its door). Simplified from NoammAddons'
     * version, which also has a wither-specific "map still shows closed color -> force closed"
     * override and a general map-color-based opened fallback; this only trusts the world block
     * itself (or the blood-room inference) rather than guessing from map color.
     */
    private static void updateDoorOpenedState(MinecraftClient mc) {
        for (var entry : DungeonGrid.allDoors().entrySet()) {
            DoorKey key = entry.getKey();
            DoorTile door = entry.getValue();
            if (door.opened()) continue;

            int doorWorldX, doorWorldZ;
            if (key.horizontal()) {
                doorWorldX = tileWorldOrigin(key.tile().x() + 1);
                doorWorldZ = tileWorldOrigin(key.tile().z()) + roomSize;
            } else {
                doorWorldX = tileWorldOrigin(key.tile().x()) + roomSize;
                doorWorldZ = tileWorldOrigin(key.tile().z() + 1);
            }
            BlockPos pos = new BlockPos(doorWorldX, DOOR_CHECK_Y, doorWorldZ);
            if (mc.world.isChunkLoaded(doorWorldX >> 4, doorWorldZ >> 4) && mc.world.getBlockState(pos).isAir()) {
                DungeonGrid.markDoorOpened(key);
                continue;
            }

            if (door.type() == DoorType.BLOOD) {
                boolean bloodRoomEntered = DungeonGrid.allRooms().values().stream()
                        .anyMatch(r -> r.type() == RoomType.BLOOD && r.state() != RoomState.UNOPENED);
                if (bloodRoomEntered) DungeonGrid.markDoorOpened(key);
            }
        }
    }

    /** World X/Z of tile (tileIndex, *) or (*, tileIndex)'s northwest corner, per axis. */
    private static int tileWorldOrigin(int tileIndex) {
        return worldOriginX + (tileIndex - entranceTileX) * 32;
    }

    private static void updatePlayerMarkers(MinecraftClient mc, MapState map) {
        if (!DungeonMapSettings.showPlayerMarkers) {
            DungeonGrid.setPlayerMarkers(List.of());
            return;
        }
        String selfName = mc.player.getName().getString();
        List<PlayerMarker> markers = new ArrayList<>();
        for (MapDecoration decoration : map.getDecorations()) {
            int pixelX = (decoration.x() >> 1) + 64;
            int pixelZ = (decoration.z() >> 1) + 64;
            float tileX = (pixelX - pixelStartX) / (float) roomGap;
            float tileZ = (pixelZ - pixelStartY) / (float) roomGap;
            float yaw = decoration.rotation() * 22.5f;
            String name = decoration.name().isPresent() ? decoration.name().get().getString() : null;
            boolean self = name != null && selfName.equalsIgnoreCase(name.replaceAll("§.", "").trim());
            markers.add(new PlayerMarker(tileX, tileZ, yaw, self, name));
        }
        DungeonGrid.setPlayerMarkers(markers);
    }

    private static byte getColor(byte[] colors, int x, int z) {
        if (x < 0 || z < 0 || x >= 128 || z >= 128) return 0;
        return colors[z * 128 + x];
    }

    /**
     * Attempts to identify one not-yet-named room per tick (throttled — a full block-column scan
     * for every unidentified room every tick would be wasteful). Ported from Odin's
     * {@code WorldScan.getRoomCore}: scans straight down at the room's center, building a string of
     * block registry names from the first non-air/gold block down to bedrock, then hashes it and
     * looks the hash up in the vendored {@link RoomData} database. {@code Block.toString()} returns
     * the registry name deterministically, so this hash is stable across game sessions/machines —
     * confirmed via javap before relying on it, not assumed.
     */
    private static void identifyOneRoom(MinecraftClient mc) {
        for (RoomTile tile : DungeonGrid.allRooms().values()) {
            if (tile.name() != null || tile.scanAttempted || tile.type() == null || tile.type() == RoomType.UNKNOWN) continue;

            // +7, not the room's true center (+16) — must match Odin's own data-collection scan
            // position exactly, or the resulting hash won't match anything in the database.
            int centerX = tileWorldOrigin(tile.pos().x()) + 7;
            int centerZ = tileWorldOrigin(tile.pos().z()) + 7;
            if (!mc.world.isChunkLoaded(centerX >> 4, centerZ >> 4)) continue;

            tile.scanAttempted = true;
            int core = getRoomCore(mc.world, centerX, centerZ);
            RoomData data = RoomData.byCore(core);
            if (data != null) DungeonGrid.identifyRoom(tile, data.name);
            return; // one scan per tick
        }
    }

    private static int getRoomCore(net.minecraft.world.World world, int x, int z) {
        StringBuilder sb = new StringBuilder(256);
        boolean foundHighest = false;
        int bedrockRun = 0;

        for (int y = 140; y >= 12; y--) {
            var state = world.getBlockState(new BlockPos(x, y, z));
            if (!foundHighest) {
                if (!state.isAir() && state.getBlock() != net.minecraft.block.Blocks.GOLD_BLOCK) {
                    foundHighest = true;
                } else {
                    sb.append('0');
                    continue;
                }
            }

            if (state.isAir() && bedrockRun >= 2 && y < 69) {
                sb.append("0".repeat(Math.max(0, y - 11)));
                break;
            }
            if (state.getBlock() == net.minecraft.block.Blocks.BEDROCK) {
                bedrockRun++;
            } else {
                bedrockRun = 0;
                if (state.getBlock() == net.minecraft.block.Blocks.OAK_PLANKS
                        || state.getBlock() == net.minecraft.block.Blocks.TRAPPED_CHEST
                        || state.getBlock() == net.minecraft.block.Blocks.CHEST) {
                    continue;
                }
            }
            sb.append(state.getBlock());
        }
        return sb.toString().hashCode();
    }

    public static boolean isCalibrated() {
        return calibrated;
    }
}
