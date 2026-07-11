package fishmod.features.dungeon;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import net.minecraft.client.MinecraftClient;

import java.util.regex.Pattern;

/**
 * Self-contained lag tracker — measures seconds lost to server lag during a
 * dungeon run by comparing wall-clock time to server-tick count.
 *
 * This replaces reading Blade's "Xs lost to lag" chat message, so the feature
 * works regardless of whether Blade's lag message setting is on.
 */
public class LagTracker {

    // Same start trigger the split timer uses (includes § color codes)
    private static final String RUN_START_MSG =
            "§e[NPC] §bMort§f: Here, I found this map when I first entered the dungeon.";

    private static final Pattern RUN_END_PATTERN =
            Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+)\\s*(\\(NEW RECORD!\\))?$");

    private static boolean active   = false;
    private static long    startMs  = 0;
    private static long    ticks    = 0;

    /** Returns seconds of accumulated lag for the current run, or 0 if no run is active. */
    public static double getCurrentLag() {
        if (!active || startMs == 0) return 0;
        double wallSec = (System.currentTimeMillis() - startMs) / 1000.0;
        double tickSec = ticks * 0.05;
        return Math.max(0, wallSec - tickSec);
    }

    public static void init() {

        // Detect run start / end from game messages
        Events.ON_GAME_MESSAGE.register(message -> {
            String s = message.getString();

            if (!active && s.equals(RUN_START_MSG)) {
                startMs = System.currentTimeMillis();
                ticks   = 0;
                active  = true;

            } else if (active && RUN_END_PATTERN.matcher(s).find()) {
                double wallSec = (System.currentTimeMillis() - startMs) / 1000.0;
                double tickSec = ticks * 0.05;
                double lag     = wallSec - tickSec;
                active = false;

                if (FishSettings.sendLagToParty && lag >= 0.1) {
                    String formatted = String.format("%.2f", lag);
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.getNetworkHandler() != null) {
                        mc.send(() -> mc.getNetworkHandler()
                                .sendChatCommand("pc " + formatted + "s lost to lag."));
                    }
                }
            }
            return false;
        });

        // Count server ticks while a run is active
        Events.ON_SERVER_TICK.register(() -> {
            if (active) ticks++;
            return false;
        });

        // Reset on location change (left dungeon, lobby, etc.)
        Events.ON_LOCATION_CHANGE.register(loc -> {
            active  = false;
            startMs = 0;
            ticks   = 0;
            return false;
        });
    }
}
