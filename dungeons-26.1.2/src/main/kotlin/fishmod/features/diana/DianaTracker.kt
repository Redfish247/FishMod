package fishmod.features.diana

import com.google.gson.GsonBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import fishmod.features.croesus.CroesusPrices
import fishmod.features.item.fishmodCustomDataTag
import fishmod.utils.Constants
import fishmod.utils.FishMsg
import fishmod.utils.Misc
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.io.File
import kotlin.math.abs

// Diana loot/mob/stat tracker (Event, Session, Total) with chat parsing and announcers
object DianaTracker {

    private const val FILE_PATH = "config/fishmod/diana_tracker.json"
    private const val SB_EPOCH = 1560276000L
    private const val DAY_S = 1200L
    private const val YEAR_S = DAY_S * 372
    private const val ELECTION_DAY = 88 // Late Spring 27, 0-based
    private const val LS_WINDOW_MS = 2000L
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    class Tracker {
        var year = 0
        var items: MutableMap<String, Long> = LinkedHashMap()
        var mobs: MutableMap<String, Long> = LinkedHashMap()
        var timeMs = 0L
        fun item(k: String) = items[k] ?: 0L
        fun mob(k: String) = mobs[k] ?: 0L
    }

    class PastEvent {
        var year = 0
        var items: MutableMap<String, Long> = LinkedHashMap()
        var mobs: MutableMap<String, Long> = LinkedHashMap()
        var timeMs = 0L
        var profit = 0L
    }

    private class Stats {
        var since: MutableMap<String, Int> = HashMap()
        var b2b: MutableSet<String> = HashSet()
        var lastAt: MutableMap<String, Long> = HashMap()
        var highestMf: MutableMap<String, Int> = HashMap()
    }

    private class Persisted {
        var event = Tracker()
        var session = Tracker()
        var total = Tracker()
        var past: MutableList<PastEvent> = ArrayList()
        var stats = Stats()
        var hiddenLines: MutableSet<String> = HashSet()
    }

    class Drop(
        val key: String, val name: String, val color: String,
        val source: String? = null, val plural: String = "",
        val ls: Boolean = false, val party: Boolean = false, val loud: Boolean = false,
        val template: () -> String = { "" },
    )

    val MOBS = listOf(
        "King Minos", "Manticore", "Minos Inquisitor", "Sphinx", "Minos Champion", "Minotaur",
        "Gaia Construct", "Harpy", "Cretan Bull", "Stranded Nymph", "Siamese Lynxes", "Minos Hunter",
    )
    private val MOB_KEYS = MOBS.map { key(it) }.toSet()
    private val PREFIXES = listOf("Empyrean ", "Exalted ", "Runic ", "Venerable ", "Stalwart ", "Blessed ")
    val RARE = linkedMapOf("MINOS_INQUISITOR" to "an Inquis", "KING_MINOS" to "a King", "MANTICORE" to "a Manticore", "SPHINX" to "a Sphinx")

