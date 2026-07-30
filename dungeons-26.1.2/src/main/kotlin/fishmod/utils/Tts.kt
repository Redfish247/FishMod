package fishmod.utils

import fishmod.utils.config.values.FishSettings
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Tiny cross-platform text-to-speech helper. Speaks short callouts through the OS's built-in TTS so
 * you can hear alerts without watching the HUD: Windows via PowerShell's System.Speech, macOS via
 * `say`, Linux via `spd-say`/`espeak`.
 *
 * All speech is dispatched on a single daemon thread and never blocks the game; text is passed as a
 * process argument (or stdin on Windows) so there's no shell-injection surface. If no TTS engine is
 * present the call simply does nothing.
 */
object Tts {

    private val POOL = Executors.newSingleThreadExecutor { r ->
        val t = Thread(r, "FishMod-TTS")
        t.isDaemon = true
        t
    }

    @Volatile
    private var lastSpeakMs: Long = 0
    private const val MIN_GAP_MS = 250L // collapse bursts so callouts don't stack into noise

    /** Speak a line (formatting codes stripped). No-op when TTS is disabled or text is empty. */
    @JvmStatic
    fun speak(text: String?) {
        if (!FishSettings.ttsEnabled || text == null) return
        val clean = text.replace(Regex("§."), "").replace(Regex("[\"'`\$]"), "").trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastSpeakMs < MIN_GAP_MS) return
        lastSpeakMs = now
        POOL.submit { run(clean) }
    }

    private fun run(text: String) {
        val os = System.getProperty("os.name", "").lowercase()
        try {
            if (os.contains("win")) {
                // Feed the phrase over stdin so we never have to quote/escape it into the command line.
                val rate = Math.max(-10, Math.min(10, FishSettings.ttsRate)).toString()
                val ps = "Add-Type -AssemblyName System.Speech;"
                    .plus("\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;")
                    .plus("\$s.Rate = " + rate + ";")
                    .plus("\$s.Speak([Console]::In.ReadToEnd())")
                val p = ProcessBuilder("powershell", "-NoProfile", "-Command", ps)
                    .redirectErrorStream(true).start()
                p.outputStream.use { os2 ->
                    os2.write(text.toByteArray(StandardCharsets.UTF_8))
                }
                p.waitFor()
            } else if (os.contains("mac")) {
                ProcessBuilder("say", text).start().waitFor()
            } else {
                // Linux: prefer speech-dispatcher, fall back to espeak.
                if (!tryRun(ProcessBuilder("spd-say", "-w", text)))
                    tryRun(ProcessBuilder("espeak", text))
            }
        } catch (ignored: Exception) {
            // No TTS engine available, or it failed — stay silent.
        }
    }

    private fun tryRun(pb: ProcessBuilder): Boolean {
        return try {
            pb.start().waitFor()
            true
        } catch (e: Exception) {
            false
        }
    }
}
