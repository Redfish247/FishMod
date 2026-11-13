package fishmod.features.dungeon.f7

import fishmod.features.HudPreview
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor

object MaxorTicks {

    private val counter = TickTimer()
    private const val WHITE = 0xffffffff.toInt()

    private fun inMaxor() = SkyblockLocation.inDungeon() && BossPhase.inP1()

    @JvmStatic
    fun init() = counter.init(shouldCount = ::inMaxor)

    @JvmStatic
    fun display(): Boolean = Floor7Settings.enableTickTimers && Floor7Settings.maxorTicksHud && inMaxor()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) =
        FishDiag.guard("MaxorTicks.1", "maxor tick hud threw") { FishDraw.drawTimer(component, context, if (HudPreview.active) EXAMPLE_TICKS else counter.tick, WHITE) }

    private const val EXAMPLE_TICKS = 247
}
