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
    private const val PAD = 4
    private var armedAt = 0L

    private fun armed() = System.currentTimeMillis() - armedAt <= CONFIRM_MS
    private fun label() = if (armed()) "§c§lClick again to confirm" else "§eReset Trackers"

    private fun bounds(screenW: Int): IntArray {
        val w = Minecraft.getInstance().font.width(label()) + PAD * 2
        return intArrayOf(screenW - w - 4, 4, screenW - 4, 4 + 9 + PAD * 2)
    }

    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is InventoryScreen) return@AfterInit
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, mx, my, _ ->
                val b = bounds(screen.width)
                val hover = mx in b[0]..b[2] && my in b[1]..b[3]
                ctx.fill(b[0], b[1], b[2], b[3], if (hover) 0xC0303030.toInt() else 0x90000000.toInt())
                ctx.text(Minecraft.getInstance().font, label(), b[0] + PAD, b[1] + PAD + 1, -1, true)
            })
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click ->
                val b = bounds(screen.width)
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
