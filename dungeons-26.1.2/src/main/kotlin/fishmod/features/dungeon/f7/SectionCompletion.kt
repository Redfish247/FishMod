package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.Misc
import fishmod.utils.config.values.Floor7
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** "Section completed!" notification + sound on section change. */
object SectionCompletion {

    private var prevTime = 0L

    @JvmStatic
    fun init() {
        Events.ON_SECTION_CHANGE.register {
            Misc.sendSound(Floor7.sectionChangeSound)
            prevTime = System.currentTimeMillis()
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        return System.currentTimeMillis() - prevTime < 1000 && Floor7.sectionCompletionNotification
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawCenteredText(context, component, Component.literal("Section completed!").withStyle(ChatFormatting.GREEN))
    }
}
