package fishmod.features.other

import fishmod.utils.Misc
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import java.util.function.Supplier

/** Clickable command buttons drawn over the survival inventory's empty space — a 1:1 port of blade-addons' InventoryButton. */
class InventoryButton(private val x: Int, private val y: Int, private val command: Supplier<String?>) {

    private val index: Int

    init {
        BUTTONS.add(this)
        index = BUTTONS.size
    }

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val str = command.get()
        if (str.isNullOrEmpty()) return
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, x, y, SIZE, SIZE)
        val textRenderer: TextRenderer = MinecraftClient.getInstance().textRenderer
        val label = "" + index
        val center = textRenderer.getWidth(label)
        context.drawText(
            textRenderer, label,
            x + (SIZE - center) / 2 + 1, y + (SIZE - textRenderer.fontHeight) / 2 + 1, 0xffffffff.toInt(), true
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
        private val BUTTON_TEXTURE: Identifier = Identifier.ofVanilla("widget/button")
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
        fun renderAll(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            for (button in BUTTONS) {
                button.render(context, mouseX, mouseY, deltaTicks)
            }
        }
    }
}
