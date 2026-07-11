package fishmod.utils;

/**
 * Live ping (RTT) from the vanilla ping protocol. During play, Minecraft's own {@code PingMeasurer}
 * periodically sends a ping request (the value that drives the F3 ping graph); the server replies
 * with a {@code PingResultS2CPacket} that echoes the {@code startTime} we sent. We catch that pong on
 * the network channel and compute {@code now - startTime} for a true client→server→client round trip
 * — the same approach Odin uses, and accurate end-to-end even on proxied/anycast servers like Hypixel
 * (unlike a TCP-edge probe or a clock-skew estimate, which can read far too low).
 */
public final class PingTracker {
    private PingTracker() {}

    private static volatile int latestMs = -1;
    private static volatile long updatedAt = 0;

    /** Push a measured round-trip time in ms (from the ping/pong round trip). */
    public static void pushRtt(long rttMs) {
        if (rttMs < 0 || rttMs > 5_000) return; // implausible — ignore
        int rtt = (int) rttMs;
        int prev = latestMs;
        latestMs = prev > 0 ? (rtt + prev) / 2 : rtt; // light EMA so it doesn't jitter
        updatedAt = System.currentTimeMillis();
    }

    /** Latest RTT estimate in ms, or -1 if not measured / stale (>60s old). */
    public static int latest() {
        if (latestMs < 0) return -1;
        if (System.currentTimeMillis() - updatedAt > 60_000) return -1;
        return latestMs;
    }

    public static void reset() { latestMs = -1; updatedAt = 0; }
}
