package fishmod.features.garden

import fishmod.features.mining.Mining
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.garden.GardenSettings as S

// Pest spawn title, infested plot waypoints, pest timer HUD, pest drop profit
object Pests {

    private val APPEARED = Regex("""^\w+! (?:A|(\d+)) .*?Pests? ha(?:s|ve) (?:appeared|spawned) in (?:Plot - (.+)|(The Barn))!""")
    private val OFFLINE = Regex("""^\w+! While you were offline, .*?Pests? spawned in Plots? (.+)!""")
    private val DROP = Regex("""received (\d+)x (.+?) for killing an? ([A-Za-z ]+)!""")
    private val NO_PESTS = Regex("""There are not any Pests on your Garden""")
    private val COUNT = Regex("""x(\d+)""")

    // Hypixel plot numbers on the 5x5 grid, north to south
    private val LAYOUT = arrayOf(
        intArrayOf(21, 13, 9, 14, 22), intArrayOf(15, 5, 1, 6, 16), intArrayOf(10, 2, 0, 3, 11),
        intArrayOf(17, 7, 4, 8, 18), intArrayOf(23, 19, 12, 20, 24),
    )
    private val labels = HashMap<String, Int>()
    private val remembered = HashMap<Int, Long>()
    private var tabPlots: List<Int> = emptyList()
    var alive = 0; private set
    private var cooldown = ""; private var spray = ""; private var bonus = ""
    private val drops = LinkedHashMap<String, Int>()
    private var kills = 0
    private var tick = 0
    private var lastScreen: Any? = null

    fun init() {
        Events.ON_GAME_MESSAGE.register { t -> onChat(Garden.strip(t.string).trim()); false }
        Events.ON_WORLD_CHANGE.register { tabPlots = emptyList(); false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            scrapePlots(mc.screen)
            if (tick++ % 10 != 0 || !Garden.inGarden()) return@register
            alive = Mining.sidebar.firstOrNull { it.contains("The Garden") }?.let { COUNT.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
            tabPlots = Mining.tabLine("Plots:")?.substringAfter(":")?.split(',', '&')?.mapNotNull { idFor(it.trim()) } ?: emptyList()
            cooldown = Mining.tabLine("Cooldown:")?.substringAfter(":")?.trim() ?: ""
            spray = Mining.tabLine("Spray:")?.substringAfter(":")?.trim() ?: ""
            bonus = Mining.tabLine("Bonus:")?.substringAfter(":")?.trim() ?: ""
            if (alive == 0 && tabPlots.isEmpty()) remembered.clear()
        }
        RenderingEvents.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (!S.gardenPestFinder || !Garden.inGarden()) return@register
            val now = System.currentTimeMillis()
            remembered.entries.removeIf { now - it.value > 6 * 60_000L }
            val plots = if (tabPlots.isNotEmpty()) tabPlots.toSet() else remembered.keys
            val start = Mining.lineStart()
            for (id in plots) {
                val c = centerOf(id) ?: continue
                val b = AABB(c.x - 1, 66.0, c.z - 1, c.x + 1, 110.0, c.z + 1)
                RenderUtils.fillBox(ps, vc, b, Mining.alpha(S.gardenPestFinderColor, S.gardenPestFinderOpacity))
                Mining.outline(ps, vc, b, Mining.alpha(S.gardenPestFinderColor, 100), 2f)
                if (S.gardenPestFinderLine) RenderUtils.screenLine(ps, vc, start, c, Mining.alpha(S.gardenPestFinderColor, 100), 2f)
            }
        }
        Garden.hud("Pest Timer", "garden_pests", 150, 40,
            { S.gardenPestHudX }, { S.gardenPestHudX = it }, { S.gardenPestHudY }, { S.gardenPestHudY = it },
            { S.gardenPestHudScale }, { S.gardenPestHudScale = it },
            { S.gardenPestHud && Garden.inGarden() }) {
            val out = arrayListOf("§c§lPests §f$alive")
            if (cooldown.isNotEmpty()) out += "§7Cooldown: " + (if (cooldown.contains("READY", true)) "§a" else "§e") + cooldown
            val plots = if (tabPlots.isNotEmpty()) tabPlots else remembered.keys.toList()
            if (plots.isNotEmpty()) out += "§7Plots: §f" + plots.joinToString(", ") { labelOf(it) }
            if (spray.isNotEmpty()) out += "§7Spray: " + (if (spray.startsWith("None")) "§c" else "§a") + spray
            if (bonus.isNotEmpty()) out += "§7Bonus: §6$bonus"
            out
        }
        Garden.hud("Pest Profit", "garden_pest_profit", 160, 50,
            { S.gardenPestProfitHudX }, { S.gardenPestProfitHudX = it }, { S.gardenPestProfitHudY }, { S.gardenPestProfitHudY = it },
            { S.gardenPestProfitHudScale }, { S.gardenPestProfitHudScale = it },
            { S.gardenPestProfit && Garden.inGarden() }, ::profitLines)
    }

