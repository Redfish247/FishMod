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

// Slayer drop-value / coins-per-hour tracker, a close port of SkyHanni's "<Boss> Profit Tracker".
object SlayerProfitTracker {

    private const val MAX_TICK_MS = 2_000L
    private const val FILE_PATH = "config/fishmod/slayer_profit.json"
    private const val MOB_COIN_CAP = 100_000L
    private const val SPAWN_COST_CAP = 350_000L
    private const val QUEST_START_WINDOW_MS = 8_000L
    private const val RESET_CONFIRM_MS = 3_000L
    private const val COINS_ROW = "Mob Kill Coins"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private val DROP_LINE = Pattern.compile("(?:[A-Z]{3,} )+DROP! \\(?(?:([\\d,]+)x )?(.+?)\\)?(?: \\(\\+.*)?$")
    private val LEADING_GLYPHS = Regex("^[^\\p{L}\\p{N}]+")
    private val SACK_PICKUP = Pattern.compile("^\\+\\s*([\\d,]+)\\s+([A-Za-z'’. ]+?)(?:\\s*\\(.*\\))?$")
    private val AUTO_SLAYER_BANK = Pattern.compile("Took ([\\d,.]+[kmb]?) coins from your bank for auto-slayer")

    private val writeExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fishmod-slayer-profit-io").apply { isDaemon = true }
    }
    private val lock = Any()

    private class Data {
        var drops: MutableMap<String, Long> = LinkedHashMap()
        var bosses: Int = 0
        var spawnCost: Long = 0
        var mobKillCoins: Long = 0
        var mobKillCoinHits: Long = 0
        var activeMs: Long = 0
    }

    private var total: MutableMap<String, Data> = HashMap()
    private val session: MutableMap<String, Data> = HashMap()
    private var hidden: MutableMap<String, MutableSet<String>> = HashMap()

    private var lastTickMs = 0L
    private var lastActivityMs = 0L
    private var idlePaused = false
    private var lastPriceRefresh = 0L
    private var lastPurse = -1.0
    private var lastQuestStartMs = 0L
    private var resetArmedAt = 0L


    private fun idleMs(): Long = FishSettings.slayerProfitIdleSeconds.coerceIn(10, 3600) * 1000L
    private fun sessionMode(): Boolean = FishSettings.slayerProfitDisplayMode.equals("This Session", true)
    private fun countKillCoins(): Boolean = FishSettings.slayerProfitCountKillCoins

    private fun key(type: SlayerType, tier: Int): String = "${type.name} $tier"
    private fun curKey(): String? {
        val t = SlayerManager.type ?: return null
        return key(t, SlayerManager.tier)
    }

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

        if (idlePaused) return

        if (lastActivityMs > 0 && now - lastActivityMs > idleMs()) {
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

    fun onQuestStarted() { lastQuestStartMs = System.currentTimeMillis() }

    fun onBossKill(type: SlayerType) {
        val k = key(type, SlayerManager.tier)
        synchronized(lock) { val (t, s) = bothData(k); t.bosses++; s.bosses++ }
        noteActivity()
        save()
    }

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

    @JvmStatic
    fun cycleMode() {
        FishSettings.slayerProfitDisplayMode = if (sessionMode()) "Total" else "This Session"
        resetArmedAt = 0L
    }

    @JvmStatic fun modeLabel(): String = if (sessionMode()) "This Session" else "Total"

    private fun isHiddenKey(k: String, name: String): Boolean = synchronized(lock) { hiddenSet(k).contains(name) }

    fun toggleHidden(type: SlayerType, tier: Int, name: String) {
        val k = key(type, tier)
        synchronized(lock) {
            val h = hiddenSet(k)
            if (!h.remove(name)) h.add(name)
        }
        save()
    }

    fun resetArmed(): Boolean = resetArmedAt != 0L && System.currentTimeMillis() - resetArmedAt <= RESET_CONFIRM_MS

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

    class Row(
        @JvmField val name: String,
        @JvmField val count: Long,
        @JvmField val value: Double,
        @JvmField val priced: Boolean,
        @JvmField val coins: Boolean = false,
    )

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

    fun profit(type: SlayerType, tier: Int): Double = profit(type, tier, rows(type, tier))

    private fun profit(type: SlayerType, tier: Int, rows: List<Row>): Double {
        val k = key(type, tier)
        val gained = rows.filter { !isHiddenKey(k, it.name) }.sumOf { it.value }
        return gained - spawnCost(type, tier).toDouble()
    }

    fun profitPerHour(type: SlayerType, tier: Int): Double = profitPerHour(type, tier, profit(type, tier))

    private fun profitPerHour(type: SlayerType, tier: Int, profit: Double): Double {
        val ms = activeMs(type, tier)
        if (ms < 5_000L) return 0.0
        return profit * 3_600_000.0 / ms
    }

    fun hasData(type: SlayerType, tier: Int): Boolean {
        val d = view(key(type, tier)) ?: return false
        return synchronized(lock) { d.bosses > 0 || d.drops.isNotEmpty() || d.spawnCost > 0 || d.mobKillCoins > 0 }
    }

    class DisplayRow(@JvmField val label: String, @JvmField val value: String, @JvmField val tag: String)

    private fun sh(v: Double): String = SlayerStatsTracker.short(v)
    private fun sep(v: Long): String = String.format("%,d", v)

    private var displayCache: List<DisplayRow>? = null
    private var displayCacheKey: String? = null
    private var displayCacheAt = 0L

    fun display(type: SlayerType, tier: Int, interactive: Boolean): List<DisplayRow> {
        if (interactive) return buildDisplay(type, tier, true)
        val now = System.currentTimeMillis()
        val k = key(type, tier)
        val cached = displayCache
        if (cached != null && displayCacheKey == k && now - displayCacheAt < 1000L) return cached
        return buildDisplay(type, tier, false).also {
            displayCache = it
            displayCacheKey = k
            displayCacheAt = now
        }
    }

    private fun buildDisplay(type: SlayerType, tier: Int, interactive: Boolean): List<DisplayRow> {
        val k = key(type, tier)
        val out = ArrayList<DisplayRow>(24)
        val allRows = rows(type, tier)

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
        for (r in allRows) {
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

        val p = profit(type, tier, allRows)
        val pc = if (p < 0) "§c" else "§6"
        val coinWord = if (Math.abs(p.toLong()) == 1L) "coin" else "coins"
        out.add(DisplayRow("§e${modeLabel()} Profit:", "$pc${sep(p.toLong())} $coinWord", "profit"))

        val pph = profitPerHour(type, tier, p)
        out.add(DisplayRow("§eProfit/h:", if (pph == 0.0) "§8—" else "${if (pph < 0) "§c" else "§6"}${sh(pph)}", "rate"))

        if (interactive) {
            val sw = if (sessionMode()) "§7[ §7Total §8| §a§lThis Session §7]" else "§7[ §a§lTotal §8| §7This Session §7]"
            out.add(DisplayRow("§7Mode: $sw", "", "mode"))
        }
        return out
    }

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
                    val t: Type = object : TypeToken<MutableMap<String, Data>>() {}.type
                    total = GSON.fromJson(root, t) ?: HashMap()
                }
            } catch (e: Exception) {
                fishmod.utils.SafeFiles.quarantine(file, e)
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
            fishmod.utils.SafeFiles.writeAtomic(File(FILE_PATH), json)
        }
    }
}
