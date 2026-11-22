package fishmod.features.kuudra

import fishmod.features.mining.Mining
import fishmod.features.mining.MiningHuds
import fishmod.utils.FishMsg
import fishmod.features.kuudra.KuudraSettings as S

object KuudraHuds {

    private fun pbs(t: Int): LongArray {
        val raw = S.kuudraPbs.split(';').firstOrNull { it.startsWith("$t=") }?.substringAfter('=') ?: return LongArray(5)
        val v = raw.split(',').map { it.toLongOrNull() ?: 0L }
        return LongArray(5) { v.getOrElse(it) { 0L } }
    }

    private fun savePbs(t: Int, a: LongArray) {
        val rest = S.kuudraPbs.split(';').filter { it.isNotBlank() && !it.startsWith("$t=") }
        S.kuudraPbs = (rest + "$t=${a.joinToString(",")}").joinToString(";")
    }

    fun onSplit(i: Int, ms: Long) {
        if (!S.kuudraSplits || Kuudra.tier == 0) return
        val pb = pbs(Kuudra.tier)
        val old = pb[i]
        if (old == 0L || ms < old) { pb[i] = ms; savePbs(Kuudra.tier, pb) }
        if (S.kuudraSplitsChat) FishMsg.send("§b${Kuudra.PHASES[i]} §fdone in §a${Kuudra.fmt(ms)}" + pbTag(ms, old))
    }

    fun onEnd(ms: Long) {
        if (!S.kuudraSplits || Kuudra.tier == 0) return
        val pb = pbs(Kuudra.tier)
        val old = pb[4]
        if (old == 0L || ms < old) { pb[4] = ms; savePbs(Kuudra.tier, pb) }
        if (S.kuudraSplitsChat) FishMsg.send("§6T${Kuudra.tier} Kuudra §fdone in §a${Kuudra.fmt(ms)}" + pbTag(ms, old))
    }

    private fun pbTag(ms: Long, old: Long) = when {
        old == 0L -> " §d(first PB)"
        ms < old -> " §d§lPB! §7(-${Kuudra.fmt(old - ms)})"
        else -> " §7(PB ${Kuudra.fmt(old)})"
    }

    private fun on() = Kuudra.inKuudra() && Kuudra.phase > 0

    fun init() {
        MiningHuds.reg("Kuudra Splits", "kuudra_splits", 120, 60,
            { S.kuudraSplitsX }, { S.kuudraSplitsX = it }, { S.kuudraSplitsY }, { S.kuudraSplitsY = it },
            { S.kuudraSplitsScale }, { S.kuudraSplitsScale = it }, { S.kuudraSplits && on() }, { S.kuudraSplits },
            example = { exSplits() }) { splitLines() }
        MiningHuds.reg("Kuudra Supplies", "kuudra_supplies", 120, 70,
            { S.kuudraSupplyHudX }, { S.kuudraSupplyHudX = it }, { S.kuudraSupplyHudY }, { S.kuudraSupplyHudY = it },
            { S.kuudraSupplyHudScale }, { S.kuudraSupplyHudScale = it }, { (S.kuudraSupplyMaster && S.kuudraSupplyHud) && Kuudra.inKuudra() && Kuudra.phase == 1 }, { (S.kuudraSupplyMaster && S.kuudraSupplyHud) },
            example = { supplyLines(4, listOf("Player1" to 21_300L, "Player2" to 33_800L, "Player3" to 41_050L, "Player4" to 52_600L)) }) {
            supplyLines(Kuudra.supplies, Kuudra.supplyTimes)
        }
        MiningHuds.reg("Kuudra Build", "kuudra_build", 90, 20,
            { S.kuudraBuildHudX }, { S.kuudraBuildHudX = it }, { S.kuudraBuildHudY }, { S.kuudraBuildHudY = it },
            { S.kuudraBuildHudScale }, { S.kuudraBuildHudScale = it }, { (S.kuudraBuildMaster && S.kuudraBuildHud) && Kuudra.inKuudra() && Kuudra.phase == 2 }, { (S.kuudraBuildMaster && S.kuudraBuildHud) },
            example = { buildLines(62, 3, 2) }) {
            buildLines(Kuudra.buildPct, Kuudra.builders, activeFresh().size)
        }
        MiningHuds.reg("Kuudra Fresh Timers", "kuudra_fresh", 90, 30,
            { S.kuudraFreshHudX }, { S.kuudraFreshHudX = it }, { S.kuudraFreshHudY }, { S.kuudraFreshHudY = it },
            { S.kuudraFreshHudScale }, { S.kuudraFreshHudScale = it }, { (S.kuudraBuildMaster && S.kuudraFreshHud) && Kuudra.inKuudra() && Kuudra.phase == 2 }, { (S.kuudraBuildMaster && S.kuudraFreshHud) },
            example = { freshLines(listOf("Player1" to 7_400L, "Player2" to 3_100L)) }) {
            freshLines(activeFresh())
        }
        MiningHuds.reg("Kuudra Health", "kuudra_hp", 100, 10,
            { S.kuudraHpHudX }, { S.kuudraHpHudX = it }, { S.kuudraHpHudY }, { S.kuudraHpHudY = it },
            { S.kuudraHpHudScale }, { S.kuudraHpHudScale = it }, { (S.kuudraBossMaster && S.kuudraHpHud) && Kuudra.inKuudra() && Kuudra.boss?.isAlive == true }, { (S.kuudraBossMaster && S.kuudraHpHud) },
            example = { listOf(if (S.kuudraHpScaled) hpText(20_000f, 4) else hpText(80_000f, 1)) }) {
            listOf(hpText(Kuudra.boss?.health ?: 0f))
        }
        MiningHuds.reg("Kuudra Chest Counter", "kuudra_chests", 90, 10,
            { S.kuudraChestCounterX }, { S.kuudraChestCounterX = it }, { S.kuudraChestCounterY }, { S.kuudraChestCounterY = it },
            { S.kuudraChestCounterScale }, { S.kuudraChestCounterScale = it }, { (S.kuudraChestsMaster && S.kuudraChestCounter) && Kuudra.inKuudra() }, { (S.kuudraChestsMaster && S.kuudraChestCounter) },
            example = { chestLines((S.kuudraChestCap - 3).coerceAtLeast(0)) }) {
            chestLines(S.kuudraChestCount)
        }
        MiningHuds.reg("Kuudra Profit", "kuudra_profit", 110, 20,
            { S.kuudraProfitX }, { S.kuudraProfitX = it }, { S.kuudraProfitY }, { S.kuudraProfitY = it },
            { S.kuudraProfitScale }, { S.kuudraProfitScale = it }, { (S.kuudraChestsMaster && S.kuudraProfit) && Kuudra.inKuudra() }, { (S.kuudraChestsMaster && S.kuudraProfit) },
            example = { profitLines(KuudraLoot.Book().apply { add(listOf(Triple("Example Item", 1, 1_200_000.0), Triple("Example Essence", 40, 340_000.0)), 1_540_000.0, 400_000.0) }) }) {
            profitLines(if (S.kuudraProfitMode == "Total") KuudraLoot.total else KuudraLoot.session)
        }
    }

