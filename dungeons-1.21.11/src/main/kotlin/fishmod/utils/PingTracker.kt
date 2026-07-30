package fishmod.utils

/** Live ping (RTT): catches the vanilla PingResultS2CPacket pong and computes now - startTime for a true round trip, accurate even on proxied servers like Hypixel. */
object PingTracker {

    @Volatile
    private var latestMs: Int = -1
    @Volatile
    private var updatedAt: Long = 0

    /** Push a measured round-trip time in ms (from the ping/pong round trip). */
    @JvmStatic
    fun pushRtt(rttMs: Long) {
        if (rttMs < 0 || rttMs > 5_000) return // implausible — ignore
        val rtt = rttMs.toInt()
        val prev = latestMs
        latestMs = if (prev > 0) (rtt + prev) / 2 else rtt // light EMA so it doesn't jitter
        updatedAt = System.currentTimeMillis()
    }

    /** Latest RTT estimate in ms, or -1 if not measured / stale (>60s old). */
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
