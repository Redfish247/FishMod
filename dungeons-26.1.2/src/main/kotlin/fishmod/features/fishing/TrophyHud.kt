package fishmod.features.fishing

import fishmod.features.mining.MiningHuds
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import fishmod.features.fishing.FishingWorldSettings as W

object TrophyHud {
    private val counts = LinkedHashMap<String, IntArray>()

    fun init() {
        FishEvents.chat.register { c ->
            if (W.trophyHud) FishingAlertText.trophy(c.chatPlain)?.let { t -> counts.getOrPut(t.name) { IntArray(4) }[t.rarity.ordinal]++ }
            false
        }
        MiningHuds.reg("Trophy Fish HUD", "fishing_trophy_hud", 150, 40,
            { W.trophyHudX }, { W.trophyHudX = it }, { W.trophyHudY }, { W.trophyHudY = it }, { W.trophyHudScale }, { W.trophyHudScale = it },
            { W.trophyHud && counts.isNotEmpty() }, { W.trophyHud },
            example = { listOf("§6Trophy Fish", "§fBlobfish\t§83 §70 §60 §b0", "§fSulphur Skitter\t§81 §71 §60 §b0") }, lines = ::lines)
    }

    private fun lines(): List<String> = listOf("§6Trophy Fish §7(" + counts.values.sumOf { it.sum() } + ")") +
        counts.entries.sortedByDescending { it.value.sum() }.map { (n, a) -> "§f" + n + "\t§8" + a[0] + " §7" + a[1] + " §6" + a[2] + " §b" + a[3] }
}
