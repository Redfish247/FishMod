package fishmod.features.slayers

import fishmod.utils.config.values.FishSettings

/**
 * Boss kill timer. Uses [System.nanoTime] (monotonic, ~µs) rather than tick counts so a laggy
 * server doesn't distort the reading.
 *
 * Two start modes ([FishSettings.slayerTimerStartMode]):
 *  - **Spawned**       — clock starts the instant the scoreboard flips to `Slay the boss!`
 *    ([SlayerManager] calls [onBossSpawned]).
 *  - **Fully Spawned** — clock starts when [SlayerBossDetector] first binds the real boss entity
 *    (roughly when its rise animation ends and it becomes attackable), i.e. [onBossEntityBound].
 *
 * Stops on the scoreboard `Boss slain!` line (or the `SLAYER QUEST COMPLETE!` chat if the board
 * skipped it on an instant kill). The result is held for display until the next quest resets it.
 *
 * Full-cycle timer
 * ---------------
 * Separately from the spawn→kill fight timer, [onBossKilled] tracks the wall-clock gap between one
 * boss kill and the next (fight + loot + walk + refill + next fight) — the real grind cadence. It
 * survives same-tier auto-slayer restarts and only clears on [reset] (quest change / world change).
 */
object SlayerTimer {

    private var startNanos = 0L
    private var pendingFullSpawn = false

    private var lastResultSeconds = -1.0
    private var lastWasPb = false

    // full-cycle (kill -> kill) timer
    private var lastKillNanos = 0L
    private var lastCycleSeconds = -1.0
    private var lastKilledCallMs = 0L

    @JvmStatic fun init() { /* state only; SlayerManager drives all transitions */ }

    private fun fullSpawnMode() = FishSettings.slayerTimerStartMode.equals("Fully Spawned", ignoreCase = true)

    @JvmStatic fun running(): Boolean = startNanos != 0L

    @JvmStatic
    fun elapsedSeconds(): Double =
        if (startNanos == 0L) 0.0 else (System.nanoTime() - startNanos) / 1_000_000_000.0

    @JvmStatic fun lastResultSeconds(): Double = lastResultSeconds
    @JvmStatic fun lastWasPb(): Boolean = lastWasPb
    @JvmStatic fun hasResult(): Boolean = lastResultSeconds >= 0.0

    /** Scoreboard flipped to "Slay the boss!". */
    @JvmStatic
    fun onBossSpawned() {
        lastResultSeconds = -1.0
        lastWasPb = false
        if (fullSpawnMode()) {
            pendingFullSpawn = true
            startNanos = 0L
        } else {
            startNanos = System.nanoTime()
            pendingFullSpawn = false
        }
    }

    /** Detector bound the boss entity — only meaningful in "Fully Spawned" mode. */
    @JvmStatic
    fun onBossEntityBound() {
        if (pendingFullSpawn && startNanos == 0L) {
            startNanos = System.nanoTime()
            pendingFullSpawn = false
        }
    }

    /** Boss died. Returns elapsed seconds, or -1 if the timer wasn't running. */
    @JvmStatic
    fun onBossSlain(): Double {
        if (startNanos == 0L) return -1.0
        val secs = (System.nanoTime() - startNanos) / 1_000_000_000.0
        startNanos = 0L
        pendingFullSpawn = false
        return secs
    }

    @JvmStatic
    fun publishResult(seconds: Double, wasPb: Boolean) {
        lastResultSeconds = seconds
        lastWasPb = wasPb
    }

    // ---------------------------------------------------------------- full-cycle timer

    /** A boss just died. May be called from both the scoreboard and the chat path for one kill —
     *  a 3s guard collapses that to a single cycle tick. */
    @JvmStatic
    fun onBossKilled() {
        val now = System.nanoTime()
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastKilledCallMs < 3_000L) return
        lastKilledCallMs = nowMs
        if (lastKillNanos != 0L) lastCycleSeconds = (now - lastKillNanos) / 1_000_000_000.0
        lastKillNanos = now
    }

    /** Duration of the last completed kill→kill cycle, or -1 if fewer than two kills recorded. */
    @JvmStatic fun lastCycleSeconds(): Double = lastCycleSeconds

    /** Live time since the last kill (0 if none yet) — the current cycle in progress. */
    @JvmStatic
    fun cycleElapsedSeconds(): Double =
        if (lastKillNanos == 0L) 0.0 else (System.nanoTime() - lastKillNanos) / 1_000_000_000.0

    @JvmStatic fun hasCycle(): Boolean = lastKillNanos != 0L

    @JvmStatic
    fun reset() {
        startNanos = 0L
        pendingFullSpawn = false
        lastResultSeconds = -1.0
        lastWasPb = false
        lastKillNanos = 0L
        lastCycleSeconds = -1.0
        lastKilledCallMs = 0L
    }
}
