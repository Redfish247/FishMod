package fishmod.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ServerAddress;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Periodic, lightweight RTT measurement: opens a plain TCP socket to the connected server, times
 * the connect handshake (one round-trip), and feeds the result to {@link PingTracker}. Runs on a
 * background thread on a 15s interval — no Netty connection, no event-loop work, no main-thread
 * cost (the previous {@code MultiplayerServerListPinger}-based approach was the cause of the frame
 * drops users were seeing after 10.48).
 */
public final class PingRefresher {
    private PingRefresher() {}

    private static long lastRefreshAt = 0;
    private static volatile boolean inFlight = false;
    private static final long INTERVAL_MS = 3_000;
    private static final int CONNECT_TIMEOUT_MS = 4_000;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            long now = System.currentTimeMillis();
            if (inFlight || now - lastRefreshAt < INTERVAL_MS) return;
            ServerInfo si = mc.getCurrentServerEntry();
            if (si == null || si.address == null) return;
            ServerAddress addr = ServerAddress.parse(si.address);
            if (addr == null) return;
            lastRefreshAt = now;
            inFlight = true;
            Thread t = new Thread(() -> measure(addr.getAddress(), addr.getPort()), "FishMod-PingProbe");
            t.setDaemon(true);
            t.start();
        });
    }

    private static void measure(String host, int port) {
        try (Socket s = new Socket()) {
            long t0 = System.nanoTime();
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            long rttMs = (System.nanoTime() - t0) / 1_000_000L;
            PingTracker.pushRtt(rttMs);
        } catch (Exception ignored) {
        } finally {
            inFlight = false;
        }
    }
}
