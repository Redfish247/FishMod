package fishmod.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object IoExecutor : Executor {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FishMod-IO").apply { isDaemon = true }
    }

    @JvmStatic
    fun init() {
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            executor.shutdown()
            try {
                executor.awaitTermination(3, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
            }
        }
    }

    override fun execute(command: Runnable) {
        if (executor.isShutdown) command.run() else executor.execute(command)
    }
}
