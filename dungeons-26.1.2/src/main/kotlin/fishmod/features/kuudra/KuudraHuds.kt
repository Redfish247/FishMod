package fishmod.features.kuudra

import fishmod.features.mining.Mining
import fishmod.features.mining.MiningHuds
import fishmod.utils.FishMsg
import fishmod.features.kuudra.KuudraSettings as S

object KuudraHuds {

    // PBs per tier: "5=s0,s1,s2,s3,total;4=..."
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
            { S.kuudraSplitsScale }, { S.kuudraSplitsScale = it }, { S.kuudraSplits && on() }) { splitLines() }
        MiningHuds.reg("Kuudra Supplies", "kuudra_supplies", 120, 70,
            { S.kuudraSupplyHudX }, { S.kuudraSupplyHudX = it }, { S.kuudraSupplyHudY }, { S.kuudraSupplyHudY = it },
            { S.kuudraSupplyHudScale }, { S.kuudraSupplyHudScale = it }, { S.kuudraSupplyHud && Kuudra.inKuudra() && Kuudra.phase == 1 }) {
            val out = arrayListOf("§6Supplies: §a${Kuudra.supplies}§7/6")
            for ((n, t) in Kuudra.supplyTimes) out += "§e$n §7${Kuudra.fmt(t)}"
            out
        }
        MiningHuds.reg("Kuudra Build", "kuudra_build", 90, 20,
            { S.kuudraBuildHudX }, { S.kuudraBuildHudX = it }, { S.kuudraBuildHudY }, { S.kuudraBuildHudY = it },
            { S.kuudraBuildHudScale }, { S.kuudraBuildHudScale = it }, { S.kuudraBuildHud && Kuudra.inKuudra() && Kuudra.phase == 2 }) {
            listOf("§bBuild: ${colorPct(Kuudra.buildPct)}%", "§bBuilders: ${colorN(Kuudra.builders)}", "§bFreshers: ${colorN(activeFresh().size)}")
        }
        MiningHuds.reg("Kuudra Fresh Timers", "kuudra_fresh", 90, 30,
            { S.kuudraFreshHudX }, { S.kuudraFreshHudX = it }, { S.kuudraFreshHudY }, { S.kuudraFreshHudY = it },
            { S.kuudraFreshHudScale }, { S.kuudraFreshHudScale = it }, { S.kuudraFreshHud && Kuudra.inKuudra() && Kuudra.phase == 2 }) {
            activeFresh().map { (n, left) -> "§6$n§f: ${"%.1f".format(left / 1000.0)}s" }
        }
        MiningHuds.reg("Kuudra Health", "kuudra_hp", 100, 10,
            { S.kuudraHpHudX }, { S.kuudraHpHudX = it }, { S.kuudraHpHudY }, { S.kuudraHpHudY = it },
            { S.kuudraHpHudScale }, { S.kuudraHpHudScale = it }, { S.kuudraHpHud && Kuudra.inKuudra() && Kuudra.boss?.isAlive == true }) {
            listOf(hpText(Kuudra.boss?.health ?: 0f))
        }
        MiningHuds.reg("Kuudra Chest Counter", "kuudra_chests", 90, 10,
            { S.kuudraChestCounterX }, { S.kuudraChestCounterX = it }, { S.kuudraChestCounterY }, { S.kuudraChestCounterY = it },
            { S.kuudraChestCounterScale }, { S.kuudraChestCounterScale = it }, { S.kuudraChestCounter && Kuudra.inKuudra() }) {
            val c = S.kuudraChestCount; val col = if (c >= S.kuudraChestCap) "§c" else if (c >= S.kuudraChestCap - 5) "§e" else "§a"
            listOf("§6Chests: $col$c§7/${S.kuudraChestCap}")
        }
        MiningHuds.reg("Kuudra Profit", "kuudra_profit", 110, 20,
            { S.kuudraProfitX }, { S.kuudraProfitX = it }, { S.kuudraProfitY }, { S.kuudraProfitY = it },
            { S.kuudraProfitScale }, { S.kuudraProfitScale = it }, { S.kuudraProfit && Kuudra.inKuudra() }) {
            val n = S.kuudraProfitChests
            listOf("§6Kuudra Profit: §a${Mining.short(S.kuudraProfitTotal)}", "§7$n paid chests" + if (n > 0) ", §a${Mining.short(S.kuudraProfitTotal / n)}§7/chest" else "")
        }
    }

    private fun splitLines(): List<String> {
        val ps = Kuudra.phaseStart
        val pb = if (Kuudra.tier > 0) pbs(Kuudra.tier) else LongArray(5)
        val out = arrayListOf("§6§lKuudra Splits" + if (Kuudra.tier > 0) " §7T${Kuudra.tier}" else "")
        val now = Kuudra.now()
        for (i in 0 until 4) {
            val start = ps[i]
            val end = ps[i + 1]
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

    fun hpText(hp: Float): String {
        val c = when { hp > 99000 -> "§a"; hp > 75000 -> "§2"; hp > 50000 -> "§e"; hp > 25000 -> "§6"; hp > 10000 -> "§c"; else -> "§4" }
        val k = hp / 1000.0
        return if (hp <= 25000 && S.kuudraHpScaled && Kuudra.tier == 5) "$c${"%.2f".format(k * 9.6)}M§7/§a240M§c❤"
        else "$c${"%.1f".format(k)}K§7/§a100K§c❤"
    }

    private fun colorPct(b: Int) = when { b >= 75 -> "§a$b"; b >= 50 -> "§e$b"; b >= 25 -> "§6$b"; else -> "§c$b" }
    private fun colorN(n: Int) = when { n >= 3 -> "§a$n"; n >= 2 -> "§e$n"; else -> "§c$n" }
}
