package fishmod.features.mining

import com.google.gson.GsonBuilder
import fishmod.features.FishHudEditor
import fishmod.features.croesus.CroesusPrices
import fishmod.utils.FishMsg
import fishmod.utils.events.Events
import fishmod.utils.networth.ItemsDb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.resources.Identifier
import java.io.File
import fishmod.features.mining.MiningSettings as S

// Mining profit tracker (same shape as the Diana tracker): Session/Total, per category, chat + sack parsing
object MiningProfitTracker {

    enum class Cat(val display: String, val color: String, val short: String) {
        CORPSES("Corpses", "§b", "Corpse"), MINING("Mining", "§a", "Mined"), FOSSIL("Fossil Excavator", "§6", "Fossil"),
        NUCLEUS("Nucleus Runs", "§5", "Nucleus"), CHEST("Powder Chests", "§e", "Chest");
        companion object { fun of(s: String) = entries.firstOrNull { it.display == s } }
    }

    val CATEGORIES = arrayOf("Combined") + Cat.entries.map { it.display }
    val SORTS = arrayOf("Most Profit", "Least Profit", "Category")

    class Tracker {
        var items: MutableMap<String, Long> = LinkedHashMap()  // "CAT|Item Name" -> count
        var counts: MutableMap<String, Long> = LinkedHashMap() // runs/corpses/chests
        var timeMs = 0L
    }
    private class Persisted { var session = Tracker(); var total = Tracker() }

    private const val FILE_PATH = "config/fishmod/mining_profit.json"
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private var data = Persisted()
    private var dirty = false
    private var lastSaveMs = 0L
    var version = 0; private set

    private val CORPSE = Regex("""^\s*(LAPIS|UMBER|TUNGSTEN|VANGUARD) CORPSE LOOT!""")
    private val BAR = Regex("""^▬{20,}$""")
    private val ITEM = Regex("""^\s{2,}[☠✦+]?\s*(.+?)(?:\s+x([\d,]+))?\s*$""")
    private val SACK_LINE = Regex("""([+-])([\d,]+) ([^(\n]+)""")
    private val SKIP = setOf("REWARDS", "LOOT", "CHEST LOCKPICKED", "LOOT CHEST COLLECTED")

    private var lootCat: Cat? = null
    private var lootEndMs = 0L
    private var lastCat: Cat? = null
    private var lastLootMs = 0L
    private var lastActivityMs = 0L
    private var lastTickMs = 0L
    private var scroll = 0

