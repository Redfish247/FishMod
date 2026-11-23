package fishmod.utils.rendering
import fishmod.compat.curScreen

import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft

object UiScale {
    private const val REFERENCE_GUI_SCALE = 2.0
    private const val FLAT_SHRINK = 0.77f

    private const val SIBLING_ENLARGE = 1.30f

    fun factor(): Float {
        val guiScale = Minecraft.getInstance().window.guiScale
        if (guiScale <= 0) FishDiag.fail("UiScale.1", "window guiScale is $guiScale, UI scale will be infinite")
        val compensation = REFERENCE_GUI_SCALE / guiScale
        val base = (compensation * FLAT_SHRINK).toFloat()
        return (if (isSiblingScreen()) base * SIBLING_ENLARGE else base) * userScale()
    }

    @JvmStatic
    fun userScale(screen: net.minecraft.client.gui.screens.Screen? = Minecraft.getInstance().curScreen): Float {
        if (screen == null) return 1f
        val pct = when (screen) {
            is fishmod.features.FishModScreen -> FishSettings.guiScaleMain
            is fishmod.features.hud.ChatCommandsScreen -> FishSettings.guiScaleChatCommands
            is fishmod.features.party.PartyLootScreen -> FishSettings.guiScalePartyLoot
            is fishmod.features.FishHudEditor -> FishSettings.guiScaleHudEditor
            is fishmod.features.hud.ItemCustomizeScreen -> FishSettings.guiScaleItemCustomize
            is fishmod.features.inventory.StorageViewerScreen -> FishSettings.guiScaleStorage
            is fishmod.features.hud.AuctionPriceScreen -> FishSettings.guiScaleAuction
            is fishmod.features.dungeon.DungeonWaypointTitleScreen -> FishSettings.guiScaleWaypoint
            is fishmod.features.diana.DianaPastEventsScreen -> FishSettings.guiScaleDianaProfit
            is fishmod.features.diana.DianaMessagesScreen -> FishSettings.guiScaleDianaMessages
            is fishmod.features.achievements.AchScreen -> FishSettings.guiScaleAchievements
            is fishmod.features.general.InventoryButtonEditScreen -> FishSettings.guiScaleInvButtons
            is fishmod.features.hud.MarketScreen -> FishSettings.guiScaleMarket
            is fishmod.features.hud.RecipeScreen -> FishSettings.guiScaleRecipe
            is fishmod.utils.update.UpdateScreen -> FishSettings.guiScaleUpdate
            else -> 100
        }
        return pct.coerceIn(50, 150) / 100f
    }

    private fun isSiblingScreen(): Boolean {
        val s = Minecraft.getInstance().curScreen ?: return false
        return s is fishmod.features.HasUiOverlay && s !is fishmod.features.FishModScreen
    }

    fun vx(real: Number): Int = (real.toDouble() / factor()).toInt()
}
