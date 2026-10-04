package fishmod.features.other

import fishmod.features.diana.CrownOfAvarice
import fishmod.features.diana.DianaTracker
import fishmod.features.mining.MiningProfitTracker
import fishmod.features.slayers.SlayerProfitTracker
import fishmod.features.slayers.SlayerStatsTracker
import fishmod.utils.FishMsg
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen

// Inventory button: click twice to reset the Mining, Diana, Crown of Avarice and Slayer session trackers
object TrackerResetButton {

    private const val CONFIRM_MS = 3_000L
    private var armedAt = 0L

    private fun armed() = System.currentTimeMillis() - armedAt <= CONFIRM_MS
    private fun label(hover: Boolean) = when {
        armed() -> "§4§l[ Click again to confirm ]"
        hover -> "§4§l[ Reset Trackers ]"
        else -> "§c§l[ Reset Trackers ]"
    }

    // Centred just under the inventory panel (176x166)
    private fun bounds(screenW: Int, screenH: Int): IntArray {
        val w = Minecraft.getInstance().font.width(label(false))
        val x = screenW / 2 - w / 2
        val y = (screenH + 166) / 2 + 4
        return intArrayOf(x, y, x + w, y + 10)
    }

    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is InventoryScreen) return@AfterInit
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, mx, my, _ ->
                val b = bounds(screen.width, screen.height)
                val hover = mx in b[0]..b[2] && my in b[1]..b[3]
                val font = Minecraft.getInstance().font
                val l = label(hover)
                ctx.text(font, l, screen.width / 2 - font.width(l) / 2, b[1], -1, true)
            })
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click ->
                val b = bounds(screen.width, screen.height)
                if (click.button() != 0 || click.x() < b[0] || click.x() > b[2] || click.y() < b[1] || click.y() > b[3]) return@AllowMouseClick true
                onClick()
                false
            })
        })
    }

    private fun onClick() {
        if (!armed()) { armedAt = System.currentTimeMillis(); return }
        armedAt = 0L
        MiningProfitTracker.resetSession(false)
        DianaTracker.resetSession(false)
        CrownOfAvarice.resetSessions()
        SlayerProfitTracker.resetSession()
        SlayerStatsTracker.reset()
        FishMsg.send("§aReset Mining, Diana, Crown of Avarice and Slayer session trackers.")
    }
}
