package fishmod.features.slayers

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import fishmod.features.croesus.CroesusPrices
import fishmod.utils.Constants
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.networth.ItemsDb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import java.io.File
import java.lang.reflect.Type
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Slayer drop-value / coins-per-hour tracker — a close port of SkyHanni's "<Boss> Profit Tracker".
 *
 * Two views, switched by clicking the mode line on the HUD (or the /fm dropdown):
 *  - **Total**       — persisted to disk, all-time, the default.
 *  - **This Session** — in-memory only, cleared every game launch.
 * Both accumulate at once; a read picks the one named by [FishSettings.slayerProfitDisplayMode].
 * Each view is kept per [SlayerType].
 *
 * Drops come from the chat lines Hypixel actually prints — the `RARE DROP! (item)` family and, if
 * Hypixel "Sack Notifications" is on, `+<n> <item>` sack lines. Stored by display name; value is
 * `CroesusPrices.price(idFor(name)) × count`, priced at render time so a price-mode change reprices
 * everything. Right-clicking a row hides it (mode-independent, persisted); a hidden row drops out of
 * the list and the total unless "Show Hidden Rows" is on. Rows under "Hide Below" — and any beyond
 * the row cap — fold into one "N items" line that still counts toward the total.
 *
 * Spawn cost is read, not guessed: the purse charge right after `SLAYER QUEST STARTED!`, or the
 * `Took <n> coins from your bank for auto-slayer` line. Small purse *gains* while in the slayer area
 * (each < 100k) are summed as "Mob Kill Coins" and count as profit when "Count Kill Coins" is on.
 *
 * `activeMs` accrues only while [SlayerManager.isActiveSlayer]; after
 * [FishSettings.slayerProfitIdleSeconds] with no drop/kill/coin it pauses and rewinds that window so
 * an AFK gap never inflates coins/hr.
 */
object SlayerProfitTracker {

    private const val MAX_TICK_MS = 2_000L
    private const val FILE_PATH = "config/fishmod/slayer_profit.json"
    private const val MOB_COIN_CAP = 100_000L      // per-gain cap for "mob kill coins"
    private const val SPAWN_COST_CAP = 500_000L    // sanity cap for one quest-start deduction
    private const val QUEST_START_WINDOW_MS = 8_000L
    private const val RESET_CONFIRM_MS = 3_000L
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    // matched against a colour-stripped, trimmed line
    //  "RARE DROP! (Revenant Viscera)"  /  "CRAZY RARE DROP! (4x Foul Flesh) (+751% Magic Find!)"
    //  /  "PET DROP! Zombie (Epic)"  /  "VERY RARE DROP! (Warden Heart)"
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
    private var total: MutableMap<String, Data> = HashMap()             // persisted
    private val session: MutableMap<String, Data> = HashMap()           // in-memory, per launch
    private var hidden: MutableMap<String, MutableSet<String>> = HashMap() // type -> hidden drop names (persisted, mode-independent)

    private var lastTickMs = 0L
    private var lastActivityMs = 0L
    private var idlePaused = false
    private var lastPriceRefresh = 0L
    private var lastPurse = -1.0
    private var lastQuestStartMs = 0L
    private var resetArmedAt = 0L

    // ON_GAME_MESSAGE fires twice for one Hypixel line (system-chat + bundle sites); swallow the echo
    private var lastLine = ""
    private var lastLineMs = 0L

    private fun idleMs(): Long = FishSettings.slayerProfitIdleSeconds.coerceIn(10, 3600) * 1000L

    private fun sessionMode(): Boolean = FishSettings.slayerProfitDisplayMode.equals("This Session", true)

    /** Both backing maps' Data for [type] — every mutation writes to both so the views stay in sync. */
    private fun bothData(type: SlayerType): Pair<Data, Data> = synchronized(lock) {
        total.getOrPut(type.name) { Data() } to session.getOrPut(type.name) { Data() }
    }

    /** The Data for the view currently on screen. */
    private fun viewData(type: SlayerType): Data = synchronized(lock) {
        (if (sessionMode()) session else total).getOrPut(type.name) { Data() }
    }

    private fun hiddenSet(type: SlayerType): MutableSet<String> =
        synchronized(lock) { hidden.getOrPut(type.name) { LinkedHashSet() } }

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
        if (resetArmedAt != 0L && now - resetArmedAt > RESET_CONFIRM_MS) resetArmedAt = 0L

        val type = SlayerManager.type
        if (type == null || !SlayerManager.isActiveSlayer()) return
        val (t, s) = bothData(type)

        if (idlePaused) return // wait for activity to resume

