package fishmod.utils

object PingTracker {

    init {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    private var tickCounter = 0

    // Sends a ping request every 2s so the pong RTT keeps the tab ping live (vanilla only does this with F3 open)
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
