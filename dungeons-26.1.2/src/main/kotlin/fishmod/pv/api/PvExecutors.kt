package fishmod.pv.api

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory

// Shared daemon pools for the pv caches.
object PvExecutors {
    @JvmField
    val SCHEDULER: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(daemon("FishPV-Refresh"))

    @JvmField
    val IO: ExecutorService = Executors.newFixedThreadPool(4, daemon("FishPV-IO"))

    @JvmStatic
    fun daemon(name: String): ThreadFactory = ThreadFactory { r -> Thread(r, name).apply { isDaemon = true } }
}
