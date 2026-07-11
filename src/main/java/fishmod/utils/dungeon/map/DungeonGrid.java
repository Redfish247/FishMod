package fishmod.utils.dungeon.map;

import fishmod.utils.config.values.DungeonMapSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Singleton holding everything known about the current dungeon run's room/door grid. Backed by a
 * sparse map rather than a fixed-size array — grid coordinates are relative to wherever the player
 * first calibrated (see MapReader), not a known absolute dungeon origin, so the grid has no fixed
 * bounds to size an array to.
 */
public class DungeonGrid {
    private static final Map<GridPos, RoomTile> rooms = new HashMap<>();
    private static final Map<GridPos, DoorTile> doors = new HashMap<>();
    /**
     * Connector cells confirmed (by map pixel color matching the neighboring room's own type,
     * rather than a door palette color) to sit inside a single merged multi-cell room rather than
     * at a real door boundary. {@link #recomputeShapes()} merges room cells across these.
     */
    private static final Set<GridPos> mergedConnectors = new HashSet<>();
    private static int nextRoomId = 0;

    private DungeonGrid() {}

    public static void reset() {
        rooms.clear();
        doors.clear();
        mergedConnectors.clear();
        nextRoomId = 0;
    }

    /** Records that a connector cell is inside a merged room rather than at a real door. */
    public static void markMerged(GridPos connectorPos) {
        if (mergedConnectors.add(connectorPos)) recomputeShapes();
    }

    public static Tile get(GridPos pos) {
        if (pos.isRoomCell()) {
            RoomTile t = rooms.get(pos);
            return t != null ? t : new UnknownTile(pos);
        }
        DoorTile t = doors.get(pos);
        return t != null ? t : new UnknownTile(pos);
    }

    /** Creates or upgrades the room-quadrant tile at pos, never regressing its state. */
    public static RoomTile updateRoom(GridPos pos, RoomType type, RoomState state) {
        RoomTile tile = rooms.computeIfAbsent(pos, p -> new RoomTile(p, type, state));
        tile.upgrade(type, state);
        recomputeShapes();
        return tile;
    }

    /** Creates or upgrades the door/connector tile at pos, never regressing its state. */
    public static DoorTile updateDoor(GridPos pos, DoorType type, RoomState state) {
        DoorTile tile = doors.computeIfAbsent(pos, p -> new DoorTile(p, type, state));
        tile.upgrade(type, state);
        return tile;
    }

    /**
     * Groups adjacent same-type room-quadrant tiles into logical multi-cell rooms: two quadrant
     * tiles 2 grid-units apart (same axis) belong to the same logical room when the connector cell
     * between them is also a revealed room-colored cell of the same type, rather than a door.
     * Re-run after every update since a newly revealed cell can merge two previously separate rooms.
     */
    private static void recomputeShapes() {
        Map<GridPos, Integer> assigned = new HashMap<>();
        for (RoomTile tile : rooms.values()) {
            if (tile.type() == null) continue;
            for (int[] dir : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                GridPos neighborPos = tile.pos().offset(dir[0], dir[1]);
                RoomTile neighbor = rooms.get(neighborPos);
                if (neighbor == null || neighbor.type() != tile.type()) continue;
                GridPos connectorPos = tile.pos().offset(dir[0] / 2, dir[1] / 2);
                if (!mergedConnectors.contains(connectorPos)) continue; // a real door means separate rooms

                int a = assigned.getOrDefault(tile.pos(), tile.roomId);
                int b = assigned.getOrDefault(neighborPos, neighbor.roomId);
                int merged = a >= 0 ? a : (b >= 0 ? b : nextRoomId++);
                assigned.put(tile.pos(), merged);
                assigned.put(neighborPos, merged);
            }
        }
        for (Map.Entry<GridPos, Integer> entry : assigned.entrySet()) {
            RoomTile t = rooms.get(entry.getKey());
            if (t != null) t.roomId = entry.getValue();
        }
    }

    /** All grid cells belonging to the same logical room as {@code tile} (its physical shape). */
    public static List<RoomTile> segmentsOf(RoomTile tile) {
        List<RoomTile> result = new ArrayList<>();
        if (tile.roomId < 0) {
            result.add(tile);
            return result;
        }
        for (RoomTile t : rooms.values()) {
            if (t.roomId == tile.roomId) result.add(t);
        }
        return result;
    }

    public static Map<GridPos, RoomTile> allRooms() {
        return rooms;
    }

    public static Map<GridPos, DoorTile> allDoors() {
        return doors;
    }

    /**
     * Like {@link #get}, but for an unresolved (UNOPENED) room cell whose local signature narrows
     * to 2+ candidate types, returns a {@link PredictedRoomTile} instead — see
     * {@link RoomSignatureDB#predictPartial} for the scope/reasoning. Never returns a prediction for
     * a cell that's already fully resolved, or when the config toggle is off.
     */
    public static Tile getWithPrediction(GridPos pos) {
        Tile tile = get(pos);
        if (!DungeonMapSettings.predictionLayerEnabled) return tile;
        if (!(tile instanceof RoomTile room) || room.state() != RoomState.UNOPENED) return tile;

        List<RoomSignatureDB.Candidate> candidates = RoomSignatureDB.predictPartial(pos);
        if (candidates.size() < 2) return tile;
        return new PredictedRoomTile(pos, candidates.get(0).type(), candidates.get(1).type());
    }

    /**
     * Records a signature observation for every fully-typed logical room seen this run. Called once
     * when leaving the dungeon (see MapReader) rather than continuously mid-run, so a room's shape
     * has settled (all its quadrants/doors as revealed as they're going to get) before it's recorded
     * — recording early would risk baking in an incomplete shape as if it were the room's real one.
     */
    public static void finalizeRunObservations() {
        Set<Integer> seenRoomIds = new HashSet<>();
        for (RoomTile tile : rooms.values()) {
            if (tile.type() == null || tile.type() == RoomType.UNKNOWN) continue;
            if (tile.roomId >= 0 && !seenRoomIds.add(tile.roomId)) continue;
            RoomSignatureDB.observe(tile);
        }
    }
}
