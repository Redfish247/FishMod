package fishmod.features.slayers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fishmod.features.croesus.CroesusPrices
import fishmod.utils.Constants
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.networth.ItemsDb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Type
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Slayer drop-value / coins-per-hour tracker — the FishMod equivalent of SkyHanni's
 * "<Boss> Profit Tracker" panel.
 *
 * What it counts
 * --------------
 * Drops are taken from the chat lines Hypixel actually prints:
 *  - the `RARE DROP! <item>` / `PET DROP! <item>` family (always shown), and
 *  - sack-pickup lines `+<n> <item>` (shown only if you have Hypixel "Sack Notifications" on —
 *    without them the bulk flesh/shard drops go to sacks silently and can't be seen client-side).
 * Each drop is stored per slayer type by its display name; value = `CroesusPrices.price(idFor(name))`
 * × count at render time, so switching price mode (Bazaar / BIN / NPC) reprices everything live.
 * Direct coin lines and items with no price id are still listed (with a "?" value).
 *
 * Spawn cost
 * ----------
 * `bosses × [FishSettings.slayerProfitSpawnCost]` is subtracted from the gross (set the per-boss
 * cost yourself — it's the combat-XP grind's opportunity cost and varies wildly by setup, so there's
 * no honest default; 0 = don't show the line).
 *
 * Active time & the idle rule
 * ---------------------------
 * `activeMs` accrues only while [SlayerManager.isActiveSlayer]. If no drop or kill happens for
 * [FishSettings.slayerProfitIdleSeconds] (default 60) the tracker **pauses and rewinds `activeMs`
 * by that idle window**, so the grace period you were AFK/looting/among menus never counts toward
 * `$/hr`. Any drop or kill resumes it. World / island / quest changes also stop the clock.
 *
 * `$/hr = (gross − spawnCost) / activeMs × 3_600_000`.
 */
object SlayerProfitTracker {

    private const val MAX_TICK_MS = 2_000L
    private const val FILE_PATH = "config/fishmod/slayer_profit.json"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    // matched against a colour-stripped, trimmed line
    private val RARE_DROP = Pattern.compile("(?:^|\\s)(?:PET |[A-Z]+ )*?(?:RARE|PET) DROP! (.+?)(?: \\(\\+.*)?$")
    private val SACK_PICKUP = Pattern.compile("^\\+\\s*([\\d,]+)\\s+([A-Za-z'’. ]+?)(?:\\s*\\(.*\\))?$")

    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fishmod-slayer-profit-io").apply { isDaemon = true }
    }
    private val lock = Any()

    private class Data {
        var drops: MutableMap<String, Long> = LinkedHashMap() // display name -> count
        var bosses: Int = 0
        var activeMs: Long = 0
    }

    // keyed by SlayerType.name
    private var all: MutableMap<String, Data> = HashMap()

    private var lastTickMs = 0L
    private var lastActivityMs = 0L
    private var idlePaused = false
    private var lastPriceRefresh = 0L

    private fun idleMs(): Long = FishSettings.slayerProfitIdleSeconds.coerceIn(10, 3600) * 1000L

    private fun data(type: SlayerType): Data = synchronized(lock) { all.getOrPut(type.name) { Data() } }

    @JvmStatic fun enabled(): Boolean = FishSettings.slayerProfitEnabled

    @JvmStatic
    fun init() {
        load()
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { tick() })
        Events.ON_GAME_MESSAGE.register { text ->
            if (enabled()) onChat(text.string.replace(Constants.STRIP_COLOR_REGEX, "").trim())
            false
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val prev = lastTickMs
        lastTickMs = now
        if (!enabled()) return

        if (now - lastPriceRefresh > 60_000L) { lastPriceRefresh = now; CroesusPrices.refreshIfStale() }

        val type = SlayerManager.type
        if (type == null || !SlayerManager.isActiveSlayer()) return
        val d = data(type)

        if (idlePaused) return // wait for activity to resume

        if (lastActivityMs > 0 && now - lastActivityMs > idleMs()) {
            // gone idle: pause and rewind the idle grace so it doesn't inflate $/hr
            synchronized(lock) { d.activeMs = (d.activeMs - idleMs()).coerceAtLeast(0) }
            idlePaused = true
            save()
            return
        }
        if (prev > 0) {
            val delta = (now - prev).coerceIn(0, MAX_TICK_MS)
            if (delta > 0) synchronized(lock) { d.activeMs += delta }
        }
    }

    private fun noteActivity() {
        lastActivityMs = System.currentTimeMillis()
        idlePaused = false
    }

    fun onBossKill(type: SlayerType) {
        synchronized(lock) { data(type).bosses++ }
        noteActivity()
        save()
    }

    private fun onChat(s: String) {
        val type = SlayerManager.type ?: return
        if (!SlayerManager.isActiveSlayer()) return

        val rare = RARE_DROP.matcher(s)
        if (rare.find()) {
            addDrop(type, rare.group(1).trim(), 1)
            return
        }
        val sack = SACK_PICKUP.matcher(s)
        if (sack.matches()) {
            val n = sack.group(1).replace(",", "").toLongOrNull() ?: return
            val name = sack.group(2).trim()
            // only accept sack lines we can resolve to a real item (or a coin line) — rejects generic "+N Mana" spam
            if (name.endsWith("Coins") || ItemsDb.idFor(name) != null) addDrop(type, name, n)
        }
    }

    private fun addDrop(type: SlayerType, name: String, count: Long) {
        if (name.isEmpty() || count <= 0) return
        synchronized(lock) {
            val d = data(type)
            d.drops[name] = (d.drops[name] ?: 0L) + count
        }
        noteActivity()
        save()
    }

    // ---------------------------------------------------------------- readout for the HUD

    class Row(@JvmField val name: String, @JvmField val count: Long, @JvmField val value: Double, @JvmField val priced: Boolean)

    /** Priced drop rows for [type], highest value first. */
    fun rows(type: SlayerType): List<Row> {
        val snap = synchronized(lock) { LinkedHashMap(data(type).drops) }
        val out = ArrayList<Row>(snap.size)
        for ((name, count) in snap) {
            val id = if (name.endsWith("Coins")) null else ItemsDb.idFor(name)
            val unit = if (id != null) CroesusPrices.price(id) else 0.0
            out.add(Row(name, count, unit * count, id != null && unit > 0))
        }
        out.sortByDescending { it.value }
        return out
    }

    fun grossValue(type: SlayerType): Double = rows(type).sumOf { it.value }
    fun spawnCost(type: SlayerType): Double = bosses(type).toDouble() * FishSettings.slayerProfitSpawnCost.coerceAtLeast(0)
    fun profit(type: SlayerType): Double = grossValue(type) - spawnCost(type)
    fun bosses(type: SlayerType): Int = synchronized(lock) { data(type).bosses }
    fun activeMs(type: SlayerType): Long = synchronized(lock) { data(type).activeMs }
    fun isPaused(): Boolean = idlePaused

    fun profitPerHour(type: SlayerType): Double {
        val ms = activeMs(type)
        if (ms < 5_000L) return 0.0
        return profit(type) * 3_600_000.0 / ms
    }

    fun hasData(type: SlayerType): Boolean = synchronized(lock) {
        val d = all[type.name] ?: return false
        d.bosses > 0 || d.drops.isNotEmpty()
    }

    @JvmStatic
    fun reset() {
        synchronized(lock) { all.clear() }
        idlePaused = false
        lastActivityMs = 0
        save()
    }

    // ---------------------------------------------------------------- persistence

    private fun load() {
        synchronized(lock) {
            val file = File(FILE_PATH)
            if (!file.exists()) return
            try {
                FileReader(file).use { r ->
                    val t: Type = object : TypeToken<MutableMap<String, Data>>() {}.type
                    GSON.fromJson<MutableMap<String, Data>?>(r, t)?.let { all = it }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun save() {
        val json = synchronized(lock) { GSON.toJson(all) }
        writeExecutor.execute {
            try {
                val file = File(FILE_PATH)
                file.parentFile?.mkdirs()
                FileWriter(file).use { it.write(json) }
            } catch (_: Exception) {
            }
        }
    }
}