    val DROPS = listOf(
        Drop("SHIMMERING_WOOL", "Shimmering Wool", "§c", "KING_MINOS", "King Minos", ls = true, party = true, loud = true) { DianaSettings.dianaMsgWool },
        Drop("MANTI_CORE", "Manti-core", "§c", "MANTICORE", "Manticores", ls = true, party = true, loud = true) { DianaSettings.dianaMsgCore },
        Drop("FATEFUL_STINGER", "Fateful Stinger", "§d", "MANTICORE", "Manticores", ls = true, party = true, loud = true) { DianaSettings.dianaMsgStinger },
        Drop("CHIMERA", "Chimera", "§d", "MINOS_INQUISITOR", "Inquisitors", ls = true, party = true, loud = true) { DianaSettings.dianaMsgChimera },
        Drop("BRAIN_FOOD", "Brain Food", "§5", "SPHINX", "Sphinx", ls = true, party = true, loud = true) { DianaSettings.dianaMsgFood },
        Drop("MINOS_RELIC", "Minos Relic", "§5", "MINOS_CHAMPION", "Champions", party = true, loud = true),
        Drop("DAEDALUS_STICK", "Daedalus Stick", "§6", "MINOTAUR", "Minotaurs", party = true, loud = true),
        Drop("BRAIDED_GRIFFIN_FEATHER", "Braided Griffin Feather", "§5", party = true, loud = true),
        Drop("WASHED_UP_SOUVENIR", "Washed-up Souvenir", "§6"),
        Drop("DWARF_TURTLE_SHELMET", "Dwarf Turtle Shelmet", "§2"),
        Drop("CROCHET_TIGER_PLUSHIE", "Crochet Tiger Plushie", "§2"),
        Drop("ANTIQUE_REMEDIES", "Antique Remedies", "§2"),
        Drop("CRETAN_URN", "Cretan Urn", "§2"),
        Drop("HILT_OF_REVELATIONS", "Hilt of Revelations", "§9"),
    )
    private val DROP_BY_KEY = DROPS.associateBy { it.key }
    private val SHARDS = mapOf(
        "King Minos" to "KING_MINOS_SHARD", "Sphinx" to "SPHINX_SHARD", "Minotaur" to "MINOTAUR_SHARD",
        "Cretan Bull" to "CRETAN_BULL_SHARD", "Harpy" to "HARPY_SHARD",
    )
    private val PRICE_ID = mapOf(
        "CHIMERA" to "ENCHANTMENT_ULTIMATE_CHIMERA_1",
        "KING_MINOS_SHARD" to "SHARD_KING_MINOS", "SPHINX_SHARD" to "SHARD_SPHINX", "MINOTAUR_SHARD" to "SHARD_MINOTAUR",
        "CRETAN_BULL_SHARD" to "SHARD_CRETAN_BULL", "HARPY_SHARD" to "SHARD_HARPY",
    )
    private val NO_PRICE = setOf("TOTAL_BURROWS", "COINS")

    // Stackable mob drops with no chat line: counted from inventory pickups and the [Sacks] hover (as SBO does)
    private val STACK_DROPS = setOf("ENCHANTED_GOLD", "ENCHANTED_ANCIENT_CLAW", "ANCIENT_CLAW")
    private val SACK_NAMES = mapOf(
        "Enchanted Gold" to "ENCHANTED_GOLD", "Enchanted Ancient Claw" to "ENCHANTED_ANCIENT_CLAW", "Ancient Claw" to "ANCIENT_CLAW",
    )
    private val SACK_LINE = Regex("""\+([\d,]+) ([^(\n]+)""")
    private const val PICKUP_WINDOW_MS = 3_000L
    private const val SACK_WINDOW_MS = 30_000L
    private val invCounts = HashMap<String, Int>()
    private var invBaseline = false

    private val BURROW = Regex("^You .*?Griffin [Bb]urrow")
    private val DUG_MOB = Regex("You dug (?:out )?(?:an? )?(.+?)!$")
    // A cocoon spawns another copy of the mob, so it counts as an extra dig
    private val COCOON_MOB = Regex("CAUGHT!.*?You cocooned (?:an? )?(.+?)!$")
    private val COINS = Regex("^Wow! You dug out ([\\d,]+) coins!")
    private val TREASURE = Regex("^RARE DROP! You dug out an? (.+?)!$")
    private val RARE_DROP = Regex("^RARE DROP! (.+)$")
    // Only the number is reliable; star glyph and spacing vary (SBO matches the same way)
    private val MF = Regex("\\(\\+([\\d,]+)[^)]*Magic Find")
    private val CHARM = Regex("^CHARM! You charmed .+? and received (\\d+) (.+?) Shards?!")
    private val LS_SHARD = Regex("^LOOT SHARE You received (\\d+) (.+?) Shards? for assisting")

    private var data = Persisted()
    val event: Tracker get() = data.event
    val session: Tracker get() = data.session
    val total: Tracker get() = data.total
    val pastEvents: List<PastEvent> get() = data.past

    @JvmStatic var version = 0; private set
    private var lastLsMs = 0L
    private val lsDeathAt = HashMap<String, Long>()
    private val lsTrackedAt = HashMap<String, Long>()
    private val lastSpawn = HashMap<String, Pair<Long, Int>>()
    private val recentDrop = HashMap<String, Long>()
    private var lastActivityMs = 0L
    private var sinceActivityMs = 0L
    private var afk = true
    private var lastTickMs = 0L
    private var tickN = 0
    private var dirty = false
    private var lastSaveMs = 0L
    private var lastLine = ""
    private var lastLineMs = 0L
    private val seenHilts = HashSet<String>()
    private var hiltBaseline = false

    fun key(name: String) = name.uppercase().replace("-", "_").replace(" ", "_")

