package fishmod.features.hud
import fishmod.compat.showScreen

import fishmod.compat.GLFW
import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.features.dungeon.trackers.CroesusPrices
import fishmod.pv.api.CoflnetApiClient
import fishmod.utils.FishUtil
import fishmod.utils.data.NeuRepo
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/** Price history for one item: bazaar (daily) or auction house (hourly), drawn as a line graph. Data: SkyCofl public API. */
class MarketScreen(private val id: String, private val parent: Screen?) : Screen(Component.literal("Market")), HasUiOverlay {

    private class Series(val label: String, val color: Int, val values: DoubleArray)
    private class Data(val times: LongArray, val series: List<Series>, val volume: Long)

    private sealed class State {
        object Loading : State()
        object Failed : State()
        class Ready(val data: Data) : State()
    }

    private class Bin(val uuid: String, val price: Long, val count: Int, val reforge: String, val tier: String, val endMs: Long)

    private sealed class BinState {
        object Loading : BinState()
        object Failed : BinState()
        class Ready(val bins: List<Bin>) : BinState()
    }

    private companion object {
        private const val ROW_H = 11
        private const val ROWS = 9
        private val PET_RE = Regex("^([A-Z0-9_]+);([0-5])$")
        private val RARITIES = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")
        private val LVL_RE = Regex("\\[Lvl [^\\]]*]\\s*")
        private val BINS = ConcurrentHashMap<String, BinState>()
        private const val W = 360
        private const val H = 290
        private const val PAD = 12
        private const val TAB_H = 15
        private const val GX = 14
        private const val GY = 140
        private const val GW = 332
        private const val GH = 104
        private val CYAN = 0xFF55FFFF.toInt()
        private val GOLD = 0xFFFFAA00.toInt()
        private val GREEN = 0xFF55FF55.toInt()
        private val RED = 0xFFFF5555.toInt()
        private class BzRange(val label: String, val fine: String?, val days: Int)
        private val BZ_RANGES = listOf(BzRange("1h", "hour", 0), BzRange("24h", "day", 0), BzRange("7d", "week", 0), BzRange("30d", null, 30), BzRange("90d", null, 90), BzRange("1y", null, 365), BzRange("All", null, Int.MAX_VALUE))
        private val AH_RANGES = listOf("24h" to "day", "7d" to "week", "30d" to "month")
        private val CACHE = ConcurrentHashMap<String, State>()
        private val TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val DAY_FMT = DateTimeFormatter.ofPattern("MMM d")
        private val CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm")
        private val HOUR_FMT = DateTimeFormatter.ofPattern("d HH:mm")
    }

    private val pet = PET_RE.matchEntire(id)?.takeIf { NeuRepo.get(id)?.get("displayname")?.asString?.contains("[Lvl") == true }
    private val rarity: String? = pet?.let { RARITIES.getOrNull(it.groupValues[2].toInt()) }
    private val tag = if (pet != null) "PET_${pet.groupValues[1]}" else bazaarTag(id)
    private val bazaar = CroesusPrices.bazaarPrice(tag, true) != null || CroesusPrices.bazaarPrice(tag, false) != null
    private var range = 1
    private var view = 0
    private var scroll = 0
    private var px = 0
    private var py = 0

    override fun isPauseScreen(): Boolean = false
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    private fun vw(): Int = (width / UiScale.factor()).toInt()
    private fun vh(): Int = (height / UiScale.factor()).toInt()

    private fun bazaarTag(id: String): String {
        val semi = id.indexOf(';')
        return if (semi > 0 && NeuRepo.get(id)?.get("itemid")?.asString == "minecraft:enchanted_book") "ENCHANTMENT_${id.substring(0, semi)}_${id.substring(semi + 1)}" else id
    }

    private fun ranges(): List<String> = if (bazaar) BZ_RANGES.map { it.label } else AH_RANGES.map { it.first }
    private fun key(): String = if (bazaar) "bz|$tag|${BZ_RANGES[range].fine}" else "ah|$tag|$rarity|${AH_RANGES[range].second}"

    override fun init() {
        px = (vw() - W) / 2
        py = (vh() - H) / 2
        load()
    }

