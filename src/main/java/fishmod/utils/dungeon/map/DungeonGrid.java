package fishmod.utils.dungeon.map;

import fishmod.utils.config.values.DungeonMapSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton holding everything known about the current dungeon run's fixed 6x6 room grid (tile
 * coordinates 0..5 per axis — see {@link GridPos}'s javadoc for why this is no longer tied to the
 * player's world position at all).
 */
public class DungeonGrid {
    private static final Map<GridPos, RoomTile> rooms = new ConcurrentHashMap<>();
    private static final Map<DoorKey, DoorTile> doors = new ConcurrentHashMap<>();
    /**
     * Pairs of adjacent same-type room tiles confirmed (by {@link MapReader}, from the map pixel
     * between them showing the room's own color rather than being blank/a door) to be part of a
     * single merged multi-cell room. {@link #recomputeShapes()} groups room tiles into logical
     * rooms across these edges.
     */
    private static final Set<String> connectedPairs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static int nextRoomId = 0;

    private DungeonGrid() {}

    private static volatile List<PlayerMarker> playerMarkers = List.of();
    /**
     * Smoothed marker state, keyed by index in the last raw decoration list (best-effort — Hypixel
     * doesn't give a stable per-player key on this custom map, but the decoration order is stable
     * enough frame-to-frame in practice). Each tick's raw target is exponentially smoothed toward
     * instead of snapped to directly, so markers glide between the map's own update ticks instead of
     * visibly jumping — same idea as NoammAddons' animated lerp, done synchronously per-tick instead
     * of with a timed coroutine animation.
     */
    private static final Map<Integer, float[]> smoothedPositions = new ConcurrentHashMap<>();
    private static final float SMOOTHING = 0.35f;

    public static void reset() {
        rooms.clear();
        doors.clear();
        connectedPairs.clear();
        nextRoomId = 0;
        playerMarkers = List.of();
        smoothedPositions.clear();
    }

    public static void setPlayerMarkers(List<PlayerMarker> markers) {
        List<PlayerMarker> smoothed = new ArrayList<>(markers.size());
        for (int i = 0; i < markers.size(); i++) {
            PlayerMarker raw = markers.get(i);
            float[] pos = smoothedPositions.computeIfAbsent(i, k -> new float[]{raw.tileX(), raw.tileZ()});
            pos[0] += (raw.tileX() - pos[0]) * SMOOTHING;
            pos[1] += (raw.tileZ() - pos[1]) * SMOOTHING;
            smoothed.add(new PlayerMarker(pos[0], pos[1], raw.yaw(), raw.self(), raw.name()));
        }
        playerMarkers = smoothed;
    }

    public static List<PlayerMarker> playerMarkers() {
        return playerMarkers;
    }

    public static Tile get(GridPos pos) {
        RoomTile t = rooms.get(pos);
        return t != null ? t : new UnknownTile(pos);
    }

    /** Creates or upgrades the room tile at pos, never regressing its state. */
    public static RoomTile updateRoom(GridPos pos, RoomType type, RoomState state) {
        RoomTile tile = rooms.computeIfAbsent(pos, p -> new RoomTile(p, type, state));
        tile.upgrade(type, state);
        return tile;
    }

    /** Creates or upgrades the door at key. */
    public static DoorTile updateDoor(DoorKey key, DoorType type) {
        DoorTile tile = doors.computeIfAbsent(key, k -> new DoorTile(type));
        tile.upgrade(type);
        return tile;
    }

    /** Marks a door as physically opened (world block check passed, or inferred). Never un-opens. */
    public static void markDoorOpened(DoorKey key) {
        DoorTile tile = doors.get(key);
        if (tile != null) tile.markOpened();
    }

    /** Marks two adjacent same-type room tiles as part of one merged multi-cell room. */
    public static void markConnected(GridPos a, GridPos b) {
        String key = edgeKey(a, b);
        if (connectedPairs.add(key)) recomputeShapes();
    }

    private static String edgeKey(GridPos a, GridPos b) {
        String sa = a.x() + "," + a.z(), sb = b.x() + "," + b.z();
        return sa.compareTo(sb) < 0 ? sa + "|" + sb : sb + "|" + sa;
    }

    private static void recomputeShapes() {
        Map<GridPos, Integer> assigned = new HashMap<>();
        for (String edge : connectedPairs) {
            String[] parts = edge.split("\\|");
            GridPos a = parsePos(parts[0]);
            GridPos b = parsePos(parts[1]);
            RoomTile ta = rooms.get(a);
            RoomTile tb = rooms.get(b);
            if (ta == null || tb == null || ta.type() != tb.type()) continue;

            int ia = assigned.getOrDefault(a, ta.roomId);
            int ib = assigned.getOrDefault(b, tb.roomId);
            int merged = ia >= 0 ? ia : (ib >= 0 ? ib : nextRoomId++);
            assigned.put(a, merged);
            assigned.put(b, merged);
        }
        for (Map.Entry<GridPos, Integer> entry : assigned.entrySet()) {
            RoomTile t = rooms.get(entry.getKey());
            if (t != null) t.roomId = entry.getValue();
        }
    }