    private fun onChat(s: String) {
        if (!Garden.inGarden()) return
        if (NO_PESTS.containsMatchIn(s)) { remembered.clear(); return }
        APPEARED.find(s)?.let { m ->
            val label = m.groupValues[2].ifEmpty { m.groupValues[3] }
            idFor(label)?.let { remembered[it] = System.currentTimeMillis() }
            if (S.gardenPestTitle) Mining.title(S.gardenPestTitleText.replace("{plot}", label), "", 1500, S.gardenPestSound)
            return
        }
        OFFLINE.find(s)?.let { m ->
            for (t in m.groupValues[1].split(Regex(",| and "))) idFor(t.trim())?.let { remembered[it] = System.currentTimeMillis() }
            return
        }
        DROP.find(s)?.let { m ->
            kills++
            val n = m.groupValues[1].toIntOrNull() ?: 1
            drops[m.groupValues[2]] = (drops[m.groupValues[2]] ?: 0) + n
        }
    }

    private fun profitLines(): List<String> {
        val rows = drops.map { (k, v) -> Triple(k, v, Garden.priceOf(k) * v) }.sortedByDescending { it.third }
        val out = arrayListOf("§c§lPest Profit")
        for (r in rows.take(10)) out += "§6${Garden.short(r.third)}\t§f${r.first} §8x${r.second}"
        out += "§7Kills: §f$kills"
        out += "§eTotal: §6${Garden.short(rows.sumOf { it.third })}"
        return out
    }

    fun resetProfit() { drops.clear(); kills = 0 }

    // "Configure Plots" menu: 5x5 grid from slot 2, names like "Plot - 4" or custom
    private fun scrapePlots(screen: Any?) {
        val scr = screen as? AbstractContainerScreen<*> ?: run { lastScreen = null; return }
        if (scr === lastScreen || Garden.strip(scr.title.string).trim() != "Configure Plots") return
        lastScreen = scr
        for (r in 0..4) for (c in 0..4) {
            val st = scr.menu.slots.getOrNull(2 + r * 9 + c)?.item ?: continue
            if (st.isEmpty) continue
            val name = Garden.strip(st.hoverName.string).trim()
            val label = if (name.contains("Plot -")) name.substringAfter("Plot -").trim() else if (name == "The Barn") name else continue
            labels[label.lowercase()] = LAYOUT[r][c]
        }
    }

    private fun idFor(label: String): Int? {
        if (label.isEmpty()) return null
        labels[label.lowercase()]?.let { return it }
        if (label == "The Barn") return 0
        return label.takeWhile { it.isDigit() }.toIntOrNull()?.takeIf { it in 1..24 }
    }

    private fun labelOf(id: Int): String = labels.entries.firstOrNull { it.value == id }?.key?.uppercase() ?: id.toString()

    private fun centerOf(id: Int): Vec3? {
        for (r in 0..4) for (c in 0..4) if (LAYOUT[r][c] == id) return Vec3((c - 2) * 96.0, 76.0, (r - 2) * 96.0)
        return null
    }
}
