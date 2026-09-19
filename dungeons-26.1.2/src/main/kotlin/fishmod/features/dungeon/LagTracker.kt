package fishmod.features.dungeon

import fishmod.utils.TabListCache
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import java.util.regex.Pattern

/** Measures seconds lost to lag by comparing wall-clock time to server-tick count. */
object LagTracker {

    // Colour-code-free substring of the Mort NPC line so chat mods that recolour/reformat it don't
    // break the trigger. Earliest and most accurate start (from t=0), when dialogue is shown.
    private const val RUN_START_FRAGMENT =
        "I found this map when I first entered the dungeon"

    private val RUN_END_PATTERN: Pattern =
        Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+)\\s*(\\(NEW RECORD!\\))?$")

    // Dungeon tab-list / sidebar run clock: "Time: 1m 4s" / "Time Elapsed: 1m 4s". This ticks up
    // only once the run actually starts (stays at "0s" through the pre-run lobby), so it's the
    // universal start signal for players who have all NPC dialogue turned off.
    private val RUN_TIME: Pattern =
        Pattern.compile("^ ?Time(?: Elapsed)?: ((?:\\d+h ?)?(?:\\d+m ?)?\\d+s)$")
    private val HMS: Pattern = Pattern.compile("(\\d+)([hms])")

    private var active = false
    private var ended = false      // run finished this dungeon instance — don't auto-restart until ON_LOCATION_CHANGE
    private var startMs: Long = 0
    private var ticks: Long = 0

    private fun start() {
        startMs = System.currentTimeMillis()
        ticks = 0
        active = true
    }

    /** Returns seconds of accumulated lag for the current run, or 0 if no run is active. */
    @JvmStatic
    fun getCurrentLag(): Double {
        if (!active || startMs == 0L) return 0.0
        val wallSec = (System.currentTimeMillis() - startMs) / 1000.0
        val tickSec = ticks * 0.05
        return maxOf(0.0, wallSec - tickSec)
    }

    /** Parsed run-clock seconds from the tab list, or -1 if no "Time:" line is present. */
    private fun scoreboardRunSeconds(): Int {
        for (entry in TabListCache.entries) {
            val m = RUN_TIME.matcher(entry.stripped.trim())
            if (!m.find()) continue
            var total = 0
            val h = HMS.matcher(m.group(1))
            while (h.find()) {
                val n = h.group(1).toIntOrNull() ?: 0
                total += when (h.group(2)) { "h" -> n * 3600; "m" -> n * 60; else -> n }
            }
            return total
        }
        return -1
    }

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { message ->
            val s = message.string

            if (!active && !ended && s.contains(RUN_START_FRAGMENT)) {
                start()

            } else if (active && RUN_END_PATTERN.matcher(s).find()) {
                val wallSec = (System.currentTimeMillis() - startMs) / 1000.0
                val tickSec = ticks * 0.05
                val lag = wallSec - tickSec
                active = false
                ended = true

                if (FishSettings.sendLagToParty && lag >= 0.1) {
                    val avgTps = if (wallSec > 0) (ticks / wallSec).coerceIn(0.0, 20.0) else 20.0
                    val msg = String.format("%.2fs lost to lag (avg %.1f TPS).", lag, avgTps)
                    fishmod.utils.ChatQueue.enqueue("pc " + msg)
                }
            }
            false
        }

        Events.ON_SERVER_TICK.register {
            // Fallback start for players with NPC dialogue off: the scoreboard run clock has begun
            // counting. Never a silent no-op, and it doesn't fire during the pre-run lobby (0s).
            if (!active && !ended && scoreboardRunSeconds() > 0) start()
            if (active) ticks++
            false
        }

        Events.ON_LOCATION_CHANGE.register { _ ->
            active = false
            ended = false
            startMs = 0
            ticks = 0
            false
        }
    }
}