    private static GridPos parsePos(String s) {
        String[] p = s.split(",");
        return new GridPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    /** Whether a and b are two tiles of the same merged multi-cell room (so the gap between them should render as part of the room, not blank). */
    public static boolean isMerged(GridPos a, GridPos b) {
        RoomTile ta = rooms.get(a);
        RoomTile tb = rooms.get(b);
        return ta != null && tb != null && ta.roomId >= 0 && ta.roomId == tb.roomId;
    }

    /** Whether tile is the one segment of its logical room that should draw the room's label (topmost, then leftmost). */
    public static boolean isLabelAnchor(RoomTile tile) {
        List<RoomTile> segments = segmentsOf(tile);
        if (segments.size() <= 1) return true;
        RoomTile anchor = segments.get(0);
        for (RoomTile t : segments) {
            if (t.pos().z() < anchor.pos().z() || (t.pos().z() == anchor.pos().z() && t.pos().x() < anchor.pos().x())) {
                anchor = t;
            }
        }
        return tile == anchor;
    }

    /** Applies an identified exact room name to every segment of tile's logical room. */
    public static void identifyRoom(RoomTile tile, String name) {
        for (RoomTile segment : segmentsOf(tile)) segment.setName(name);
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

    public static Map<DoorKey, DoorTile> allDoors() {
        return doors;
    }

    /** How many logical (already-merged) rooms of the given type have been confirmed this run. */
    public static int countLogicalRoomsOfType(RoomType type) {
        Set<Integer> countedRoomIds = new HashSet<>();
        int count = 0;
        for (RoomTile tile : rooms.values()) {
            if (tile.type() != type) continue;
            if (tile.roomId >= 0) {
                if (!countedRoomIds.add(tile.roomId)) continue;
            }
            count++;
        }
        return count;
    }

    /**
     * Like {@link #get}, but for an unresolved (UNOPENED) room whose local signature narrows to
     * 2+ candidate types, returns a {@link PredictedRoomTile} instead — see
     * {@link RoomSignatureDB#predictPartial} for the scope/reasoning.
     *
     * <p>Candidates that are impossible given real Hypixel dungeon-generation constraints are
     * filtered out first: exactly one Trap, Miniboss, and Blood room ever exist per dungeon, and
     * Puzzle rooms are capped at whatever count the tab list's "Puzzles: (N)" line reports (via
     * {@link fishmod.features.dungeon.DungeonScore#getPuzzleCount()}). Once that many of a type
     * have already been confirmed on the map, it's removed from every remaining prediction —
     * otherwise a room type that's already been found elsewhere would keep getting suggested again.
     */
    public static Tile getWithPrediction(GridPos pos) {
        Tile tile = get(pos);
        if (!DungeonMapSettings.predictionLayerEnabled) return tile;
        if (!(tile instanceof RoomTile room) || room.state() != RoomState.UNOPENED) return tile;

        List<RoomSignatureDB.Candidate> candidates = RoomSignatureDB.predictPartial(pos);
        List<RoomType> types = new ArrayList<>();
        for (RoomSignatureDB.Candidate c : candidates) {
            if (isExhausted(c.type())) continue;
            if (c.type() == RoomType.FAIRY && !isAdjacentToWitherDoor(pos)) continue;
            types.add(c.type());
        }
        if (types.size() < 2) return tile;
        return new PredictedRoomTile(pos, types);
    }

    private static boolean isExhausted(RoomType type) {
        return switch (type) {
            // The entrance is the room you start in — always immediately known, never ambiguous,
            // so it should never be offered as a candidate for some other undiscovered room.
            case ENTRANCE -> true;
            // Blood's exact position can't be reliably narrowed just from wither-door adjacency
            // (multiple wither doors can exist before the real one leading to blood) — never
            // predicted, always left to reveal itself on the map like everything else.
            case BLOOD -> true;
            case TRAP -> countLogicalRoomsOfType(RoomType.TRAP) >= 1;
            case MINIBOSS -> countLogicalRoomsOfType(RoomType.MINIBOSS) >= 1;
            case PUZZLE -> {
                int total = fishmod.features.dungeon.DungeonScore.getPuzzleCount();
                yield total > 0 && countLogicalRoomsOfType(RoomType.PUZZLE) >= total;
            }
            default -> false;
        };
    }

    /**
     * The Fairy room always sits past a wither door — Hypixel never places it anywhere else — so
     * it's only offered as a prediction candidate for a room that's actually adjacent to a
     * confirmed wither door, not any arbitrary undiscovered room whose door pattern happens to match.
     */
    private static boolean isAdjacentToWitherDoor(GridPos pos) {
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            GridPos neighbor = pos.offset(dir[0], dir[1]);
            DoorKey key = dir[0] == 1 ? new DoorKey(pos, true)
                    : dir[0] == -1 ? new DoorKey(neighbor, true)
                    : dir[1] == 1 ? new DoorKey(pos, false)
                    : new DoorKey(neighbor, false);
            DoorTile door = doors.get(key);
            if (door != null && door.type() == DoorType.WITHER) return true;
        }
        return false;
    }

    /**
     * Records a signature observation for every fully-typed logical room seen this run. Called once
     * when leaving the dungeon (see MapReader) rather than continuously mid-run, so a room's shape
     * has settled before it's recorded.
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
