package fishmod.features.garden

import fishmod.utils.Misc
import fishmod.utils.TabListCache
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.network.chat.Component
import fishmod.features.garden.GardenMilestoneSettings as S

// Garden level overflow (HUD + chat) and hoe level overflow / muted hoe sounds (tab/lore formats are guesses)
object GardenLevels {

    // Garden XP needed per level 1..15, then a flat step for overflow (SkyHanni values from memory, unverified)
    private val XP = longArrayOf(70, 100, 140, 240, 600, 1500, 2000, 2500, 3000, 5000, 7500, 10000, 10000, 10000, 10000)
    private const val OVER_STEP = 10000L

    private val TAB_LEVEL = Regex("Garden Level:?\\s*(\\d+)(?:.*?([\\d.,]+[kK]?)\\s*/\\s*([\\d.,]+[kK]?))?")
    private val GXP = Regex("\\+([\\d,]+) Garden Experience")
    private val HOE_LEVEL = Regex("Level:? (\\d+)(?:/(\\d+))?")

    private var lastLevel = -1
    private var hoeLines: List<String> = emptyList()
    private var tick = 0

    private fun cum(level: Int): Long {
        var t = 0L
        for (i in 0 until level) t += if (i < XP.size) XP[i] else OVER_STEP
        return t
    }

    // level, xp into level, xp needed for next level
    private fun levelOf(total: Long): Triple<Int, Long, Long> {
        var lvl = 0
        var left = total
        while (true) {
            val need = if (lvl < XP.size) XP[lvl] else OVER_STEP
            if (left < need) return Triple(lvl, left, need)
            left -= need; lvl++
        }
    }

    private fun num(s: String): Double {
        val t = s.replace(",", "")
        return if (t.endsWith("k", true)) (t.dropLast(1).toDoubleOrNull() ?: 0.0) * 1000 else t.toDoubleOrNull() ?: 0.0
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            if (tick++ % 20 == 0) { scanTab(); scanHoe() }
        }
        Events.ON_GAME_MESSAGE.register { t ->
            val m = GXP.find(Garden.strip(t.string))
            if (m != null && Garden.inGarden() && S.gardenLevelXp > 0) {
                S.gardenLevelXp += m.groupValues[1].replace(",", "").toLongOrNull() ?: 0
                checkLevelUp()
            }
            false
        }
        Garden.hud("Garden Level", "garden_level", 150, 24,
            { S.gardenLevelHudX }, { S.gardenLevelHudX = it }, { S.gardenLevelHudY }, { S.gardenLevelHudY = it },
            { S.gardenLevelHudScale }, { S.gardenLevelHudScale = it },
            { S.gardenLevelHud && Garden.inGarden() && S.gardenLevelXp > 0 }) {
            val (l, x, n) = levelOf(S.gardenLevelXp)
            val c = Garden.legacy(S.gardenLevelColor)
            listOf("$c§lGarden Level §f$l" + (if (l > XP.size) " §7(+${l - XP.size} overflow)" else ""),
                "§7XP: §f${"%,d".format(x)}§7/§f${"%,d".format(n)} §8(${"%.1f".format(x * 100.0 / n)}%)")
        }
        Garden.hud("Hoe Level", "garden_hoe", 130, 24,
            { S.gardenHoeHudX }, { S.gardenHoeHudX = it }, { S.gardenHoeHudY }, { S.gardenHoeHudY = it },
            { S.gardenHoeHudScale }, { S.gardenHoeHudScale = it },
            { S.gardenHoeHud && Garden.inGarden() && hoeLines.isNotEmpty() }) { hoeLines }
    }

    private fun scanTab() {
        if (!Garden.inGarden()) return
        for (e in TabListCache.entries) {
            val m = TAB_LEVEL.find(e.stripped.trim()) ?: continue
            val lvl = m.groupValues[1].toInt()
            val into = if (m.groupValues[2].isNotEmpty()) num(m.groupValues[2]).toLong() else 0L
            // resync total from the tab (xp-into-level is only present when the widget shows it)
            if (m.groupValues[2].isNotEmpty() || S.gardenLevelXp == 0L) S.gardenLevelXp = cum(lvl) + into
            checkLevelUp()
            return
        }
    }

    private fun checkLevelUp() {
        val l = levelOf(S.gardenLevelXp).first
        if (lastLevel in 0 until l && l > XP.size && S.gardenLevelChat)
            Misc.addChatMessage(Component.literal("§a§lGARDEN LEVEL UP! §7Overflow level §f$l §7(+${l - XP.size})"))
        lastLevel = l
    }

    private fun scanHoe() {
        val st = Minecraft.getInstance().player?.mainHandItem
        if (st == null || st.isEmpty || !isHoe(ItemUtil.getId(st))) { hoeLines = emptyList(); return }
        val m = Garden.lore(st).firstNotNullOfOrNull { HOE_LEVEL.find(it) } ?: run { hoeLines = emptyList(); return }
        val lvl = m.groupValues[1].toInt()
        val max = m.groupValues[2].toIntOrNull()
        val c = Garden.legacy(S.gardenHoeColor)
        hoeLines = listOf("$c§lHoe Level §f$lvl" + (if (max != null) "§7/§f$max" + (if (lvl > max) " §a(+${lvl - max} overflow)" else "") else ""))
    }

    private fun isHoe(id: String?) = id != null && id.contains("HOE", true)

    // Called from SoundEngineMixin: true cancels the sound
    @JvmStatic
    fun muteSound(instance: SoundInstance): Boolean {
        if (!S.gardenHoeMute || !instance.identifier.path.startsWith("item.hoe.")) return false
        return isHoe(Minecraft.getInstance().player?.mainHandItem?.let { ItemUtil.getId(it) })
    }
}
