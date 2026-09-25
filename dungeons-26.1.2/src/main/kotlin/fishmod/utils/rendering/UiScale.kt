package fishmod.utils.rendering

import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft

object UiScale {
    private const val REFERENCE_GUI_SCALE = 2.0
    private const val FLAT_SHRINK = 0.77f

    private const val SIBLING_ENLARGE = 1.30f

    fun factor(): Float {
        val guiScale = Minecraft.getInstance().window.guiScale
        val compensation = (REFERENCE_GUI_SCALE / guiScale).coerceAtMost(1.0)
        val base = (compensation * FLAT_SHRINK).toFloat()
        return (if (isSiblingScreen()) base * SIBLING_ENLARGE else base) * userScale()
    }

    // "GUI Settings" per-screen size (UI Customization card); 1.0 when the toggle is off.
    @JvmStatic
    fun userScale(screen: net.minecraft.client.gui.screens.Screen? = Minecraft.getInstance().screen): Float {
        if (!FishSettings.guiSettings || screen == null) return 1f
        val pct = when (screen) {
            is fishmod.features.FishModScreen -> FishSettings.guiScaleMain
            is fishmod.features.ChatCommandsScreen -> FishSettings.guiScaleChatCommands
            is fishmod.features.PartyLootScreen -> FishSettings.guiScalePartyLoot
            is fishmod.features.FishHudEditor -> FishSettings.guiScaleHudEditor
            is fishmod.features.item.ItemCustomizeScreen -> FishSettings.guiScaleItemCustomize
            is fishmod.features.storage.StorageViewerScreen -> FishSettings.guiScaleStorage
            is fishmod.features.item.AuctionPriceScreen -> FishSettings.guiScaleAuction
            is fishmod.features.dungeon.DungeonWaypointTitleScreen -> FishSettings.guiScaleWaypoint
            is fishmod.features.CreditsScreen -> FishSettings.guiScaleCredits
            else -> 100
        }
        return pct.coerceIn(50, 150) / 100f
    }

    private fun isSiblingScreen(): Boolean {
        val s = Minecraft.getInstance().screen ?: return false
        return s is fishmod.features.HasUiOverlay && s !is fishmod.features.FishModScreen
    }

    fun vx(real: Number): Int = (real.toDouble() / factor()).toInt()
}