    @JvmStatic
    fun init() {
        load()
        CroesusPrices.refreshIfStale()
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        ClientLifecycleEvents.CLIENT_STOPPING.register { flushSave(true) }
        Events.ON_WORLD_CHANGE.register {
            flushSave(true); lastActivityMs = 0L; sinceActivityMs = 0L; afk = true; hiltBaseline = false; seenHilts.clear()
            invBaseline = false
            false
        }
        Events.ON_GAME_MESSAGE.register { text ->
            if (!Diana.inHub()) return@register false
            val s = text.string.replace(Constants.STRIP_COLOR_REGEX, "").trim()
            if (DianaSettings.dianaTracker && s.startsWith("[Sacks]")) onSacks(text)
            if (DianaSettings.dianaTracker) onChat(s) else hideOnly(s)
        }
        RareMobs.deathListeners.add(::onRareMobDeath)
        RareMobs.sinceProvider = ::since
        DianaTrackerHud.init()
    }

    // Message hider still works with the tracker off
    private fun hideOnly(s: String): Boolean {
        if (DianaMessageHider.shouldHide(s)) return true
        if (!DianaSettings.dianaMessageHider || s.contains(": ")) return false
        if (BURROW.containsMatchIn(s)) return true
        val name = DUG_MOB.find(s)?.groupValues?.get(1) ?: return false
        return PREFIXES.fold(name) { n, p -> n.removePrefix(p) } in MOBS
    }

    // Returns true to hide the line
    private fun onChat(s: String): Boolean {
        if (s.isEmpty() || s.contains(": ") && !s.startsWith("RARE DROP!")) return DianaMessageHider.shouldHide(s)
        val now = System.currentTimeMillis()
        val dup = s == lastLine && now - lastLineMs < 300
        lastLine = s; lastLineMs = now

        if (BURROW.containsMatchIn(s)) {
            if (!dup) track("TOTAL_BURROWS", 1)
            return DianaSettings.dianaMessageHider
        }
        COINS.find(s)?.let { m ->
            if (!dup) m.groupValues[1].replace(",", "").toLongOrNull()?.let { track("COINS", it) }
            return false
        }
        TREASURE.find(s)?.let { m ->
            if (dup) return false
            when (val n = m.groupValues[1]) {
                "Griffin Feather", "Mythos Fragment" -> track(key(n), 1)
                "Braided Griffin Feather" -> onDrop(DROP_BY_KEY.getValue("BRAIDED_GRIFFIN_FEATHER"), 0)
            }
            return false
        }
        RARE_DROP.find(s)?.let { m ->
            if (dup) return false
            val body = m.groupValues[1]
            val mf = MF.find(body)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
            val d = DROPS.firstOrNull { body.contains(it.name, true) }
            if (d != null && (d.key != "CHIMERA" || body.contains("Enchanted Book"))) onDrop(d, mf)
            return false
        }
        if (s.startsWith("LOOT SHARE")) {
            LS_SHARD.find(s)?.let { m -> if (!dup) shard(m.groupValues[2], m.groupValues[1]) ; return false }
            if (s.startsWith("LOOT SHARE You received loot for assisting")) onLootShare(now)
            return false
        }
        CHARM.find(s)?.let { m -> if (!dup) shard(m.groupValues[2], m.groupValues[1]); return false }
        DUG_MOB.find(s)?.let { m ->
            var name = m.groupValues[1]
            PREFIXES.firstOrNull { name.startsWith(it) }?.let { name = name.removePrefix(it) }
            if (name in MOBS) {
                if (!dup) onMobDug(name)
                return DianaSettings.dianaMessageHider
            }
        }
        COCOON_MOB.find(s)?.let { m ->
            var name = m.groupValues[1]
            PREFIXES.firstOrNull { name.startsWith(it) }?.let { name = name.removePrefix(it) }
            if (name == "Siamese Lynx") name = "Siamese Lynxes"
            if (name in MOBS && !dup) onMobDug(name)
            return false
        }
        return DianaMessageHider.shouldHide(s)
    }

    private fun shard(name: String, amount: String) {
        val k = SHARDS[name] ?: return
        track(k, amount.toLongOrNull() ?: return)
    }

    private fun onLootShare(now: Long) {
        lastLsMs = now
        val due = lsDeathAt.filterValues { now - it <= LS_WINDOW_MS }.keys
        lsDeathAt.clear()
        due.forEach(::trackLsMob)
    }