    private fun load() {
        val k = key()
        if (CACHE[k] is State.Ready) return
        if (CACHE.putIfAbsent(k, State.Loading) != null && CACHE[k] !is State.Failed) return
        CACHE[k] = State.Loading
        val fut = if (bazaar) CoflnetApiClient.bazaarHistory(tag, BZ_RANGES[range].fine) else CoflnetApiClient.auctionHistory(tag, AH_RANGES[range].second, rarity)
        fut.whenComplete { res, err ->
            try {
                if (err != null || res == null || !res.isPresent || res.get().size() == 0) { CACHE[k] = State.Failed; return@whenComplete }
                CACHE[k] = State.Ready(if (bazaar) parseBazaar(res.get()) else parseAuction(res.get()))
            } catch (e: Exception) {
                FishDiag.fail("MarketScreen.1", "market history parse failed for $tag", e)
                CACHE[k] = State.Failed
            }
        }
    }

    private fun loadBins() {
        val cur = BINS[binKey()]
        if (cur is BinState.Ready || cur is BinState.Loading) return
        BINS[binKey()] = BinState.Loading
        CoflnetApiClient.activeBins(tag, rarity).whenComplete { res, err ->
            try {
                if (err != null || res == null || !res.isPresent) { BINS[binKey()] = BinState.Failed; return@whenComplete }
                val bins = CoflnetApiClient.objects(res.get()).map {
                    Bin(it.get("uuid")?.asString ?: "", it.get("startingBid")?.asLong ?: 0L, it.get("count")?.asInt ?: 1,
                        it.get("reforge")?.asString?.takeIf { r -> r != "None" } ?: "", it.get("tier")?.asString ?: "", epoch(it.get("end")?.asString))
                }.filter { it.uuid.isNotEmpty() && it.price > 0 }.sortedBy { it.price }
                BINS[binKey()] = BinState.Ready(bins)
            } catch (e: Exception) {
                FishDiag.fail("MarketScreen.3", "active BIN parse failed for $tag", e)
                BINS[binKey()] = BinState.Failed
            }
        }
    }

    private fun binKey(): String = "$tag|$rarity"

    private fun binsReady(): List<Bin>? = (BINS[binKey()] as? BinState.Ready)?.bins

