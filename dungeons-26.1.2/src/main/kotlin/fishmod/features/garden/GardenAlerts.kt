package fishmod.features.garden

import fishmod.utils.FishUtil
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
        lines = alertLines(title to sub)
        until = System.currentTimeMillis() + S.gardenAlertSeconds.coerceIn(1, 15) * 1000L
        if (sound) FishUtil.sendSound2D(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f)
    }

    private fun alertLines(t: Pair<String, String>) = listOf(t.first, t.second).filter { it.isNotEmpty() }

    // The alert the player's settings can fire: rare crop first, else rare reward
    private fun example(): List<String> = when {
        S.gardenRareCrop && S.gardenRareCropTitle -> alertLines(GardenWidgets.rareCropAlert("RARE CROP! Cropie"))
        S.gardenRareRewardWarn -> alertLines(Visitors.rareRewardAlert("Overgrown Grass"))
        else -> emptyList()
    }

    fun init() {
        Garden.hud("Rare Drop Alert", "garden_alert", 160, 24,
            { S.gardenAlertX }, { S.gardenAlertX = it }, { S.gardenAlertY }, { S.gardenAlertY = it },
            { S.gardenAlertScale }, { S.gardenAlertScale = it },
            { S.gardenAlert && System.currentTimeMillis() < until }, { S.gardenAlert },
            example = ::example) { lines }
    }
}
