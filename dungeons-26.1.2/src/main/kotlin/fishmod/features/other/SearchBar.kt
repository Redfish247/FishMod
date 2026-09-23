package fishmod.features.other

import com.mojang.blaze3d.platform.Window
import fishmod.mixin.accessors.KeyBindingAccessor
import fishmod.utils.MathParser
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.DrawEvents
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

object SearchBar {

    private const val SEARCH_Y = 20
    private const val SEARCH_WIDTH = 150
    private const val SEARCH_HEIGHT = 20
    private var searchBar: EditBox? = null
    private var shouldDisplayVal = false
    private var searchTerm = ""
    private var parsedValue = Double.NaN

    @JvmStatic
    fun init() {
        DrawEvents.INVENTORY_SLOT_AFTER.register { context, item, x, y ->
            if (!FishSettings.inventorySearchEnabled || !shouldDisplay() || searchTerm.isEmpty() || !parsedValue.isNaN()) return@register
            if (!matches(item)) {
                context.fill(x, y, x + 16, y + 16, 0xaa111111.toInt())
            } else if (FishSettings.inventorySearchHighlight) {
                val c = FishSettings.inventorySearchHighlightColor
                context.fill(x, y, x + 16, y + 1, c)
                context.fill(x, y + 15, x + 16, y + 16, c)
                context.fill(x, y + 1, x + 1, y + 15, c)
                context.fill(x + 15, y + 1, x + 16, y + 15, c)
            }
        }
    }

    private fun matches(item: ItemStack): Boolean {
        val name = item.hoverName.string.lowercase()
        if (item.isEmpty || name == "air") return false

        return name.contains(searchTerm) || ItemUtil.containsIgnoreCaseLore(item, searchTerm)
    }

    @JvmStatic
    fun render(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        if (!FishSettings.inventorySearchEnabled || !exists() || !shouldDisplay()) return
        val bar = searchBar!!
        // re-centre every frame so a window resize doesn't strand the bar
        bar.x = (Minecraft.getInstance().window.guiScaledWidth - SEARCH_WIDTH) / 2
        bar.extractRenderState(context, mouseX, mouseY, deltaTicks)

        if (!parsedValue.isNaN()) {
            val expression = "  §e= §2" + RenderUtils.formatNumber(parsedValue.toFloat())
            val textRenderer: Font = Minecraft.getInstance().font ?: return

            val textX = bar.x + textRenderer.width(searchTerm) + 4
            val textY = bar.y + (bar.height - 8) / 2
            context.text(textRenderer, expression, textX, textY, 0xffffffff.toInt(), true)
        }
    }

    @JvmStatic
    fun keyPressed(input: KeyEvent): Boolean {
        if (!FishSettings.inventorySearchEnabled || !exists()) return false
        val bar = searchBar!!

        val ctrlIsPressed = (input.modifiers() and GLFW.GLFW_MOD_CONTROL) != 0

        if (ctrlIsPressed && input.key() == GLFW.GLFW_KEY_F) {
            if (FishSettings.inventorySearchAlwaysShow) {
                bar.isFocused = !bar.isFocused
            } else {
                shouldDisplayVal = !shouldDisplayVal
                bar.isFocused = shouldDisplayVal
            }
            return true
        } else if (shouldDisplay() && bar.isFocused) {
            try {
                val mc = Minecraft.getInstance()
                val dropCode = (mc.options.keyDrop as KeyBindingAccessor).boundKey.value
                if (input.key() == dropCode) { bar.isFocused = false; return false }
            } catch (ignored: Exception) {
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER) {
                if (!parsedValue.isNaN()) {
                    bar.value = RenderUtils.formatNumber(parsedValue.toFloat())
                }
            } else if (input.key() != GLFW.GLFW_KEY_ESCAPE) {
                bar.keyPressed(input)
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun CharTyped(input: CharacterEvent) {
        if (!FishSettings.inventorySearchEnabled || !exists() || !searchBar!!.isFocused || !shouldDisplay()) return
        searchBar!!.charTyped(input)
    }

    @JvmStatic
    fun onMouseClick(click: MouseButtonEvent) {
        if (!FishSettings.inventorySearchEnabled || !exists() || !shouldDisplay()) return
        searchBar!!.isFocused = inBounds(click.x(), click.y())
    }

    @JvmStatic
    fun shouldDisplay(): Boolean = shouldDisplayVal || FishSettings.inventorySearchAlwaysShow

    private fun exists(): Boolean {
        if (searchBar != null) return true

        val mc = Minecraft.getInstance()
        val textRenderer: Font? = mc.font
        val window: Window? = mc.window

        if (window == null || textRenderer == null) return false

        val bar = EditBox(
            textRenderer, (window.guiScaledWidth - SEARCH_WIDTH) / 2, SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT, Component.literal("")
        )
        bar.setResponder { string ->
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
