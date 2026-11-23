package fishmod.features.end

import fishmod.features.mining.MiningHuds
import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.TabListCache
import fishmod.utils.TextStrip
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain

// Endstone Protector stage (tab row "Protector: Resting (200)" / "Rising (Tier 2/5, 149)" / "Spawned", format from ds-mod)
// and an Ender Node tracker (SkyHanni-style: nodes mined and what they gave, from chat).
object EndTrackers {
    private val ROW = Regex("""^(Resting|Rising|Spawned)(?: \((?:Tier (\d+/\d+), )?(\d+)\))?$""")
    private val NODE = Regex("""^ENDER NODE! You found (?:(\d+)x )?(.+?)!?$""")
    private var stage = ""
    private var tier = ""
    private var left = ""
    private var spawnedAt = 0L
    private var defeatedAt = 0L

    private var nodes = 0
    private var firstNodeAt = 0L
    private val drops = LinkedHashMap<String, Int>()

    fun init() {
        FishEvents.worldChange.register { stage = ""; spawnedAt = 0L; defeatedAt = 0L; false }
        FishEvents.chat.register { text ->
            val s = TextStrip.plain(text.chatPlain).trim()
            if (S.protectorHud) {
                if (s.contains("The Endstone Protector has emerged") || s == "BEWARE - An Endstone Protector has risen!") { spawnedAt = System.currentTimeMillis(); defeatedAt = 0L }
                else if (s == "The Endstone Protector has been defeated!" && spawnedAt > 0) defeatedAt = System.currentTimeMillis()
            }
            if (S.endNodeTracker) NODE.find(s)?.let { m ->
                if (nodes == 0) firstNodeAt = System.currentTimeMillis()
                nodes++
                val n = m.groupValues[1].toIntOrNull() ?: 1
                drops.merge(m.groupValues[2], n, Int::plus)
            }
            false
        }
        MiningHuds.reg("Endstone Protector", "end_protector", 110, 25,
            { S.protectorHudX }, { S.protectorHudX = it }, { S.protectorHudY }, { S.protectorHudY = it }, { S.protectorHudScale }, { S.protectorHudScale = it },
            { S.protectorHud && protectorLines().isNotEmpty() }, { S.protectorHud },
            example = { listOf("§5Stage\t§eRising Tier 2/5", "§5Kills Left\t§a149") }, lines = { protectorLines() })
        MiningHuds.reg("End Node Tracker", "end_nodes", 130, 60,
            { S.endNodeX }, { S.endNodeX = it }, { S.endNodeY }, { S.endNodeY = it }, { S.endNodeScale }, { S.endNodeScale = it },
            { S.endNodeTracker && nodes > 0 }, { S.endNodeTracker },
            example = { listOf("§dEnder Nodes\t§f42", "§7Enchanted End Stone\t§f12", "§7Ender Pearl\t§f30") }, lines = { nodeLines() })
    }

    fun reset() { nodes = 0; drops.clear(); firstNodeAt = 0L }

    private fun readRow() {
        stage = ""
        for (e in TabListCache.entries) {
            val t = e.stripped.trim()
            if (!t.startsWith("Protector:")) continue
            val m = ROW.find(t.removePrefix("Protector:").trim()) ?: return
            stage = m.groupValues[1]; tier = m.groupValues[2]; left = m.groupValues[3]
            return
        }
    }

    private fun clock(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

    private fun protectorLines(): List<String> {
        readRow()
        val now = System.currentTimeMillis()
        if (stage == "Resting" || stage == "Rising") {
            val label = if (stage == "Rising" && tier.isNotEmpty()) "Rising Tier $tier" else stage
            return listOf("§5Stage\t§e$label", "§5Kills Left\t§a$left")
        }
        if (defeatedAt > 0 && stage != "Spawned") return listOf("§5Stage\t§aDefeated", "§5Fight\t§f" + clock(defeatedAt - spawnedAt))
        if (stage == "Spawned" || spawnedAt > 0 && defeatedAt == 0L) {
            return listOf("§5Stage\t§cSpawned") + (if (spawnedAt > 0) listOf("§5Alive\t§f" + clock(now - spawnedAt)) else emptyList())
        }
        return emptyList()
    }

    private fun nodeLines(): List<String> {
        val out = ArrayList<String>()
        out.add("§dEnder Nodes\t§f$nodes")
        val hours = (System.currentTimeMillis() - firstNodeAt) / 3_600_000.0
        if (hours > 0.005) out.add("§dNodes/hour\t§f" + (nodes / hours).toInt())
        for ((k, v) in drops.entries.sortedByDescending { it.value }.take(8)) out.add("§7$k\t§f$v")
        return out
    }
}
