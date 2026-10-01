package fishmod.utils.debug

import fishmod.utils.config.values.FishSettings
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Silent-until-broken diagnostics. Every call site has a unique code (`ClassName.N`) so a pasted
 * report points straight at the line. Nothing is logged unless something actually goes wrong.
 */
object FishDiag {

    class Entry(val code: String, val message: String, val error: String?, val time: String, val context: String)

    private const val MAX_ENTRIES = 300
    private const val FULL_LOGS_PER_CODE = 3
    private const val CHAT_COOLDOWN_MS = 60_000L

    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val recent = ArrayDeque<Entry>()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "FishMod-Diag").apply { isDaemon = true } }
    private val logFile: File by lazy {
        File(Minecraft.getInstance().gameDirectory, "logs/fishmod-debug.log").also { it.parentFile.mkdirs() }
    }
    @Volatile private var lastChatAt = 0L
    @Volatile private var headerWritten = false

    @JvmStatic
    fun fail(code: String, message: String) = record(code, message, null)

    @JvmStatic
    fun fail(code: String, message: String, t: Throwable?) = record(code, message, t)

    /** Logs [message] when [ok] is false. Returns [ok] so it can gate an early return. */
    inline fun check(ok: Boolean, code: String, message: () -> String): Boolean {
        if (!ok) fail(code, message())
        return ok
    }

    /** Logs when [value] is null. Returns [value] unchanged. */
    inline fun <T> notNull(value: T?, code: String, message: () -> String): T? {
        if (value == null) fail(code, message())
        return value
    }

    /** Runs [block]; on throw, logs and returns null instead of crashing the caller. */
    inline fun <T> guard(code: String, message: String, block: () -> T): T? =
        try { block() } catch (t: Throwable) { fail(code, message, t); null }

    @JvmStatic
    fun count(): Int = counts.size

    @JvmStatic
    fun clear() {
        counts.clear()
        synchronized(recent) { recent.clear() }
    }

    private fun record(code: String, message: String, t: Throwable?) {
        if (isTransientNetwork(t)) return
        try {
            val n = counts.computeIfAbsent(code) { AtomicInteger() }.incrementAndGet()
            if (n > FULL_LOGS_PER_CODE) {
                if (n == FULL_LOGS_PER_CODE + 1) Debug.LOGGER.warn("[FishDiag {}] repeating, further hits only counted", code)
                return
            }
            val entry = Entry(code, message, t?.let { stackSummary(it) }, LocalDateTime.now().format(timeFmt), context())
            synchronized(recent) {
                recent.addLast(entry)
                while (recent.size > MAX_ENTRIES) recent.removeFirst()
            }
            if (t != null) Debug.LOGGER.warn("[FishDiag {}] {}", code, message, t)
            else Debug.LOGGER.warn("[FishDiag {}] {}", code, message)
            writer.execute { appendToFile(entry) }
            if (n == 1) notifyChat(code)
        } catch (_: Throwable) {
        }
    }

    private fun context(): String = try {
        val loc = fishmod.utils.Location.getCurrentLocation()
        "loc=$loc thread=${Thread.currentThread().name}"
    } catch (_: Throwable) {
        "thread=${Thread.currentThread().name}"
    }

    private fun stackSummary(t: Throwable): String {
        val sb = StringBuilder(t.toString())
        t.stackTrace.take(6).forEach { sb.append("\n    at ").append(it) }
        t.cause?.let { sb.append("\n  caused by ").append(it) }
        return sb.toString()
    }

    private fun appendToFile(e: Entry) {
        try {
            if (!headerWritten) {
                headerWritten = true
                logFile.appendText("\n==== FishMod ${modVersion()} | MC ${mcVersion()} | session ${LocalDateTime.now()} ====\n")
            }
            logFile.appendText(format(e) + "\n")
        } catch (_: Throwable) {
        }
    }

    private fun format(e: Entry): String {
        val sb = StringBuilder("[${e.time}] ${e.code} x${counts[e.code]?.get() ?: 1}: ${e.message} (${e.context})")
        e.error?.let { sb.append("\n  ").append(it) }
        return sb.toString()
    }

    private fun notifyChat(code: String) {
        if (!FishSettings.debugReports || !FishSettings.debugChatNotices) return
        val now = System.currentTimeMillis()
        if (now - lastChatAt < CHAT_COOLDOWN_MS) return
        lastChatAt = now
        val msg = Component.literal("§8[FishMod] §7Something didn't work right (§e$code§7). ")
            .append(Component.literal("§b§n[Copy report]").withStyle { s ->
                s.withClickEvent(ClickEvent.RunCommand("/fm debug"))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("Copies a debug report to send to RedFish2471")))
            })
        fishmod.utils.Misc.addChatMessage(msg)
    }

    /** Full paste-able report: header, per-code counts, and the recent entries. */
    @JvmStatic
    fun buildReport(): String {
        val sb = StringBuilder()
        sb.append("FishMod debug report\n")
        sb.append("Version: ${modVersion()} | MC ${mcVersion()} | ${LocalDateTime.now()}\n")
        sb.append("Location: ").append(context()).append('\n')
        sb.append("Codes (${counts.size}):\n")
        counts.entries.sortedByDescending { it.value.get() }.forEach { sb.append("  ${it.key} x${it.value.get()}\n") }
        sb.append("Recent:\n")
        val snapshot = synchronized(recent) { recent.toList() }
        snapshot.takeLast(60).forEach { sb.append(format(it)).append('\n') }
        return sb.toString()
    }

    @JvmStatic
    fun summaryLines(limit: Int = 10): List<String> =
        counts.entries.sortedByDescending { it.value.get() }.take(limit).map { "${it.key} x${it.value.get()}" }

    @JvmStatic
    fun logPath(): String = logFile.absolutePath

    // Timeouts / dropped connections are the network, not a bug.
    private fun isTransientNetwork(t: Throwable?): Boolean {
        var c = t
        var depth = 0
        while (c != null && depth++ < 8) {
            if (c is java.net.http.HttpTimeoutException || c is java.net.ConnectException ||
                c is java.net.SocketTimeoutException || c is java.net.UnknownHostException ||
                c is java.nio.channels.ClosedChannelException) return true
            if (c is java.io.IOException && c.message?.contains("closed", ignoreCase = true) == true) return true
            c = c.cause
        }
        return false
    }

    private fun modVersion(): String = try {
        net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("fishmod-dungeons")
            .map { it.metadata.version.friendlyString }.orElse("?")
    } catch (_: Throwable) { "?" }

    private fun mcVersion(): String = try { SharedConstants.getCurrentVersion().name() } catch (_: Throwable) { "?" }
}
