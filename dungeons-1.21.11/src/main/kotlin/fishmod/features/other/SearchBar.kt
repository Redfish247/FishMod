package fishmod.features.other

import fishmod.mixin.accessors.KeyBindingAccessor
import fishmod.utils.MathParser
import fishmod.utils.config.values.ExtraOptions
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.DrawEvents
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.client.util.Window
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

object SearchBar {

    private const val SEARCH_Y = 20
    private const val SEARCH_WIDTH = 150
    private const val SEARCH_HEIGHT = 20
    private var searchBar: TextFieldWidget? = null
    private var shouldDisplayVal = false
    private var searchTerm = ""
    private var parsedValue = Double.NaN

    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_AFTER.register { context, item, x, y ->
            if (shouldDisplay() && searchTerm.isNotEmpty() && ExtraOptions.toggleableSearchBar && parsedValue.isNaN()) {
                if (!matches(item)) {
                    context.fill(x, y, x + 16, y + 16, 0xaa111111.toInt())
                }
            }
        }
    }

    private fun matches(item: ItemStack): Boolean {
        val name = item.name.string.lowercase()
        if (name == "air") return false

        return name.contains(searchTerm) || ItemUtil.containsIgnoreCaseLore(item, searchTerm)
    }

    @JvmStatic
    fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        if (!exists() || !shouldDisplay() || !ExtraOptions.toggleableSearchBar) return
        val bar = searchBar!!
        bar.render(context, mouseX, mouseY, deltaTicks)

        if (!parsedValue.isNaN()) {
            val expression = "  §e= §2" + RenderUtils.formatNumber(parsedValue.toFloat())
            val textRenderer: TextRenderer = MinecraftClient.getInstance().textRenderer ?: return

            val textX = bar.x + textRenderer.getWidth(searchTerm) + 4
            val textY = bar.y + (bar.height - 8) / 2
            context.drawText(textRenderer, expression, textX, textY, 0xffffffff.toInt(), true)
        }
    }

    @JvmStatic
    fun keyPressed(input: KeyInput): Boolean {
        if (!exists() || !ExtraOptions.toggleableSearchBar) return false
        val bar = searchBar!!

        val ctrlIsPressed = (input.modifiers() and GLFW.GLFW_MOD_CONTROL) != 0

        if (ctrlIsPressed && input.key() == GLFW.GLFW_KEY_F) {
            shouldDisplayVal = !shouldDisplayVal
            return true
        } else if (shouldDisplay() && bar.isFocused) {
            // Never eat the player's drop key — pressing it should drop the item, not type into search.
            // Unfocus the search so the keystroke falls through to vanilla's drop handling.
            try {
                val mc = MinecraftClient.getInstance()
                val dropCode = (mc.options.dropKey as KeyBindingAccessor).boundKey.code
                if (input.key() == dropCode) { bar.isFocused = false; return false }
            } catch (ignored: Exception) {
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER) {
                if (!parsedValue.isNaN()) {
                    bar.text = RenderUtils.formatNumber(parsedValue.toFloat())
                }
            } else if (input.key() != GLFW.GLFW_KEY_ESCAPE) {
                bar.keyPressed(input)
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun CharTyped(input: CharInput) {
        if (!exists() || !searchBar!!.isFocused || !shouldDisplay() || !ExtraOptions.toggleableSearchBar) return
        searchBar!!.charTyped(input)
    }

    @JvmStatic
    fun onMouseClick(click: Click) {
        if (!exists() || !shouldDisplay() || !ExtraOptions.toggleableSearchBar) return
        searchBar!!.isFocused = inBounds(click.x(), click.y())
    }

    @JvmStatic
    fun shouldDisplay(): Boolean = shouldDisplayVal

    private fun exists(): Boolean {
        if (searchBar != null) return true

        val mc = MinecraftClient.getInstance()
        val textRenderer: TextRenderer? = mc.textRenderer
        val window: Window? = mc.window

        if (window == null || textRenderer == null) return false

        val bar = TextFieldWidget(
            textRenderer, (window.scaledWidth - SEARCH_WIDTH) / 2, SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT, Text.literal("")
        )
        bar.setChangedListener { string ->
            searchTerm = string.lowercase()
            parsedValue = MathParser.parseExpression(searchTerm)
        }
        searchBar = bar
        return true
    }

    private fun inBounds(x: Double, y: Double): Boolean {
        val bar = searchBar!!
        val sx = bar.x
        val sy = bar.y
        val sw = bar.width
        val sh = bar.height

        return x >= sx && x <= sx + sw && y >= sy && y <= sy + sh
    }
}
