package fishmod.features.slayers

import fishmod.utils.config.values.FishSettings

object SlayerTimer {

    private var startNanos = 0L
    private var pendingFullSpawn = false

    private var lastResultSeconds = -1.0
    private var lastWasPb = false

    private var lastKillNanos = 0L
    private var lastCycleSeconds = -1.0
    private var lastKilledCallMs = 0L

    private fun fullSpawnMode() = FishSettings.slayerTimerStartMode.equals("Fully Spawned", ignoreCase = true)

    @JvmStatic fun running(): Boolean = startNanos != 0L

    @JvmStatic
    fun elapsedSeconds(): Double =
        if (startNanos == 0L) 0.0 else (System.nanoTime() - startNanos) / 1_000_000_000.0

    @JvmStatic fun lastResultSeconds(): Double = lastResultSeconds
    @JvmStatic fun lastWasPb(): Boolean = lastWasPb
    @JvmStatic fun hasResult(): Boolean = lastResultSeconds >= 0.0

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

    @JvmStatic
    fun onBossEntityBound() {
        if (pendingFullSpawn && startNanos == 0L) {
            startNanos = System.nanoTime()
            pendingFullSpawn = false
        }
    }

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

    @JvmStatic
    fun onBossKilled() {
        val now = System.nanoTime()
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastKilledCallMs < 3_000L) return
        lastKilledCallMs = nowMs
        if (lastKillNanos != 0L) lastCycleSeconds = (now - lastKillNanos) / 1_000_000_000.0
        lastKillNanos = now
    }

    @JvmStatic fun lastCycleSeconds(): Double = lastCycleSeconds

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
