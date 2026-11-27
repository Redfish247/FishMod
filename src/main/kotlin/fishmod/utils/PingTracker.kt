package fishmod.utils

object PingTracker {

    init {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
        // Nothing else sends ping requests in the play state, so without this the tracker is never fed and
        // callers fall back to the tab-list latency, which Hypixel pins at 1.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { mc ->
            try {
                val conn = mc.connection ?: return@register
                if (mc.player == null) return@register
                val now = System.currentTimeMillis()
                if (now - lastSent < 2_000) return@register
                lastSent = now
                sent++
                if (sent == 6 && pongs == 0) fishmod.utils.debug.FishDebug.LOGGER.info("[PingTracker] 6 ping requests sent, no pong response: server ignores play-state pings")
                conn.send(net.minecraft.network.protocol.ping.ServerboundPingRequestPacket(net.minecraft.util.Util.getMillis()))
            } catch (t: Throwable) {
                fishmod.utils.debug.FishDiag.fail("PingTracker.1", "ping request send failed", t)
            }
        }
    }

    private var tickCounter = 0

    @JvmStatic
    fun init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (++tickCounter < 40) return@register
            tickCounter = 0
            if (mc.player == null) return@register
            try {
                mc.connection?.send(net.minecraft.network.protocol.ping.ServerboundPingRequestPacket(net.minecraft.util.Util.getMillis()))
            } catch (t: Throwable) {
                fishmod.utils.debug.FishDiag.fail("PingTracker.1", "ping request send failed", t)
            }
        }
    }

    @Volatile
    private var latestMs: Int = -1
    private var lastSent: Long = 0
    private var sent = 0
    @Volatile
    private var pongs = 0

    @JvmStatic
    fun start() {}

    @Volatile
    private var updatedAt: Long = 0

    @JvmStatic
    fun pushRtt(rttMs: Long) {
        if (rttMs < 0 || rttMs > 5_000) return
        pongs++
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
        sent = 0
        pongs = 0
    }
}
