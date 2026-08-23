package fishmod.features.dungeon.f7.s4

import config.practical.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.config.values.Floor7
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** Optional S4 state table: name, S4 contributions, and current status per tracked player. */
object S4DebugHud {

    @JvmStatic
    fun display(): Boolean = Floor7.s4DebugHudEnabled && S4Tracker.isActive()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val textRenderer = Minecraft.getInstance().font ?: return
        val x = component.scaledX
        val y = component.scaledY

        context.text(textRenderer, Component.literal("S4 Tracker").withColor(Constants.GOLD), x, y, -0x1, true)

        val players = S4Tracker.getPlayers().sortedBy { it.name }
        for ((i, state) in players.withIndex()) {
            val rowY = y + Constants.TEXT_HEIGHT * (i + 1)
            val text = Component.literal("${state.name}: ")
                .append(Component.literal(statusLabel(state.status)).withColor(statusColor(state.status)))
                .append(Component.literal(" (${state.contributionCount})"))
            context.text(textRenderer, text, x, rowY, -0x1, true)
        }
    }

    private fun statusLabel(status: S4Status): String = when (status) {
        S4Status.ACTIVE -> "..."
        S4Status.CONTRIBUTED -> "✓ done"
        S4Status.CORE_EARLY -> "⚠ early"
        S4Status.CORE_ON_TIME -> "✓ core"
        S4Status.CORE_LATE -> "⚠ late"
        S4Status.POSSIBLE_MISSED -> "? missed"
        S4Status.DEAD -> "☠ dead"
    }

    private fun statusColor(status: S4Status): Int = when (status) {
        S4Status.ACTIVE -> Constants.GRAY
        S4Status.CONTRIBUTED, S4Status.CORE_ON_TIME -> Constants.GREEN
        S4Status.CORE_EARLY, S4Status.CORE_LATE, S4Status.POSSIBLE_MISSED -> Constants.GOLD
        S4Status.DEAD -> Constants.RED
    }
}
