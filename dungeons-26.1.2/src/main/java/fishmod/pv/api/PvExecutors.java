package fishmod.pv.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

// Shared daemon pools for the pv caches.
public final class PvExecutors {
	public static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(daemon("BetterPV-Refresh"));
	public static final ExecutorService IO = Executors.newFixedThreadPool(4, daemon("BetterPV-IO"));

	private PvExecutors() {
	}

	public static ThreadFactory daemon(String name) {
		return r -> {
			Thread t = new Thread(r, name);
			t.setDaemon(true);
			return t;
		};
	}
}