    private fun lsRecent() = System.currentTimeMillis() - lastLsMs <= LS_WINDOW_MS

    // Hooked to RareMobs death listeners
    @JvmStatic
    fun onRareMobDeath(name: String, nearby: Boolean) {
        val k = MOBS.firstOrNull { name.contains(it) }?.let(::key)?.takeIf { it in RARE } ?: return
        if (lsRecent()) trackLsMob(k) else if (nearby) lsDeathAt[k] = System.currentTimeMillis()
    }

    private fun trackLsMob(k: String) {
        val now = System.currentTimeMillis()
        if (now - (lsTrackedAt[k] ?: 0L) < 2000) return
        lsTrackedAt[k] = now
        track(k + "_LS", 1)
        DROPS.filter { it.source == k && it.ls }.forEach { inc(it.key + "_LS") }
    }

    private fun onMobDug(name: String) {
        val k = key(name)
        track(k, 1)
        track("TOTAL_MOBS", 1)
        RARE.keys.forEach { inc("MOBS_$it") }
        DROPS.filter { it.source == k }.forEach { inc(it.key) }
        val label = RARE[k] ?: return
        val st = data.stats
        val took = st.since["MOBS_$k"] ?: 0
        lastSpawn[k] = System.currentTimeMillis() to took
        val last = st.lastAt[k]
        if (DianaSettings.dianaStatsMessage) {
            FishMsg.send(
                if (last != null) "§eTook §c$took §eMobs and §c${fmtTime(data.total.timeMs - last)} §eto get $label!"
                else "§eTook §c$took §eMobs to get $label!"
            )
        }
        st.lastAt[k] = data.total.timeMs
        if (last != null) b2b("MOBS_$k", took, name)
        st.since["MOBS_$k"] = 0
    }

    private fun inc(k: String) {
        val v = (data.stats.since[k] ?: 0) + 1
        data.stats.since[k] = v
        if (v >= 2) data.stats.b2b.remove(k)
    }

    private fun b2b(k: String, took: Int, label: String) {
        if (took != 1) return
        if (!data.stats.b2b.add(k)) FishMsg.send("§cb2b2b $label!") else FishMsg.send("§cb2b $label!")
    }

    private fun onDrop(d: Drop, mf: Int, fromInventory: Boolean = false) {
        val now = System.currentTimeMillis()
        if (d.key == "HILT_OF_REVELATIONS") {
            if (now - (recentDrop[d.key] ?: 0L) < 5000) return
            recentDrop[d.key] = now
        }
        val ls = d.ls && lsRecent()
        val sk = if (ls) d.key + "_LS" else d.key
        val took = data.stats.since[sk] ?: 0
        val hadBefore = (data.total.items[sk]?.toLong() ?: 0L) > 0
        track(sk, 1)
        if (!ls && mf > 0 && mf > (data.stats.highestMf[d.key] ?: 0)) data.stats.highestMf[d.key] = mf
        if (d.source != null) {
            if (DianaSettings.dianaStatsMessage) FishMsg.send("§eTook §c$took §e${d.plural} to ${if (ls) "lootshare" else "get"} ${d.name}!")
            if (hadBefore) b2b(sk, took, (if (ls) "Lootshare " else "") + d.name)
            data.stats.since[sk] = 0
        }
        if (d.loud) announce(d, mf, ls, took)
        else if (fromInventory && DianaSettings.dianaAnnouncers && DianaSettings.dianaHiltMessage) FishMsg.send("§6§lRARE DROP! §r${d.color}${d.name}§e #${event.item(d.key)}${priceSuffix(d.key)}")
    }

    private fun priceSuffix(k: String): String {
        val p = priceOf(k)
        return if (p > 0) "§6 (+${short(p)} coins)" else ""
    }

