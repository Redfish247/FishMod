package fishmod.features.fishing

import fishmod.compat.hudHidden
import fishmod.features.FishHudEditor
import fishmod.features.ProfitTrackerKit
import fishmod.utils.SkyblockLocation
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

// Line builders (pure, tested) and the HUD registration for the fishing trackers.
object FishingTrackerLines {
    private fun title(t: String, total: Boolean) =
        "§b§l$t §7(${if (total) "Total" else "Session"})" + (if (FishingTrackerStore.paused) " §c[paused]" else "")

    // Session mode rates use the session clock; total mode keeps the bucket's active time
    private fun rateMs(b: FishingBucket, total: Boolean, sessionMs: Long) = if (total) b.activeMs else sessionMs
    private fun timeLine(total: Boolean, sessionMs: Long) = "§7Session time §f${FishingTrackerMath.clock(sessionMs)}"

    private fun rarityColor(name: String): String = SeaCreatures.byName(name)?.let { "§" + it.rarity.code } ?: "§f"

    @JvmStatic
    fun seaCreatures(b: FishingBucket, total: Boolean, cap: Int, sessionMs: Long = 0L): List<String> {
        val out = ArrayList<String>()
        out += title("Sea Creatures", total)
        if (!total) out += timeLine(total, sessionMs)
        val sorted = b.scCounts.entries.sortedByDescending { it.value }
        for ((i, e) in sorted.withIndex()) {
            if (i >= cap) { out += "§7| §8${sorted.size - cap} more"; break }
            out += "§7| ${rarityColor(e.key)}${e.key} §f${"%,d".format(e.value)} §8${FishingTrackerMath.pct(e.value, b.catches)}"
        }
        out += "§7Total §f${"%,d".format(b.catches)} §7Double §f${b.doubles} §7Cocoon §f${b.cocoons}"
        return out
    }

    @JvmStatic
    fun profit(b: FishingBucket, total: Boolean, cap: Int, costPerCatch: Long, sessionMs: Long = 0L): List<String> {
        val out = ArrayList<String>()
        out += title("Fishing Profit", total)
        if (!total) out += timeLine(total, sessionMs)
        val sorted = b.drops.entries.sortedByDescending { b.dropCoins[it.key] ?: 0.0 }
        for ((i, e) in sorted.withIndex()) {
            if (i >= cap) { out += "§7| §8${sorted.size - cap} more"; break }
            val v = b.dropCoins[e.key] ?: 0.0
            out += "§7| §f${e.key} x${e.value} §6${if (v > 0) FishingTrackerMath.short(v) else "?"}"
        }
        val gross = b.dropValue()
        val costs = costPerCatch * b.catches.toDouble()
        val net = gross - costs
        out += "§7Drops §6${FishingTrackerMath.short(gross)}"
        if (costPerCatch > 0) out += "§7Costs §c-${FishingTrackerMath.short(costs)}"
        out += "§7Net §6${FishingTrackerMath.short(net)}"
        val ph = FishingTrackerMath.perHour(net, rateMs(b, total, sessionMs))
        out += "§7Coins/hr §6" + (if (ph == 0.0) "-" else FishingTrackerMath.short(ph))
        return out
    }

    @JvmStatic
    fun efficiency(b: FishingBucket, total: Boolean, sessionMs: Long = 0L): List<String> {
        val ms = rateMs(b, total, sessionMs)
        fun rate(n: Long): String { val r = FishingTrackerMath.perHour(n.toDouble(), ms); return if (r == 0.0) "-" else "%.1f".format(r) }
        return listOf(
            title("Fishing Efficiency", total),
            (if (total) "§7Active §f" else "§7Session time §f") + FishingTrackerMath.clock(ms),
            "§7SC/hr §f${rate(b.catches)}",
            "§7Catches/hr §f${rate(b.events)}",
        )
    }

