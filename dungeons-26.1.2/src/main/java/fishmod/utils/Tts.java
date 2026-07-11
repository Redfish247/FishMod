package fishmod.utils;

import fishmod.utils.config.values.FishSettings;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny cross-platform text-to-speech helper. Speaks short callouts through the OS's built-in TTS so
 * you can hear alerts without watching the HUD: Windows via PowerShell's System.Speech, macOS via
 * {@code say}, Linux via {@code spd-say}/{@code espeak}.
 *
 * All speech is dispatched on a single daemon thread and never blocks the game; text is passed as a
 * process argument (or stdin on Windows) so there's no shell-injection surface. If no TTS engine is
 * present the call simply does nothing.
 */
public final class Tts {

    private Tts() {}

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FishMod-TTS");
        t.setDaemon(true);
        return t;
    });

    private static volatile long lastSpeakMs = 0;
    private static final long MIN_GAP_MS = 250; // collapse bursts so callouts don't stack into noise

    /** Speak a line (formatting codes stripped). No-op when TTS is disabled or text is empty. */
    public static void speak(String text) {
        if (!FishSettings.ttsEnabled || text == null) return;
        String clean = text.replaceAll("§.", "").replaceAll("[\"'`$]", "").trim();
        if (clean.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastSpeakMs < MIN_GAP_MS) return;
        lastSpeakMs = now;
        POOL.submit(() -> run(clean));
    }

    private static void run(String text) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                // Feed the phrase over stdin so we never have to quote/escape it into the command line.
                String rate = String.valueOf(Math.max(-10, Math.min(10, FishSettings.ttsRate)));
                String ps = "Add-Type -AssemblyName System.Speech;"
                        + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;"
                        + "$s.Rate = " + rate + ";"
                        + "$s.Speak([Console]::In.ReadToEnd())";
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", ps)
                        .redirectErrorStream(true).start();
                try (OutputStream os2 = p.getOutputStream()) {
                    os2.write(text.getBytes(StandardCharsets.UTF_8));
                }
                p.waitFor();
            } else if (os.contains("mac")) {
                new ProcessBuilder("say", text).start().waitFor();
            } else {
                // Linux: prefer speech-dispatcher, fall back to espeak.
                if (!tryRun(new ProcessBuilder("spd-say", "-w", text)))
                    tryRun(new ProcessBuilder("espeak", text));
            }
        } catch (Exception ignored) {
            // No TTS engine available, or it failed — stay silent.
        }
    }

    private static boolean tryRun(ProcessBuilder pb) {
        try { pb.start().waitFor(); return true; }
        catch (Exception e) { return false; }
    }
}