    private fun announce(d: Drop, mf: Int, ls: Boolean, took: Int) {
        if (!DianaSettings.dianaAnnouncers) return
        val lsN = event.item(d.key + "_LS")
        val count = event.item(d.key) + lsN
        val countS = if (!d.ls) " #$count" else if (ls) " Total #$count LS #$lsN" else " #$count"
        val price = priceOf(d.key)
        val mfS = if (mf > 0) " (+$mf ✯ Magic Find)" else ""
        val lsS = if (ls) " (LS)" else ""
        val custom = custom(d, mf, ls, count, price, took)
        if (DianaSettings.dianaRareDropChat) {
            if (custom != null) Misc.addChatMessage(Component.literal(custom))
            else FishMsg.send("§lRARE DROP! §r${d.color}${d.name}§b$mfS§d$lsS§e$countS${priceSuffix(d.key)}")
        }
        if (DianaSettings.dianaLootScreen) {
            Misc.forceTitle(Component.literal("${d.color}§l${d.name}$lsS!"), Component.literal(if (price > 0) "§6${short(price)} coins" else ""))
        }
        if (DianaSettings.dianaLootParty && d.party) {
            val msg = custom?.replace(Constants.STRIP_COLOR_REGEX, "")
                ?: ("[FishMod] RARE DROP! ${d.name}$mfS$lsS$countS" + if (price > 0) " (+${short(price)} coins)" else "")
            fishmod.utils.ChatQueue.enqueue("pc $msg")
        }
    }

    // Drops whose message can be edited in the Diana Messages popup; blank setting = default template
    val EDITABLE = listOf("CHIMERA", "MANTI_CORE", "FATEFUL_STINGER", "BRAIN_FOOD", "SHIMMERING_WOOL")

    fun drop(key: String): Drop = DROP_BY_KEY.getValue(key)

    fun defaultMsg(d: Drop): String =
        "&6&lRARE DROP! &r${d.color.replace('§', '&')}${d.name} &b(+{mf} ✯ Magic Find) &d{lstext} &e#{amount} &6(+{price} coins)"

    private val MF_PART = Regex("\\s*\\(\\+\\{mf\\}%? ✯ Magic Find\\)")
    private val PRICE_PART = Regex("\\s*\\(\\+\\{price\\} coins\\)")
    private val SPACES = Regex(" {2,}")
    private val SPACE_CODES_SPACE = Regex(""" +((?:§.)+) +""")

    // Fills a drop template; empty MF / price brackets are dropped so the line stays clean
    fun fillTemplate(tpl: String, mf: Int, ls: Boolean, count: Long, pct: Double, price: Double, took: Int): String {
        var t = tpl
        if (mf <= 0) t = t.replace(MF_PART, "")
        if (price <= 0) t = t.replace(PRICE_PART, "")
        var out = t.replace("{mf}", if (mf > 0) "$mf" else "")
            .replace("{amount}", count.toString())
            .replace("{percentage}", "%.2f%%".format(pct))
            .replace("{price}", short(price))
            .replace("{since}", took.toString())
            .replace("{lstext}", if (ls) "(LS)" else "")
            .replace('&', '§')
        if (mf <= 0) out = out.replace(Regex("\\s*\\(\\+%? ✯ Magic Find\\)"), "")
        // An empty slot like {lstext} leaves "§d " between two spaces; fold those into one space
        var prev: String
        do { prev = out; out = out.replace(SPACE_CODES_SPACE, " $1") } while (out != prev)
        return out.replace(SPACES, " ").trim()
    }

    private fun custom(d: Drop, mf: Int, ls: Boolean, count: Long, price: Double, took: Int): String? {
        val tpl = d.template().ifBlank { if (d.key in EDITABLE) defaultMsg(d) else "" }.takeIf { it.isNotBlank() } ?: return null
        val src = d.source?.let { event.mob(it) } ?: 0L
        val pct = if (src > 0) event.item(d.key) * 100.0 / src else 0.0
        return fillTemplate(tpl, mf, ls, count, pct, price, took)
    }

    private fun track(k: String, n: Long) {
        checkYear()
        val mob = k == "TOTAL_MOBS" || k.removeSuffix("_LS") in MOB_KEYS
        for (t in listOf(data.event, data.session, data.total)) {
            val m = if (mob) t.mobs else t.items
            m[k] = (m[k] ?: 0L) + n
        }
        lastActivityMs = System.currentTimeMillis()
        sinceActivityMs = 0L
        if (afk) { afk = false; version++ }
        changed()
    }

    private fun changed() { dirty = true; version++ }

    // Mobs since last <mob> and its chance % in the event tracker
    @JvmStatic
    fun since(mob: String): Pair<Int, Double> {
        val name = MOBS.firstOrNull { mob.contains(it, true) } ?: return 0 to 0.0
        val k = key(name)
        val recent = lastSpawn[k]
        val n = if (recent != null && System.currentTimeMillis() - recent.first < 2000) recent.second
            else (data.stats.since["MOBS_$k"] ?: 0) + 1
        val tot = event.mob("TOTAL_MOBS")
        return n to (if (tot > 0) event.mob(k) * 100.0 / tot else 0.0)
    }

