package fishmod.utils.data;

import java.util.HashMap;
import java.util.Map;

public class Data {

    public static class DungeonData {
        public Map<String, Long> classXp = new HashMap<>();
    }

    public static DungeonData dungeon = new DungeonData();
}
