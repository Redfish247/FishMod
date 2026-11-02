package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import fishmod.utils.debug.FishDiag

object GoldorTicks {

    private val timer = TickTimer()

    @JvmStatic
    fun init() {
        timer.init(
            shouldCount = { SkyblockLocation.inDungeon() && BossPhase.inTerminals() },
            resetOn = { SkyblockLocation.inDungeon() }
        )
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7Settings.enableTickTimers && Floor7Settings.goldorTicksHud && SkyblockLocation.inDungeon() && BossPhase.inTerminals()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        var num = timer.tick * FishConstants.TICK_DURATION
        var mod = num % 3
        if (Floor7Settings.goldorUseDeathTicks && !Floor7Settings.goldorTicksCountUp) mod = 3.0 - mod
        if (Floor7Settings.goldorUseDeathTicks) num = mod
        val color = if (mod < 1) FishConstants.GREEN else if (mod < 2) FishConstants.GOLD else FishConstants.RED
        FishDiag.check(timer.tick >= 0, "GoldorTickTimer.1") { "goldor tick timer negative: ${timer.tick}" }
        FishDiag.guard("GoldorTickTimer.2", "goldor tick timer render threw") { FishDraw.drawTimer(component, context, num, color) }
    }
}
