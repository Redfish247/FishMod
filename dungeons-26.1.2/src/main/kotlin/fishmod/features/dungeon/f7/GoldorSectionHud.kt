package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import fishmod.utils.debug.FishDiag

object GoldorSectionHud {

    private var section = 1

    @JvmStatic
    fun init() {
        FishEvents.ON_SECTION_CHANGE.register {
            section++
            FishDiag.check(section <= 5, "CurrentSection.1") { "section counter went past 5: $section" }
            false
        }
        FishEvents.ON_LOCATION_CHANGE.register {
            section = 1
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7Settings.showCurrentSection && BossPhase.inTerminals()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("CurrentSection.2", "current section render threw") { FishDraw.drawPrefixedText(component, context, "Section", " $section") }
    }
}
