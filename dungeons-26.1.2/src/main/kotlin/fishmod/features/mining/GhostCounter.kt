package fishmod.features.mining

import fishmod.utils.SkyblockLocation
import fishmod.utils.TabListCache
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.monster.Creeper
import fishmod.features.slayer.BossToolsSettings as S

// Ghost HUD in The Mist (left-aligned): kills, sorrows, bags of cash, magic find drops, kills/h
object GhostCounter {
    // from SkyHanni (GhostTracker patterns)
    private val DROP = Regex("RARE DROP! (.+) \\(\\+(\\d+)%? ✯ Magic Find\\)")
    private const val BAG = "The ghost's death materialized 1,000,000 coins from the mists!"
    private val BESTIARY = Regex("\\s*Ghost (\\d+|[XVI]+): ([\\d,.]+)/([\\d,.]+)")

    private var kills = 0L
    private var lastBestiary = -1L
    private var sorrows = 0
    private var bags = 0
    private var mfDrops = 0
    private var mfTotal = 0L
    private var sinceSorrow = 0L
    private var startMs = 0L
    private var lastKillMs = 0L
    private var tick = 0
    private var tabMissing = false
    private val deadGhosts = HashSet<Int>()

    fun reset() { kills = 0; sorrows = 0; bags = 0; mfDrops = 0; mfTotal = 0; sinceSorrow = 0; startMs = 0; lastKillMs = 0 }

    private fun inMist(): Boolean = SkyblockLocation.`in`(SkyblockLocation.DWARVEN_MINES) &&
        (Mining.area.contains("The Mist", true) || fishmod.utils.SidebarCache.entries.any { it.plain.contains("The Mist") })

    fun init() {
        FishEvents.chat.register { text ->
            if (S.ghostCounter && inMist()) onChat(text.chatPlain.trim())
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
            if (!S.ghostCounter) return@EndTick
            if (tick++ % 20 == 0) scanTab()
            if (tabMissing && tick % 2 == 0) scanDeaths()
        })
        MiningHuds.reg("Ghost Counter", "mining_ghost_counter", 150, 70,
            { S.ghostX }, { S.ghostX = it }, { S.ghostY }, { S.ghostY = it },
            { S.ghostScale }, { S.ghostScale = it },
            { S.ghostCounter && inMist() }, { S.ghostCounter },
            example = { listOf("§9Ghosts", "§7Kills: §e1,204", "§7Kills/h: §e2,410", "§7Sorrows: §91", "§7Since Sorrow: §e312") }, lines = ::lines)
    }

    private var wasInMist = false

    private fun scanTab() {
        val mist = inMist()
        if (mist != wasInMist) { wasInMist = mist; fishmod.utils.debug.FishDebug.LOGGER.info("[GhostCounter] inMist={} area='{}'", mist, Mining.area) }
        if (!mist) { lastBestiary = -1; tabMissing = false; return }
        val n = TabListCache.entries.firstNotNullOfOrNull { BESTIARY.matchEntire(it.stripped)?.groupValues?.get(2) }
            ?.replace(",", "")?.replace(".", "")?.toLongOrNull()
        tabMissing = n == null
        if (n == null) return
        if (lastBestiary >= 0 && n > lastBestiary && n - lastBestiary <= 50) {
            val d = n - lastBestiary
            val now = System.currentTimeMillis()
            if (startMs == 0L) startMs = now
            kills += d; sinceSorrow += d; lastKillMs = now
        }
        lastBestiary = n
    }

    // Fallback when the Ghost bestiary tab widget is off: count ghost deaths within 10 blocks
    private fun scanDeaths() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val p = mc.player ?: return
        for (e in level.getEntitiesOfClass(Creeper::class.java, p.boundingBox.inflate(16.0)) { it.isDeadOrDying && fishmod.features.visuals.MiningBoxes.isGhost(it) }) {
            if (!deadGhosts.add(e.id)) continue
            val now = System.currentTimeMillis()
            if (startMs == 0L) startMs = now
            kills++; sinceSorrow++; lastKillMs = now
        }
        if (deadGhosts.size > 300) deadGhosts.retainAll { level.getEntity(it) != null }
    }

    private fun onChat(s: String) {
        if (s == BAG) { bags++; return }
        val m = DROP.find(s) ?: return
        mfDrops++
        mfTotal += m.groupValues[2].toLongOrNull() ?: 0L
        if (m.groupValues[1].contains("Sorrow")) { sorrows++; sinceSorrow = 0 }
    }

    private fun f(n: Long) = "%,d".format(n)

    private fun lines(): List<String> {
        val hrs = if (startMs == 0L) 0.0 else (lastKillMs - startMs) / 3_600_000.0
        val rate = if (hrs > 1.0 / 60) (kills / hrs).toLong() else 0L
        val avgMf = if (mfDrops > 0) mfTotal / mfDrops else 0L
        return listOf("§9Ghosts", "§7Kills: §e" + f(kills), "§7Kills/h: §e" + f(rate), "§7Sorrows: §9$sorrows",
            "§7Since Sorrow: §e" + f(sinceSorrow), "§7Bags of Cash: §6$bags", "§7MF Drops: §b$mfDrops §7(avg §b$avgMf%§7)")
    }
}
