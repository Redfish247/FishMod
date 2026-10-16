package fishmod.features.garden

import com.google.gson.GsonBuilder
import fishmod.features.FishHudEditor
import fishmod.features.croesus.CroesusPrices
import fishmod.features.mining.MiningHuds
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
import fishmod.features.garden.GardenSettings as S

// Farming profit tracker (same shape as the Mining one): Session/Total, per category, chat + sack parsing
object FarmingProfitTracker {

    enum class Cat(val display: String, val color: String, val short: String) {
        PESTS("Pests", "§c", "Pest"), CROPS("Crops", "§a", "Crop"), VISITORS("Visitors", "§b", "Visitor"), RARE("Rare Crops", "§6", "Rare");
        companion object { fun of(s: String) = entries.firstOrNull { it.display == s } }
    }

    val CATEGORIES = arrayOf("Combined") + Cat.entries.map { it.display }
    val SORTS = arrayOf("Most Profit", "Least Profit", "Category")

    class Tracker {
        var items: MutableMap<String, Long> = LinkedHashMap()  // "CAT|Item Name" -> count
        var counts: MutableMap<String, Long> = LinkedHashMap()
        var timeMs = 0L
    }
    private class Persisted { var session = Tracker(); var total = Tracker() }

    private const val FILE_PATH = "config/fishmod/farming_profit.json"
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private var data = Persisted()
    private var dirty = false
    private var lastSaveMs = 0L
    var version = 0; private set

    private val DROP = Regex("""received (\d+)x (.+?) for killing an? ([A-Za-z ]+)!""")
    private val ACCEPTED = Regex("""^OFFER ACCEPTED with (.+?) \((\w+)\)""")
    private val COPPER = Regex("""^\+([\d,]+) Copper""")
    private val GXP = Regex("""^\+([\d,]+) Garden Experience""")
    private val REWARD_ITEM = Regex("""^\+\s*(?:([\d,]+)x?\s+)?(.+?)(?:\s+x([\d,]+))?$""")
    private val SACK_LINE = Regex("""([+-])([\d,]+) ([^(\n]+)""")
    private val PAREN = Regex("""\(([^)]+)\)""")
    private val TRAIL_PAREN = Regex("""\s*\(.*\)\s*$""")

    private var rewardUntil = 0L
    private var lastActivityMs = 0L
    private var lastTickMs = 0L
    private var scroll = 0

