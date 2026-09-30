package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.config.values.Floor7
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import fishmod.utils.debug.FishDiag

object SectionCompletion {

    private var prevTime = 0L

    @JvmStatic
    fun init() {
        Events.ON_SECTION_CHANGE.register {
            val now = System.currentTimeMillis()
            prevTime = now
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        return System.currentTimeMillis() - prevTime < 1000 && Floor7.sectionCompletionNotification
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("SectionCompletion.2", "section completed render threw") { RenderUtils.drawCenteredText(context, component, Component.literal("Section completed!").withStyle(ChatFormatting.GREEN)) }
    }
}
