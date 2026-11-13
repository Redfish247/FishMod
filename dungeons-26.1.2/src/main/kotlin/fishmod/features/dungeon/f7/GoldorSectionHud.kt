package fishmod.features.dungeon.f7

import fishmod.features.HudPreview
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor

object GoldorSectionHud {

    private const val SECTIONS = 4
    private var cleared = 0

    @JvmStatic
    fun init() {
        FishEvents.sectionChange.register {
            cleared++
            FishDiag.check(cleared < SECTIONS + 1, "GoldorSectionHud.1") { "section counter went past ${SECTIONS + 1}: ${cleared + 1}" }
            false
        }
        FishEvents.locationChange.register { cleared = 0; false }
    }

    @JvmStatic
    fun display(): Boolean = Floor7Settings.currentSectionHud && BossPhase.inTerminals()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("GoldorSectionHud.2", "goldor section hud threw") { FishDraw.drawPrefixedText(component, context, "P3", " ${(if (HudPreview.active) 2 else cleared + 1)}/$SECTIONS") }
    }
}
