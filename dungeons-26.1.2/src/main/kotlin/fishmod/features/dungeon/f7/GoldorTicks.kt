package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor

// P3 tick clock; Goldor's death tick fires every 60 ticks (3s).
object GoldorTicks {

    private const val CYCLE_SECONDS = 3.0
    private val counter = TickTimer()

    private fun inGoldor() = SkyblockLocation.inDungeon() && BossPhase.inTerminals()

    @JvmStatic
    fun init() = counter.init(shouldCount = ::inGoldor, resetOn = { SkyblockLocation.inDungeon() })

    @JvmStatic
    fun display(): Boolean = Floor7Settings.enableTickTimers && Floor7Settings.goldorTicksHud && inGoldor()

    private fun cycleColor(intoCycle: Double): Int = when {
        intoCycle < 1 -> FishConstants.GREEN
        intoCycle < 2 -> FishConstants.GOLD
        else -> FishConstants.RED
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.check(counter.tick >= 0, "GoldorTicks.1") { "goldor tick counter negative: ${counter.tick}" }
        val elapsed = counter.tick * FishConstants.TICK_DURATION
        val intoCycle = elapsed % CYCLE_SECONDS
        val shown = when {
            !Floor7Settings.goldorUseDeathTicks -> elapsed
            Floor7Settings.goldorTicksCountUp -> intoCycle
            else -> CYCLE_SECONDS - intoCycle
        }
        // colour follows what is shown when counting down, else the cycle position
        val colorBase = if (Floor7Settings.goldorUseDeathTicks && !Floor7Settings.goldorTicksCountUp) shown else intoCycle
        FishDiag.guard("GoldorTicks.2", "goldor tick hud threw") { FishDraw.drawTimer(component, context, shown, cycleColor(colorBase)) }
    }
}
