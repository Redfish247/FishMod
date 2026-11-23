package fishmod.features.hunting

import fishmod.features.dungeon.trackers.CroesusPrices
import fishmod.features.ProfitTrackerKit
import fishmod.features.mining.Mining
import fishmod.features.mining.MiningHuds
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import fishmod.features.fishing.FishingWorldSettings as W

object ShardTracker {
    private val counts = LinkedHashMap<String, Int>()
    private val CAUGHT = Regex("^You caught an? (.+?) and gained (?:a|(\\d+)x) (.+?) Shard!$")
    private val DIRECT = Regex("^You caught (?:a|x(\\d+)) (.+?) Shards?!$")
    private val FLOOR = Regex("^FLOOR DROP! You found (.+?) Shard on the ground!$")
    private val HIVE = Regex("^HIVE! You found .+ and (.+?) Shard!$")
    private val CHARM = Regex("^CHARM! You charmed the (.+?) and received (\\d+) (.+?) Shards?!$")
    private val LOOT_SHARE = Regex("^LOOT SHARE You received (\\d+) (.+?) Shards? for assisting .+!$")
    private val CONTRIB = Regex("^SHARD! Your contribution earned you the (.+?) Shard!$")
    private val SENT = Regex("^You sent an? (.+?) Shard to your Hunting Box\\.$")
    private val COUNT_SUFFIX = Regex(" \\(\\d+\\)$")
    private const val BAZAAR_BUY = "[Bazaar] Bought "

    // from NoFrills (ShardData.parseId): shard names whose bazaar id differs
    private val ID_FIX = mapOf("cinderbat" to "SHARD_CINDER_BAT", "abyssal lanternfish" to "SHARD_ABYSSAL_LANTERN",
        "stridersurfer" to "SHARD_STRIDER_SURFER", "bogged" to "SHARD_SEA_ARCHER", "loch emperor" to "SHARD_SEA_EMPEROR",
        "end stone protector" to "SHARD_ENDSTONE_PROTECTOR", "lotusfish" to "SHARD_LOTUS_FISH", "inkling" to "SHARD_NIGHT_SQUID",
        "field mouse" to "SHARD_PEST", "flipflopper" to "SHARD_FLIP_FLOPPER", "earthworm" to "SHARD_TERMITE",
        "zealot bruiser" to "SHARD_BRUISER", "seashine" to "SHARD_SEA_SHINE", "beetle" to "SHARD_CROPEETLE",
        "wither spectre" to "SHARD_WITHER_SPECTER", "inferno demonlord" to "SHARD_BURNINGSOUL")

    private var captured = 0
    private var startMs = 0L
    private var activeMs = 0L
    private var lastGainMs = 0L
    private var lastOtherMs = 0L

    fun init() {
        FishEvents.chat.register { text ->
            if (W.shardTracker) parse(text.chatPlain.trim().replace(COUNT_SUFFIX, ""))
            false
        }
        FishEvents.worldChange.register { if (W.shardTrackerResetOnSwap) reset(); false }
        MiningHuds.reg("Shard Tracker", "fishing_shards", 150, 80,
            { W.shardTrackerX }, { W.shardTrackerX = it }, { W.shardTrackerY }, { W.shardTrackerY = it },
            { W.shardTrackerScale }, { W.shardTrackerScale = it },
            { W.shardTracker && counts.isNotEmpty() && System.currentTimeMillis() - lastGainMs < 60_000 }, { W.shardTracker },
            example = { listOf("§d§lShard Tracker", "§6800k §7(3.9M/h)\t§7| §9Honeybuzz §f5", "§6400k §7(2M/h)\t§7| §fTepid §f8",
                "§7Shards §f13 §7(§f64§7/h)", "§7Captured §f9", "§7Playtime §f12m 5s", "§eProfit §61.2M §7(5.9M/h)") }, lines = ::lines)
    }

    fun reset() { counts.clear(); captured = 0; startMs = 0L; activeMs = 0L; lastGainMs = 0L; lastOtherMs = 0L }

    private fun parse(s: String) {
        val now = System.currentTimeMillis()
        if (s.startsWith(BAZAAR_BUY)) { lastOtherMs = now; return }
        CAUGHT.find(s)?.let { captured++; add(it.groupValues[3], it.groupValues[2].toIntOrNull() ?: 1); return }
        CHARM.find(s)?.let { captured++; add(it.groupValues[3], it.groupValues[2].toIntOrNull() ?: 1); return }
        DIRECT.find(s)?.let { captured++; add(it.groupValues[2], it.groupValues[1].toIntOrNull() ?: 1); return }
        LOOT_SHARE.find(s)?.let { add(it.groupValues[2], it.groupValues[1].toIntOrNull() ?: 1); return }
        FLOOR.find(s)?.let { add(it.groupValues[1], 1); return }
        HIVE.find(s)?.let { add(it.groupValues[1], 1); return }
        CONTRIB.find(s)?.let { add(it.groupValues[1], 1); return }
        SENT.find(s)?.let { if (now - lastOtherMs > 3000 && now - lastGainMs > 3000) add(it.groupValues[1], 1) }
    }

    private fun add(name: String, n: Int) {
        val now = System.currentTimeMillis()
        if (startMs == 0L) startMs = now
        else activeMs += minOf(now - lastGainMs, 300_000L)
        lastGainMs = now
        counts[name] = (counts[name] ?: 0) + n
    }

    private fun id(name: String): String { val k = name.lowercase(); return ID_FIX[k] ?: "SHARD_" + name.uppercase().replace(' ', '_') }

    private fun price(name: String) = CroesusPrices.bazaarPrice(id(name), true) ?: 0.0

    private fun rate(v: Double) = ProfitTrackerKit.perHour(v, activeMs)

    private fun time(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "${s / 3600}h ${s % 3600 / 60}m" else "${s / 60}m ${s % 60}s" }

    private fun lines(): List<String> {
        val total = counts.values.sum()
        val profitOn = W.shardTrackerProfit
        val rows = counts.map { (name, n) -> Triple(name, n, if (profitOn) price(name) * n else n.toDouble()) }
        val profit = if (profitOn) rows.sumOf { it.third } else 0.0
        val out = arrayListOf("§d§lShard Tracker")
        rows.sortedByDescending { it.third }.take(W.shardTrackerRows).forEach { (name, n, v) ->
            val value = if (profitOn && v > 0) "§6" + Mining.short(v) + " §7(" + Mining.short(rate(v)) + "/h)" else ""
            out += value + "\t§7| " + (ProfitTrackerKit.rarityColor(id(name)) ?: "§f") + name + " §f" + "%,d".format(n)
        }
        out += "§7Shards §f" + "%,d".format(total) + (if (activeMs < 60_000) "" else " §7(§f" + Mining.short(rate(total.toDouble())) + "§7/h)")
        out += "§7Captured §f" + "%,d".format(captured)
        out += "§7Playtime §f" + time(activeMs)
        if (profitOn) out += "§eProfit §6" + Mining.short(profit) + " §7(" + Mining.short(rate(profit)) + "/h)"
        return out
    }
}
