package fishmod.features.mining

import fishmod.utils.Location
import fishmod.utils.events.Events
import fishmod.features.mining.MiningSettings as S

// Glacite Mineshaft pity: tab widget when shown, otherwise weighted block count (SkyHanni weights, max 2000)
object MineshaftPity {

    private const val MAX = 2000
    private val TAB = Regex("""Glacite Mineshafts: ([\d,]+)/2,000""")
    private val perBlock = HashMap<PityBlock, Double>()
    private var lastShaftMs = 0L
    private var halfPoints = 0.0

    fun init() {
        Mining.breakListeners += Mining.BreakListener { _, old, original ->
            if (!S.miningPity || !Mining.inTunnels() || inGrace()) return@BreakListener
            val pb = MiningBlocks.pity(old) ?: return@BreakListener
            // Spread blocks (Pickobulus etc.) count half, like SkyHanni
            val add = if (original) pb.weight.toDouble() else pb.weight / 2.0
            perBlock[pb] = (perBlock[pb] ?: 0.0) + add
            halfPoints += add
            S.miningPityPoints = halfPoints.toInt().coerceAtMost(MAX)
            S.miningPityBlocks++
        }
        Events.ON_GAME_MESSAGE.register { text ->
            if (Mining.strip(text.string).contains("You found a Glacite Mineshaft portal!")) reset()
            false
        }
        Events.ON_LOCATION_CHANGE.register { loc ->
            if (loc == Location.MINESHAFT) reset()
            false
        }
        MiningHuds.reg("Mineshaft Pity", "mining_pity", 150, 40,
            { S.miningPityHudX }, { S.miningPityHudX = it }, { S.miningPityHudY }, { S.miningPityHudY = it },
            { S.miningPityHudScale }, { S.miningPityHudScale = it },
            { S.miningPity && (Mining.inTunnels() || fishmod.features.FishHudEditor.isOpen()) }, ::lines)
        halfPoints = S.miningPityPoints.toDouble()
    }

    private fun inGrace() = System.currentTimeMillis() - lastShaftMs < S.miningPityGraceSec * 1000L

    private fun reset() {
        lastShaftMs = System.currentTimeMillis()
        perBlock.clear(); halfPoints = 0.0
        S.miningPityPoints = 0; S.miningPityBlocks = 0
    }

    fun points(): Int {
        Mining.tabLine("Glacite Mineshafts:")?.let { l -> TAB.find(l)?.let { return it.groupValues[1].replace(",", "").toInt() } }
        return halfPoints.toInt().coerceAtMost(MAX)
    }

    private fun lines(): List<String> {
        val out = ArrayList<String>()
        out += "§3§lMineshaft Pity"
        if (inGrace()) {
            out += "§7Waiting §e${(S.miningPityGraceSec - (System.currentTimeMillis() - lastShaftMs) / 1000).coerceAtLeast(0)}s"
            return out
        }
        val pts = points()
        out += "§3Counter: §e$pts§6/§e$MAX §7(${"%.1f".format(pts * 100.0 / MAX)}%)"
        if (S.miningPityShowChance) out += "§3Chance: §e1§6/§e${MAX - pts}"
        out += "§3Blocks: §e${S.miningPityBlocks}"
        if (S.miningPityBreakdown) {
            val left = MAX - pts
            for (pb in PityBlock.entries) out += "§7 ${pb.display}: §f${(left + pb.weight - 1) / pb.weight} §8left"
        }
        return out
    }
}
