package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

// One-second banner whenever a P3 section is cleared.
object SectionDoneHud {

    private const val SHOW_MS = 1000L
    private var clearedAt = 0L

    @JvmStatic
    fun init() = FishEvents.sectionChange.register { clearedAt = System.currentTimeMillis(); false }

    @JvmStatic
    fun display(): Boolean = Floor7Settings.sectionDoneAlert && System.currentTimeMillis() - clearedAt < SHOW_MS

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("SectionDoneHud.1", "section cleared banner threw") { FishDraw.drawCenteredText(context, component, Component.literal("§a§lSECTION CLEARED")) }
    }
}
