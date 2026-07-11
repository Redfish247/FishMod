package fishmod.utils.dungeon.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FishMod's own local, self-growing signature database — see the "Local self-learning prediction
 * layer" section of the dungeon-map plan for why this exists instead of a bundled dataset: no
 * pre-built shape+door-to-type database was found anywhere during porting research, so instead of
 * fabricating one, this only ever reflects what was actually observed in Eli's own runs. It starts
 * empty (zero predictions) and grows every time a room fully resolves.
 *
 * <p>Stored in config/fishmod-dungeon-rooms.json as: {@code { signatureKey: { "PUZZLE": 3, "TRAP": 1 } } }.
 */
public class RoomSignatureDB {

    private static final String FILE_PATH = "config/fishmod-dungeon-rooms.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, Map<String, Integer>> data = new HashMap<>();

    static {
        load();
    }

    public static void observe(RoomTile tile) {
        RoomType type = tile.type();
        if (type == null || type == RoomType.UNKNOWN) return;
        String key = RoomSignature.of(tile).key();
        Map<String, Integer> counts = data.computeIfAbsent(key, k -> new HashMap<>());
        counts.merge(type.name(), 1, Integer::sum);
        save();
    }

    public record Candidate(RoomType type, int count) {}

    /**
     * Returns candidate room types for a not-yet-resolved room cell, based only on which of its
     * own 4 immediate sides are confirmed doors so far — everything else is treated as unknown.
     * <p>
     * Scoped to single-cell (1x1) candidates only: Hypixel's map never paints "definitely no door
     * here," only "door revealed here," so a room that later turns out to span multiple cells looks
     * identical to a 1x1 room until one of its merge connectors is revealed. Predicting a shape that
     * isn't confirmed yet would mean fabricating certainty the map doesn't give us, so this only
     * predicts the deliberately narrower "if this ends up being a single room, which types have
     * matched these doors before" question.
     */
    public static List<Candidate> predictPartial(GridPos pos) {
        // Relative (dx, dz) offsets, converted from doorSidesOf's absolute positions.
        List<int[]> knownDoorSides = new ArrayList<>();
        for (int[] abs : RoomSignature.doorSidesOf(pos)) {
            knownDoorSides.add(new int[]{abs[0] - pos.x(), abs[1] - pos.z()});
        }
        if (knownDoorSides.isEmpty()) return List.of(); // nothing to narrow down yet

        List<int[]> allSides = List.of(new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1});
        List<int[]> unknownSides = new ArrayList<>();
        for (int[] dir : allSides) {
            boolean known = knownDoorSides.stream().anyMatch(d -> d[0] == dir[0] && d[1] == dir[1]);
            if (!known) unknownSides.add(dir);
        }

        Map<RoomType, Integer> totals = new HashMap<>();
        int combinations = 1 << unknownSides.size();
        for (int mask = 0; mask < combinations; mask++) {
            List<int[]> doors = new ArrayList<>(knownDoorSides);
            for (int i = 0; i < unknownSides.size(); i++) {
                if ((mask & (1 << i)) != 0) doors.add(unknownSides.get(i));
            }
            String key = RoomSignature.singleCellKey(doors);
            Map<String, Integer> counts = data.get(key);
            if (counts == null) continue;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                try {
                    RoomType type = RoomType.valueOf(entry.getKey());
                    totals.merge(type, entry.getValue(), Integer::sum);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        List<Candidate> result = new ArrayList<>();
        for (Map.Entry<RoomType, Integer> entry : totals.entrySet()) {
            result.add(new Candidate(entry.getKey(), entry.getValue()));
        }
        result.sort((a, b) -> b.count() - a.count());
        return result;
    }

    private static void load() {
        File file = new File(FILE_PATH);
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
            Map<String, Map<String, Integer>> loaded = GSON.fromJson(reader, type);
            if (loaded != null) data = loaded;
        } catch (Exception ignored) {}
    }

    private static void save() {
        try {
            File file = new File(FILE_PATH);
            file.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception ignored) {}
    }
}
