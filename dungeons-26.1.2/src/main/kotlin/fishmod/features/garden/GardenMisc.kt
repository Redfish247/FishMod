package fishmod.features.garden

import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import fishmod.features.garden.GardenMilestoneSettings as S

// Visitor Log Book stats HUD + Help Carrolyn checklist (menu/chat formats are guesses, unverified)
object GardenMisc {

    private val STAT = Regex("^([A-Za-z' ]+): ([\\d,]+)(?:/([\\d,]+))?$")
    private val CARROLYN_ITEMS = listOf("Expired Pumpkin", "Exportable Carrots", "Supreme Chocolate Bar")

    private val logBook = LinkedHashMap<String, String>()
    private var tick = 0

    private fun done() = S.gardenCarrolynDone.split(",").filter { it.isNotBlank() }.toSet()

    fun resetCarrolyn() { S.gardenCarrolynDone = "" }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || tick++ % 20 != 0) return@register
            scanLogBook()
        }
        Events.ON_GAME_MESSAGE.register { t ->
            val s = Garden.strip(t.string)
            if (Garden.inGarden() && s.contains("Carrolyn", true))
                for (i in CARROLYN_ITEMS) if (s.contains(i, true) && !s.contains("need", true) && !s.contains("bring", true))
                    S.gardenCarrolynDone = (done() + i).joinToString(",")
            false
        }
        Garden.hud("Log Book", "garden_logbook", 150, 50,
            { S.gardenLogBookHudX }, { S.gardenLogBookHudX = it }, { S.gardenLogBookHudY }, { S.gardenLogBookHudY = it },
            { S.gardenLogBookHudScale }, { S.gardenLogBookHudScale = it },
            { S.gardenLogBookHud && Garden.inGarden() && logBook.isNotEmpty() }) {
            listOf(Garden.legacy(S.gardenLogBookColor) + "§lLog Book") + logBook.map { "§7${it.key}: §f${it.value}" }
        }
        Garden.hud("Help Carrolyn", "garden_carrolyn", 150, 40,
            { S.gardenCarrolynHudX }, { S.gardenCarrolynHudX = it }, { S.gardenCarrolynHudY }, { S.gardenCarrolynHudY = it },
            { S.gardenCarrolynHudScale }, { S.gardenCarrolynHudScale = it },
            { S.gardenCarrolyn && Garden.inGarden() }) {
            val d = done()
            listOf(Garden.legacy(S.gardenCarrolynColor) + "§lHelp Carrolyn") + CARROLYN_ITEMS.map { (if (it in d) "§a✔ §7" else "§c✘ §f") + it }
        }
    }

    // Caches "Name: x/y" stat lines from the Logbook menu's info item
    private fun scanLogBook() {
        val scr = Minecraft.getInstance().screen as? AbstractContainerScreen<*> ?: return
        val t = Garden.strip(scr.title.string)
        if (!t.contains("Logbook", true) && !t.contains("Log Book", true)) return
        for (s in scr.menu.slots) {
            val st = s.item
            if (st.isEmpty) continue
            val n = Garden.strip(st.hoverName.string)
            if (!n.contains("Logbook", true) && !n.contains("Log Book", true) && !n.contains("Statistics", true)) continue
            for (l in Garden.lore(st)) {
                val m = STAT.find(l.trim()) ?: continue
                logBook[m.groupValues[1]] = m.groupValues[2] + (if (m.groupValues[3].isNotEmpty()) "/" + m.groupValues[3] else "")
            }
        }
    }
}
