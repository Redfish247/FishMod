package fishmod.features.garden
import fishmod.compat.curScreen

import fishmod.utils.Misc
import fishmod.utils.TabListCache
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import fishmod.features.garden.GardenMilestoneSettings as S

// Garden level overflow (HUD + chat) and hoe level overflow / muted hoe sounds; XP math copied from SkyHanni's GardenApi
object GardenLevels {

    // SkyHanni repo Garden.json garden_exp, then a flat step per overflow level
    private val XP = longArrayOf(0, 70, 70, 140, 240, 600, 1500, 2000, 2500, 3000, 10000, 10000, 10000, 10000, 10000)
    private const val OVERFLOW_EXP = 10000L
    private const val MAX_LEVEL = 15

    private val TAB_LEVEL = Regex("Garden Level:?\\s*(\\d+)(?:.*?([\\d.,]+[kK]?)\\s*/\\s*([\\d.,]+[kK]?))?")
    private val GXP = Regex("\\+([\\d,]+) Garden Experience")
    private val HOE_LEVEL = Regex("Level:? (\\d+)(?:/(\\d+))?")
    private val PROGRESS_TO = Regex("Progress to Level ([^:]+)")
    private val NEXT_XP = Regex("([\\d.,]+[kKmM]?)\\s*/\\s*[\\d.,]+[kKmM]?\\s*$")
    private val OVERFLOW_LINE = Regex("^\\s*([\\d,]+)\\s*$")
    private val ROMAN = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100)

    private var lastLevel = -1
    private var hoeLines: List<String> = emptyList()
    private var tick = 0

    // Total XP needed to reach `level`
    private fun cum(level: Int): Long {
        var t = 0L
        for (i in 0 until level) t += if (i < XP.size) XP[i] else OVERFLOW_EXP
        return t
    }

    // level, xp into level, xp needed for next level
    private fun levelOf(total: Long): Triple<Int, Long, Long> {
        var tier = 0
        var sum = 0L
        for (x in XP) { sum += x; if (sum > total) return build(tier, total); tier++ }
        sum += OVERFLOW_EXP
        while (sum < total) { tier++; sum += OVERFLOW_EXP }
        return build(tier, total)
    }

    private fun build(level: Int, total: Long): Triple<Int, Long, Long> {
        val base = cum(level)
        return Triple(level, total - base, cum(level + 1) - base)
    }

    private fun num(s: String): Double {
        val t = s.replace(",", "")
        return when {
            t.endsWith("k", true) -> (t.dropLast(1).toDoubleOrNull() ?: 0.0) * 1000
            t.endsWith("m", true) -> (t.dropLast(1).toDoubleOrNull() ?: 0.0) * 1_000_000
            else -> t.toDoubleOrNull() ?: 0.0
        }
    }

    private fun levelNum(s: String): Int? {
        val t = s.trim()
        t.toIntOrNull()?.let { return it }
        if (t.isEmpty() || t.any { it !in ROMAN }) return null
        var total = 0
        for ((i, c) in t.withIndex()) {
            val v = ROMAN[c]!!
            total += if (i + 1 < t.length && ROMAN[t[i + 1]]!! > v) -v else v
        }
        return total
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            if (tick++ % 20 == 0) { scanTab(); scanHoe() }
            if (tick % 5 == 0) scanMenu()
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
            { S.gardenLevelHud && Garden.inGarden() }, { S.gardenLevelHud }) {
            val c = Garden.legacy(S.gardenLevelColor)
            if (S.gardenLevelXp <= 0) return@hud listOf("$c§lGarden Level §f? §cOpen the Garden Desk!")
            val (l, x, n) = levelOf(S.gardenLevelXp)
            listOf("$c§lGarden Level §f$l" + (if (l > MAX_LEVEL) " §7(+${l - MAX_LEVEL} overflow)" else ""),
                "§7XP: §f${"%,d".format(x)}§7/§f${"%,d".format(n)} §8(${"%.1f".format(x * 100.0 / n)}%)")
        }
        Garden.hud("Hoe Level", "garden_hoe", 130, 24,
            { S.gardenHoeHudX }, { S.gardenHoeHudX = it }, { S.gardenHoeHudY }, { S.gardenHoeHudY = it },
            { S.gardenHoeHudScale }, { S.gardenHoeHudScale = it },
            { S.gardenHoeHud && Garden.inGarden() && hoeLines.isNotEmpty() }, { S.gardenHoeHud }) { hoeLines }
    }

    // Sync like SkyHanni: Garden Level item in the Desk (slot 4) or SkyBlock Menu (slot 10); also any Garden Level item in the Farming Skill menu
    private fun scanMenu() {
        val scr = Minecraft.getInstance().curScreen as? AbstractContainerScreen<*> ?: return
        val t = Garden.strip(scr.title.string).trim()
        val slots = scr.menu.slots
        val st: ItemStack = when {
            t.equals("Desk", true) -> slots.getOrNull(4)?.item
            t.equals("SkyBlock Menu", true) -> slots.getOrNull(10)?.item
            t.contains("Farming Skill", true) || t.contains("Garden Level", true) ->
                slots.firstOrNull { !it.item.isEmpty && Garden.strip(it.item.hoverName.string).startsWith("Garden Level", true) }?.item
            else -> null
        } ?: return
        if (st.isEmpty) return
        val n = Garden.strip(st.hoverName.string)
        if (!n.startsWith("Garden Level", true) && !n.startsWith("Garden Desk", true)) return
        var cur = 0
        var next = 0L
        for (l in Garden.lore(st)) {
            PROGRESS_TO.find(l)?.let { m -> levelNum(m.groupValues[1])?.let { cur = it - 1 } }
            if (l.contains("Max level reached", true)) cur = MAX_LEVEL
            NEXT_XP.find(l)?.let { next = num(it.groupValues[1]).toLong() }
            OVERFLOW_LINE.find(l)?.let { m ->
                S.gardenLevelXp = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@let
                checkLevelUp()
                return
            }
        }
        if (cur <= 0 && next <= 0) return
        S.gardenLevelXp = cum(cur) + next
        checkLevelUp()
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
        if (lastLevel in 0 until l && l > MAX_LEVEL && S.gardenLevelChat)
            Misc.addChatMessage(Component.literal("§a§lGARDEN LEVEL UP! §7Overflow level §f$l §7(+${l - MAX_LEVEL})"))
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
