package fishmod.features.dungeon

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import java.util.function.Consumer

/** Minimal one-field title-entry screen, opened when sneak-right-clicking to place a waypoint. */
class DungeonWaypointTitleScreen(private val onSubmit: Consumer<String?>?) : Screen(Text.literal("Dungeon Waypoint Title")) {

    private lateinit var field: TextFieldWidget

    override fun init() {
        val w = 220
        val h = 20
        val x = (this.width - w) / 2
        val y = this.height / 2 - 10

        field = TextFieldWidget(this.textRenderer, x, y, w, h, Text.literal("Title"))
        field.setMaxLength(64)
        addDrawableChild(field)
        setInitialFocus(field)

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Add Waypoint")) { submit() }
                .dimensions(x, y + 26, w, 20).build()
        )
    }

    private fun submit() {
        val text = field.text
        close()
        onSubmit?.accept(if (text == null || text.isBlank()) null else text)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            submit()
            return true
        }
        return super.keyPressed(input)
    }

    override fun render(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(ctx, mouseX, mouseY, delta)
        ctx.drawText(
            this.textRenderer, Text.literal("Waypoint title (Enter to confirm):"),
            (this.width - 220) / 2, this.height / 2 - 24, 0xFFFFFFFF.toInt(), true
        )
    }

    override fun shouldPause(): Boolean {
        return false
    }
}
