package fishmod.features.fishing

import fishmod.features.mining.MiningHuds
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import fishmod.features.fishing.FishingWorldSettings as W

// Golden Fish HUD: time fishing, spawn window and despawn countdown
object GoldenFishTimer {
    // from SkyHanni (GoldenFishTimer patterns, colours stripped)
    private const val SPAWN = "You spot a Golden Fish surface from beneath the lava!"
    private const val INTERACT = "The Golden Fish escapes your hook but looks weakened."
    private const val WEAK = "The Golden Fish is weak!"
    private const val GONE = "The Golden Fish swims back beneath the lava..."
    private const val MIN_MS = 8 * 60_000L // from SkyHanni: spawns 8-12 min after you start fishing
    private const val MAX_MS = 12 * 60_000L
    private const val DESPAWN_MS = 60_000L

    private var since = 0L
    private var spawnedAt = 0L
    private var hits = 0

    fun init() {
        FishEvents.chat.register { text ->
            if (W.goldenFishTimer) onChat(text.chatPlain.trim())
            false
        }
        FishEvents.worldChange.register { since = 0L; spawnedAt = 0L; hits = 0; false }
        MiningHuds.reg("Golden Fish Timer", "fishing_golden_fish", 140, 30,
            { W.goldenFishX }, { W.goldenFishX = it }, { W.goldenFishY }, { W.goldenFishY = it },
            { W.goldenFishScale }, { W.goldenFishScale = it },
            { W.goldenFishTimer && active() }, { W.goldenFishTimer },
            example = { listOf("§6Golden Fish", "§7Fishing\t§b6m 12s", "§7Can spawn\t§b1m 48s") }, lines = ::lines)
    }

    private fun active(): Boolean {
        if (!Fishing.active()) return false
        if (since == 0L && net.minecraft.client.Minecraft.getInstance().player?.fishing != null) since = System.currentTimeMillis()
        return since != 0L
    }

    private fun onChat(s: String) {
        val now = System.currentTimeMillis()
        when (s) {
            SPAWN -> { spawnedAt = now; hits = 0 }
            INTERACT -> { hits++; spawnedAt = now }
            WEAK -> { hits = 3; spawnedAt = now }
            GONE -> { spawnedAt = 0L; hits = 0; since = now }
            else -> if (s.contains("TROPHY FISH!") && s.contains("Golden Fish")) { spawnedAt = 0L; hits = 0; since = now }
        }
    }

    private fun fmt(ms: Long): String { val s = (ms.coerceAtLeast(0L) + 999) / 1000; return if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s" }

    private fun lines(): List<String> {
        val now = System.currentTimeMillis()
        if (spawnedAt != 0L) {
            val left = spawnedAt + DESPAWN_MS - now
            if (left > 0) return listOf("§6Golden Fish §a($hits/3)", "§7Despawns in\t§b" + fmt(left)) + if (hits >= 3) listOf("§c§lPULL") else emptyList()
            spawnedAt = 0L
        }
        val t = now - since
        val wait = if (t < MIN_MS) "§7Can spawn\t§b" + fmt(MIN_MS - t) else if (t < MAX_MS) "§7Spawns by\t§e" + fmt(MAX_MS - t) else "§7Spawn\t§aany moment"
        return listOf("§6Golden Fish", "§7Fishing\t§b" + fmt(t), wait)
    }
}