    fun paused() = afk

    fun isHidden(id: String) = id in data.hiddenLines

    fun toggleHidden(id: String) {
        if (!data.hiddenLines.remove(id)) data.hiddenLines.add(id)
        changed()
    }

    fun sinceCount(k: String): Int = data.stats.since[k] ?: 0
    fun highestMf(k: String): Int = data.stats.highestMf[k] ?: 0

    fun electedYear(): Int {
        val s = System.currentTimeMillis() / 1000 - SB_EPOCH
        val year = (s / YEAR_S).toInt() + 1
        val day = ((s % YEAR_S) / DAY_S).toInt()
        return if (day >= ELECTION_DAY) year else year - 1
    }

    private fun checkYear() {
        val y = electedYear()
        val ev = data.event
        if (ev.year == 0) { ev.year = y; changed(); return }
        if (ev.year >= y) return
        if (ev.mobs.values.any { it > 0 }) {
            data.past.add(PastEvent().also {
                it.year = ev.year; it.items = LinkedHashMap(ev.items); it.mobs = LinkedHashMap(ev.mobs)
                it.timeMs = ev.timeMs; it.profit = profit(ev).toLong()
            })
        }
        data.event = Tracker().also { it.year = y }
        changed()
        FishMsg.send("§aDiana event tracker reset for Year $y.")
    }

    // Bazaar items follow the Diana price mode; everything else falls back to lowest BIN
    fun priceOf(k: String): Double {
        val base = k.removeSuffix("_LS")
        if (base in NO_PRICE) return 0.0
        val id = PRICE_ID[base] ?: base
        CroesusPrices.bazaarPrice(id, DianaSettings.dianaPriceMode != "Insta Sell")?.let { if (it > 0) return it }
        return CroesusPrices.price(id)
    }

    fun profit(t: Tracker): Double =
        t.items.entries.sumOf { (k, n) -> if (k == "COINS") n.toDouble() else priceOf(k) * n }

    fun perHour(v: Double, t: Tracker): Double = if (t.timeMs < 60_000) 0.0 else v * 3_600_000.0 / t.timeMs

    fun tracker(mode: String): Tracker? = when (mode) {
        "Event" -> data.event
        "Session" -> data.session
        "Total" -> data.total
        else -> null
    }

    private fun afkMs() = DianaSettings.dianaAfkTimeout.coerceIn(15, 900) * 1000L

    private fun tick(mc: Minecraft) {
        val now = System.currentTimeMillis()
        val prev = lastTickMs
        lastTickMs = now
        tickN++
        if (prev > 0 && lastActivityMs > 0 && !afk) {
            if (now - lastActivityMs <= afkMs()) {
                val d = (now - prev).coerceIn(0, 2000)
                sinceActivityMs += d
                data.event.timeMs += d; data.session.timeMs += d; data.total.timeMs += d
                if (tickN % 20 == 0) version++
            } else {
                // Went AFK: pause and take back the idle time counted since the last dig
                for (t in listOf(data.event, data.session, data.total)) t.timeMs = (t.timeMs - sinceActivityMs).coerceAtLeast(0)
                sinceActivityMs = 0L
                afk = true
                version++
            }
            dirty = true
        }
        if (tickN % 1200 == 0) {
            checkYear()
            if (Diana.active()) CroesusPrices.refreshIfStale()
        }
        if (tickN % 10 == 0 && DianaSettings.dianaTracker) mc.player?.let { scanHilts(it) }
        if (tickN % 5 == 0 && DianaSettings.dianaTracker) mc.player?.let { scanStackDrops(mc, it) }
        flushSave(false)
    }

    private fun mobDiedWithin(ms: Long) = System.currentTimeMillis() - RareMobs.lastDianaMobDeathMs <= ms

