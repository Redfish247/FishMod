package fishmod.features.dungeon

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.function.Consumer

/**
 * Minimal one-field title-entry screen, opened when sneak-right-clicking to place a waypoint.
 */
class DungeonWaypointTitleScreen(private val onSubmit: Consumer<String?>?) : Screen(Component.literal("Dungeon Waypoint Title")) {

    private lateinit var field: EditBox

    override fun init() {
        val w = 220
        val h = 20
        val x = (this.width - w) / 2
        val y = this.height / 2 - 10

        field = EditBox(this.font, x, y, w, h, Component.literal("Title"))
        field.setMaxLength(64)
        addRenderableWidget(field)
        setInitialFocus(field)

        addRenderableWidget(
            Button.builder(Component.literal("Add Waypoint")) { submit() }
                .bounds(x, y + 26, w, 20).build()
        )
    }

    private fun submit() {
        val text = field.value
        onClose()
        onSubmit?.accept(if (text == null || text.isBlank()) null else text)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            submit()
            return true
        }
        return super.keyPressed(input)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(ctx, mouseX, mouseY, delta)
        ctx.text(
            this.font, Component.literal("Waypoint title (Enter to confirm):"),
            (this.width - 220) / 2, this.height / 2 - 24, 0xFFFFFFFF.toInt(), true
        )
    }

    override fun isPauseScreen(): Boolean {
        return false
    }
}
