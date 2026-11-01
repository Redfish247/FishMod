package fishmod.features.other

import fishmod.utils.Misc
import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.Buttons
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.function.Supplier

class InventoryButton(
    private val index: Int,
    private val defX: Int,
    private val defY: Int,
    private val command: Supplier<String?>,
    private val name: Supplier<String?>,
    private val icon: Supplier<String?>,
) {
    var x = defX
    var y = defY

    init {
        BUTTONS.add(this)
    }

    private var iconKey: String? = null
    private var iconStack: ItemStack = ItemStack.EMPTY

    private fun stack(): ItemStack {
        val id = icon.get()?.trim().orEmpty()
        if (id != iconKey) {
            iconKey = id
            iconStack = runCatching {
                if (id.isEmpty()) ItemStack.EMPTY
                else BuiltInRegistries.ITEM.getOptional(Identifier.parse(id.lowercase())).orElse(null)
                    ?.takeIf { it != Items.AIR }?.let { ItemStack(it) } ?: ItemStack.EMPTY
            }.getOrDefault(ItemStack.EMPTY)
        }
        return iconStack
    }

    private fun active() = !command.get().isNullOrBlank()

    fun width(): Int {
        val label = name.get()?.trim().orEmpty()
        return if (label.isEmpty()) SIZE else SIZE + Minecraft.getInstance().font.width(label) + 4
    }

    fun render(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!active() && !editMode) return
        val w = width()
        val hover = inBounds(mouseX.toDouble(), mouseY.toDouble())
        context.blitSprite(RenderPipelines.GUI_TEXTURED, if (hover) BUTTON_HOVER else BUTTON_TEXTURE, x, y, w, SIZE)
        val font = Minecraft.getInstance().font
        val st = stack()
        if (!st.isEmpty) context.item(st, x + 1, y + 1)
        else {
            val num = "" + (index + 1)
            context.text(font, num, x + (SIZE - font.width(num)) / 2 + 1, y + (SIZE - font.lineHeight) / 2 + 1, 0xffffffff.toInt(), true)
        }
        val label = name.get()?.trim().orEmpty()
        if (label.isNotEmpty()) context.text(font, label, x + SIZE, y + (SIZE - font.lineHeight) / 2 + 1, 0xffffffff.toInt(), true)
        if (editMode && !active()) context.fill(x, y, x + w, y + SIZE, 0x80000000.toInt())
    }

    fun onClick() {
        try {
            val str = command.get()
            if (str.isNullOrBlank()) return
            Misc.executeCommand(str)
        } catch (e: Exception) {
            FishDiag.fail("InventoryButton.1", "inventory button ${index + 1} click failed", e)
        }
    }

    fun inBounds(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x && mouseX < x + width() && mouseY >= y && mouseY < y + SIZE

    companion object {
        private val BUTTON_TEXTURE: Identifier = Identifier.withDefaultNamespace("widget/button")
        private val BUTTON_HOVER: Identifier = Identifier.withDefaultNamespace("widget/button_highlighted")
        private const val SIZE = 18
        private const val GRID = 6
        private val BUTTONS = ArrayList<InventoryButton>()

        @JvmField var editMode = false
        private var dragging: InventoryButton? = null
        private var dragDX = 0.0
        private var dragDY = 0.0
        private var loadedLayout: String? = null

        // edit toggle sits just above the inventory's top-right corner
        private const val EDIT_X = 176 - 12
        private const val EDIT_Y = -13

        private fun syncLayout() {
            if (Buttons.layout == loadedLayout) return
            loadedLayout = Buttons.layout
            val parts = Buttons.layout.split(';')
            for (b in BUTTONS) {
                val xy = parts.getOrNull(b.index)?.split(',')
                val px = xy?.getOrNull(0)?.trim()?.toIntOrNull()
                val py = xy?.getOrNull(1)?.trim()?.toIntOrNull()
                b.x = px ?: b.defX
                b.y = py ?: b.defY
            }
        }

        private fun saveLayout() {
            Buttons.layout = BUTTONS.joinToString(";") { "${it.x},${it.y}" }
            loadedLayout = Buttons.layout
            runCatching { FishConfig.manager.save() }.onFailure { FishDiag.fail("InventoryButton.3", "saving button layout failed", it) }
        }

        private fun onEditToggle(mouseX: Double, mouseY: Double) =
            mouseX >= EDIT_X && mouseX < EDIT_X + 12 && mouseY >= EDIT_Y && mouseY < EDIT_Y + 12

        @JvmStatic
        fun parseClicks(mouseX: Double, mouseY: Double): Boolean {
            syncLayout()
            if (onEditToggle(mouseX, mouseY)) {
                editMode = !editMode
                dragging = null
                return true
            }
            for (button in BUTTONS.asReversed()) {
                if (!button.inBounds(mouseX, mouseY)) continue
                if (editMode) {
                    dragging = button
                    dragDX = mouseX - button.x
                    dragDY = mouseY - button.y
                    return true
                }
                if (!button.active()) continue
                button.onClick()
                return true
            }
            return false
        }

        @JvmStatic
        fun drag(mouseX: Double, mouseY: Double): Boolean {
            val b = dragging ?: return false
            b.x = Math.floorDiv((mouseX - dragDX).toInt() + GRID / 2, GRID) * GRID
            b.y = Math.floorDiv((mouseY - dragDY).toInt() + GRID / 2, GRID) * GRID
            return true
        }

        @JvmStatic
        fun release(): Boolean {
            if (dragging == null) return false
            dragging = null
            saveLayout()
            return true
        }

        @JvmStatic
        fun onClose() {
            if (dragging != null) release()
            editMode = false
        }

        @JvmStatic
        fun resetLayout() {
            for (b in BUTTONS) { b.x = b.defX; b.y = b.defY }
            Buttons.layout = ""
            loadedLayout = ""
            runCatching { FishConfig.manager.save() }
        }

        @JvmStatic
        fun renderAll(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            syncLayout()
            val font = Minecraft.getInstance().font
            if (editMode) {
                for (gx in -120..296 step GRID) context.fill(gx, -60, gx + 1, 226, 0x18FFFFFF)
                for (gy in -60..226 step GRID) context.fill(-120, gy, 296, gy + 1, 0x18FFFFFF)
                context.text(font, "Drag buttons, click ✎ when done", 0, -24, 0xffffff55.toInt(), true)
            }
            val hoverEdit = onEditToggle(mouseX.toDouble(), mouseY.toDouble())
            context.fill(EDIT_X, EDIT_Y, EDIT_X + 12, EDIT_Y + 12, if (editMode || hoverEdit) 0xC0406080.toInt() else 0x80000000.toInt())
            context.text(font, "✎", EDIT_X + 3, EDIT_Y + 2, 0xffffffff.toInt(), true)
            for (button in BUTTONS) {
                try {
                    button.render(context, mouseX, mouseY)
                } catch (e: Exception) {
                    FishDiag.fail("InventoryButton.2", "inventory button ${button.index + 1} render failed", e)
                }
            }
        }
    }
}
