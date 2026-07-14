package fishmod.utils.dungeon.map;

import fishmod.utils.config.values.DungeonMapSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntSupplier;

/**
 * Ported from NoammAddons' ClearInfoUpdater: tracks which player(s) were near a room the moment it
 * flipped to {@link RoomState#CLEARED}, classifying each clear as solo (exactly one player nearby)
 * or stacked (more than one), plus a run-end secrets/deaths summary.
 *
 * <p>Wired per-module: each dungeons-* module supplies {@link #secretCountSupplier} /
 * {@link #deathCountSupplier} (from its own {@code DungeonScore}, same pattern as
 * {@link DungeonGrid#puzzleCountSupplier}) and calls {@link #tick()} once per server tick alongside
 * {@link DungeonGrid} updates, then {@link #buildSummary()} + {@link #reset()} on run end.
 */
public class ClearInfoUpdater {
    /** Tiles within this many grid-cell units of a room's own position count as "at the room" when it clears. */
    private static final float PROXIMITY_TILES = 1.5f;

    public static IntSupplier secretCountSupplier = () -> 0;
    public static IntSupplier deathCountSupplier = () -> 0;

    private static final Map<GridPos, RoomState> lastKnownState = new HashMap<>();
    private static final Map<String, Integer> soloClears = new TreeMap<>();
    private static final Map<String, Integer> stackedClears = new TreeMap<>();

    private ClearInfoUpdater() {}

    public static void reset() {
        lastKnownState.clear();
        soloClears.clear();
        stackedClears.clear();
    }

    /** Call once per server tick, after {@link DungeonGrid}'s own room-state updates for that tick. */
    public static void tick() {
        for (RoomTile tile : DungeonGrid.allRooms().values()) {
            RoomState previous = lastKnownState.get(tile.pos());
            if (previous != RoomState.CLEARED && tile.state() == RoomState.CLEARED) {
                onRoomCleared(tile);
            }
            lastKnownState.put(tile.pos(), tile.state());
        }
    }

    private static void onRoomCleared(RoomTile room) {
        java.util.List<String> nearby = new java.util.ArrayList<>();
        for (PlayerMarker marker : DungeonGrid.playerMarkers()) {
            float dx = marker.tileX() - room.pos().x();
            float dz = marker.tileZ() - room.pos().z();
            if (Math.sqrt(dx * dx + dz * dz) <= PROXIMITY_TILES) {
                String name = marker.self() ? "You" : marker.name();
                if (name != null) nearby.add(name.replaceAll("§.", "").trim());
            }
        }
        if (nearby.isEmpty()) return;
        Map<String, Integer> target = nearby.size() == 1 ? soloClears : stackedClears;
        for (String name : nearby) {
            target.merge(name, 1, Integer::sum);
        }
    }

    /** Plain-text run-end summary; each module sends this via its own chat API when {@link DungeonMapSettings#playerClearInfoEnabled}. */
    public static String buildSummary() {
        int soloTotal = soloClears.values().stream().mapToInt(Integer::intValue).sum();
        int stackedTotal = stackedClears.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder sb = new StringBuilder();
        sb.append("Rooms cleared: ").append(soloTotal).append(" solo, ").append(stackedTotal).append(" stacked");
        if (!soloClears.isEmpty()) {
            sb.append(" (solo: ");
            appendCounts(sb, soloClears);
            sb.append(")");
        }
        if (!stackedClears.isEmpty()) {
            sb.append(" (stacked: ");
            appendCounts(sb, stackedClears);
            sb.append(")");
        }
        sb.append(" | Secrets: ").append(secretCountSupplier.getAsInt());
        sb.append(" | Deaths: ").append(deathCountSupplier.getAsInt());
        return sb.toString();
    }

    private static void appendCounts(StringBuilder sb, Map<String, Integer> counts) {
        boolean first = true;
        for (var entry : counts.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append(" ").append(entry.getValue());
            first = false;
        }
    }
}
