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
 * "<Boss> Profit Tracker" panel, kept per slayer type and persisted to disk.
 *
 * Drops
 * -----
 * From the chat lines Hypixel actually prints:
 *  - the `RARE DROP! <item>` / `PET DROP! <item>` family (always shown), and
 *  - sack-pickup lines `+<n> <item>` (shown only if Hypixel "Sack Notifications" is on — without
 *    them the bulk flesh/shard drops go to sacks silently and can't be seen client-side).
 * Stored by display name; value = `CroesusPrices.price(idFor(name)) × count` at render time, so
 * changing price mode reprices everything. Unpriceable items are still listed (shown as "?").
 *
 * Spawn cost — read, not guessed
 * ------------------------------
 * The coins Hypixel actually takes to start the quest, exactly like SkyHanni:
 *  - the purse drop right after `SLAYER QUEST STARTED!` (manual start), and
 *  - the `Took <n> coins from your bank for auto-slayer...` chat line (auto-slayer).
 * No hard-coded per-tier table (the wikis disagree and it varies by boss/version).
 *
 * Mob Kill Coins
 * -------------
 * Small purse *gains* while grinding in the slayer area (each < 100k, to exclude bazaar/AH/quest
 * payouts) are summed as "Mob Kill Coins" and count as profit.
 *
 * Active time & the idle rule
 * ---------------------------
 * `activeMs` accrues only while [SlayerManager.isActiveSlayer]. If no drop/kill/coin happens for
 * [FishSettings.slayerProfitIdleSeconds] (default 60) the tracker pauses AND rewinds `activeMs` by
 * that window, so an AFK/looting gap never inflates `$/hr`. Any drop, kill or coin gain resumes it.
 *
 * `$/hr = (dropValue + mobKillCoins − spawnCost) / activeMs × 3_600_000`.
 */
object SlayerProfitTracker {

    private const val MAX_TICK_MS = 2_000L
    private const val FILE_PATH = "config/fishmod/slayer_profit.json"
    private const val MOB_COIN_CAP = 100_000L      // per-gain cap for "mob kill coins"
    private const val SPAWN_COST_CAP = 500_000L    // sanity cap for one quest-start deduction
    private const val QUEST_START_WINDOW_MS = 8_000L
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    // matched against a colour-stripped, trimmed line
    //  "RARE DROP! (Revenant Viscera)"  /  "CRAZY RARE DROP! (4x Foul Flesh) (+751% Magic Find!)"
    //  /  "PET DROP! Zombie (Epic)"
    private val DROP_LINE = Pattern.compile("(?:[A-Z]{3,} )+DROP! \\(?(?:([\\d,]+)x )?(.+?)\\)?(?: \\(\\+.*)?$")
    private val LEADING_GLYPHS = Regex("^[^\\p{L}\\p{N}]+")
    private val SACK_PICKUP = Pattern.compile("^\\+\\s*([\\d,]+)\\s+([A-Za-z'’. ]+?)(?:\\s*\\(.*\\))?$")
    private val AUTO_SLAYER_BANK = Pattern.compile("Took ([\\d,.]+[kmb]?) coins from your bank for auto-slayer")

    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fishmod-slayer-profit-io").apply { isDaemon = true }
    }
    private val lock = Any()

    private class Data {
        var drops: MutableMap<String, Long> = LinkedHashMap() // display name -> count
        var bosses: Int = 0
        var spawnCost: Long = 0        // coins spent starting quests (positive number, subtracted)
        var mobKillCoins: Long = 0     // small purse gains while grinding
        var mobKillCoinHits: Long = 0  // how many such gains (the "Nx" in the Mob Kill Coins row)
        var activeMs: Long = 0
    }

    // keyed by SlayerType.name
    private var all: MutableMap<String, Data> = HashMap()

    private var lastTickMs = 0L
    private var lastActivityMs = 0L
    private var idlePaused = false
    private var lastPriceRefresh = 0L
    private var lastPurse = -1.0
    private var lastQuestStartMs = 0L

    // ON_GAME_MESSAGE fires twice for one Hypixel line (system-chat + bundle sites); swallow the echo
    private var lastLine = ""
    private var lastLineMs = 0L

    private fun idleMs(): Long = FishSettings.slayerProfitIdleSeconds.coerceIn(10, 3600) * 1000L

    private fun data(type: SlayerType): Data = synchronized(lock) { all.getOrPut(type.name) { Data() } }

    @JvmStatic fun enabled(): Boolean = FishSettings.slayerProfitEnabled

    @JvmStatic
    fun init() {
        load()
        ItemsDb.initAsync()
        CroesusPrices.refreshIfStale()
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

    // ---------------------------------------------------------------- hooks

    fun onQuestStarted() { lastQuestStartMs = System.currentTimeMillis() }

    fun onBossKill(type: SlayerType) {
        synchronized(lock) { data(type).bosses++ }
        noteActivity()
        save()
    }

    /** Purse value from the sidebar scan (or -1). Attributes deductions to spawn cost and small
     *  gains to mob-kill coins; ignores everything else. */
    fun observePurse(purse: Double) {
        if (purse < 0) { lastPurse = -1.0; return }
        val prev = lastPurse
        lastPurse = purse
        if (!enabled() || prev < 0) return
        val type = SlayerManager.type ?: return
        if (!SlayerManager.isActiveSlayer()) return

        val delta = (purse - prev).toLong()
        if (delta == 0L) return
        if (delta < 0) {
            val cost = -delta
            if (cost in 1..SPAWN_COST_CAP &&
                System.currentTimeMillis() - lastQuestStartMs <= QUEST_START_WINDOW_MS
            ) {
                synchronized(lock) { data(type).spawnCost += cost }
                noteActivity()
                save()
            }
        } else if (delta in 1..MOB_COIN_CAP) {
            synchronized(lock) { val d = data(type); d.mobKillCoins += delta; d.mobKillCoinHits++ }
            noteActivity()
            save()
        }
    }

    private fun onChat(s: String) {
        val type = SlayerManager.type ?: return

        // de-dupe the double-fired line (ON_GAME_MESSAGE fires from two mixin sites)
        val now = System.currentTimeMillis()
        if (s == lastLine && now - lastLineMs < 1_500L) return
        lastLine = s
        lastLineMs = now

        val bank = AUTO_SLAYER_BANK.matcher(s)
        if (bank.find()) {
            val c = parseShort(bank.group(1))
            if (c in 1..SPAWN_COST_CAP) {
                synchronized(lock) { data(type).spawnCost += c }
                noteActivity()
                save()
            }
            return
        }

        if (!SlayerManager.isActiveSlayer()) return

        val drop = DROP_LINE.matcher(s)
        if (drop.find()) {
            val count = drop.group(1)?.replace(",", "")?.toLongOrNull() ?: 1L
            val name = LEADING_GLYPHS.replace(drop.group(2).trim(), "").trim()
            addDrop(type, name, count)
            return
        }
        val sack = SACK_PICKUP.matcher(s)
        if (sack.matches()) {
            val n = sack.group(1).replace(",", "").toLongOrNull() ?: return
            val name = sack.group(2).trim()
            // only accept sack lines we can resolve to a real item (rejects generic "+N Mana" spam)
            if (name.endsWith("Coins") || ItemsDb.idFor(name) != null) addDrop(type, name, n)
        }
    }

    private fun parseShort(s: String): Long {
        val t = s.lowercase().replace(",", "")
        val mult = when {
            t.endsWith("k") -> 1_000.0
            t.endsWith("m") -> 1_000_000.0
            t.endsWith("b") -> 1_000_000_000.0
            else -> 1.0
        }
        val body = if (mult == 1.0) t else t.dropLast(1)
        return ((body.toDoubleOrNull() ?: 0.0) * mult).toLong()
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

    /** Priced drop rows for [type], highest value first (Mob Kill Coins appended by the HUD). */
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

    fun dropValue(type: SlayerType): Double = rows(type).sumOf { it.value }
    fun mobKillCoins(type: SlayerType): Long = synchronized(lock) { all[type.name]?.mobKillCoins ?: 0L }
    fun mobKillCoinHits(type: SlayerType): Long = synchronized(lock) { all[type.name]?.mobKillCoinHits ?: 0L }
    fun spawnCost(type: SlayerType): Long = synchronized(lock) { all[type.name]?.spawnCost ?: 0L }
    fun bosses(type: SlayerType): Int = synchronized(lock) { all[type.name]?.bosses ?: 0 }
    fun activeMs(type: SlayerType): Long = synchronized(lock) { all[type.name]?.activeMs ?: 0L }
    fun isPaused(): Boolean = idlePaused

    fun profit(type: SlayerType): Double =
        dropValue(type) + mobKillCoins(type).toDouble() - spawnCost(type).toDouble()

    fun profitPerHour(type: SlayerType): Double {
        val ms = activeMs(type)
        if (ms < 5_000L) return 0.0
        return profit(type) * 3_600_000.0 / ms
    }

    fun hasData(type: SlayerType): Boolean = synchronized(lock) {
        val d = all[type.name] ?: return false
        d.bosses > 0 || d.drops.isNotEmpty() || d.spawnCost > 0 || d.mobKillCoins > 0
    }

    @JvmStatic
    fun reset() {
        synchronized(lock) { all.clear() }
        idlePaused = false
        lastActivityMs = 0
        lastPurse = -1.0
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
