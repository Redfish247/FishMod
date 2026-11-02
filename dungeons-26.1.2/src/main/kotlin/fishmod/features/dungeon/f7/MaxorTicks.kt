package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import fishmod.utils.debug.FishDiag

object MaxorTicks {

    private val timer = TickTimer()

    @JvmStatic
    fun init() {
        timer.init(shouldCount = { SkyblockLocation.inDungeon() && BossPhase.inP1() })
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7Settings.enableTickTimers && Floor7Settings.enableMaxorTickTimer && SkyblockLocation.inDungeon() && BossPhase.inP1()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("MaxorTickTimer.1", "maxor tick timer render threw") { FishDraw.drawTimer(component, context, timer.tick, 0xffffffff.toInt()) }
    }
}