    // Claws / enchanted gold picked up right after a Diana mob dies; menus reset the baseline so moving items isn't counted
    private fun scanStackDrops(mc: Minecraft, p: net.minecraft.world.entity.player.Player) {
        val cur = HashMap<String, Int>()
        for (st in p.inventory.nonEquipmentItems) {
            val id = ItemUtil.getId(st) ?: continue
            if (id in STACK_DROPS) cur[id] = (cur[id] ?: 0) + st.count
        }
        val menu = mc.screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>
        if (invBaseline && !menu && Diana.inHub() && mobDiedWithin(PICKUP_WINDOW_MS)) {
            for ((id, n) in cur) {
                val gained = n - (invCounts[id] ?: 0)
                if (gained > 0) track(id, gained.toLong())
            }
        }
        invCounts.clear(); invCounts.putAll(cur); invBaseline = true
    }

    // "[Sacks] +N items" lists what went straight to sacks in its hover text
    private fun onSacks(text: Component) {
        if (!mobDiedWithin(SACK_WINDOW_MS)) return
        val hovers = ArrayList<String>()
        fun walk(c: Component) {
            (c.style.hoverEvent as? net.minecraft.network.chat.HoverEvent.ShowText)?.let { hovers += it.value().string }
            c.siblings.forEach(::walk)
        }
        walk(text)
        for (h in hovers) for (m in SACK_LINE.findAll(h.replace(Constants.STRIP_COLOR_REGEX, ""))) {
            val name = m.groupValues[2].replace("Ingot", "").trim()
            val id = SACK_NAMES[name] ?: continue
            m.groupValues[1].replace(",", "").toLongOrNull()?.let { track(id, it) }
        }
    }

    // Hypixel sends no chat line for hilts, so watch for a fresh one in the inventory
    private fun scanHilts(p: net.minecraft.world.entity.player.Player) {
        val cur = ArrayList<Pair<String, Long>>()
        for (st in p.inventory.nonEquipmentItems) {
            if (ItemUtil.getId(st) != "HILT_OF_REVELATIONS") continue
            val u = ItemUtil.getUuid(st) ?: continue
            cur += u to (st.fishmodCustomDataTag()?.getLongOr("timestamp", 0L) ?: 0L)
        }
        if (!hiltBaseline) { cur.forEach { seenHilts.add(it.first) }; hiltBaseline = true; return }
        val now = System.currentTimeMillis()
        for ((u, ts) in cur) {
            if (!seenHilts.add(u) || !Diana.inHub()) continue
            val fresh = now - RareMobs.lastDianaMobDeathMs < 10_000 || (ts > 0 && abs(now - ts) < 30_000)
            if (fresh) onDrop(DROP_BY_KEY.getValue("HILT_OF_REVELATIONS"), 0, fromInventory = true)
        }
    }

    @JvmStatic
    fun resetSession() {
        data.session = Tracker()
        changed()
        FishMsg.send("§aDiana session tracker reset.")
    }

    @JvmStatic
    fun openPastEvents() {
        val mc = Minecraft.getInstance()
        mc.schedule { mc.setScreen(DianaPastEventsScreen()) }
    }

    @JvmStatic
    fun command(): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal("diana")
            .then(ClientCommands.literal("resetsession").executes { resetSession(); 1 })
            .then(ClientCommands.literal("pastevents").executes { openPastEvents(); 1 })