    fun init() {
        load()
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { save(true) }
        Events.ON_WORLD_CHANGE.register { save(true); pending.clear(); rewardUntil = 0L; false }
        Events.ON_GAME_MESSAGE.register { text ->
            if (S.gardenProfit && Garden.inGarden()) runCatching { onChat(text) }
            false
        }

        FishHudEditor.register("Farming Profit", { S.gardenProfitHudX }, { S.gardenProfitHudX = it },
            { S.gardenProfitHudY }, { S.gardenProfitHudY = it }, 180, 120, { S.gardenProfitHudScale }, { S.gardenProfitHudScale = it },
            { S.gardenProfit })
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "farming_profit")) { ctx, _ ->
            val mc = Minecraft.getInstance()
            if (!FishHudEditor.isOpen() && mc.screen !is InventoryScreen && shown())
                MiningHuds.draw(ctx, lines(), S.gardenProfitHudX, S.gardenProfitHudY, S.gardenProfitHudScale)
        }
        // In the inventory the HUD stays up and scrolls with the mouse wheel
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is InventoryScreen) return@AfterInit
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, mx, my, _ ->
                if (!shown()) return@AfterExtract
                val l = lines()
                val sc = S.gardenProfitHudScale
                MiningHuds.draw(ctx, l, S.gardenProfitHudX, S.gardenProfitHudY, sc)
                if (S.gardenProfitMode != "Total")
                    fishmod.features.other.TrackerResetButton.draw(ctx, "farming", S.gardenProfitHudX, S.gardenProfitHudY + (l.size * 10 * sc).toInt() + 2, sc, mx, my) { resetSession() }
            })
            ScreenMouseEvents.allowMouseScroll(screen).register(ScreenMouseEvents.AllowMouseScroll { _, mx, my, _, v ->
                !(shown() && over(mx, my) && scrollBy(if (v > 0) -1 else 1))
            })
        })
    }

    private fun shown() = S.gardenProfit && Garden.inGarden()

    private fun over(mx: Double, my: Double): Boolean {
        val sc = S.gardenProfitHudScale
        val lx = (mx - S.gardenProfitHudX) / sc; val ly = (my - S.gardenProfitHudY) / sc
        return lx in 0.0..220.0 && ly >= 0 && ly <= (S.gardenProfitLines + 4) * 10
    }

    private fun scrollBy(d: Int): Boolean { scroll = (scroll + d).coerceAtLeast(0); version++; return true }

    // ---- parsing ----

    private fun onChat(text: Component) {
        val s = Garden.strip(text.string).trim()
        val now = System.currentTimeMillis()
        if (s.startsWith("[Sacks]")) { onSacks(text, now); return }
        DROP.find(s)?.let { m ->
            count("Pests Killed")
            credit(Cat.PESTS, m.groupValues[2].trim(), m.groupValues[1].toLongOrNull() ?: 1L, now)
            return
        }
        if (s.startsWith("RARE CROP!")) { onRareCrop(s, now); return }
        if (ACCEPTED.containsMatchIn(s)) { count("Visitors"); rewardUntil = now + 2000; return }
        if (now < rewardUntil && s.startsWith("+")) onReward(s, now)
    }

    private fun onReward(s: String, now: Long) {
        COPPER.find(s)?.let { count("Copper", num(it.groupValues[1])); return }
        GXP.find(s)?.let { count("Garden XP", num(it.groupValues[1])); return }
        val m = REWARD_ITEM.find(s) ?: return
        val n = num(m.groupValues[1].ifEmpty { m.groupValues[3] }).takeIf { it > 0 } ?: 1L
        credit(Cat.VISITORS, m.groupValues[2].trim(), n, now)
    }

    // Unknown line format: count it, and credit whichever candidate name ItemsDb can price
    private fun onRareCrop(s: String, now: Long) {
        count("Rare Crops")
        val rest = s.removePrefix("RARE CROP!").trim()
        val cands = listOf(rest, rest.replace(TRAIL_PAREN, ""), rest.substringBefore(" (")) + PAREN.findAll(rest).map { it.groupValues[1] }.toList()
        val name = cands.map { it.trim() }.firstOrNull { it.isNotEmpty() && idOf(it) != null } ?: return
        credit(Cat.RARE, name, 1L, now)
    }

    private fun credit(cat: Cat, name: String, n: Long, now: Long) {
        if (name.isEmpty() || n <= 0 || idOf(name) == null) return
        add(cat, name, n)
        pending.getOrPut(norm(name)) { ArrayList() } += longArrayOf(n, now)
    }

    private fun num(s: String) = s.replace(",", "").toLongOrNull() ?: 0L

    // Chat-credited items that will reappear in the batched [Sacks] summary; deducted so they aren't also "Crops"
    private val pending = HashMap<String, MutableList<LongArray>>()
    private fun norm(name: String) = name.trimStart { !it.isLetterOrDigit() }.lowercase()

    private fun consumePending(item: String, n: Long, now: Long): Long {
        val q = pending[norm(item)] ?: return n
        q.removeAll { now - it[1] > 90_000 }
        var left = n
        val it = q.iterator()
        while (left > 0 && it.hasNext()) {
            val e = it.next()
            val take = minOf(left, e[0]); left -= take; e[0] -= take
            if (e[0] <= 0) it.remove()
        }
        return left
    }

    private fun idOf(name: String): String? = ItemsDb.idFor(name) ?: ItemsDb.idFor(name.trimStart { !it.isLetterOrDigit() })

    // Sack hover lists gained items; whatever chat didn't already count is farmed crops
    private fun onSacks(text: Component, now: Long) {
        val hovers = LinkedHashSet<String>()
        fun walk(c: Component) {
            (c.style.hoverEvent as? HoverEvent.ShowText)?.let { hovers += Garden.strip(it.value().string) }
            c.siblings.forEach(::walk)
        }
        walk(text)
        for (m in SACK_LINE.findAll(hovers.joinToString("\n"))) {
            if (m.groupValues[1] != "+") continue
            val n = num(m.groupValues[2]); if (n <= 0) continue
            val item = m.groupValues[3].trim()
            if (idOf(item) == null) continue
            val rest = consumePending(item, n, now)
            if (rest > 0) add(Cat.CROPS, item, rest)
        }
    }

    private fun add(cat: Cat, name: String, n: Long) {
        val k = cat.name + "|" + name
        for (t in listOf(data.session, data.total)) t.items[k] = (t.items[k] ?: 0L) + n
        lastActivityMs = System.currentTimeMillis()
        changed()
    }

    private fun count(k: String, n: Long = 1L) {
        for (t in listOf(data.session, data.total)) t.counts[k] = (t.counts[k] ?: 0L) + n
        lastActivityMs = System.currentTimeMillis()
        changed()
    }

    private fun changed() { dirty = true; version++ }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (lastTickMs > 0 && now - lastActivityMs < S.gardenProfitAfkSec.coerceIn(10, 900) * 1000L) {
            val d = (now - lastTickMs).coerceIn(0, 2000)
            data.session.timeMs += d; data.total.timeMs += d; dirty = true
        }
        lastTickMs = now
        save(false)
    }

    // ---- values ----

    fun priceOf(name: String): Double {
        val id = idOf(name) ?: return 0.0
        CroesusPrices.bazaarPrice(id, S.gardenProfitPriceMode != "Insta Sell")?.let { if (it > 0) return it }
        return CroesusPrices.price(id)
    }

    private val RARITY = mapOf(
        "COMMON" to "§f", "UNCOMMON" to "§a", "RARE" to "§9", "EPIC" to "§5", "LEGENDARY" to "§6",
        "MYTHIC" to "§d", "DIVINE" to "§b", "SPECIAL" to "§c", "VERY_SPECIAL" to "§c",
    )

    private fun rarityColor(name: String): String? {
        val id = idOf(name) ?: return null
        return RARITY[ItemsDb.get(id)?.get("tier")?.asString ?: "COMMON"]
    }

    private fun tracker(): Tracker = if (S.gardenProfitMode == "Total") data.total else data.session

    private class Row(val cat: Cat, val name: String, val n: Long, val value: Double, val split: Map<Cat, Long> = emptyMap())

    private var cacheVer = -1; private var cacheAt = 0L; private var cache: List<String> = emptyList()

    fun lines(): List<String> {
        val now = System.currentTimeMillis()
        if (cacheVer == version && now - cacheAt < 1000) return cache
        cacheVer = version; cacheAt = now
        val t = tracker()
        val only = Cat.of(S.gardenProfitCategory)
        var rows = t.items.mapNotNull { (k, n) ->
            val cat = runCatching { Cat.valueOf(k.substringBefore('|')) }.getOrNull() ?: return@mapNotNull null
            if (only != null && cat != only) return@mapNotNull null
            val name = k.substringAfter('|')
            Row(cat, name, n, priceOf(name) * n)
        }
        // Same item from several sources: one row, per-source counts shown after it
        rows = rows.groupBy { it.name }.map { (name, rs) ->
            if (rs.size == 1) rs[0]
            else Row(rs.maxBy { it.n }.cat, name, rs.sumOf { it.n }, rs.sumOf { it.value }, rs.sortedBy { it.cat.ordinal }.associate { it.cat to it.n })
        }
        rows = when (S.gardenProfitSort) {
            "Least Profit" -> rows.sortedBy { it.value }
            "Category" -> rows.sortedWith(compareBy<Row> { it.cat.ordinal }.thenByDescending { it.value })
            else -> rows.sortedByDescending { it.value }
        }
        val max = S.gardenProfitLines.coerceIn(5, 25)
        scroll = scroll.coerceIn(0, (rows.size - max).coerceAtLeast(0))
        val out = ArrayList<String>()
        out += "${Garden.legacy(S.gardenProfitColor)}§lFarming Profit §7(${S.gardenProfitCategory}, ${S.gardenProfitMode})"
        if (scroll > 0) out += "§8  ▲ $scroll more"
        for (r in rows.drop(scroll).take(max)) {
            val v = if (r.value > 0) "§6${Garden.short(r.value)}" else ""
            out += "$v	§7| ${rarityColor(r.name) ?: r.cat.color}${r.name} §f${"%,d".format(r.n)}" +
                (if (r.split.isEmpty() || !S.gardenProfitShowSources) "" else " §8(" + r.split.entries.joinToString(" ") { "${it.key.color}${it.key.short} §f${"%,d".format(it.value)}" } + "§8)")
        }
        val below = rows.size - scroll - max
        if (below > 0) out += "§8  ▼ $below more §7(scroll in inventory)"
        val counts = when (only) {
            Cat.PESTS -> listOf("Pests Killed"); Cat.VISITORS -> listOf("Visitors", "Copper", "Garden XP")
            Cat.RARE -> listOf("Rare Crops"); Cat.CROPS -> emptyList()
            null -> listOf("Pests Killed", "Visitors", "Copper", "Garden XP", "Rare Crops")
        }
        for (c in counts) t.counts[c]?.let { out += "§7$c §f${"%,d".format(it)}" }
        val profit = rows.sumOf { it.value }
        val ph = perHour(profit, t)
        out += "§7Playtime §f${fishmod.features.diana.DianaTracker.fmtTime(t.timeMs)}"
        out += "§eProfit §6${Garden.short(profit)} §7(${Garden.short(ph)}/h)"
        cache = out
        return out
    }

    private fun perHour(v: Double, t: Tracker) = if (t.timeMs < 60_000) 0.0 else v * 3_600_000.0 / t.timeMs

    fun resetSession(msg: Boolean = true) { data.session = Tracker(); changed(); if (msg) FishMsg.send("§aFarming profit session reset.") }
    fun resetTotal() { data.total = Tracker(); changed(); FishMsg.send("§aFarming profit total reset.") }

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
