package fishmod.features.other

import fishmod.utils.Misc
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import java.util.function.Supplier

/**
 * Clickable command buttons drawn over the survival inventory's empty space, numbered by
 * registration order. Coordinates are relative to the inventory background's top-left;
 * `InventoryScreenMixin` translates the matrix/mouse offsets before calling [renderAll]/[parseClicks].
 */
class InventoryButton(private val x: Int, private val y: Int, private val command: Supplier<String?>) {

    private val index: Int

    init {
        BUTTONS.add(this)
        index = BUTTONS.size
    }

    fun render(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val str = command.get()
        if (str.isNullOrEmpty()) return
        context.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, x, y, SIZE, SIZE)
        val textRenderer: Font = Minecraft.getInstance().font
        val label = "" + index
        val center = textRenderer.width(label)
        context.text(
            textRenderer, label,
            x + (SIZE - center) / 2 + 1, y + (SIZE - textRenderer.lineHeight) / 2 + 1, 0xffffffff.toInt(), true
        )
    }

    fun onClick() {
        val str = command.get()
        if (str.isNullOrEmpty()) return
        Misc.executeCommand(str)
    }

    fun inBounds(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= x && mouseX <= x + SIZE && mouseY >= y && mouseY <= y + SIZE
    }

    companion object {
        private val BUTTON_TEXTURE: Identifier = Identifier.withDefaultNamespace("widget/button")
        private const val SIZE = 18
        private val BUTTONS = ArrayList<InventoryButton>()

        @JvmStatic
        fun parseClicks(mouseX: Double, mouseY: Double) {
            for (button in BUTTONS) {
                if (button.inBounds(mouseX, mouseY)) {
                    button.onClick()
                }
            }
        }

        @JvmStatic
        fun renderAll(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            for (button in BUTTONS) {
                button.render(context, mouseX, mouseY, deltaTicks)
            }
        }
    }
}
