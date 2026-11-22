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

    private companion object {
        private const val W = 300
        private const val H = 232
        private const val PAD = 12
        private const val TAB_H = 15
        private const val GX = 12
        private const val GY = 124
        private const val GW = 276
        private const val GH = 74
        private val CYAN = 0xFF55FFFF.toInt()
        private val GOLD = 0xFFFFAA00.toInt()
        private val GREEN = 0xFF55FF55.toInt()
        private val RED = 0xFFFF5555.toInt()
        private val BZ_RANGES = listOf("7d" to 7, "30d" to 30, "90d" to 90, "1y" to 365, "All" to Int.MAX_VALUE)
        private val AH_RANGES = listOf("24h" to "day", "7d" to "week", "30d" to "month")
        private val CACHE = ConcurrentHashMap<String, State>()
        private val TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val DAY_FMT = DateTimeFormatter.ofPattern("MMM d")
        private val HOUR_FMT = DateTimeFormatter.ofPattern("d HH:mm")
    }

    private val tag = bazaarTag(id)
    private val bazaar = CroesusPrices.bazaarPrice(tag, true) != null || CroesusPrices.bazaarPrice(tag, false) != null
    private var range = 1
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

    private fun ranges(): List<String> = if (bazaar) BZ_RANGES.map { it.first } else AH_RANGES.map { it.first }
    private fun key(): String = if (bazaar) "bz|$tag" else "ah|$tag|${AH_RANGES[range].second}"

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
        val fut = if (bazaar) CoflnetApiClient.bazaarHistory(tag) else CoflnetApiClient.auctionHistory(tag, AH_RANGES[range].second)
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
        val keep = if (bazaar) BZ_RANGES[range].second else n
        return (n - keep).coerceAtLeast(0) to n
    }

    private fun name(): String = RecipeViewer.displayName(id)
    private fun rangeX(): Int = px + W - PAD - 70
    private fun rangeY(): Int = py + GY + GH + 8
    private fun openX(): Int = px + W - PAD - 70
    private fun openY(): Int = py + H - 24

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val scale = UiScale.factor()
        ctx.pose().pushMatrix()
        ctx.pose().scale(scale, scale)
        ctx.fill(0, 0, vw(), vh(), 0xB3000000.toInt())
        ScreenTheme.panel(ctx, px, py, px + W, py + H, 10, ScreenTheme.surface(10), ScreenTheme.surface(40))
        ScreenTheme.panel(ctx, px + GX, py + GY, px + GX + GW, py + GY + GH, 6, ScreenTheme.surface(22), ScreenTheme.surface(40))
        try { ctx.item(RecipeViewer.stackFor(id), px + PAD + 4, py + 15) } catch (e: Exception) { FishDiag.fail("MarketScreen.2", "item render failed for $id", e) }
        ctx.pose().popMatrix()
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, rawX: Int, rawY: Int, delta: Float) {
        val mx = UiScale.vx(rawX)
        val my = UiScale.vx(rawY)
        UiRecorder.clear()
        val title = name()
        UiRecorder.textBold(RecipeViewer.clean(title), (px + PAD + 28).toFloat(), (py + 14).toFloat(), 9f, RecipeViewer.colorOf(title))
        UiRecorder.text(if (bazaar) "Bazaar" else "Auction House", (px + PAD + 28).toFloat(), (py + 27).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        ScreenTheme.nRect(px + PAD, py + 44, W - PAD * 2, 1, ScreenTheme.surface(40))

        drawStats()
        UiRecorder.textBold("Price History", (px + GX).toFloat(), (py + GY - 12).toFloat(), 7.5f, ScreenTheme.TEXT_COLOR)
        val state = CACHE[key()]
        val data = (state as? State.Ready)?.data
        when {
            data == null && state is State.Failed -> centered(if (tag.contains(';')) "No history for this item" else "No price history found", py + GY + GH / 2f - 4)
            data == null -> centered("Loading...", py + GY + GH / 2f - 4)
            else -> drawGraph(data, mx, my)
        }
        button("Range: ${ranges()[range]}", rangeX(), rangeY(), 70, mx, my)
        button(if (bazaar) "Open Bazaar" else "Open Auction House", openX() - (if (bazaar) 0 else 30), openY(), if (bazaar) 70 else 100, mx, my)
        UiRecorder.text("Data: SkyCofl  ·  Esc: close", (px + PAD).toFloat(), (py + H - 20).toFloat(), 6f, ScreenTheme.SUBTEXT_COLOR)

        super.extractRenderState(ctx, rawX, rawY, delta)
        if (data != null) tooltip(data, mx, my)
    }

    private fun centered(s: String, y: Float) =
        UiRecorder.text(s, px + GX + GW / 2f - UiRecorder.textWidth(s, 7f) / 2f, y, 7f, ScreenTheme.SUBTEXT_COLOR)

    private fun stat(label: String, value: String, color: Int, x: Int, y: Int) {
        UiRecorder.text(label, x.toFloat(), y.toFloat(), 7f, ScreenTheme.SUBTEXT_COLOR)
        UiRecorder.textBold(value, x.toFloat(), (y + 10).toFloat(), 8f, color)
    }

    private fun drawStats() {
        val x1 = px + PAD
        val x2 = px + W / 2 + 6
        val y1 = py + 52
        val y2 = py + 80
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
        UiRecorder.text(FishUtil.abbr(hi), (px + GX + 4).toFloat(), (py + GY + 2).toFloat(), 5.5f, ScreenTheme.SUBTEXT_COLOR)
        UiRecorder.text(FishUtil.abbr(lo), (px + GX + 4).toFloat(), (py + GY + GH - 9).toFloat(), 5.5f, ScreenTheme.SUBTEXT_COLOR)
        UiRecorder.text(fmtAxis(d.times[from]), (px + GX).toFloat(), (py + GY + GH + 3).toFloat(), 5.5f, ScreenTheme.SUBTEXT_COLOR)
        val end = fmtAxis(d.times[to - 1])
        UiRecorder.text(end, px + GX + GW - UiRecorder.textWidth(end, 5.5f), (py + GY + GH + 3).toFloat(), 5.5f, ScreenTheme.SUBTEXT_COLOR)
        var lx = px + GX + 80f
        for (s in d.series) {
            UiRecorder.fillRect(lx, (py + GY - 9).toFloat(), 5f, 5f, s.color)
            UiRecorder.text(s.label, lx + 8, (py + GY - 11).toFloat(), 6.5f, s.color)
            lx += 14 + UiRecorder.textWidth(s.label, 6.5f)
        }
    }

    private fun fmtAxis(t: Long): String =
        java.time.Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(if (bazaar) DAY_FMT else HOUR_FMT)

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

    private fun button(label: String, x: Int, y: Int, w: Int, mx: Int, my: Int) {
        val hov = inside(mx, my, x, y, w, TAB_H)
        ScreenTheme.nRoundedRectRing(x, y, w, TAB_H, TAB_H / 2, 1, if (hov) ScreenTheme.surface(34) else ScreenTheme.surface(22), ScreenTheme.surface(44))
        UiRecorder.text(label, x + w / 2f - UiRecorder.textWidth(label, 6.5f) / 2f, y + 4f, 6.5f, if (hov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR)
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        if (inside(mx, my, rangeX(), rangeY(), 70, TAB_H)) {
            range = (range + 1) % ranges().size
            load()
            return true
        }
        val ow = if (bazaar) 70 else 100
        if (inside(mx, my, openX() - (if (bazaar) 0 else 30), openY(), ow, TAB_H)) {
            val q = RecipeViewer.clean(name()).trim()
            Minecraft.getInstance().connection?.sendCommand(if (bazaar) "bz $q" else "ahs $q")
            return true
        }
        return super.mouseClicked(click, doubled)
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
