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
 * Like SkyHanni, one bucket per **slayer type + tier** ("Revenant Horror 5" is separate from
 * "Revenant Horror 4"), and two views switched from the HUD (chat open) or the /fm dropdown:
 *  - **Total**        — persisted to disk, all-time, the default.
 *  - **This Session** — in-memory only, cleared every game launch.
 * Both accumulate at once; a read picks the one named by [FishSettings.slayerProfitDisplayMode].
 *
 * Drops come from the chat lines Hypixel prints — the `RARE DROP! (item)` family and, if Hypixel
 * "Sack Notifications" is on, `+<n> <item>` sack lines (SkyHanni instead diffs the inventory/sacks;
 * client-side without that infra, chat is what we have). Value = `CroesusPrices.price(idFor(name)) ×
 * count`, priced at render time so a price-mode change reprices everything.
 *
 * "Mob Kill Coins" is a synthetic drop row (SkyHanni's `SKYBLOCK_COIN` pseudo-item): small purse
 * *gains* while in the slayer area, each < 100k, summed; its "count" is the number of paying kills.
 *
 * Spawn cost is read, not guessed: the purse charge (−350k cap) right after `SLAYER QUEST STARTED!`,
 * or the `Took <n> coins from your bank for auto-slayer` line. It shows on its own line and is
 * subtracted from profit.
 *
 * `activeMs` accrues only while [SlayerManager.isActiveSlayer]; after
 * [FishSettings.slayerProfitIdleSeconds] with no drop/kill/coin it pauses and rewinds that window so
 * an AFK gap never inflates coins/hr (SkyHanni's `afkTimeout`, default 60s).
 *
 * Left-click a drop row (chat open) to hide it; right-click the title to arm a 3s "click again to
 * reset" that clears the shown view.
 */
object SlayerProfitTracker {

    private const val MAX_TICK_MS = 2_000L
    private const val FILE_PATH = "config/fishmod/slayer_profit.json"
    private const val MOB_COIN_CAP = 100_000L      // reject a single "mob kill" gain >= this
    private const val SPAWN_COST_CAP = 350_000L    // SkyHanni's coinsCap for one quest-start charge
    private const val QUEST_START_WINDOW_MS = 8_000L
    private const val RESET_CONFIRM_MS = 3_000L
    private const val COINS_ROW = "Mob Kill Coins"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

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
        var spawnCost: Long = 0        // coins spent starting quests (positive; subtracted)
        var mobKillCoins: Long = 0     // small purse gains while grinding
        var mobKillCoinHits: Long = 0  // how many such gains
        var activeMs: Long = 0
    }

    // keyed by "<SlayerType.name> <tier>"
    private var total: MutableMap<String, Data> = HashMap()             // persisted
    private val session: MutableMap<String, Data> = HashMap()           // in-memory, per launch
    private var hidden: MutableMap<String, MutableSet<String>> = HashMap() // key -> hidden drop names (persisted, mode-independent)

    private var lastTickMs = 0L
    private var lastActivityMs = 0L
    private var idlePaused = false
    private var lastPriceRefresh = 0L
    private var lastPurse = -1.0
    private var lastQuestStartMs = 0L
    private var resetArmedAt = 0L

    // ON_GAME_MESSAGE fires twice for one Hypixel line; swallow the echo
    private var lastLine = ""
    private var lastLineMs = 0L

    private fun idleMs(): Long = FishSettings.slayerProfitIdleSeconds.coerceIn(10, 3600) * 1000L
    private fun sessionMode(): Boolean = FishSettings.slayerProfitDisplayMode.equals("This Session", true)
    private fun countKillCoins(): Boolean = FishSettings.slayerProfitCountKillCoins

    private fun key(type: SlayerType, tier: Int): String = "${type.name} $tier"
    private fun curKey(): String? {
        val t = SlayerManager.type ?: return null
        return key(t, SlayerManager.tier)
    }

    /** Both backing maps' Data for [k] — every mutation writes to both so the views stay in sync. */
    private fun bothData(k: String): Pair<Data, Data> = synchronized(lock) {
        total.getOrPut(k) { Data() } to session.getOrPut(k) { Data() }
    }

    private fun viewMap(): MutableMap<String, Data> = if (sessionMode()) session else total
    private fun view(k: String): Data? = synchronized(lock) { viewMap()[k] }
    private fun hiddenSet(k: String): MutableSet<String> =
        synchronized(lock) { hidden.getOrPut(k) { LinkedHashSet() } }

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

        val k = curKey()
        if (k == null || !SlayerManager.isActiveSlayer()) return
        val (t, s) = bothData(k)

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

    // ---------------------------------------------------------------- hooks (called by SlayerManager)

    fun onQuestStarted() { lastQuestStartMs = System.currentTimeMillis() }

    fun onBossKill(type: SlayerType) {
        val k = key(type, SlayerManager.tier)
        synchronized(lock) { val (t, s) = bothData(k); t.bosses++; s.bosses++ }
        noteActivity()
        save()
    }

    /** Purse value from the sidebar scan (or -1). Deductions → spawn cost, small gains → mob coins. */
    fun observePurse(purse: Double) {
        if (purse < 0) { lastPurse = -1.0; return }
        val prev = lastPurse
        lastPurse = purse
        if (!enabled() || prev < 0) return
        val k = curKey() ?: return
        if (!SlayerManager.isActiveSlayer()) return

        val delta = (purse - prev).toLong()
        if (delta == 0L) return
        if (delta < 0) {
            val cost = -delta
            if (cost in 1..SPAWN_COST_CAP &&
                System.currentTimeMillis() - lastQuestStartMs <= QUEST_START_WINDOW_MS
            ) {
                synchronized(lock) { val (t, s) = bothData(k); t.spawnCost += cost; s.spawnCost += cost }
                noteActivity()
                save()
            }
        } else if (delta in 1 until MOB_COIN_CAP) {
            synchronized(lock) {
                val (t, s) = bothData(k)
                t.mobKillCoins += delta; t.mobKillCoinHits++
                s.mobKillCoins += delta; s.mobKillCoinHits++
            }
            noteActivity()
            save()
        }
    }

    private fun onChat(s: String) {
        val k = curKey() ?: return

        // de-dupe the double-fired line
        val now = System.currentTimeMillis()
        if (s == lastLine && now - lastLineMs < 1_500L) return
        lastLine = s
        lastLineMs = now

        val bank = AUTO_SLAYER_BANK.matcher(s)
        if (bank.find()) {
            val c = parseShort(bank.group(1))
            if (c in 1..SPAWN_COST_CAP) {
                synchronized(lock) { val (t, se) = bothData(k); t.spawnCost += c; se.spawnCost += c }
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
            addDrop(k, name, count)
            return
        }
        val sack = SACK_PICKUP.matcher(s)
        if (sack.matches()) {
            val n = sack.group(1).replace(",", "").toLongOrNull() ?: return
            val name = sack.group(2).trim()
            if (name.endsWith("Coins") || ItemsDb.idFor(name) != null) addDrop(k, name, n)
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

    private fun addDrop(k: String, name: String, count: Long) {
        if (name.isEmpty() || count <= 0 || name == COINS_ROW) return
        synchronized(lock) {
            val (t, s) = bothData(k)
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

    private fun isHiddenKey(k: String, name: String): Boolean = synchronized(lock) { hiddenSet(k).contains(name) }

    /** Left-click a drop row (chat open): toggle its hidden flag (mode-independent, saved). */
    fun toggleHidden(type: SlayerType, tier: Int, name: String) {
        val k = key(type, tier)
        synchronized(lock) {
            val h = hiddenSet(k)
            if (!h.remove(name)) h.add(name)
        }
        save()
    }

    fun resetArmed(): Boolean = resetArmedAt != 0L && System.currentTimeMillis() - resetArmedAt <= RESET_CONFIRM_MS

    /** Right-click the title: first press arms, a second inside [RESET_CONFIRM_MS] resets the shown view. */
    @JvmStatic
    fun armOrConfirmReset() {
        if (resetArmed()) { resetArmedAt = 0L; reset() }
        else resetArmedAt = System.currentTimeMillis()
    }

    @JvmStatic
    fun reset() {
        val k = curKey()
        synchronized(lock) {
            if (k != null) viewMap().remove(k) else viewMap().clear()
        }
        idlePaused = false
        lastActivityMs = 0
        lastPurse = -1.0
        save()
    }

    // ---------------------------------------------------------------- readout

    class Row(
        @JvmField val name: String,
        @JvmField val count: Long,
        @JvmField val value: Double,
        @JvmField val priced: Boolean,
        @JvmField val coins: Boolean = false,
    )

    /** All rows for the shown view of [type]/[tier], highest value first. Includes the synthetic
     *  "Mob Kill Coins" row and hidden rows (callers filter). */
    fun rows(type: SlayerType, tier: Int): List<Row> {
        val k = key(type, tier)
        val d = view(k)
        val snap = synchronized(lock) { if (d != null) LinkedHashMap(d.drops) else LinkedHashMap() }
        val out = ArrayList<Row>(snap.size + 1)
        for ((name, count) in snap) {
            val id = if (name.endsWith("Coins")) null else ItemsDb.idFor(name)
            val unit = if (id != null) CroesusPrices.price(id) else 0.0
            out.add(Row(name, count, unit * count, id != null && unit > 0))
        }
        if (countKillCoins()) {
            val mkc = d?.mobKillCoins ?: 0L
            if (mkc > 0) out.add(Row(COINS_ROW, d?.mobKillCoinHits ?: 0L, mkc.toDouble(), priced = true, coins = true))
        }
        out.sortByDescending { it.value }
        return out
    }

    fun spawnCost(type: SlayerType, tier: Int): Long = view(key(type, tier))?.spawnCost ?: 0L
    fun bosses(type: SlayerType, tier: Int): Int = view(key(type, tier))?.bosses ?: 0
    fun activeMs(type: SlayerType, tier: Int): Long = view(key(type, tier))?.activeMs ?: 0L
    fun isPaused(): Boolean = idlePaused

    /** Sum of visible (non-hidden) row values minus spawn cost. */
    fun profit(type: SlayerType, tier: Int): Double {
        val k = key(type, tier)
        val gained = rows(type, tier).filter { !isHiddenKey(k, it.name) }.sumOf { it.value }
        return gained - spawnCost(type, tier).toDouble()
    }

    fun profitPerHour(type: SlayerType, tier: Int): Double {
        val ms = activeMs(type, tier)
        if (ms < 5_000L) return 0.0
        return profit(type, tier) * 3_600_000.0 / ms
    }

    fun hasData(type: SlayerType, tier: Int): Boolean {
        val d = view(key(type, tier)) ?: return false
        return synchronized(lock) { d.bosses > 0 || d.drops.isNotEmpty() || d.spawnCost > 0 || d.mobKillCoins > 0 }
    }

    /** One display line: [label] left, right-aligned [value] ("" = none); [tag] routes clicks. */
    class DisplayRow(@JvmField val label: String, @JvmField val value: String, @JvmField val tag: String)

    private fun sh(v: Double): String = SlayerStatsTracker.short(v)
    private fun sep(v: Long): String = String.format("%,d", v)

    /**
     * The SkyHanni-style panel for [type]/[tier], top to bottom. [interactive] (chat open) reveals
     * hidden rows struck-through and appends the mode switcher line.
     * Tags: `title`, `item:<name>`, `coins`, `collapsed`, `cost`, `bosses`, `profit`, `rate`, `mode`.
     */
    fun display(type: SlayerType, tier: Int, interactive: Boolean): List<DisplayRow> {
        val k = key(type, tier)
        val out = ArrayList<DisplayRow>(24)

        val cat = "${type.displayName} $tier"
        out.add(
            if (resetArmed()) DisplayRow("§c§lClick again to reset ${modeLabel()}!", "", "title")
            else DisplayRow("§e§l$cat Profit Tracker" + (if (isPaused()) " §8(idle)" else ""), "", "title")
        )

        val cap = FishSettings.slayerProfitLines.coerceIn(3, 30)
        val minVal = FishSettings.slayerProfitMinValue.coerceAtLeast(0).toDouble()
        val revealHidden = interactive || FishSettings.slayerProfitShowHidden

        var visibleShown = 0
        var collapsedValue = 0.0
        var collapsedCount = 0
        for (r in rows(type, tier)) {
            val hiddenRow = isHiddenKey(k, r.name)
            if (hiddenRow && !revealHidden) continue
            if (!hiddenRow && (visibleShown >= cap || (minVal > 0 && r.value < minVal && !r.coins))) {
                collapsedValue += r.value
                collapsedCount++
                continue
            }
            if (!hiddenRow) visibleShown++
            val nm = if (r.coins) "§6Mob Kill Coins" else "§f${r.name}"
            val label = if (hiddenRow) "§7${sep(r.count)}x §8§m${r.name}" else "§7${sep(r.count)}x $nm"
            val value = when {
                hiddenRow -> "§7§m${sh(r.value)}"
                r.priced -> "§6${sh(r.value)}"
                else -> "§8?"
            }
            out.add(DisplayRow(label, value, if (r.coins) "coins" else "item:${r.name}"))
        }
        if (collapsedCount > 0) {
            val noun = if (collapsedCount == 1) "item" else "items"
            out.add(DisplayRow("§7$collapsedCount more $noun", "§6${sh(collapsedValue)}", "collapsed"))
        }

        out.add(DisplayRow(" §7Slayer Spawn Costs:", "§c-${sh(spawnCost(type, tier).toDouble())}", "cost"))
        out.add(DisplayRow("§7Bosses killed:", "§e${sep(bosses(type, tier).toLong())}", "bosses"))

        val p = profit(type, tier)
        val pc = if (p < 0) "§c" else "§6"
        val coinWord = if (Math.abs(p.toLong()) == 1L) "coin" else "coins"
        out.add(DisplayRow("§e${modeLabel()} Profit:", "$pc${sep(p.toLong())} $coinWord", "profit"))

        val pph = profitPerHour(type, tier)
        out.add(DisplayRow("§eProfit/h:", if (pph == 0.0) "§8—" else "${if (pph < 0) "§c" else "§6"}${sh(pph)}", "rate"))

        if (interactive) {
            val sw = if (sessionMode()) "§7[ §7Total §8| §a§lThis Session §7]" else "§7[ §a§lTotal §8| §7This Session §7]"
            out.add(DisplayRow("§7Mode: $sw", "", "mode"))
        }
        return out
    }

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
                    p?.hidden?.forEach { (kk, v) -> hidden[kk] = LinkedHashSet(v) }
                } else {
                    // legacy: file was a bare Map<String, Data> keyed by SlayerType.name (no tier)
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
            hidden.forEach { (kk, v) -> h[kk] = ArrayList(v) }
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
