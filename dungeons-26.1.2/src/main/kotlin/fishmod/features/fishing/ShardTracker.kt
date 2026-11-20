package fishmod.features.fishing

import fishmod.features.mining.MiningHuds
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import fishmod.features.fishing.FishingWorldSettings as W

// Session HUD of shards caught (formats from Eli's logs)
object ShardTracker {
    private val counts = LinkedHashMap<String, Int>()
    private val CAUGHT = Regex("^You caught an? (.+?) and gained (?:a|(\\d+)x) (.+?) Shard!$")
    private val DIRECT = Regex("^You caught (?:a|x(\\d+)) (.+?) Shards?!$")
    private val FLOOR = Regex("^FLOOR DROP! You found (.+?) Shard on the ground!$")
    private val HIVE = Regex("^HIVE! You found .+ and (.+?) Shard!$")

    fun init() {
        FishEvents.chat.register { text ->
            if (W.shardTracker) parse(text.chatPlain.trim().replace(Regex(" \\(\\d+\\)$"), ""))
            false
        }
        FishEvents.worldChange.register { if (W.shardTrackerResetOnSwap) counts.clear(); false }
        MiningHuds.reg("Shard Tracker", "fishing_shards", 130, 40,
            { W.shardTrackerX }, { W.shardTrackerX = it }, { W.shardTrackerY }, { W.shardTrackerY = it },
            { W.shardTrackerScale }, { W.shardTrackerScale = it },
            { W.shardTracker && counts.isNotEmpty() }, { W.shardTracker },
            example = { listOf("§dShards", "§fTepid\t§a8", "§fHoneybuzz\t§a5") }, lines = ::lines)
    }

    private fun parse(s: String) {
        CAUGHT.find(s)?.let { add(it.groupValues[3], it.groupValues[2].toIntOrNull() ?: 1); return }
        DIRECT.find(s)?.let { add(it.groupValues[2], it.groupValues[1].toIntOrNull() ?: 1); return }
        FLOOR.find(s)?.let { add(it.groupValues[1], 1); return }
        HIVE.find(s)?.let { add(it.groupValues[1], 1) }
    }

    private fun add(name: String, n: Int) { counts[name] = (counts[name] ?: 0) + n }

    private fun lines(): List<String> = listOf("§dShards §7(" + counts.values.sum() + ")") +
        counts.entries.sortedByDescending { it.value }.take(W.shardTrackerRows).map { "§f${it.key}\t§a${it.value}" }
}
