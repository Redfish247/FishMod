package fishmod.utils;

/**
 * Live ping (RTT) estimate from server keep-alive timestamps. Vanilla servers (and Hypixel) set the
 * keep-alive id to {@code System.currentTimeMillis()} when sending; we compute {@code now - id} on
 * receipt, double it (out + back), and EMA-smooth so it doesn't jitter. Refreshes every 15s as the
 * server tick-fires keep-alives.
 */
public final class PingTracker {
    private PingTracker() {}

    private static volatile int latestMs = -1;
    private static volatile long updatedAt = 0;

    /** Push a measured round-trip time directly (e.g. from a TCP handshake). */
    public static void pushRtt(long rttMs) {
        if (rttMs < 0 || rttMs > 5_000) return;
        int rtt = (int) rttMs;
        int prev = latestMs;
        latestMs = prev > 0 ? (rtt + prev) / 2 : rtt;
        updatedAt = System.currentTimeMillis();
    }

    /** Push a one-way-latency estimate (server send → our receive, in ms). */
    public static void pushOneWay(long oneWayMs) {
        if (oneWayMs < 0 || oneWayMs > 5_000) return; // looks like the id isn't a timestamp — ignore
        int rtt = (int) Math.min(2_000, oneWayMs * 2);
        int prev = latestMs;
        latestMs = prev > 0 ? (rtt + prev) / 2 : rtt; // light EMA
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
