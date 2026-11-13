package fishmod.features.garden

import fishmod.utils.events.chatRaw
import fishmod.features.mining.Mining
import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import fishmod.features.garden.GardenSettings as S

object GardenWidgets {

    private var composter: List<String> = emptyList()
    private var jacob: List<String> = emptyList()
    private var composterWarned = false
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || tick++ % 20 != 0) return@register
            if (!Garden.inGarden()) { composter = emptyList(); jacob = emptyList(); return@register }
            composter = Mining.tabWidget("Composter:")
            jacob = Mining.tabWidget("Jacob's Contest:")
            val idle = composter.any { it.contains("INACTIVE", true) || it.startsWith("Fuel: 0") || it.startsWith("Organic Matter: 0") }
            if (idle && !composterWarned && S.gardenComposterWarn) GardenTitles.show(GardenTitles.composterIdle(),"", 2000, true)
            composterWarned = idle
        }
        FishEvents.chat.register { t ->
            val s = if (S.gardenRareCrop) Garden.strip(t.chatRaw).trim() else ""
            if (S.gardenRareCrop && RARE_CROP.containsMatchIn(s)) {
                S.gardenRareCropCount++
                if (S.gardenRareCropTitle) rareCropAlert(s).let { (t, sub) -> GardenAlerts.show(t, sub) }
            }
            false
        }
        Garden.hud("Composter", "garden_composter", 150, 50,
            { S.gardenComposterHudX }, { S.gardenComposterHudX = it }, { S.gardenComposterHudY }, { S.gardenComposterHudY = it },
            { S.gardenComposterHudScale }, { S.gardenComposterHudScale = it },
            { S.gardenComposterHud && composter.isNotEmpty() }, { S.gardenComposterHud },
            example = { composterLines(listOf("Organic Matter: 12,500", "Fuel: 8,200", "Time Left: 4m 30s", "Stored Compost: 38")) }) { composterLines(composter) }
        Garden.hud("Jacob's Contest", "garden_jacob", 150, 50,
            { S.gardenJacobHudX }, { S.gardenJacobHudX = it }, { S.gardenJacobHudY }, { S.gardenJacobHudY = it },
            { S.gardenJacobHudScale }, { S.gardenJacobHudScale = it },
            { S.gardenJacobHud && jacob.isNotEmpty() }, { S.gardenJacobHud },
            example = { jacobLines(listOf("Starts In: 37m", "Wheat", "Carrot", "Melon")) }) { jacobLines(jacob) }
        Garden.hud("Rare Crops", "garden_rare_crop", 120, 12,
            { S.gardenRareCropHudX }, { S.gardenRareCropHudX = it }, { S.gardenRareCropHudY }, { S.gardenRareCropHudY = it },
            { S.gardenRareCropHudScale }, { S.gardenRareCropHudScale = it },
            { S.gardenRareCrop && Garden.inGarden() }, { S.gardenRareCrop },
            example = { rareCropLines(7) }) { rareCropLines(S.gardenRareCropCount) }
    }

    private fun rareCropLines(n: Int) = listOf(Garden.legacy(S.gardenRareCropColor) + "Rare Crops: §f$n")

    private fun composterLines(rows: List<String>) = listOf(Garden.legacy(S.gardenComposterColor) + "§lComposter") + rows.map(::composterLine)

    private fun jacobLines(rows: List<String>) = listOf(Garden.legacy(S.gardenJacobColor) + "§lJacob's Contest") + rows.map(::jacobLine)

    private val RARE_CROP = Regex("^(?:VERY )?RARE CROP!")

    // Alert title + sub for a stripped rare crop chat line (also the HUD editor example)
    fun rareCropAlert(s: String): Pair<String, String> {
        val rest = s.replace(RARE_CROP, "").trim()
        val name = rest.substringBefore(" (").trim()
        val rc = GardenAlerts.rarityColor(rest, name) ?: Garden.legacy(S.gardenRareCropColor)
        return rc + "§l" + (if (s.startsWith("VERY")) "VERY RARE CROP!" else "RARE CROP!") to rc + rest
    }

    private val CROP_COLOR = mapOf(
        "wheat" to "§6", "carrot" to "§6", "potato" to "RGB:8B5A2B", "pumpkin" to "§6", "melon" to "§a",
        "mushroom" to "§c", "cocoa" to "§6", "cactus" to "§2", "sugar cane" to "§a", "nether wart" to "§4",
        "sunflower" to "§e", "moonflower" to "§9", "wild rose" to "§c",
    )

    private fun composterLine(l: String): String {
        val t = l.trim()
        val i = t.indexOf(':')
        if (i < 1) return Garden.legacy(S.gardenComposterLabelColor) + t
        val label = t.substring(0, i + 1)
        val v = t.substring(i + 1).trim()
        val vc = when {
            v.contains("INACTIVE", true) || v == "0" -> S.gardenComposterWarnColor
            label.startsWith("Organic", true) -> S.gardenComposterOrganicColor
            label.startsWith("Fuel", true) -> S.gardenComposterFuelColor
            label.startsWith("Time", true) -> S.gardenComposterTimeColor
            label.startsWith("Stored", true) -> S.gardenComposterCompostColor
            else -> S.gardenComposterTimeColor
        }
        return Garden.legacy(S.gardenComposterLabelColor) + label + " " + Garden.legacy(vc) + v
    }

    private fun jacobLine(l: String): String {
        val t = l.trim()
        val i = t.indexOf(':')
        if (i in 1..18) return "§7" + t.substring(0, i + 1) + " §e" + t.substring(i + 1).trim()
        val c = CROP_COLOR.entries.firstOrNull { t.contains(it.key, true) }?.value ?: "§f"
        if (c.startsWith("RGB:")) return fishmod.features.mining.MiningHuds.rgb(c.substring(4), t)
        return c + t
    }
}
