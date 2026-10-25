package fishmod.features.garden
import fishmod.compat.curScreen

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
    private val PLOT_SEP = Regex(",| and ")

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
    // Vacuum trail: angry-villager particles lead to the pest, the farthest one is the guess
    private val trail = ArrayList<Vec3>()
    private var lastTrail = 0L
    private var guess: Vec3? = null

    private fun holdingVacuum(): Boolean =
        net.minecraft.client.Minecraft.getInstance().player?.mainHandItem
            ?.let { fishmod.utils.data.ItemUtil.getId(it) }?.contains("VACUUM") == true

    private fun updateGuess() {
        val me = net.minecraft.client.Minecraft.getInstance().player ?: return
        if (trail.size < 2) return
        guess = trail.maxByOrNull { it.distanceToSqr(me.position()) }
    }

    fun init() {
        Events.ON_GAME_MESSAGE.register { t -> if (Garden.inGarden()) onChat(Garden.strip(t.string).trim()); false }
        Events.ON_WORLD_CHANGE.register { tabPlots = emptyList(); trail.clear(); guess = null; false }
        Events.ON_PARTICLE.register { p ->
            if (S.gardenPestFinder && p.particle.type === net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER && p.count == 1 && p.maxSpeed == 0f && Garden.inGarden() && holdingVacuum()) {
                val now = System.currentTimeMillis()
                if (now - lastTrail > 2500L) { trail.clear(); guess = null }
                lastTrail = now
                trail.add(Vec3(p.x, p.y, p.z))
                updateGuess()
            }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            scrapePlots(mc.curScreen)
            if (tick++ % 10 != 0 || !Garden.inGarden()) return@register
            alive = Mining.sidebar.firstOrNull { it.contains("The Garden") || it.contains("ൠ") }?.let { COUNT.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
            tabPlots = Mining.tabLine("Plots:")?.substringAfter(":")?.split(',', '&')?.mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 0..24 } } ?: emptyList()
            cooldown = Mining.tabLine("Cooldown:")?.substringAfter(":")?.trim() ?: ""
            spray = Mining.tabLine("Spray:")?.substringAfter(":")?.trim() ?: ""
            bonus = Mining.tabLine("Bonus:")?.substringAfter(":")?.trim() ?: ""
        }
        RenderingEvents.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (!S.gardenPestFinder || !Garden.inGarden()) return@register
            val now = System.currentTimeMillis()
            remembered.entries.removeIf { now - it.value > 6 * 60_000L }
            val plots = if (tabPlots.isNotEmpty()) tabPlots.toSet() else remembered.keys
            val start = Mining.lineStart()
            val g = guess
            if (g != null) {
                val me = net.minecraft.client.Minecraft.getInstance().player
                if (now - lastTrail > 20_000L || (me != null && me.position().distanceToSqr(g) < 16.0)) guess = null
            }
            guess?.let { gp ->
                val gb = AABB(gp.x - 0.5, gp.y - 0.5, gp.z - 0.5, gp.x + 0.5, gp.y + 0.5, gp.z + 0.5)
                RenderUtils.fillBox(ps, vc, gb, Mining.alpha(S.gardenPestMarkerColor, 120))
                Mining.outline(ps, vc, gb, Mining.alpha(S.gardenPestMarkerColor, 255), 2f)
                if (S.gardenPestFinderLine) RenderUtils.screenLine(ps, vc, start, gp, Mining.alpha(S.gardenPestLineColor, 255), 2f)
            }
            for (id in plots) {
                val c = centerOf(id) ?: continue
                val b = AABB(c.x - 48, 66.0, c.z - 48, c.x + 48, 110.0, c.z + 48)
                val me = net.minecraft.client.Minecraft.getInstance().player
                val inside = me != null && me.x >= b.minX && me.x <= b.maxX && me.z >= b.minZ && me.z <= b.maxZ
                if (!inside) RenderUtils.fillBox(ps, vc, b, Mining.alpha(S.gardenPestFinderColor, S.gardenPestFinderOpacity))
                Mining.outline(ps, vc, b, Mining.alpha(S.gardenPestFinderColor, 100), 2f)
                if (S.gardenPestFinderLine && guess == null) RenderUtils.screenLine(ps, vc, start, c, Mining.alpha(S.gardenPestLineColor, 100), 2f)
            }
        }
        Garden.hud("Pest Timer", "garden_pests", 150, 40,
            { S.gardenPestHudX }, { S.gardenPestHudX = it }, { S.gardenPestHudY }, { S.gardenPestHudY = it },
            { S.gardenPestHudScale }, { S.gardenPestHudScale = it },
            { S.gardenPestHud && Garden.inGarden() }, { S.gardenPestHud }) {
            val out = arrayListOf("${Garden.legacy(S.gardenPestHudColor)}§lPests §f$alive")
            if (cooldown.isNotEmpty()) out += "§7Cooldown: " + (if (cooldown.contains("READY", true)) "§a" else "§e") + cooldown
            val plots = if (tabPlots.isNotEmpty()) tabPlots else remembered.keys.toList()
            if (plots.isNotEmpty()) out += "§7Plots: §f" + plots.joinToString(", ") { labelOf(it) }
            if (spray.isNotEmpty()) out += "§7Spray: " + (if (spray.startsWith("None")) "§c" else "§a") + spray
            if (bonus.isNotEmpty()) out += "§7Bonus: §6$bonus"
            out
        }
    }

    private fun onChat(s: String) {
        if (!Garden.inGarden()) return
        if (NO_PESTS.containsMatchIn(s)) { remembered.clear(); return }
        APPEARED.find(s)?.let { m ->
            val label = m.groupValues[2].ifEmpty { m.groupValues[3] }
            idFor(label)?.let { remembered[it] = System.currentTimeMillis() }
            if (S.gardenPestTitle) GardenTitles.show(S.gardenPestTitleText.replace("{plot}", label), "", 1500, S.gardenPestSound)
            return
        }
        OFFLINE.find(s)?.let { m ->
            for (t in m.groupValues[1].split(PLOT_SEP)) idFor(t.trim())?.let { remembered[it] = System.currentTimeMillis() }
            return
        }
        DROP.find(s)?.let { m ->
            kills++
            val n = m.groupValues[1].toIntOrNull() ?: 1
            drops[m.groupValues[2]] = (drops[m.groupValues[2]] ?: 0) + n
        }
    }

    fun profitValue(): Double = drops.entries.sumOf { (k, v) -> Garden.priceOf(k) * v }
    fun killCount(): Int = kills


    // "Configure Plots" menu: 5x5 grid from slot 2, names like "Plot - 4" or custom
    private fun scrapePlots(screen: Any?) {
        val scr = screen as? AbstractContainerScreen<*> ?: run { lastScreen = null; return }
        if (scr === lastScreen || Garden.strip(scr.title.string).trim() != "Configure Plots") return
        var found = false
        for (r in 0..4) for (c in 0..4) {
            val st = scr.menu.slots.getOrNull(2 + r * 9 + c)?.item ?: continue
            if (st.isEmpty) continue
            val name = Garden.strip(st.hoverName.string).trim()
            val label = if (name.contains("Plot -")) name.substringAfter("Plot -").trim() else if (name == "The Barn") name else continue
            labels[label.lowercase()] = LAYOUT[r][c]; found = true
        }
        if (found) lastScreen = scr
    }

    private fun idFor(label: String): Int? {
        if (label.isEmpty()) return null
        labels[label.lowercase()]?.let { return it }
        if (label == "The Barn") return 0
        return label.toIntOrNull()?.takeIf { it in 1..24 }
    }

    private fun labelOf(id: Int): String = labels.entries.firstOrNull { it.value == id }?.key?.uppercase() ?: id.toString()

    private fun centerOf(id: Int): Vec3? {
        for (r in 0..4) for (c in 0..4) if (LAYOUT[r][c] == id) return Vec3((c - 2) * 96.0, 76.0, (r - 2) * 96.0)
        return null
    }
}
