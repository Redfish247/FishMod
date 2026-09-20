package fishmod.utils

object PingTracker {

    @Volatile
    private var latestMs: Int = -1
    @Volatile
    private var updatedAt: Long = 0

    @JvmStatic
    fun pushRtt(rttMs: Long) {
        if (rttMs < 0 || rttMs > 5_000) return
        val rtt = rttMs.toInt()
        val prev = latestMs
        latestMs = if (prev > 0) (rtt + prev) / 2 else rtt
        updatedAt = System.currentTimeMillis()
    }

    @JvmStatic
    fun latest(): Int {
        if (latestMs < 0) return -1
        if (System.currentTimeMillis() - updatedAt > 60_000) return -1
        return latestMs
    }

    @JvmStatic
    fun reset() {
        latestMs = -1
        updatedAt = 0
    }
}