    private fun timeLeft(endMs: Long): String {
        val s = ((endMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        return when { s >= 86400 -> "${s / 86400}d"; s >= 3600 -> "${s / 3600}h"; s >= 60 -> "${s / 60}m"; else -> "${s}s" }
    }

    private fun rowY(i: Int): Int = py + GY + 3 + i * ROW_H
    private fun viewX(): Int = px + PAD
    private fun listingsView(): Boolean = !bazaar && view == 1

    private fun epoch(s: String?): Long = try { LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() } catch (_: Exception) { 0L }

    private fun parseBazaar(arr: com.google.gson.JsonArray): Data {
        val rows = CoflnetApiClient.objects(arr).map { epoch(it.get("timestamp")?.asString) to it }.filter { it.first > 0 }.sortedBy { it.first }
        val buy = DoubleArray(rows.size) { rows[it].second.get("buy")?.asDouble ?: 0.0 }
        val sell = DoubleArray(rows.size) { rows[it].second.get("sell")?.asDouble ?: 0.0 }
        val vol = rows.lastOrNull()?.second?.get("buyMovingWeek")?.asLong ?: 0L
        return Data(LongArray(rows.size) { rows[it].first }, listOf(Series("Instant Buy", CYAN, buy), Series("Instant Sell", GOLD, sell)), vol)
    }

    private fun parseAuction(arr: com.google.gson.JsonArray): Data {
        val rows = CoflnetApiClient.objects(arr).map { epoch(it.get("time")?.asString) to it }.filter { it.first > 0 }.sortedBy { it.first }
        fun col(n: String) = DoubleArray(rows.size) { rows[it].second.get(n)?.asDouble ?: 0.0 }
        val vol = rows.sumOf { it.second.get("volume")?.asLong ?: 0L }
        return Data(LongArray(rows.size) { rows[it].first }, listOf(Series("Average", GREEN, col("avg")), Series("Lowest", CYAN, col("min")), Series("Highest", RED, col("max"))), vol)
    }

    /** Last N points of the loaded data for the selected range. */
    private fun visible(d: Data): Pair<Int, Int> {
        val n = d.times.size
        val keep = if (bazaar) (if (BZ_RANGES[range].fine != null) n else BZ_RANGES[range].days) else n
        return (n - keep).coerceAtLeast(0) to n
    }

    private fun name(): String = LVL_RE.replace(RecipeViewer.displayName(id), "")
    private fun barY(): Int = py + H - 32
    private fun openW(): Int = if (bazaar) 80 else 110
    private fun openX(): Int = px + W - PAD - openW()
    private fun pillY(): Int = py + GY - 18
    private fun pillW(i: Int): Int = UiRecorder.textWidth(ranges()[i], 6.5f).toInt() + 12
    private fun pillX(i: Int): Int {
        var x = px + GX + GW
        for (j in ranges().indices.reversed()) { x -= pillW(j); if (j == i) return x; x -= 3 }
        return x
    }
    private fun pillAt(mx: Int, my: Int): Int {
        if (listingsView() || my < pillY() || my >= pillY() + 13) return -1
        return ranges().indices.firstOrNull { mx >= pillX(it) && mx < pillX(it) + pillW(it) } ?: -1
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val scale = UiScale.factor()
        ctx.pose().pushMatrix()
        ctx.pose().scale(scale, scale)
        ctx.fill(0, 0, vw(), vh(), 0xB3000000.toInt())
        ScreenTheme.panel(ctx, px, py, px + W, py + H, 10, ScreenTheme.surface(10), ScreenTheme.surface(40))
        ScreenTheme.panel(ctx, px + PAD, py + 50, px + W - PAD, py + 112, 8, ScreenTheme.surface(18), ScreenTheme.surface(40))
        ScreenTheme.panel(ctx, px + GX, py + GY, px + GX + GW, py + GY + GH, 6, ScreenTheme.surface(22), ScreenTheme.surface(40))
        try { ctx.item(RecipeViewer.stackFor(id), px + PAD + 4, py + 15) } catch (e: Exception) { FishDiag.fail("MarketScreen.2", "item render failed for $id", e) }
        ctx.pose().popMatrix()
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, rawX: Int, rawY: Int, delta: Float) {
        val mx = UiScale.vx(rawX)
        val my = UiScale.vx(rawY)
        UiRecorder.clear()
        val title = name()
        UiRecorder.textBold(RecipeViewer.clean(title), (px + PAD + 28).toFloat(), (py + 14).toFloat(), 10f, RecipeViewer.colorOf(title))
        UiRecorder.text(if (bazaar) "Bazaar" else if (rarity != null) "${rarity.lowercase().replaceFirstChar { it.uppercase() }} Pet · Auction House" else "Auction House", (px + PAD + 28).toFloat(), (py + 28).toFloat(), 7.5f, ScreenTheme.SUBTEXT_COLOR)
        UiRecorder.fillRect((px + 10).toFloat(), py.toFloat(), (W - 20).toFloat(), 2f, ScreenTheme.ACCENT)
        ScreenTheme.nRect(px + PAD, py + 44, W - PAD * 2, 1, ScreenTheme.surface(40))

        drawStats()
        UiRecorder.textBold("Price History", (px + GX).toFloat(), (py + GY - 13).toFloat(), 8.5f, ScreenTheme.TEXT_COLOR)
        val state = CACHE[key()]
        val data = (state as? State.Ready)?.data
        if (listingsView()) { drawBins(mx, my) } else when {
            data == null && state is State.Failed -> centered("No price history found", py + GY + GH / 2f - 4)
            data == null -> centered("Loading...", py + GY + GH / 2f - 4)
            else -> drawGraph(data, mx, my)
        }
        if (!listingsView()) for (i in ranges().indices) pill(ranges()[i], pillX(i), pillW(i), mx, my, i == range)
        if (!bazaar) button(if (view == 0) "View: History" else "View: Lowest BINs", viewX(), barY(), 104, mx, my, view == 1)
        button(if (bazaar) "Open Bazaar" else "Open Auction House", openX(), barY(), openW(), mx, my, false)
        UiRecorder.text("Data: SkyCofl  ·  Esc: close", (px + PAD).toFloat(), (py + H - 12).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)

        super.extractRenderState(ctx, rawX, rawY, delta)
        if (data != null && !listingsView()) tooltip(data, mx, my)
    }

    private fun drawBins(mx: Int, my: Int) {
        val bins = binsReady()
        if (bins == null) {
            centered(if (BINS[binKey()] is BinState.Failed) "Could not load auctions" else "Loading...", py + GY + GH / 2f - 4)
            return
        }
        if (bins.isEmpty()) { centered("No active BIN listings", py + GY + GH / 2f - 4); return }
        val x = (px + GX + 6).toFloat()
        for (r in 0 until ROWS) {
            val b = bins.getOrNull(scroll + r) ?: break
            val y = rowY(r)
            if (inside(mx, my, px + GX + 2, y - 1, GW - 4, ROW_H)) UiRecorder.fillRect((px + GX + 2).toFloat(), (y - 1).toFloat(), (GW - 4).toFloat(), ROW_H.toFloat(), ScreenTheme.ink(40))
            UiRecorder.text("#${scroll + r + 1}", x, y.toFloat(), 7.5f, ScreenTheme.SUBTEXT_COLOR)
            UiRecorder.textBold("%,d".format(b.price), x + 24, y.toFloat(), 7.5f, GOLD)
            val info = listOf(if (b.count > 1) "x${b.count}" else "", b.reforge, b.tier.lowercase().replaceFirstChar { it.uppercase() }).filter { it.isNotEmpty() }.joinToString(" · ")
            UiRecorder.text(info, x + 120, y.toFloat(), 7.5f, ScreenTheme.TEXT_COLOR)
            val t = timeLeft(b.endMs)
            UiRecorder.text(t, px + GX + GW - 8 - UiRecorder.textWidth(t, 7.5f), y.toFloat(), 7.5f, ScreenTheme.SUBTEXT_COLOR)
        }
        val hint = "${bins.size} listings · scroll · click to view"
        UiRecorder.text(hint, px + GX + GW - UiRecorder.textWidth(hint, 6.5f), (py + GY + GH + 4).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
    }

    private fun centered(s: String, y: Float) =
        UiRecorder.text(s, px + GX + GW / 2f - UiRecorder.textWidth(s, 7f) / 2f, y, 7f, ScreenTheme.SUBTEXT_COLOR)

    private fun stat(label: String, value: String, color: Int, x: Int, y: Int) {
        UiRecorder.text(label, x.toFloat(), y.toFloat(), 7.5f, ScreenTheme.SUBTEXT_COLOR)
        UiRecorder.textBold(value, x.toFloat(), (y + 11).toFloat(), 9.5f, color)
    }

    private fun drawStats() {
        val x1 = px + PAD + 8
        val x2 = px + W / 2 + 8
        val y1 = py + 54
        val y2 = py + 86
        if (bazaar) {
            val buy = CroesusPrices.bazaarPrice(tag, true) ?: 0.0
            val sell = CroesusPrices.bazaarPrice(tag, false) ?: 0.0
            stat("Instant Buy", coins(buy), GOLD, x1, y1)
            stat("Instant Sell", coins(sell), GOLD, x2, y1)
            stat("Spread", coins(buy - sell), GOLD, x1, y2)
            stat("Margin", if (sell > 0) "%.1f%%".format((buy - sell) / sell * 100) else "-", GOLD, x2, y2)
        } else {
            val d = (CACHE[key()] as? State.Ready)?.data
            val last = { s: Int -> d?.series?.getOrNull(s)?.values?.lastOrNull() }
            val bin = CroesusPrices.cachedPrice(id).takeIf { it > 0 }
            stat("Lowest BIN", bin?.let { coins(it) } ?: "-", GOLD, x1, y1)
            stat("Latest Average", last(0)?.let { coins(it) } ?: "-", GOLD, x2, y1)
            stat("Range Low", d?.series?.getOrNull(1)?.values?.filter { it > 0 }?.minOrNull()?.let { coins(it) } ?: "-", GOLD, x1, y2)
            stat("Sales in Range", d?.let { "%,d".format(it.volume) } ?: "-", GOLD, x2, y2)
        }
    }

    private fun coins(v: Double) = if (v >= 1000) "${"%,.1f".format(v)} coins" else "${"%,.0f".format(v)} coins"

    private fun graphRange(d: Data): Triple<Int, Int, Pair<Double, Double>> {
        val (from, to) = visible(d)
        var lo = Double.MAX_VALUE
        var hi = 0.0
        for (s in d.series) for (i in from until to) { val v = s.values[i]; if (v > 0) { if (v < lo) lo = v; if (v > hi) hi = v } }
        if (lo == Double.MAX_VALUE) { lo = 0.0 }
        val pad = ((hi - lo) * 0.08).coerceAtLeast(hi * 0.002).coerceAtLeast(1.0)
        return Triple(from, to, (lo - pad).coerceAtLeast(0.0) to hi + pad)
    }

    private fun xAt(i: Int, from: Int, to: Int): Float {
        val n = (to - from - 1).coerceAtLeast(1)
        return px + GX + 4f + (GW - 8f) * (i - from) / n
    }

    private fun yAt(v: Double, lo: Double, hi: Double): Float {
        val f = if (hi > lo) ((v - lo) / (hi - lo)).toFloat() else 0.5f
        return py + GY + GH - 4f - (GH - 8f) * f
    }

    private fun drawGraph(d: Data, mx: Int, my: Int) {
        val (from, to, mm) = graphRange(d)
        if (to - from < 2) { centered("Not enough data yet", py + GY + GH / 2f - 4); return }
        val (lo, hi) = mm
        UiRecorder.pushScissor((px + GX + 1).toFloat(), (py + GY + 1).toFloat(), (GW - 2).toFloat(), (GH - 2).toFloat())
        for (s in d.series) {
            for (i in from until to - 1) {
                if (s.values[i] <= 0 || s.values[i + 1] <= 0) continue
                UiRecorder.line(xAt(i, from, to), yAt(s.values[i], lo, hi), xAt(i + 1, from, to), yAt(s.values[i + 1], lo, hi), 1f, s.color)
            }
        }
        val hov = hoverIndex(d, mx, my, from, to)
        if (hov >= 0) UiRecorder.fillRect(xAt(hov, from, to), (py + GY + 1).toFloat(), 1f, (GH - 2).toFloat(), ScreenTheme.ink(150))
        UiRecorder.popScissor()
        UiRecorder.text(FishUtil.abbr(hi), (px + GX + 4).toFloat(), (py + GY + 2).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        UiRecorder.text(FishUtil.abbr(lo), (px + GX + 4).toFloat(), (py + GY + GH - 10).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        UiRecorder.text(fmtAxis(d.times[from]), (px + GX).toFloat(), (py + GY + GH + 4).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        val end = fmtAxis(d.times[to - 1])
        UiRecorder.text(end, px + GX + GW - UiRecorder.textWidth(end, 6.5f), (py + GY + GH + 4).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        val total = d.series.sumOf { (14 + UiRecorder.textWidth(it.label, 7.5f)).toDouble() }.toFloat() - 6f
        var lx = px + GX + GW / 2f - total / 2f
        for (s in d.series) {
            UiRecorder.fillRect(lx, (py + GY + GH + 5).toFloat(), 6f, 6f, s.color)
            UiRecorder.text(s.label, lx + 8, (py + GY + GH + 4).toFloat(), 7.5f, s.color)
            lx += 14 + UiRecorder.textWidth(s.label, 7.5f)
        }
    }

    private fun fmtAxis(t: Long): String =
        java.time.Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(when { bazaar && BZ_RANGES[range].fine == null -> DAY_FMT; bazaar && BZ_RANGES[range].fine != "week" -> CLOCK_FMT; else -> HOUR_FMT })

    private fun hoverIndex(d: Data, mx: Int, my: Int, from: Int, to: Int): Int {
        if (mx < px + GX || mx > px + GX + GW || my < py + GY || my > py + GY + GH) return -1
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (i in from until to) { val dist = Math.abs(xAt(i, from, to) - mx); if (dist < bestDist) { bestDist = dist; best = i } }
        return best
    }

    private fun tooltip(d: Data, mx: Int, my: Int) {
        val (from, to, _) = graphRange(d)
        val i = hoverIndex(d, mx, my, from, to)
        if (i < 0) return
        val lines = ArrayList<Component>()
        lines.add(Component.literal("§f" + java.time.Instant.ofEpochMilli(d.times[i]).atZone(ZoneId.systemDefault()).format(TIME_FMT)))
        for (s in d.series) {
            val code = when (s.color) { CYAN -> "§b"; GOLD -> "§6"; GREEN -> "§a"; else -> "§c" }
            lines.add(Component.literal("$code${s.label}: §f${coins(s.values[i])}"))
        }
        ScreenTheme.nItemTooltip(lines, mx, my, vw(), vh(), paintScale = UiScale.factor())
    }

    private fun button(label: String, x: Int, y: Int, w: Int, mx: Int, my: Int, selected: Boolean) {
        val hov = inside(mx, my, x, y, w, TAB_H)
        val fill = when { selected -> ScreenTheme.ACCENT; hov -> ScreenTheme.surface(34); else -> ScreenTheme.surface(22) }
        ScreenTheme.nRoundedRectRing(x, y, w, TAB_H, TAB_H / 2, 1, fill, if (selected) ScreenTheme.ACCENT else ScreenTheme.surface(44))
        val color = when { selected -> ScreenTheme.onAccent(); hov -> ScreenTheme.TEXT_COLOR; else -> ScreenTheme.SUBTEXT_COLOR }
        UiRecorder.text(label, x + w / 2f - UiRecorder.textWidth(label, 7f) / 2f, y + 4f, 7f, color)
    }

    private fun pill(label: String, x: Int, w: Int, mx: Int, my: Int, selected: Boolean) {
        val y = pillY()
        val hov = inside(mx, my, x, y, w, 13)
        val fill = when { selected -> ScreenTheme.ACCENT; hov -> ScreenTheme.surface(34); else -> ScreenTheme.surface(22) }
        ScreenTheme.nRoundedRectRing(x, y, w, 13, 6, 1, fill, if (selected) ScreenTheme.ACCENT else ScreenTheme.surface(44))
        val color = when { selected -> ScreenTheme.onAccent(); hov -> ScreenTheme.TEXT_COLOR; else -> ScreenTheme.SUBTEXT_COLOR }
        UiRecorder.text(label, x + w / 2f - UiRecorder.textWidth(label, 6.5f) / 2f, y + 3.5f, 6.5f, color)
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        if (!bazaar && inside(mx, my, viewX(), barY(), 104, TAB_H)) {
            view = 1 - view
            scroll = 0
            if (view == 1) loadBins()
            return true
        }
        if (listingsView()) {
            val bins = binsReady()
            if (bins != null) for (r in 0 until ROWS) {
                val b = bins.getOrNull(scroll + r) ?: break
                if (inside(mx, my, px + GX + 2, rowY(r) - 1, GW - 4, ROW_H)) {
                    Minecraft.getInstance().connection?.sendCommand("viewauction ${b.uuid}")
                    Minecraft.getInstance().showScreen(null)
                    return true
                }
            }
        }
        pillAt(mx, my).takeIf { it >= 0 }?.let { range = it; scroll = 0; load(); return true }
        if (inside(mx, my, openX(), barY(), openW(), TAB_H)) {
            val q = RecipeViewer.clean(name()).trim()
            Minecraft.getInstance().connection?.sendCommand(if (bazaar) "bz $q" else "ahs $q")
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (!listingsView()) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        val max = ((binsReady()?.size ?: 0) - ROWS).coerceAtLeast(0)
        scroll = (scroll - Math.signum(verticalAmount).toInt()).coerceIn(0, max)
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        if (Minecraft.getInstance().options.keyInventory.matches(input)) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun onClose() {
        Minecraft.getInstance().showScreen(parent)
    }

    override fun paintUiOverlay() {
        fishmod.utils.rendering.UiRenderer.paint(width, height, UiScale.factor())
    }
}