    private fun profitLines(bk: KuudraLoot.Book): List<String> {
        val prof = bk.value - bk.cost
        val out = arrayListOf("§6§lKuudra Loot §7(${S.kuudraProfitMode})",
            "§fProfit: " + (if (prof >= 0) "§a" else "§c") + Mining.short(prof),
            "§7${bk.chests} paid chests" + if (bk.chests > 0) ", " + Mining.short(prof / bk.chests) + "/chest" else "")
        val items = bk.items.values.sortedByDescending { it.value }.take(S.kuudraProfitRows)
        for (it in items) out += "§a${Mining.short(it.value)}\t§f${it.count}x ${it.name}"
        return out
    }

    private fun chestLines(c: Int): List<String> {
        val col = if (c >= S.kuudraChestCap) "§c" else if (c >= S.kuudraChestCap - 5) "§e" else "§a"
        return listOf("§6Chests: $col$c§7/${S.kuudraChestCap}")
    }

    private fun supplyLines(supplies: Int, times: List<Pair<String, Long>>): List<String> {
        val out = arrayListOf("§6Supplies: §a$supplies§7/6")
        for ((n, t) in times) out += "§e$n §7${Kuudra.fmt(t)}"
        return out
    }

    private fun buildLines(pct: Int, builders: Int, freshers: Int) =
        listOf("§bBuild: ${colorPct(pct)}%", "§bBuilders: ${colorN(builders)}", "§bFreshers: ${colorN(freshers)}")

    private fun freshLines(rows: List<Pair<String, Long>>) = rows.map { (n, left) -> "§6$n§f: ${fishmod.utils.Fmt.f1(left / 1000.0)}s" }

    private fun exSplits(): List<String> = splitLines(5, longArrayOf(1_000L, 46_000L, 120_000L, 0L, 0L), 150_000L)

    private fun splitLines(): List<String> = splitLines(Kuudra.tier, Kuudra.phaseStart, Kuudra.now())

    private fun splitLines(tier: Int, ps: LongArray, now: Long): List<String> {
        val pb = if (tier > 0) pbs(tier) else LongArray(5)
        val out = arrayListOf("§6§lKuudra Splits" + if (tier > 0) " §7T$tier" else "")
        for (i in 0 until 4) {
            val start = ps[i]
            // a DEFEAT mid-phase only sets ps[4]; freeze the running split there too
            val end = ps[i + 1].takeIf { it > 0L } ?: ps[4]
            val v = when { start == 0L -> "§7-"; end == 0L -> "§e" + Kuudra.fmt(now - start); else -> "§a" + Kuudra.fmt(end - start) }
            out += "§b${Kuudra.PHASES[i]}: $v" + if (pb[i] > 0) " §8(${Kuudra.fmt(pb[i])})" else ""
        }
        if (ps[0] > 0) out += "§fTotal: §a" + Kuudra.fmt((if (ps[4] > 0) ps[4] else now) - ps[0]) + if (pb[4] > 0) " §8(${Kuudra.fmt(pb[4])})" else ""
        return out
    }

    fun activeFresh(): List<Pair<String, Long>> {
        val now = Kuudra.now()
        return Kuudra.freshers.mapNotNull { (n, t) -> (10_000L - (now - t)).takeIf { it > 0 }?.let { n to it } }
    }

    fun hpText(hp: Float, phase: Int = Kuudra.phase): String {
        val c = when { hp > 99000 -> "§a"; hp > 75000 -> "§2"; hp > 50000 -> "§e"; hp > 25000 -> "§6"; hp > 10000 -> "§c"; else -> "§4" }
        val k = hp / 1000.0
        return if (phase == 4 && S.kuudraHpScaled && hp <= 25000f && Kuudra.tier !in 1..4) "$c${fishmod.utils.Fmt.f1(hp * 9600.0 / 1_000_000.0)}M§7/§a240M§c❤"
        else "$c${fishmod.utils.Fmt.f1(k)}K§7/§a100K§c❤"
    }

    private fun colorPct(b: Int) = when { b >= 75 -> "§a$b"; b >= 50 -> "§e$b"; b >= 25 -> "§6$b"; else -> "§c$b" }
    private fun colorN(n: Int) = when { n >= 3 -> "§a$n"; n >= 2 -> "§e$n"; else -> "§c$n" }
}
