package fishmod.features.garden

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import fishmod.features.garden.GardenExtraSettings as X

// Charmed visitor + mushroom pet perk progress, read from the tab list widget whose header is configurable (format is a guess)
object GardenPerks {

    private var charmed: List<String> = emptyList()
    private var mushroom: List<String> = emptyList()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || tick++ % 20 != 0) return@register
            if (!Garden.inGarden()) { charmed = emptyList(); mushroom = emptyList(); return@register }
            charmed = if (X.gardenCharmed) GardenExtras.tabBlock(X.gardenCharmedHeader) else emptyList()
            mushroom = if (X.gardenMushroom) GardenExtras.tabBlock(X.gardenMushroomHeader) else emptyList()
        }
        Garden.hud("Charmed Visitor", "garden_charmed", 130, 24,
            { X.gardenCharmedHudX }, { X.gardenCharmedHudX = it }, { X.gardenCharmedHudY }, { X.gardenCharmedHudY = it },
            { X.gardenCharmedHudScale }, { X.gardenCharmedHudScale = it },
            { X.gardenCharmed && charmed.isNotEmpty() }) { listOf(Garden.legacy(X.gardenCharmedColor) + "§lCharmed Visitor") + charmed.map { "§7$it" } }
        Garden.hud("Mushroom Pet Perk", "garden_mushroom", 130, 24,
            { X.gardenMushroomHudX }, { X.gardenMushroomHudX = it }, { X.gardenMushroomHudY }, { X.gardenMushroomHudY = it },
            { X.gardenMushroomHudScale }, { X.gardenMushroomHudScale = it },
            { X.gardenMushroom && mushroom.isNotEmpty() }) { listOf(Garden.legacy(X.gardenMushroomColor) + "§lMushroom Perk") + mushroom.map { "§7$it" } }
    }
}
