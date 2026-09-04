package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Displays the current terminal section number. */
object CurrentSection {

    private var section = 1

    @JvmStatic
    fun init() {
        Events.ON_SECTION_CHANGE.register {
            section++
            false
        }
        Events.ON_LOCATION_CHANGE.register {
            section = 1
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7.showCurrentSection && Phase.inTerminals()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawPrefixedText(component, context, "Section", " $section")
    }
}
