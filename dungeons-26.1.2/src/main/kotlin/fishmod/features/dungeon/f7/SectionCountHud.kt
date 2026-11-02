package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.data.TextTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.GoldorSection
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

// Done/total for the current P3 section, optionally with the last thing that was completed.
object SectionCountHud {

    private const val DEFAULT_TOTAL = 7
    private val GATE_LINES = mapOf(
        "The gate has been destroyed!" to "Gate Destroyed",
        "The gate will open in 5 seconds!" to "Break Gate"
    )
    private val LAST_COLORS = mapOf(
        "Lever" to "§c", "Gate Destroyed" to "§c", "Device" to "§d", "Terminal" to "§b", "Break Gate" to "§5§l"
    )

    private var done = 0
    private var total = DEFAULT_TOTAL
    private var last = ""

    @JvmStatic
    fun init() {
        FishEvents.chat.register { msg ->
            if (Floor7Settings.sectionShowPrevious && BossPhase.inTerminals()) {
                GATE_LINES[msg.string]?.let { last = it }
            }
            false
        }
        FishEvents.terminalStep.register { _, _, kind, now, of ->
            FishDiag.check(of > 0 && now in 0..of, "SectionCountHud.1") { "bad terminal progress $now/$of ($kind)" }
            done = now; total = of; last = TextTools.capitaliseFirst(kind)
            FishDiag.check(last in LAST_COLORS, "SectionCountHud.2") { "unknown objective '$last'" }
            false
        }
        FishEvents.sectionChange.register {
            if (done == total) done = 0
            total = GoldorSection.totalFor(GoldorSection.getSection()).also { t ->
                FishDiag.check(t > 0, "SectionCountHud.3") { "no terminal total for section ${GoldorSection.getSection()}" }
            }
            last = ""
            false
        }
        FishEvents.locationChange.register { done = 0; total = DEFAULT_TOTAL; false }
    }

    private fun countColor(): String {
        val left = total - done
        return when {
            left <= 0 -> "§6§l"
            left == 1 || (done == 7 && total == 8) -> "§a"
            done >= 3 -> "§e"
            else -> "§c"
        }
    }

    private fun label(): Component {
        val count = if (Floor7Settings.sectionProgressColored) "§f(${countColor()}$done§f/§a$total§f)" else "§a(§c$done§a/$total)"
        val prefix = if (Floor7Settings.sectionShowPrevious) (LAST_COLORS[last] ?: "") + last + " " else ""
        return Component.literal(prefix + count)
    }

    @JvmStatic
    fun display(): Boolean = Floor7Settings.sectionProgressHud && BossPhase.inTerminals()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("SectionCountHud.4", "section count hud threw") { FishDraw.drawCenteredText(context, component, label()) }
    }
}
