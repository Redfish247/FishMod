package fishmod.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import fishmod.utils.debug.FishDiag
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
                val done = executor.awaitTermination(3, TimeUnit.SECONDS)
                FishDiag.check(done, "IoExecutor.2") { "IO executor did not finish within 3s on shutdown, writes may be lost" }
            } catch (e: InterruptedException) {
                FishDiag.fail("IoExecutor.1", "interrupted while flushing IO on shutdown", e)
            }
        }
    }

    override fun execute(command: Runnable) {
        val wrapped = Runnable {
            try {
                command.run()
            } catch (t: Throwable) {
                FishDiag.fail("IoExecutor.3", "background IO task threw", t)
            }
        }
        if (executor.isShutdown) wrapped.run() else executor.execute(wrapped)
    }
}
