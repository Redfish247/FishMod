package fishmod.features.end

import fishmod.compat.curScreen
import fishmod.features.dungeon.trackers.CroesusPrices
import fishmod.features.mining.MiningHuds
import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

// Session dragon profit: kills per type, your eyes, essence and items picked up after a dragon dies
object DragonProfitTracker {

    // from Eli's logs
    private val SPAWN = Regex("^☬ The (\\w+) Dragon has spawned!$")
    private val DOWN = Regex("^(\\w+) DRAGON DOWN!$")
    private val ESSENCE = Regex("^\\+(\\d+) Dragon Essence$")
    // from SkyHanni (DragonFeatures eyePlacedPattern / eyeRemovedPattern)
    private const val EYE_PLACED = "☬ You placed a Summoning Eye!"
    private const val EYE_RECOVERED = "You recovered a Summoning Eye!"
    private const val LOOT_WINDOW_MS = 60_000L

    private class Loot(val name: String, var count: Long)

    private val kills = LinkedHashMap<String, Int>()
    private val loot = LinkedHashMap<String, Loot>()
    private var eyesPlaced = 0
    private var pendingEyes = 0
    private var lootUntil = 0L
    private var lastInv: Map<String, Int>? = null
    private var tick = 0

    fun reset() { kills.clear(); loot.clear(); eyesPlaced = 0; pendingEyes = 0; lootUntil = 0L; lastInv = null }

    private fun inEnd() = SkyblockLocation.`in`(SkyblockLocation.THE_END)

    fun init() {
        FishEvents.chat.register { text ->
            if (S.dragonTracker && inEnd()) FishDiag.guard("DragonProfitTracker.1", "dragon tracker chat failed") { onChat(TextStrip.plain(text.chatPlain).trim()) }
            false
        }
        FishEvents.worldChange.register { pendingEyes = 0; lootUntil = 0L; lastInv = null; false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (S.dragonTracker && ++tick % 10 == 0 && System.currentTimeMillis() < lootUntil) {
                FishDiag.guard("DragonProfitTracker.2", "dragon loot scan failed") { scanInventory(mc) }
            }
        }
        MiningHuds.reg("Dragon Profit", "end_dragon_profit", 170, 80,
            { S.dragonTrackerX }, { S.dragonTrackerX = it }, { S.dragonTrackerY }, { S.dragonTrackerY = it },
            { S.dragonTrackerScale }, { S.dragonTrackerScale = it },
            { S.dragonTracker && inEnd() }, { S.dragonTracker },
            example = { listOf("§5§lDragon Profit", "§7Kills: §f4 §8(Young 2, Wise 1, Old 1)", "§7Eyes placed: §f3 §c-3.3M",
                "§f12x §5Young Dragon Fragment §6+1.1M", "§f20x §dDragon Essence §6+40k", "§7Profit: §6-2.1M") },
            lines = ::lines)
    }

    private fun onChat(s: String) {
        when {
            s.startsWith(EYE_PLACED) -> pendingEyes++
            s == EYE_RECOVERED -> if (pendingEyes > 0) pendingEyes--
            else -> {
                SPAWN.matchEntire(s)?.let { eyesPlaced += pendingEyes; pendingEyes = 0; return }
                DOWN.matchEntire(s)?.let { m ->
                    val type = m.groupValues[1].lowercase().replaceFirstChar { it.uppercase() }
                    kills.merge(type, 1, Int::plus)
                    lootUntil = System.currentTimeMillis() + LOOT_WINDOW_MS
                    lastInv = Minecraft.getInstance().player?.let { inventoryCounts(it) }
                    return
                }
                ESSENCE.matchEntire(s)?.let { m -> if (System.currentTimeMillis() < lootUntil) add("ESSENCE_DRAGON", "§dDragon Essence", m.groupValues[1].toLong()) }
            }
        }
    }

    private fun inventoryCounts(p: net.minecraft.world.entity.player.Player): Map<String, Int> {
        val m = HashMap<String, Int>()
        for (i in 0 until 36) {
            val st = p.inventory.getItem(i)
            if (st.isEmpty) continue
            val id = ItemTools.getId(st) ?: continue
            m.merge(id, st.count, Int::plus)
        }
        return m
    }

    // Items gained in the inventory during the loot window count as dragon loot
    private fun scanInventory(mc: Minecraft) {
        val p = mc.player ?: return
        val now = inventoryCounts(p)
        val before = lastInv
        lastInv = now
        if (before == null || mc.curScreen != null) return
        for ((id, n) in now) {
            val d = n - (before[id] ?: 0)
            if (d <= 0) continue
            val st = (0 until 36).map { p.inventory.getItem(it) }.firstOrNull { ItemTools.getId(it) == id } ?: continue
            add(id, st.hoverName.string, d.toLong())
        }
    }

    private fun add(id: String, name: String, n: Long) {
        loot.getOrPut(id) { Loot(name, 0) }.count += n
    }

    private fun sh(v: Double): String = fishmod.features.diana.DianaTracker.short(v)

    private fun lines(): List<String> {
        val total = kills.values.sum()
        if (total == 0 && eyesPlaced == 0 && loot.isEmpty()) return listOf("§5§lDragon Profit", "§7No dragons yet")
        val eyeCost = eyesPlaced * CroesusPrices.price("SUMMONING_EYE")
        val rows = loot.entries.map { (id, l) -> Triple(l, id, CroesusPrices.price(id) * l.count) }.sortedByDescending { it.third }
        val profit = rows.sumOf { it.third } - eyeCost
        val out = ArrayList<String>()
        out.add("§5§lDragon Profit")
        out.add("§7Kills: §f$total" + if (kills.isEmpty()) "" else " §8(" + kills.entries.joinToString(", ") { "${it.key} ${it.value}" } + ")")
        out.add("§7Eyes placed: §f$eyesPlaced" + if (eyeCost > 0) " §c-${sh(eyeCost)}" else "")
        for ((l, _, v) in rows.take(8)) out.add("§f${l.count}x ${l.name} §6+${sh(v)}")
        out.add("§7Profit: " + (if (profit >= 0) "§6+" else "§c-") + sh(Math.abs(profit)))
        return out
    }
}
