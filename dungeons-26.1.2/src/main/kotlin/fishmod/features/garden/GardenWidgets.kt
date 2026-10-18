package fishmod.features.garden

import fishmod.features.mining.Mining
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import fishmod.features.garden.GardenSettings as S

// Composter and Jacob's Contest HUDs from the tab list, rare crop counter
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
            if (idle && !composterWarned && S.gardenComposterWarn) GardenTitles.show(Garden.legacy(S.gardenComposterWarnColor) + "Composter Inactive!", "", 2000, true)
            composterWarned = idle
        }
        Events.ON_GAME_MESSAGE.register { t ->
            val s = Garden.strip(t.string).trim()
            if (S.gardenRareCrop && RARE_CROP.containsMatchIn(s)) {
                S.gardenRareCropCount++
                if (S.gardenRareCropTitle) {
                    val rest = s.replace(RARE_CROP, "").trim()
                    val name = rest.substringBefore(" (").trim()
                    val rc = GardenAlerts.rarityColor(rest, name) ?: Garden.legacy(S.gardenRareCropColor)
                    GardenAlerts.show(rc + "§l" + (if (s.startsWith("VERY")) "VERY RARE CROP!" else "RARE CROP!"), rc + rest)
                }
            }
            false
        }
        Garden.hud("Composter", "garden_composter", 150, 50,
            { S.gardenComposterHudX }, { S.gardenComposterHudX = it }, { S.gardenComposterHudY }, { S.gardenComposterHudY = it },
            { S.gardenComposterHudScale }, { S.gardenComposterHudScale = it },
            { S.gardenComposterHud && composter.isNotEmpty() }) { listOf(Garden.legacy(S.gardenComposterColor) + "§lComposter") + composter.map(::composterLine) }
        Garden.hud("Jacob's Contest", "garden_jacob", 150, 50,
            { S.gardenJacobHudX }, { S.gardenJacobHudX = it }, { S.gardenJacobHudY }, { S.gardenJacobHudY = it },
            { S.gardenJacobHudScale }, { S.gardenJacobHudScale = it },
            { S.gardenJacobHud && jacob.isNotEmpty() }) { listOf(Garden.legacy(S.gardenJacobColor) + "§lJacob's Contest") + jacob.map(::jacobLine) }
        Garden.hud("Rare Crops", "garden_rare_crop", 120, 12,
            { S.gardenRareCropHudX }, { S.gardenRareCropHudX = it }, { S.gardenRareCropHudY }, { S.gardenRareCropHudY = it },
            { S.gardenRareCropHudScale }, { S.gardenRareCropHudScale = it },
            { S.gardenRareCrop && Garden.inGarden() }) { listOf(Garden.legacy(S.gardenRareCropColor) + "Rare Crops: §f${S.gardenRareCropCount}") }
    }

    private val RARE_CROP = Regex("^(?:VERY )?RARE CROP!")

    private val CROP_COLOR = mapOf(
        "wheat" to "§6", "carrot" to "§6", "potato" to "§e", "pumpkin" to "§6", "melon" to "§a",
        "mushroom" to "§c", "cocoa" to "§6", "cactus" to "§2", "sugar cane" to "§a", "nether wart" to "§4",
    )

    // Label in grey, value coloured per row; INACTIVE / 0 shown in the warning colour
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

    // "Starts In: 37m" gets a yellow value; crop rows get their crop colour
    private fun jacobLine(l: String): String {
        val t = l.trim()
        val i = t.indexOf(':')
        if (i in 1..18) return "§7" + t.substring(0, i + 1) + " §e" + t.substring(i + 1).trim()
        val c = CROP_COLOR.entries.firstOrNull { t.contains(it.key, true) }?.value ?: "§f"
        return c + t
    }
}
