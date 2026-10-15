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
            if (idle && !composterWarned && S.gardenComposterWarn) Mining.title("§cComposter Inactive!", "", 2000, true)
            composterWarned = idle
        }
        Events.ON_GAME_MESSAGE.register { t ->
            val s = Garden.strip(t.string).trim()
            if (S.gardenRareCrop && s.startsWith("RARE CROP!")) {
                S.gardenRareCropCount++
                if (S.gardenRareCropTitle) Mining.title("§6§lRARE CROP!", "§f" + s.removePrefix("RARE CROP!").trim(), 2000, true)
            }
            false
        }
        Garden.hud("Composter", "garden_composter", 150, 50,
            { S.gardenComposterHudX }, { S.gardenComposterHudX = it }, { S.gardenComposterHudY }, { S.gardenComposterHudY = it },
            { S.gardenComposterHudScale }, { S.gardenComposterHudScale = it },
            { S.gardenComposterHud && composter.isNotEmpty() }) { listOf("§2§lComposter") + composter.map { "§7$it" } }
        Garden.hud("Jacob's Contest", "garden_jacob", 150, 50,
            { S.gardenJacobHudX }, { S.gardenJacobHudX = it }, { S.gardenJacobHudY }, { S.gardenJacobHudY = it },
            { S.gardenJacobHudScale }, { S.gardenJacobHudScale = it },
            { S.gardenJacobHud && jacob.isNotEmpty() }) { listOf("§e§lJacob's Contest") + jacob.map { "§f$it" } }
        Garden.hud("Rare Crops", "garden_rare_crop", 120, 12,
            { S.gardenRareCropHudX }, { S.gardenRareCropHudX = it }, { S.gardenRareCropHudY }, { S.gardenRareCropHudY = it },
            { S.gardenRareCropHudScale }, { S.gardenRareCropHudScale = it },
            { S.gardenRareCrop && Garden.inGarden() }) { listOf("§6Rare Crops: §f${S.gardenRareCropCount}") }
    }
}