        if (lastActivityMs > 0 && now - lastActivityMs > idleMs()) {
            // gone idle: pause and rewind the idle grace so it doesn't inflate coins/hr
            synchronized(lock) {
                t.activeMs = (t.activeMs - idleMs()).coerceAtLeast(0)
                s.activeMs = (s.activeMs - idleMs()).coerceAtLeast(0)
            }
            idlePaused = true
            save()
            return
        }
        if (prev > 0) {
            val delta = (now - prev).coerceIn(0, MAX_TICK_MS)
            if (delta > 0) synchronized(lock) { t.activeMs += delta; s.activeMs += delta }
        }
    }

    private fun noteActivity() {
        lastActivityMs = System.currentTimeMillis()
        idlePaused = false
    }

    // ---------------------------------------------------------------- hooks

    fun onQuestStarted() { lastQuestStartMs = System.currentTimeMillis() }

    fun onBossKill(type: SlayerType) {
        synchronized(lock) { val (t, s) = bothData(type); t.bosses++; s.bosses++ }
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
                synchronized(lock) { val (t, s) = bothData(type); t.spawnCost += cost; s.spawnCost += cost }
                noteActivity()
                save()
            }
        } else if (delta in 1..MOB_COIN_CAP) {
            synchronized(lock) {
                val (t, s) = bothData(type)
                t.mobKillCoins += delta; t.mobKillCoinHits++
                s.mobKillCoins += delta; s.mobKillCoinHits++
            }
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
                synchronized(lock) { val (t, se) = bothData(type); t.spawnCost += c; se.spawnCost += c }
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
            val (t, s) = bothData(type)
            t.drops[name] = (t.drops[name] ?: 0L) + count
            s.drops[name] = (s.drops[name] ?: 0L) + count
        }
        noteActivity()
        save()
    }

    // ---------------------------------------------------------------- mode / hidden / reset

    /** Flip Total <-> This Session and persist it. */
    @JvmStatic
    fun cycleMode() {
        FishSettings.slayerProfitDisplayMode = if (sessionMode()) "Total" else "This Session"
        resetArmedAt = 0L
    }

    @JvmStatic fun modeLabel(): String = if (sessionMode()) "This Session" else "Total"

    fun isHidden(type: SlayerType, name: String): Boolean = synchronized(lock) { hiddenSet(type).contains(name) }

    /** Right-click a drop row: toggle its hidden flag (mode-independent, saved). */
    fun toggleHidden(type: SlayerType, name: String) {
        synchronized(lock) {
            val h = hiddenSet(type)
            if (!h.remove(name)) h.add(name)
        }
        save()
    }

    /** True once armed and still inside the confirm window. */
    fun resetArmed(): Boolean = resetArmedAt != 0L && System.currentTimeMillis() - resetArmedAt <= RESET_CONFIRM_MS

    /** Right-click the title: first press arms, a second press inside [RESET_CONFIRM_MS] resets. */
    @JvmStatic
    fun armOrConfirmReset() {
        if (resetArmed()) { resetArmedAt = 0L; reset() }
        else resetArmedAt = System.currentTimeMillis()
    }

    // ---------------------------------------------------------------- readout for the HUD

    class Row(@JvmField val name: String, @JvmField val count: Long, @JvmField val value: Double, @JvmField val priced: Boolean)

    /** All priced drop rows for [type] in the current view, highest value first (includes hidden). */
    fun rows(type: SlayerType): List<Row> {
        val snap = synchronized(lock) { LinkedHashMap(viewData(type).drops) }
        val out = ArrayList<Row>(snap.size)
        for ((name, count) in snap) {
            val id = if (name.endsWith("Coins")) null else ItemsDb.idFor(name)
            val unit = if (id != null) CroesusPrices.price(id) else 0.0
            out.add(Row(name, count, unit * count, id != null && unit > 0))
        }
        out.sortByDescending { it.value }
        return out
    }

    private fun countKillCoins(): Boolean = FishSettings.slayerProfitCountKillCoins

    fun dropValue(type: SlayerType): Double = rows(type).filter { !isHidden(type, it.name) }.sumOf { it.value }
    fun mobKillCoins(type: SlayerType): Long = synchronized(lock) { view(type)?.mobKillCoins ?: 0L }
    fun mobKillCoinHits(type: SlayerType): Long = synchronized(lock) { view(type)?.mobKillCoinHits ?: 0L }
    fun spawnCost(type: SlayerType): Long = synchronized(lock) { view(type)?.spawnCost ?: 0L }
    fun bosses(type: SlayerType): Int = synchronized(lock) { view(type)?.bosses ?: 0 }
    fun activeMs(type: SlayerType): Long = synchronized(lock) { view(type)?.activeMs ?: 0L }
    fun isPaused(): Boolean = idlePaused

    private fun view(type: SlayerType): Data? = (if (sessionMode()) session else total)[type.name]

    fun profit(type: SlayerType): Double {
        val coins = if (countKillCoins()) mobKillCoins(type).toDouble() else 0.0
        return dropValue(type) + coins - spawnCost(type).toDouble()
    }

    fun profitPerHour(type: SlayerType): Double {
        val ms = activeMs(type)
        if (ms < 5_000L) return 0.0
        return profit(type) * 3_600_000.0 / ms
    }

    fun hasData(type: SlayerType): Boolean = synchronized(lock) {
        val d = view(type) ?: return false
        d.bosses > 0 || d.drops.isNotEmpty() || d.spawnCost > 0 || d.mobKillCoins > 0
    }

    /** One display line: [label] on the left, right-aligned [value] ("" = none), [tag] routes clicks. */
    class DisplayRow(@JvmField val label: String, @JvmField val value: String, @JvmField val tag: String)

    private fun sh(v: Double): String = SlayerStatsTracker.short(v)

    /**
     * The full SkyHanni-style panel for [type], top to bottom. Tags: `title`, `item:<name>`,
     * `collapsed`, `coins`, `cost`, `bosses`, `profit`, `rate`, `mode`, `spacer`, `hint`.
     */
    fun display(type: SlayerType): List<DisplayRow> {
        val out = ArrayList<DisplayRow>(20)
        val armed = resetArmed()
        val title = "§6§l${type.displayName} Profit Tracker" +
            (if (isPaused()) " §8(idle)" else "")
        out.add(DisplayRow(if (armed) "§c§lClick again to reset!" else title, "", "title"))

        val cap = FishSettings.slayerProfitLines.coerceIn(1, 20)
        val minVal = FishSettings.slayerProfitMinValue.coerceAtLeast(0).toDouble()
        val showHidden = FishSettings.slayerProfitShowHidden

        val all = rows(type)
        val shown = ArrayList<Row>()
        var collapsedValue = 0.0
        var collapsedCount = 0
        for (r in all) {
            val hiddenRow = isHidden(type, r.name)
            if (hiddenRow && !showHidden) {           // hidden: not drawn, not counted
                continue
            }
            if (!hiddenRow && (shown.size >= cap || (minVal > 0 && r.value < minVal))) {
                collapsedValue += r.value
                collapsedCount++
                continue
            }
            shown.add(r)
            val strike = if (hiddenRow) "§m" else ""
            val v = if (r.priced) "§a$strike${sh(r.value)}" else "§8$strike?"
            out.add(DisplayRow("§7${fmt(r.count)}x §f$strike${r.name}", v, "item:${r.name}"))
        }
        if (collapsedCount > 0) {
            val noun = if (collapsedCount == 1) "item" else "items"
            out.add(DisplayRow("§7$collapsedCount more $noun", "§a${sh(collapsedValue)}", "collapsed"))
        }

        if (countKillCoins()) {
            val mkc = mobKillCoins(type)
            if (mkc > 0) {
                val hits = mobKillCoinHits(type)
                out.add(DisplayRow("§7${fmt(hits)}x §6Mob Kill Coins", "§a${sh(mkc.toDouble())}", "coins"))
            }
        }

        val cost = spawnCost(type)
        if (cost > 0) out.add(DisplayRow("§7Spawn Cost", "§c-${sh(cost.toDouble())}", "cost"))

        out.add(DisplayRow("§7Bosses Killed", "§e${fmt(bosses(type).toLong())}", "bosses"))

        val p = profit(type)
        out.add(DisplayRow("§6§lTotal Profit", "${if (p < 0) "§c" else "§a"}${if (p < 0) "-" else "+"}${sh(Math.abs(p))}", "profit"))
        out.add(DisplayRow("§6Profit/h", "§6${rate(profitPerHour(type))}", "rate"))

        out.add(DisplayRow("§7[ ${if (sessionMode()) "§7Total §8| §a§lThis Session" else "§a§lTotal §8| §7This Session"} §7]", "", "mode"))
        return out
    }

    @JvmStatic
    fun reset() {
        synchronized(lock) {
            if (sessionMode()) session.clear() else { total.clear() }
        }
        idlePaused = false
        lastActivityMs = 0
        lastPurse = -1.0
        save()
    }

    private fun fmt(v: Long): String = String.format("%,d", v)
    private fun rate(v: Double): String = if (v <= 0.0) "§8—" else sh(v)

    // ---------------------------------------------------------------- persistence

    // nullable: Gson bypasses field initializers, so an absent key leaves the field null
    private class Persisted {
        var total: MutableMap<String, Data>? = null
        var hidden: MutableMap<String, MutableList<String>>? = null
    }

    private fun load() {
        synchronized(lock) {
            val file = File(FILE_PATH)
            if (!file.exists()) return
            try {
                val root = JsonParser.parseString(file.readText())
                if (root is JsonObject && root.has("total")) {
                    val p: Persisted? = GSON.fromJson(root, Persisted::class.java)
                    total = p?.total ?: HashMap()
                    hidden = HashMap()
                    p?.hidden?.forEach { (k, v) -> hidden[k] = LinkedHashSet(v) }
                } else {
                    // legacy: file was a bare Map<String, Data>
                    val t: Type = object : TypeToken<MutableMap<String, Data>>() {}.type
                    total = GSON.fromJson(root, t) ?: HashMap()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun save() {
        val json = synchronized(lock) {
            val p = Persisted()
            p.total = total
            val h = HashMap<String, MutableList<String>>()
            hidden.forEach { (k, v) -> h[k] = ArrayList(v) }
            p.hidden = h
            GSON.toJson(p)
        }
        writeExecutor.execute {
            try {
                val file = File(FILE_PATH)
                file.parentFile?.mkdirs()
                file.writeText(json)
            } catch (_: Exception) {
            }
        }
    }
}
