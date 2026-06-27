package fishmod.features;

import fishmod.utils.Tts;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;

/**
 * Wires a handful of high-value SkyBlock chat events to spoken {@link Tts} callouts. Self-contained:
 * it listens to game messages and speaks short phrases for the events whose category is enabled, so
 * no other feature needs to know TTS exists. Each callout respects both the master {@code ttsEnabled}
 * gate and its per-category toggle.
 */
public final class TtsCallouts {

    private TtsCallouts() {}

    private static String lastSpoken = "";
    private static long lastSpokenMs = 0;

    public static void init() {
        Events.ON_GAME_MESSAGE.register(text -> {
            if (!FishSettings.ttsEnabled || text == null) return false;
            onChat(text.getString().replaceAll("§.", "").trim().toUpperCase());
            return false;
        });
    }

    private static void onChat(String up) {
        if (FishSettings.ttsRareDrops) {
            if (up.contains("PRAISE RNGESUS"))      { say("Praise RNGesus"); return; }
            if (up.contains("INSANE DROP"))         { say("Insane drop");    return; }
            if (up.contains("CRAZY RARE DROP"))     { say("Crazy rare drop"); return; }
            if (up.contains("VERY RARE DROP"))      { say("Very rare drop");  return; }
            if (up.contains("RARE DROP"))           { say("Rare drop");       return; }
            if (up.contains("GREAT CATCH"))         { say("Great catch");     return; }
        }
        if (FishSettings.ttsSlayer) {
            if (up.contains("SLAYER QUEST COMPLETE")) { say("Slayer complete"); return; }
            if (up.contains("NICE! SLAYER BOSS SLAIN")) { say("Boss slain");    return; }
            if (up.contains("SLAYER QUEST STARTED"))  { say("Quest started");   return; }
        }
    }

    /** Speak, de-duping the exact same phrase fired twice within a second (Hypixel doubles some lines). */
    private static void say(String phrase) {
        long now = System.currentTimeMillis();
        if (phrase.equals(lastSpoken) && now - lastSpokenMs < 1000) return;
        lastSpoken = phrase;
        lastSpokenMs = now;
        Tts.speak(phrase);
    }
}
