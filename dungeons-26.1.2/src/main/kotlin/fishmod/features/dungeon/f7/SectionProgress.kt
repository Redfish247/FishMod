package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.config.values.Floor7
import fishmod.utils.data.TextUtil
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** Terminal-section progress (completed/total), optional prev-objective label. */
object SectionProgress {

    private var completed = 0
    private var sectionTotal = 7
    private var prevObjective = ""
    private var completedFormat = ""
    private var objectiveFormat = ""

    @JvmStatic
    fun init() {
        updateObjectiveFormat()
        updateProgressFormat()

        Events.ON_GAME_MESSAGE.register { message ->
            if (!Phase.inTerminals() || !Floor7.sectionPrevObjective) return@register false
            val string = message.string
            if (string == "The gate has been destroyed!") {
                prevObjective = "Gate Destroyed"; updateObjectiveFormat()
            } else if (string == "The gate will open in 5 seconds!") {
                prevObjective = "Break Gate"; updateObjectiveFormat()
            }
            false
        }
        Events.ON_TERMINAL.register { formattedName, action, objective, current, total ->
            completed = current
            sectionTotal = total
            prevObjective = TextUtil.capitaliseFirst(objective)
            updateObjectiveFormat()
            updateProgressFormat()
            false
        }
        Events.ON_SECTION_CHANGE.register {
            if (completed == sectionTotal) completed = 0
            sectionTotal = getTotal()
            prevObjective = ""
            updateObjectiveFormat()
            updateProgressFormat()
            false
        }
        Events.ON_LOCATION_CHANGE.register { newLocation ->
            completed = 0
            sectionTotal = 7
            false
        }
    }

    private fun updateObjectiveFormat() {
        objectiveFormat = when (prevObjective) {
            "Lever", "Gate Destroyed" -> "§c"
            "Device" -> "§d"
            "Terminal" -> "§b"
            "Break Gate" -> "§5§l"
            else -> ""
        }
    }

    private fun updateProgressFormat() {
        completedFormat = if (completed >= sectionTotal) "§6§l"
        else if (sectionTotal - completed == 1 || (completed == 7 && sectionTotal == 8)) "§a"
        else if (completed >= 3) "§e"
        else "§c"
    }

    private fun getTotal(): Int = Section.totalFor(Section.getSection())

    private fun getProgressText(): Component {
        if (Floor7.sectionColorProgress) {
            return Component.literal("§f(" + completedFormat + completed + "§f/§a" + sectionTotal + "§f)")
        }
        return Component.literal("§a(§c" + completed + "§a/" + sectionTotal + ")")
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7.showSectionProgress && Phase.inTerminals()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        if (Floor7.sectionPrevObjective) {
            RenderUtils.drawCenteredText(
                context, component,
                Component.literal("$objectiveFormat$prevObjective ").append(getProgressText())
            )
        } else {
            RenderUtils.drawCenteredText(context, component, getProgressText())
        }
    }
}