    @JvmStatic
    fun barn(s: BarnState, now: Long, redAfterSec: Int): List<String> {
        if (s.startedAt == 0L) return listOf("§b§lBarn Fishing", "§7No catches yet")
        val el = now - s.startedAt
        val col = if (s.full) "§c" else if (el >= redAfterSec * 1000L) "§c" else "§a"
        return listOf("§b§lBarn Fishing", "§7Time $col${FishingTrackerMath.clock(el)} §7Caught §f${s.count}" + (if (s.full) " §c(barn full)" else ""))
    }

    @JvmStatic
    fun festival(s: FestivalState, now: Long): List<String> {
        if (s.startedAt == 0L) return listOf("§b§lFishing Festival", "§7No sharks yet")
        fun n(name: String) = s.sharks[name] ?: 0L
        val left = FestivalState.DURATION_MS - (now - s.startedAt)
        return listOf(
            "§b§lFishing Festival" + (if (s.ended || left <= 0) " §c[over]" else " §7(${FishingTrackerMath.clock(left)} left)"),
            "§7Sharks: §f${s.total()} §7(§6${n("Great White Shark")} §5${n("Tiger Shark")} §9${n("Blue Shark")} §a${n("Nurse Shark")}§7)",
        )
    }

    // One data-driven block per matching area def
    @JvmStatic
    fun areas(total: Boolean, loc: SkyblockLocation, now: Long, treasure: Boolean, dice: Boolean, cap: Int, sessionMs: Long = 0L): List<String> {
        val out = ArrayList<String>()
        for (a in FishingTrackerAreas.ALL) {
            if (a.id == FishingTrackerAreas.TREASURE && !treasure) continue
            if (a.id == FishingTrackerAreas.DICE && !dice) continue
            if (!a.inArea(loc)) continue
            val b = FishingTrackerStore.bucket("area:" + a.id, total)
            if (a.recentOnly && now - b.lastLineAt > 600_000L) continue
            out += title(a.title, total)
            if (!total) out += timeLine(total, sessionMs)
            var rows = 0
            for (e in b.scCounts.entries.sortedByDescending { it.value }) { if (rows++ >= cap) break; out += "§7| ${rarityColor(e.key)}${e.key} §f${e.value} §8${FishingTrackerMath.pct(e.value, b.catches)}" }
            for (e in b.drops.entries.sortedByDescending { it.value }) { if (rows++ >= cap) break; out += "§7| §d${e.key} §f${e.value}" }
            for (e in b.lines.entries.sortedBy { it.key }) { if (rows++ >= cap) break; out += "§7| §f${e.key} §7x${e.value}" }
            if (a.pitySc != null && a.pityDrop != null) out += "§7${a.pitySc}s since ${a.pityDrop}: §f${b.pity}"
            if (b.lineCoins != 0.0) out += "§7Net §6${FishingTrackerMath.short(b.lineCoins)}"
        }
        return out
    }
}

object FishingTrackerHud {
    private class Hud(val id: String, val lines: () -> List<String>, val on: () -> Boolean, val gx: () -> Int, val gy: () -> Int, val gs: () -> Double) {
        val cache = ProfitTrackerKit.LineCache()
    }

    private val S = FishingTrackerSettings

    private fun total() = S.fishingTrackerMode == "Total"
    private fun cap() = S.fishingTrackerLines.coerceIn(1, 30)

