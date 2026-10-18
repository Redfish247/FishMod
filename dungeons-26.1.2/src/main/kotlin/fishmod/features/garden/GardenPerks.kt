package fishmod.features.garden

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import fishmod.features.garden.GardenExtraSettings as X

// Charmed visitor + mushroom pet perk progress, read from the tab list widget whose header is configurable (format is a guess)
object GardenPerks {

    private var charmed: List<String> = emptyList()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || tick++ % 20 != 0) return@register
            if (!Garden.inGarden()) { charmed = emptyList(); return@register }
            charmed = if (X.gardenCharmed) GardenExtras.tabBlock(X.gardenCharmedHeader) else emptyList()
        }
        Garden.hud("Charmed Visitor", "garden_charmed", 130, 24,
            { X.gardenCharmedHudX }, { X.gardenCharmedHudX = it }, { X.gardenCharmedHudY }, { X.gardenCharmedHudY = it },
            { X.gardenCharmedHudScale }, { X.gardenCharmedHudScale = it },
            { X.gardenCharmed && charmed.isNotEmpty() }, { X.gardenCharmed }) { listOf(Garden.legacy(X.gardenCharmedColor) + "§lCharmed Visitor") + charmed.map { "§7$it" } }
    }
}
