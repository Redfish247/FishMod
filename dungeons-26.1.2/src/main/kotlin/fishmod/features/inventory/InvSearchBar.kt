package fishmod.features.inventory

import fishmod.features.general.*

import fishmod.utils.debug.FishDiag
import com.mojang.blaze3d.platform.Window
import fishmod.mixin.accessors.KeyBindingAccessor
import fishmod.utils.ExprParser
import fishmod.features.ScreenTheme
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemTools
import fishmod.utils.rendering.DrawHooks
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import fishmod.compat.GLFW
import fishmod.compat.curScreen

object InvSearchBar {

    private const val SEARCH_WIDTH = 150
    private const val SEARCH_HEIGHT = 20
    private var searchBar: EditBox? = null
    private var shouldDisplayVal = false
    private var searchTerm = ""
    private var parsedValue = Double.NaN

    private fun on() = FishSettings.inventorySearchEnabled && !fishmod.compat.BladeCompat.active && !storageOpen()

    private fun storageOpen(): Boolean {
        val screen = Minecraft.getInstance().curScreen as? net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*> ?: return false
        return StorageOverlay.isActive(screen)
    }

    private fun defX(): Int = (Minecraft.getInstance().window.guiScaledWidth - SEARCH_WIDTH) / 2
    private fun defY(): Int = Minecraft.getInstance().window.guiScaledHeight - 62
    private fun posX(): Int = if (FishSettings.inventorySearchX < 0) defX() else FishSettings.inventorySearchX
    private fun posY(): Int = if (FishSettings.inventorySearchY < 0) defY() else FishSettings.inventorySearchY

    @JvmStatic
    fun term(): String = if (on() && shouldDisplay()) searchTerm else ""

