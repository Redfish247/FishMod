package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import fishmod.utils.debug.FishDiag

object SectionDoneHud {

    private var prevTime = 0L

    @JvmStatic
    fun init() {
        FishEvents.ON_SECTION_CHANGE.register {
            val now = System.currentTimeMillis()
            prevTime = now
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        return System.currentTimeMillis() - prevTime < 1000 && Floor7Settings.sectionDoneAlert
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("SectionCompletion.2", "section completed render threw") { FishDraw.drawCenteredText(context, component, Component.literal("Section completed!").withStyle(ChatFormatting.GREEN)) }
    }
}
