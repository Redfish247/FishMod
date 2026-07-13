package fishmod.utils.dungeon.map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A specific dungeon room design: name, shape, secret/crypt counts, and a list of "core" block
 * fingerprint hashes that identify it (a room design can have multiple recorded cores because
 * decorative details like carpet/banner color vary between instances without changing the layout).
 *
 * <p>Vendored from Odin (github.com/odtheking/Odin, BSD-3-Clause) at
 * {@code /data/dungeon_rooms.json} — not hand-collected by this mod. See {@link RoomIdentifier}
 * for how a room's core hash is computed from world blocks to look up an entry here.
 */
public class RoomData {
    public String name;
    public String type;
    public String shape;
    public List<Integer> cores;
    public int crypts;
    public int secrets;
    public int trappedChests;

    private static final Map<Integer, RoomData> BY_CORE = new HashMap<>();
    private static final Map<String, RoomData> BY_NAME = new HashMap<>();

    static {
        try (InputStream stream = RoomData.class.getResourceAsStream("/data/dungeon_rooms.json")) {
            if (stream != null) {
                try (Reader reader = new InputStreamReader(stream)) {
                    Type listType = new TypeToken<List<RoomData>>() {}.getType();
                    List<RoomData> rooms = new Gson().fromJson(reader, listType);
                    if (rooms != null) {
                        for (RoomData room : rooms) {
                            if (room.name != null) BY_NAME.put(room.name, room);
                            if (room.cores == null) continue;
                            for (int core : room.cores) BY_CORE.put(core, room);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static RoomData byCore(int core) {
        return BY_CORE.get(core);
    }

    public static RoomData byName(String name) {
        return BY_NAME.get(name);
    }

    /** Cell count implied by {@link #shape}, or -1 if unrecognized/unset. */
    public int cellCount() {
        if (shape == null) return -1;
        return switch (shape) {
            case "1x1" -> 1;
            case "1x2" -> 2;
            case "1x3", "L" -> 3;
            case "1x4", "2x2" -> 4;
            default -> -1;
        };
    }
}