    private fun reg(
        name: String, id: String, w: Int, h: Int,
        gx: () -> Int, sx: (Int) -> Unit, gy: () -> Int, sy: (Int) -> Unit, gs: () -> Double, ss: (Double) -> Unit,
        on: () -> Boolean, lines: () -> List<String>,
    ) {
        val visible = { Fishing.enabled() && S.fishingTrackers && on() }
        FishHudEditor.register(name, gx, sx, gy, sy, w, h, gs, ss, visible)
        val hud = Hud(id, lines, visible, gx, gy, gs)
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", id)) { ctx, _ ->
            if (!FishHudEditor.isOpen() && hud.on() && FishingTrackerStore.shouldDraw(Fishing.active(), System.currentTimeMillis(), S.fishingShowWhen == "Either")) draw(ctx, hud)
        }
    }

    private fun draw(ctx: GuiGraphicsExtractor, hud: Hud) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return
        // Cached lines: rebuilt on a data change, else once a second (clocks)
        val rows = hud.cache.get(FishingTrackerStore.version, hud.lines)
        if (rows.isEmpty()) return
        val sc = hud.gs().toFloat()
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(hud.gx().toFloat(), hud.gy().toFloat())
        pose.scale(sc, sc)
        for (i in rows.indices) ctx.text(mc.font, rows[i], 0, i * 10, -1, true)
        pose.popMatrix()
    }

    fun init() {
        reg("Fishing Sea Creatures", "fishing_sc", 150, 80, { S.fishingScHudX }, { S.fishingScHudX = it }, { S.fishingScHudY }, { S.fishingScHudY = it },
            { S.fishingScHudScale }, { S.fishingScHudScale = it }, { S.fishingScHud }) {
            FishingTrackerLines.seaCreatures(FishingTrackerStore.bucket(FishingTrackerStore.MAIN, total()), total(), cap(), FishingTrackerStore.sessionElapsed(System.currentTimeMillis()))
        }
        reg("Fishing Profit", "fishing_profit", 150, 70, { S.fishingProfitHudX }, { S.fishingProfitHudX = it }, { S.fishingProfitHudY }, { S.fishingProfitHudY = it },
            { S.fishingProfitHudScale }, { S.fishingProfitHudScale = it }, { S.fishingProfitHud }) {
            FishingTrackerLines.profit(FishingTrackerStore.bucket(FishingTrackerStore.MAIN, total()), total(), cap(), S.fishingCostPerCatch.toLong(), FishingTrackerStore.sessionElapsed(System.currentTimeMillis()))
        }
        reg("Fishing Efficiency", "fishing_eff", 120, 40, { S.fishingEffHudX }, { S.fishingEffHudX = it }, { S.fishingEffHudY }, { S.fishingEffHudY = it },
            { S.fishingEffHudScale }, { S.fishingEffHudScale = it }, { S.fishingEffHud }) {
            FishingTrackerLines.efficiency(FishingTrackerStore.bucket(FishingTrackerStore.MAIN, total()), total(), FishingTrackerStore.sessionElapsed(System.currentTimeMillis()))
        }
        reg("Barn Fishing Timer", "fishing_barn", 140, 20, { S.fishingBarnHudX }, { S.fishingBarnHudX = it }, { S.fishingBarnHudY }, { S.fishingBarnHudY = it },
            { S.fishingBarnHudScale }, { S.fishingBarnHudScale = it }, { S.fishingBarnHud }) {
            FishingTrackerLines.barn(FishingTrackerStore.barn, System.currentTimeMillis(), S.fishingBarnSeconds)
        }
        reg("Fishing Festival", "fishing_festival", 170, 20, { S.fishingFestivalHudX }, { S.fishingFestivalHudX = it }, { S.fishingFestivalHudY }, { S.fishingFestivalHudY = it },
            { S.fishingFestivalHudScale }, { S.fishingFestivalHudScale = it }, { S.fishingFestivalHud }) {
            FishingTrackerLines.festival(FishingTrackerStore.festival, System.currentTimeMillis())
        }
        reg("Fishing Area Tracker", "fishing_area", 150, 60, { S.fishingAreaHudX }, { S.fishingAreaHudX = it }, { S.fishingAreaHudY }, { S.fishingAreaHudY = it },
            { S.fishingAreaHudScale }, { S.fishingAreaHudScale = it }, { S.fishingAreaHud }) {
            FishingTrackerLines.areas(total(), Fishing.location(), System.currentTimeMillis(), S.fishingAreaTreasure, S.fishingAreaDice, cap(), FishingTrackerStore.sessionElapsed(System.currentTimeMillis()))
        }
    }
}