    @JvmStatic
    fun init() {
        fishmod.features.FishHudEditor.register(
            "Inventory Search",
            { posX() }, { v -> FishSettings.inventorySearchX = v },
            { posY() }, { v -> FishSettings.inventorySearchY = v },
            SEARCH_WIDTH, SEARCH_HEIGHT,
            { on() },
        )
        DrawHooks.INVENTORY_SLOT_AFTER.register { context, item, x, y ->
            if (!on() || !shouldDisplay() || searchTerm.isEmpty() || !parsedValue.isNaN()) return@register
            val matched = try {
                matches(item)
            } catch (e: Exception) {
                FishDiag.fail("InvSearchBar.3", "search match failed for term '$searchTerm'", e)
                true
            }
            if (!matched) {
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

    private val matchCache = java.util.WeakHashMap<ItemStack, Boolean>()
    private var matchCacheTerm = ""

    private fun matches(item: ItemStack): Boolean {
        if (item.isEmpty) return false
        if (matchCacheTerm != searchTerm) {
            matchCacheTerm = searchTerm
            matchCache.clear()
        }
        return matchCache.getOrPut(item) {
            val name = item.hoverName.string
            name != "Air" && (name.contains(searchTerm, ignoreCase = true) || ItemTools.containsIgnoreCaseLore(item, searchTerm))
        }
    }

    @JvmStatic
    fun render(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        if (!on() || !exists() || !shouldDisplay()) return
        val bar = searchBar!!
        val ox = posX(); val oy = posY()
        bar.x = ox + 20; bar.y = oy; bar.width = SEARCH_WIDTH - 26
        drawFrame(context, ox, oy, bar)
        val textRenderer: Font = Minecraft.getInstance().font ?: return
        val endX = try { drawValue(context, textRenderer, bar) } catch (e: Exception) {
            FishDiag.fail("InvSearchBar.4", "search bar render failed", e)
            return
        }

        if (parsedValue.isFinite()) {
            val expression = "  §e= §2" + FishDraw.formatNumber(parsedValue.toFloat())
            context.text(textRenderer, expression, endX + 2, bar.y + (bar.height - 8) / 2, ScreenTheme.TEXT_COLOR, false)
        }
    }

    private fun drawValue(ctx: GuiGraphicsExtractor, font: Font, bar: EditBox): Int {
        val v = bar.value
        val avail = bar.width - 4
        val cur = bar.cursorPosition.coerceIn(0, v.length)
        var start = 0
        while (start < cur && font.width(v.substring(start, cur)) > avail) start++
        val shown = font.plainSubstrByWidth(v.substring(start), avail)
        val tx = bar.x + 2
        val ty = bar.y + (bar.height - 8) / 2
        ctx.text(font, shown, tx, ty, ScreenTheme.TEXT_COLOR, false)
        if (bar.isFocused && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val cx = tx + font.width(v.substring(start, cur))
            ctx.fill(cx, ty - 1, cx + 1, ty + 9, ScreenTheme.TEXT_COLOR)
        }
        return tx + font.width(shown)
    }

    private fun drawFrame(ctx: GuiGraphicsExtractor, x: Int, y: Int, bar: EditBox) {
        val fill = (0xCC shl 24) or (ScreenTheme.surface(0x0C) and 0xFFFFFF)
        val ring = if (bar.isFocused) ScreenTheme.over((0x90 shl 24) or (ScreenTheme.ACCENT and 0xFFFFFF), ScreenTheme.surface(0x2E)) else ScreenTheme.surface(0x2E)
        ScreenTheme.roundedRectRing(ctx, x, y, SEARCH_WIDTH, SEARCH_HEIGHT, 5, 1, fill, ring)
        val ic = ScreenTheme.SUBTEXT_COLOR
        val ix = x + 7; val iy = y + (SEARCH_HEIGHT - 9) / 2
        ctx.fill(ix + 1, iy, ix + 5, iy + 1, ic); ctx.fill(ix + 1, iy + 5, ix + 5, iy + 6, ic)
        ctx.fill(ix, iy + 1, ix + 1, iy + 5, ic); ctx.fill(ix + 5, iy + 1, ix + 6, iy + 5, ic)
        ctx.fill(ix + 5, iy + 6, ix + 6, iy + 7, ic); ctx.fill(ix + 6, iy + 7, ix + 7, iy + 8, ic); ctx.fill(ix + 7, iy + 8, ix + 8, iy + 9, ic)
        if (bar.value.isEmpty() && !bar.isFocused) {
            val font = Minecraft.getInstance().font
            ctx.text(font, "Search...", ix + 13, y + (SEARCH_HEIGHT - 8) / 2, ic, false)
        }
    }

    @JvmStatic
    fun keyPressed(input: KeyEvent): Boolean {
        if (!on() || !exists()) return false
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
            } catch (e: Exception) {
                FishDiag.fail("InvSearchBar.1", "failed to read drop key binding", e)
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER) {
                if (parsedValue.isFinite()) {
                    bar.value = FishDraw.formatNumber(parsedValue.toFloat())
                }
            } else if (input.key() != GLFW.GLFW_KEY_ESCAPE) {
                bar.keyPressed(input)
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun charTyped(input: CharacterEvent) {
        if (!on() || !exists() || !searchBar!!.isFocused || !shouldDisplay()) return
        searchBar!!.charTyped(input)
    }

    @JvmStatic
    fun onMouseClick(click: MouseButtonEvent) {
        if (!on() || !exists() || !shouldDisplay()) return
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
            textRenderer, posX() + 20, posY(), SEARCH_WIDTH - 26, SEARCH_HEIGHT, Component.literal("")
        )
        bar.setBordered(false)
        bar.setResponder { string ->
            searchTerm = string.lowercase()
            parsedValue = try {
                ExprParser.parseExpression(searchTerm)
            } catch (e: Exception) {
                FishDiag.fail("InvSearchBar.2", "ExprParser threw on '$searchTerm'", e)
                Double.NaN
            }
        }
        searchBar = bar
        return true
    }

    private fun inBounds(x: Double, y: Double): Boolean {
        val sx = posX()
        val sy = posY()
        val sw = SEARCH_WIDTH
        val sh = SEARCH_HEIGHT

        return x >= sx && x <= sx + sw && y >= sy && y <= sy + sh
    }
}