    // Party command replies from the Event tracker, null if not a Diana command
    @JvmStatic
    fun partyReply(cmd: String, arg: String?): String? {
        val e = data.event
        fun pct(a: Long, b: Long) = if (b <= 0) "0.00" else "%.2f".format(a * 100.0 / b)
        fun fmt(label: String, n: Long, of: Long) = "$label: $n (${pct(n, of)}%)"
        val tot = e.mob("TOTAL_MOBS")
        val r = when (cmd.lowercase()) {
            "chim", "chimera" -> fmt("Chimera", e.item("CHIMERA"), e.mob("MINOS_INQUISITOR")) + " +${e.item("CHIMERA_LS")} LS"
            "chimls" -> fmt("Chimera LS", e.item("CHIMERA_LS"), e.mob("MINOS_INQUISITOR_LS"))
            "inq", "inqs", "inquis" -> fmt("Inquisitor", e.mob("MINOS_INQUISITOR"), tot)
            "king" -> fmt("King", e.mob("KING_MINOS"), tot)
            "manti" -> fmt("Manticore", e.mob("MANTICORE"), tot)
            "sphinx" -> fmt("Sphinx", e.mob("SPHINX"), tot)
            "core" -> fmt("Cores", e.item("MANTI_CORE"), e.mob("MANTICORE"))
            "stinger" -> fmt("Stingers", e.item("FATEFUL_STINGER"), e.mob("MANTICORE"))
            "wool" -> fmt("Wool", e.item("SHIMMERING_WOOL"), e.mob("KING_MINOS"))
            "food" -> fmt("Brain Food", e.item("BRAIN_FOOD"), e.mob("SPHINX"))
            "relic", "relics" -> fmt("Relics", e.item("MINOS_RELIC"), e.mob("MINOS_CHAMPION"))
            "stick", "sticks" -> fmt("Sticks", e.item("DAEDALUS_STICK"), e.mob("MINOTAUR"))
            "hilt" -> fmt("Hilts", e.item("HILT_OF_REVELATIONS"), e.mob("MINOS_HUNTER"))
            "burrow", "burrows" -> "Burrows: ${"%,d".format(e.item("TOTAL_BURROWS"))} (${"%.2f".format(perHour(e.item("TOTAL_BURROWS").toDouble(), e))}/h)"
            "mob", "mobs" -> "Mobs: $tot (${"%.2f".format(perHour(tot.toDouble(), e))}/h)"
            "profit" -> profit(e).let { "Profit: ${short(it)} ${short(perHour(it, e))}/h" }
            "playtime" -> "Playtime: ${fmtTime(e.timeMs)}"
            "mf" -> listOf("SHIMMERING_WOOL" to "Wool", "MANTI_CORE" to "Core", "FATEFUL_STINGER" to "Stinger", "CHIMERA" to "Chim",
                "MINOS_RELIC" to "Relic", "BRAIN_FOOD" to "Food", "DAEDALUS_STICK" to "Stick")
                .joinToString(" ") { (k, l) -> "$l (${highestMf(k)}% ✯)" }
            "since" -> sinceReply(arg) ?: return null
            "diana" -> return "Diana cmds: .chim .inq .king .manti .sphinx .core .stinger .wool .food .relic .stick .hilt .burrows .mobs .profit .playtime .mf .since <x>"
            else -> return null
        }
        return "[FishMod] $r"
    }

    private fun sinceReply(arg: String?): String? {
        fun s(k: String) = sinceCount(k)
        return when (arg?.lowercase()) {
            "chim", "chimera", "chims", "book" -> "Inqs since chim: ${s("CHIMERA")}"
            "chimls", "lschim" -> "Inqs since lootshare chim: ${s("CHIMERA_LS")}"
            "stick", "sticks" -> "Minos since stick: ${s("DAEDALUS_STICK")}"
            "relic", "relics" -> "Champs since relic: ${s("MINOS_RELIC")}"
            "inq", "inqs", "inquis", "inquisitor" -> "Mobs since inq: ${s("MOBS_MINOS_INQUISITOR")}"
            "king", "kings" -> "Mobs since king: ${s("MOBS_KING_MINOS")}"
            "manti", "mantis" -> "Mobs since manti: ${s("MOBS_MANTICORE")}"
            "sphinx" -> "Mobs since sphinx: ${s("MOBS_SPHINX")}"
            "core", "cores" -> "Mantis since core: ${s("MANTI_CORE")}"
            "stinger" -> "Mantis since stinger: ${s("FATEFUL_STINGER")}"
            "wool", "wools" -> "Kings since wool: ${s("SHIMMERING_WOOL")}"
            "food" -> "Sphinx since food: ${s("BRAIN_FOOD")}"
            else -> null
        }
    }

    fun short(v: Double): String {
        val a = abs(v)
        return when {
            a >= 1e9 -> "%.2fB".format(v / 1e9)
            a >= 1e6 -> "%.2fM".format(v / 1e6)
            a >= 1e3 -> "%.1fK".format(v / 1e3)
            else -> "%.0f".format(v)
        }
    }

    fun fmtTime(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = s % 3600 / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m ${s % 60}s"
    }

    private fun load() {
        val f = File(FILE_PATH)
        if (!f.exists()) return
        try {
            data = GSON.fromJson(f.readText(), Persisted::class.java) ?: Persisted()
        } catch (e: Exception) {
            fishmod.utils.SafeFiles.quarantine(f, e)
            data = Persisted()
        }
    }

    private fun flushSave(force: Boolean) {
        if (!dirty) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSaveMs < 30_000L) return
        dirty = false
        lastSaveMs = now
        val json = GSON.toJson(data)
        fishmod.utils.IoExecutor.execute { fishmod.utils.SafeFiles.writeAtomic(File(FILE_PATH), json) }
    }
}
