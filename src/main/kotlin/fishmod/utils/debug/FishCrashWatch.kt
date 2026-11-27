package fishmod.utils.debug

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property

/** Global safety net: reports any logged error or uncaught exception that touches FishMod code, even from unguarded handlers. */
object FishCrashWatch {

    private const val OWN_LOGGER = "fishmod"
    @Volatile private var installed = false

    @JvmStatic
    fun init() {
        if (installed) return
        installed = true
        try { installAppender() } catch (t: Throwable) { FishDebug.LOGGER.warn("[FishCrashWatch] log appender not installed: {}", t.toString()) }
        try { installUncaught() } catch (t: Throwable) { FishDebug.LOGGER.warn("[FishCrashWatch] uncaught handler not installed: {}", t.toString()) }
    }

    private fun installAppender() {
        val root = LogManager.getRootLogger() as org.apache.logging.log4j.core.Logger
        val appender = object : AbstractAppender("FishModCrashWatch", null, null, true, Property.EMPTY_ARRAY) {
            override fun append(event: LogEvent) {
                try { onEvent(event) } catch (_: Throwable) {}
            }
        }
        appender.start()
        root.addAppender(appender)
    }

    private fun installUncaught() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            try {
                if (fishFrame(t) != null) {
                    report("Uncaught", "uncaught exception on thread '${thread.name}'", t)
                    FishDiag.flushNow()
                }
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, t)
        }
    }

    private fun onEvent(event: LogEvent) {
        if (event.loggerName == OWN_LOGGER) return
        val level = event.level
        val thrown = event.thrown
        val msg = event.message?.formattedMessage ?: ""
        if (level.isMoreSpecificThan(Level.ERROR) && thrown != null && fishFrame(thrown) != null && thrownByFishMod(event, thrown)) {
            report("Log", "[${event.loggerName}] $msg", thrown)
            if (level == Level.FATAL) FishDiag.flushNow()
            return
        }
        if (level.isMoreSpecificThan(Level.WARN) && msg.contains("fishmod", ignoreCase = true) &&
            event.loggerName.contains("mixin", ignoreCase = true)) {
            FishDiag.fail("Log.mixin", "[${event.loggerName}] ${msg.take(300)}")
        }
    }

    private fun thrownByFishMod(event: LogEvent, t: Throwable): Boolean {
        if (event.loggerName.contains("fishmod", ignoreCase = true)) return true
        var root: Throwable = t
        var depth = 0
        while (root.cause != null && root.cause !== root && depth++ < 8) root = root.cause!!
        val top = root.stackTrace.firstOrNull { !it.className.startsWith("java.") && !it.className.startsWith("jdk.") && !it.className.startsWith("sun.") } ?: return true
        return top.className.startsWith("fishmod.") || top.className.startsWith("net.minecraft.")
    }

    private fun report(prefix: String, message: String, t: Throwable) {
        val where = fishFrame(t)
        val code = "$prefix.${where?.className?.substringAfterLast('.') ?: "?"}.${where?.methodName ?: "?"}"
        FishDiag.fail(code, message.take(300), t)
    }

    /** First stack frame in FishMod code along the cause chain, or null when the error doesn't involve FishMod. */
    private fun fishFrame(t: Throwable): StackTraceElement? {
        var c: Throwable? = t
        var depth = 0
        while (c != null && depth++ < 8) {
            c.stackTrace.firstOrNull { it.className.startsWith("fishmod.") && !it.className.startsWith("fishmod.utils.debug.") }?.let { return it }
            c = c.cause
        }
        return null
    }
}