    fun init() {
        load()
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { save(true) }
        Events.ON_WORLD_CHANGE.register { save(true); lootCat = null; powderSeen.clear(); false }
        Events.ON_GAME_MESSAGE.register { text ->
            if (S.miningProfit && Mining.inMiningIsland()) onChat(text)
            false
        }
        Mining.breakListeners += Mining.BreakListener { _, _, _ -> lastActivityMs = System.currentTimeMillis() }

        FishHudEditor.register("Mining Profit", { S.miningProfitHudX }, { S.miningProfitHudX = it },
            { S.miningProfitHudY }, { S.miningProfitHudY = it }, 180, 120, { S.miningProfitHudScale }, { S.miningProfitHudScale = it },
            { S.miningProfit })
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "mining_profit")) { ctx, _ ->
            val mc = Minecraft.getInstance()
            if (!FishHudEditor.isOpen() && mc.screen !is InventoryScreen && shown())
                MiningHuds.draw(ctx, lines(), S.miningProfitHudX, S.miningProfitHudY, S.miningProfitHudScale)
        }
        // In the inventory the HUD stays up and scrolls with the mouse wheel
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is InventoryScreen) return@AfterInit
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, mx, my, _ ->
                if (!shown()) return@AfterExtract
                val l = lines()
                val sc = S.miningProfitHudScale
                MiningHuds.draw(ctx, l, S.miningProfitHudX, S.miningProfitHudY, sc)
                fishmod.features.other.TrackerResetButton.draw(ctx, "mining", S.miningProfitHudX, S.miningProfitHudY + (l.size * 10 * sc).toInt() + 2, sc, mx, my) {
                    if (S.miningProfitMode == "Total") resetTotal() else resetSession()
                }
            })
            ScreenMouseEvents.allowMouseScroll(screen).register(ScreenMouseEvents.AllowMouseScroll { _, mx, my, _, v ->
                !(shown() && over(mx, my) && scrollBy(if (v > 0) -1 else 1))
            })
        })
    }

    private fun shown() = S.miningProfit && Mining.inMiningIsland()

    private fun over(mx: Double, my: Double): Boolean {
        val sc = S.miningProfitHudScale
        val lx = (mx - S.miningProfitHudX) / sc; val ly = (my - S.miningProfitHudY) / sc
        return lx in 0.0..220.0 && ly >= 0 && ly <= (S.miningProfitLines + 4) * 10
    }

    private fun scrollBy(d: Int): Boolean { scroll = (scroll + d).coerceAtLeast(0); version++; return true }

    // ---- parsing ----

    private fun onChat(text: Component) {
        val s = Mining.strip(text.string)
        val now = System.currentTimeMillis()
        if (s.startsWith("[Sacks]")) { onSacks(text, now); return }
        CORPSE.find(s)?.let { startLoot(Cat.CORPSES, "Corpses", it.groupValues[1].lowercase().replaceFirstChar(Char::uppercase) + " Corpses"); return }
        when {
            s.contains("EXCAVATION COMPLETE") -> { startLoot(Cat.FOSSIL, "Excavations", null); return }
            s.contains("CRYSTAL NUCLEUS LOOT BUNDLE") -> { startLoot(Cat.NUCLEUS, "Nucleus Runs", null); return }
            s.trim() == "CHEST LOCKPICKED" -> { startLoot(Cat.CHEST, "Powder Chests", null); return }
        }
        val cat = lootCat ?: return
        if (now > lootEndMs || BAR.matches(s.trim())) { endLoot(); return }
        val m = ITEM.find(s) ?: return
        val name = m.groupValues[1].trim()
        if (name.isEmpty() || name in SKIP || name.endsWith("!") || name.endsWith(":")) return
        val n = m.groupValues[2].replace(",", "").toLongOrNull() ?: 1L
        if (!trackable(name) || name.endsWith("Powder")) return
        add(cat, name, n)
    }

    private fun trackable(name: String) = name.endsWith("Powder") || name.startsWith("Enchanted Book") || idOf(name) != null

    private val GEM = Regex("""(Rough|Flawed|Fine|Flawless|Perfect) (\w+) Gemstone$""")

    // Sack names carry a symbol prefix (e.g. "☘ Rough Jade Gemstone"), so build gem ids directly
    private fun idOf(name: String): String? =
        GEM.find(name)?.let { "${it.groupValues[1].uppercase()}_${it.groupValues[2].uppercase()}_GEM" } ?: ItemsDb.idFor(name) ?: ItemsDb.idFor(name.trimStart { !it.isLetterOrDigit() })

    private fun startLoot(cat: Cat, countKey: String, sub: String?) {
        endLoot()
        lootCat = cat; lootEndMs = System.currentTimeMillis() + 3000
        count(countKey); sub?.let { count(it) }
    }

    private fun endLoot() {
        lootCat?.let { lastCat = it; lastLootMs = System.currentTimeMillis() }
        lootCat = null
    }

    // Sack hover lists items; attributed to the loot just collected, otherwise plain mining
    private fun onSacks(text: Component, now: Long) {
        val hovers = LinkedHashSet<String>()
        fun walk(c: Component) {
            (c.style.hoverEvent as? HoverEvent.ShowText)?.let { hovers += Mining.strip(it.value().string) }
            c.siblings.forEach(::walk)
        }
        walk(text)
        val cat = lootCat ?: lastCat?.takeIf { now - lastLootMs < 5000 } ?: Cat.MINING
        // Loot blocks already list their items; sacks only add plain mining drops
        if (cat != Cat.MINING) return
        for (m in SACK_LINE.findAll(hovers.joinToString("\n"))) {
            if (m.groupValues[1] != "+") continue
            val n = m.groupValues[2].replace(",", "").toLongOrNull() ?: continue
            val item = m.groupValues[3].trim()
            if (!item.endsWith("Powder")) add(Cat.MINING, item, n)
        }
    }

    private fun add(cat: Cat, name: String, n: Long) {
        val k = cat.name + "|" + name
        for (t in listOf(data.session, data.total)) t.items[k] = (t.items[k] ?: 0L) + n
        lastActivityMs = System.currentTimeMillis()
        changed()
    }

    private fun count(k: String) {
        for (t in listOf(data.session, data.total)) t.counts[k] = (t.counts[k] ?: 0L) + 1
        lastActivityMs = System.currentTimeMillis()
        changed()
    }

    private val POWDER = Regex("""^(Mithril|Gemstone|Glacite): ([\d,]+)$""")
    private val POWDER_COLOR = mapOf("Glacite Powder" to "§b", "Mithril Powder" to "§2", "Gemstone Powder" to "§d")
    private val powderSeen = HashMap<String, Long>()
    private var tickN = 0

    // Powder from the tab's Powders widget: gains since last read, credited to the loot just opened
    private fun readPowder(now: Long) {
        for (l in Mining.tabWidget("Powders:")) {
            val m = POWDER.find(l) ?: continue
            val v = m.groupValues[2].replace(",", "").toLongOrNull() ?: continue
            val key = m.groupValues[1] + " Powder"
            val prev = powderSeen.put(key, v) ?: continue
            if (v <= prev) continue
            val cat = lootCat ?: lastCat?.takeIf { now - lastLootMs < 10_000 } ?: Cat.MINING
            add(cat, key, v - prev)
        }
    }

    private fun changed() { dirty = true; version++ }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (lastTickMs > 0 && now - lastActivityMs < S.miningProfitAfkSec.coerceIn(10, 900) * 1000L) {
            val d = (now - lastTickMs).coerceIn(0, 2000)
            data.session.timeMs += d; data.total.timeMs += d; dirty = true
        }
        lastTickMs = now
        if (lootCat != null && now > lootEndMs) endLoot()
        if (S.miningProfit && Mining.inMiningIsland() && tickN++ % 20 == 0) readPowder(now)
        save(false)
    }

    // ---- values ----

    fun priceOf(name: String): Double {
        if (name.endsWith("Powder")) return 0.0
        val id = idOf(name) ?: return 0.0
        CroesusPrices.bazaarPrice(id, S.miningProfitPriceMode != "Insta Sell")?.let { if (it > 0) return it }
        return CroesusPrices.price(id)
    }

    private val RARITY = mapOf(
        "COMMON" to "§f", "UNCOMMON" to "§a", "RARE" to "§9", "EPIC" to "§5", "LEGENDARY" to "§6",
        "MYTHIC" to "§d", "DIVINE" to "§b", "SPECIAL" to "§c", "VERY_SPECIAL" to "§c",
    )

    // Name colour from the item's Hypixel tier; null for powder/unknown items
    private fun rarityColor(name: String): String? {
        val id = idOf(name) ?: return null
        return RARITY[ItemsDb.get(id)?.get("tier")?.asString ?: "COMMON"]
    }

    private fun tracker(): Tracker = if (S.miningProfitMode == "Total") data.total else data.session

    private class Row(val cat: Cat, val name: String, val n: Long, val value: Double, val split: Map<Cat, Long> = emptyMap())

    private var cacheVer = -1; private var cacheAt = 0L; private var cache: List<String> = emptyList()

    fun lines(): List<String> {
        val now = System.currentTimeMillis()
        if (cacheVer == version && now - cacheAt < 1000) return cache
        cacheVer = version; cacheAt = now
        val t = tracker()
        val only = Cat.of(S.miningProfitCategory)
        var rows = t.items.mapNotNull { (k, n) ->
            val cat = runCatching { Cat.valueOf(k.substringBefore('|')) }.getOrNull() ?: return@mapNotNull null
            if (only != null && cat != only) return@mapNotNull null
            val name = k.substringAfter('|')
            if (name.endsWith("Powder")) return@mapNotNull null
            Row(cat, name, n, priceOf(name) * n)
        }
        // Same item from several sources: one row, per-source counts shown after it
        rows = rows.groupBy { it.name }.map { (name, rs) ->
            if (rs.size == 1) rs[0]
            else Row(rs.maxBy { it.n }.cat, name, rs.sumOf { it.n }, rs.sumOf { it.value }, rs.sortedBy { it.cat.ordinal }.associate { it.cat to it.n })
        }
        rows = when (S.miningProfitSort) {
            "Least Profit" -> rows.sortedBy { it.value }
            "Category" -> rows.sortedWith(compareBy<Row> { it.cat.ordinal }.thenByDescending { it.value })
            else -> rows.sortedByDescending { it.value }
        }
        val max = S.miningProfitLines.coerceIn(5, 25)
        scroll = scroll.coerceIn(0, (rows.size - max).coerceAtLeast(0))
        val out = ArrayList<String>()
        out += "§e§lMining Profit §7(${S.miningProfitCategory}, ${S.miningProfitMode})"
        if (scroll > 0) out += "§8  ▲ $scroll more"
        for (r in rows.drop(scroll).take(max)) {
            val v = if (r.value > 0) "§6${Mining.short(r.value)}" else ""
            out += "$v	§7| ${rarityColor(r.name) ?: r.cat.color}${r.name} §f${"%,d".format(r.n)}" +
                (if (r.split.isEmpty() || !S.miningProfitShowSources) "" else " §8(" + r.split.entries.joinToString(" ") { "${it.key.color}${it.key.short} §f${"%,d".format(it.value)}" } + "§8)")
        }
        val below = rows.size - scroll - max
        if (below > 0) out += "§8  ▼ $below more §7(scroll in inventory)"
        val counts = when (only) {
            Cat.CORPSES -> listOf("Corpses"); Cat.FOSSIL -> listOf("Excavations"); Cat.NUCLEUS -> listOf("Nucleus Runs")
            Cat.CHEST -> listOf("Powder Chests"); Cat.MINING -> emptyList()
            null -> listOf("Corpses", "Nucleus Runs", "Excavations", "Powder Chests")
        }
        for (c in counts) t.counts[c]?.let {
            val extra = if (only == null && c == "Corpses") " §7(§6${Mining.short(rows.filter { r -> r.cat == Cat.CORPSES }.sumOf { r -> r.value })}§7)" else ""
            out += "§7$c §f${"%,d".format(it)}$extra"
        }
        if (S.miningProfitShowPowder) {
            val powder = HashMap<String, Long>()
            for ((k, n) in t.items) {
                val name = k.substringAfter('|')
                if (!name.endsWith("Powder")) continue
                if (only != null && !k.startsWith(only.name + "|")) continue
                powder[name] = (powder[name] ?: 0L) + n
            }
            for ((name, n) in powder.entries.sortedBy { it.key })
                out += "${POWDER_COLOR[name] ?: "§7"}$name §f${"%,d".format(n)} §7(${Mining.short(perHour(n.toDouble(), t))}/h)"
        }
        val profit = rows.sumOf { it.value }
        val ph = perHour(profit, t)
        out += "§7Playtime §f${fishmod.features.diana.DianaTracker.fmtTime(t.timeMs)}"
        out += "§eProfit §6${Mining.short(profit)} §7(${Mining.short(ph)}/h)"
        cache = out
        return out
    }

    private fun perHour(v: Double, t: Tracker) = if (t.timeMs < 60_000) 0.0 else v * 3_600_000.0 / t.timeMs

    fun resetSession(msg: Boolean = true) { data.session = Tracker(); changed(); if (msg) FishMsg.send("§aMining profit session reset.") }
    fun resetTotal() { data.total = Tracker(); changed(); FishMsg.send("§aMining profit total reset.") }

    private fun load() {
        val f = File(FILE_PATH)
        if (!f.exists()) return
        try { data = GSON.fromJson(f.readText(), Persisted::class.java) ?: Persisted() }
        catch (e: Exception) { fishmod.utils.SafeFiles.quarantine(f, e); data = Persisted() }
    }

    private fun save(force: Boolean) {
        if (!dirty) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSaveMs < 30_000L) return
        dirty = false; lastSaveMs = now
        val json = GSON.toJson(data)
        fishmod.utils.IoExecutor.execute { fishmod.utils.SafeFiles.writeAtomic(File(FILE_PATH), json) }
    }
}
