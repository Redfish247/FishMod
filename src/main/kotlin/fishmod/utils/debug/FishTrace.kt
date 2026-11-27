package fishmod.utils.debug

import net.minecraft.client.Minecraft
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Category trace log for dev debugging: /fmdev trace <cat|all|off|chat>. Writes logs/fishmod-trace.log. */
object FishTrace {

    val CATEGORIES = listOf("scan", "room", "door", "map", "score", "boss", "p3", "term", "dragon", "solver", "secret", "chat", "party", "run", "loot", "net", "loc", "fishing", "slayer", "kuudra", "diana", "mining", "garden", "rift")

    private val enabled = ConcurrentHashMap.newKeySet<String>()
    @Volatile var toChat = false
    @Volatile private var any = false
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "FishMod-Trace").apply { isDaemon = true } }
    private val logFile: File by lazy { File(Minecraft.getInstance().gameDirectory, "logs/fishmod-trace.log").also { it.parentFile.mkdirs() } }
    private val lastByKey = ConcurrentHashMap<String, String>()

    @JvmStatic fun on(cat: String): Boolean = any && enabled.contains(cat)

    /** Lazy message: the lambda only runs when the category is on. */
    @JvmStatic
    inline fun log(cat: String, msg: () -> String) {
        if (on(cat)) emit(cat, msg())
    }

    /** Logs only when the value for [key] changed since last time (state transitions, no tick spam). */
    inline fun change(cat: String, key: String, value: () -> String) {
        if (!on(cat)) return
        val v = value()
        if (lastChanged(cat, key, v)) emit(cat, "$key -> $v")
    }

    fun lastChanged(cat: String, key: String, v: String): Boolean = lastByKey.put("$cat/$key", v) != v

    fun emit(cat: String, msg: String) {
        val line = "[${LocalDateTime.now().format(timeFmt)}] [$cat] $msg"
        FishDebug.LOGGER.info("[FishTrace] {}", line)
        writer.execute { try { logFile.appendText(line + "\n") } catch (_: Throwable) {} }
        if (toChat) Minecraft.getInstance().execute {
            try { fishmod.utils.FishUtil.addChatMessage(net.minecraft.network.chat.Component.literal("§8[T] §7[$cat] §f$msg")) } catch (_: Throwable) {}
        }
    }

    fun set(cat: String, value: Boolean) {
        if (value) enabled.add(cat) else enabled.remove(cat)
        any = enabled.isNotEmpty()
        lastByKey.clear()
    }

    fun allOn() { enabled.addAll(CATEGORIES); any = true; lastByKey.clear() }
    fun allOff() { enabled.clear(); any = false; lastByKey.clear() }
    fun status(): String = if (enabled.isEmpty()) "trace off" else "trace on: " + enabled.sorted().joinToString(" ") + if (toChat) " (chat)" else ""
    fun logPath(): String = logFile.absolutePath
}
