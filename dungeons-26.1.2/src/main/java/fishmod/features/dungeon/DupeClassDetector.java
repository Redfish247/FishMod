package fishmod.features.dungeon;

import fishmod.utils.Misc;
import fishmod.utils.config.values.Dungeons;
import fishmod.utils.dungeon.DungeonClass;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.events.Events;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DupeClassDetector {

    private static final Set<DungeonClass> announced = ConcurrentHashMap.newKeySet();

    public static void init() {

        Events.ON_PHASE_CHANGE.register(() -> {
            if (Phase.runJustStarted()) {
                announced.clear();
            }
            return false;
        });

        Events.ON_RUN_END.register(() -> {
            announced.clear();
            return false;
        });

        Events.ON_PLAYER_ENTRY.register(receivedEntry -> {
            if (!Dungeons.detectDuplicateClass) return false;
            checkForDupes();
            return false;
        });
    }

    private static void checkForDupes() {
        Map<DungeonClass, List<String>> byClass = new EnumMap<>(DungeonClass.class);

        DungeonClass.getAll().forEach((name, dungeonClass) -> {
            if (dungeonClass == null) return;
            if (dungeonClass == DungeonClass.MAGE && Dungeons.ignoreDupeMage) return;
            byClass.computeIfAbsent(dungeonClass, k -> new ArrayList<>()).add(name);
        });

        byClass.forEach((dungeonClass, names) -> {
            if (names.size() < 2) return;
            if (!announced.add(dungeonClass)) return;

            Misc.addChatMessage(Component.literal("§c§lDupe Class Detected §8> §f" + names.size()
                    + " " + capitalize(dungeonClass.name()) + " §7(" + joinNames(names) + ")"));

            if (Dungeons.dupeClassPartyChat) {
                Misc.executeCommand("pc Dupe Class Detected > " + names.size()
                        + " " + capitalize(dungeonClass.name()) + " (" + joinNames(names) + ")");
            }
        });
    }

    private static String joinNames(List<String> names) {
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " & " + names.get(1);
        return String.join(", ", names.subList(0, names.size() - 1)) + " & " + names.get(names.size() - 1);
    }

    private static String capitalize(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
