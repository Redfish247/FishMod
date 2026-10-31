package fishmod.features.garden

import fishmod.utils.Misc
import net.minecraft.sounds.SoundEvents
import fishmod.features.garden.GardenSettings as S

// On-screen rare drop / rare reward alert, placed and scaled in the HUD editor
object GardenAlerts {

    private var lines: List<String> = emptyList()
    private var until = 0L

    private val RARITY = mapOf(
        "COMMON" to "§f", "UNCOMMON" to "§a", "RARE" to "§9", "EPIC" to "§5", "LEGENDARY" to "§6",
        "MYTHIC" to "§d", "DIVINE" to "§b", "SPECIAL" to "§c", "VERY_SPECIAL" to "§c",
    )

    // Chat colour of the first name ItemsDb knows, from the item's Hypixel tier
    fun rarityColor(vararg names: String): String? {
        for (n in names) {
            val id = fishmod.utils.networth.ItemsDb.idFor(n.trim()) ?: continue
            val tier = fishmod.utils.networth.ItemsDb.get(id)?.get("tier")?.asString ?: continue
            return RARITY[tier]
        }
        return null
    }

    fun show(title: String, sub: String, sound: Boolean = true) {
        if (!S.gardenAlert) return
        lines = listOf(title, sub).filter { it.isNotEmpty() }
        until = System.currentTimeMillis() + S.gardenAlertSeconds.coerceIn(1, 15) * 1000L
        if (sound) Misc.sendSound2D(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f)
    }

    fun init() {
        Garden.hud("Rare Drop Alert", "garden_alert", 160, 24,
            { S.gardenAlertX }, { S.gardenAlertX = it }, { S.gardenAlertY }, { S.gardenAlertY = it },
            { S.gardenAlertScale }, { S.gardenAlertScale = it },
            { S.gardenAlert && System.currentTimeMillis() < until }, { S.gardenAlert }) { lines }
    }
}
