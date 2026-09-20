package fishmod.features.item

import fishmod.features.HasNvgOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.rendering.NvgContext
import fishmod.utils.rendering.NvgGlStateGuard
import fishmod.utils.rendering.NvgRecorder
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.SignBlockEntity
import org.lwjgl.glfw.GLFW

/**
 * Stands in for Hypixel's sign-edit GUI on the auction price line: a single field prefilled by
 * [AuctionPriceAutofill], plus Confirm. Closing the screen (Confirm, Enter, or Escape) always
 * sends the sign update — mirrors vanilla sign-edit behaviour of committing on close.
 */
class AuctionPriceScreen(
    private val sign: SignBlockEntity,
    private val originalLines: Array<String>,
    private val item: ItemStack,
    private val suggested: Long
) : Screen(Component.literal("Auction Price")), HasNvgOverlay {

    private companion object {
        private const val SCRIM = 0xB3000000.toInt()
        private const val BG = 0xF20E1016.toInt()
        private const val BORDER = 0xFF2A2D38.toInt()
    }

    private lateinit var priceField: EditBox
    private var panelX = 0
    private var panelY = 0
    private val panelW = 200
    private val panelH = 108
    private var confirmX = 0
    private var confirmY = 0
    private var confirmW = 0
    private var confirmH = 0

    override fun isPauseScreen(): Boolean = false
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    private fun vw(): Int = (this.width / UiScale.factor()).toInt()
    private fun vh(): Int = (this.height / UiScale.factor()).toInt()

    override fun init() {
        panelX = (vw() - panelW) / 2
        panelY = (vh() - panelH) / 2

        val fieldW = panelW - 24
        val fieldX = panelX + 12
        val fieldY = panelY + 46
        priceField = EditBox(this.font, fieldX, fieldY, fieldW, 18, Component.literal("Price"))
        priceField.setMaxLength(12)
        var filtering = false
        priceField.setResponder { s ->
            if (filtering || s.isEmpty() || s.matches(Regex("\\d{1,12}"))) return@setResponder
            filtering = true
            priceField.setValue(s.replace(Regex("[^\\d]"), ""))
            filtering = false
        }
        priceField.value = if (suggested > 0) suggested.toString() else ""
        priceField.moveCursorToEnd(false)
        priceField.isFocused = true

        confirmW = fieldW
        confirmH = 20
        confirmX = fieldX
        confirmY = fieldY + 26
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (minecraft?.player == null) return
        val scale = UiScale.factor()
        ctx.pose().pushMatrix()
        ctx.pose().scale(scale, scale)
        val itemX = panelX + panelW / 2 - 8
        val itemY = panelY + 6
        ctx.item(item, itemX, itemY)
        ctx.itemDecorations(font, item, itemX, itemY)
        ctx.pose().popMatrix()
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mouseX = UiScale.vx(mouseX)
        val mouseY = UiScale.vx(mouseY)
        NvgRecorder.clear()
        ScreenTheme.nRect(0, 0, vw(), vh(), SCRIM)
        ScreenTheme.nPanel(panelX, panelY, panelX + panelW, panelY + panelH, 8, BG, BORDER)

        val cx = panelX + panelW / 2
        centered(item.hoverName.string, cx, panelY + 30)
        val hint = if (suggested > 0) "auto: -${FishSettingsPercent()}% of value" else "no known value — enter a price"
        centered(hint, cx, panelY + 40, ScreenTheme.SUBTEXT_COLOR, 0.55f)

        ScreenTheme.nTextField(priceField, true, priceField.x, priceField.y, priceField.width, priceField.height)

        val hov = inside(mouseX, mouseY, confirmX, confirmY, confirmW, confirmH)
        ScreenTheme.nRoundedRect(confirmX, confirmY, confirmW, confirmH, 5, if (hov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT)
        centered("Confirm", cx, confirmY + (confirmH - 8) / 2, 0xFF06302F.toInt(), 0.8f)

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun FishSettingsPercent(): Int = fishmod.utils.config.values.FishSettings.auctionAutofillPercent

    private fun centered(s: String, cx: Int, y: Int, color: Int = ScreenTheme.TEXT_COLOR, scale: Float = ScreenTheme.TEXT_SCALE) {
        ScreenTheme.nst(s, cx - ScreenTheme.nstw(s, scale) / 2, y, color, scale)
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
        mx >= x && mx <= x + w && my >= y && my <= y + h

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        if (inside(mx, my, confirmX, confirmY, confirmW, confirmH)) { onClose(); return true }
        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val key = input.key()
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        priceField.keyPressed(input)
        return true
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        priceField.charTyped(input)
        return true
    }

    override fun onClose() {
        val value = priceField.value.trim()
        Minecraft.getInstance().connection?.send(
            ServerboundSignUpdatePacket(sign.blockPos, true, value, originalLines[1], originalLines[2], originalLines[3])
        )
        Minecraft.getInstance().setScreen(null)
    }

    private val nvgGlState = NvgGlStateGuard()
    private var nvgFailureLogged = false

    override fun paintNvgOverlay() {
        nvgGlState.capture()
        try {
            val ctx = NvgContext.get()
            val pixelRatio = Minecraft.getInstance().window.guiScale.toFloat()
            org.lwjgl.nanovg.NanoVG.nvgBeginFrame(ctx, this.width.toFloat(), this.height.toFloat(), pixelRatio)
            NvgRecorder.replay(UiScale.factor())
            org.lwjgl.nanovg.NanoVG.nvgEndFrame(ctx)
        } catch (t: Throwable) {
            if (!nvgFailureLogged) {
                nvgFailureLogged = true
                fishmod.utils.debug.Debug.LOGGER.error("[NanoVG] AuctionPriceScreen paintNvgOverlay failed", t)
            }
        } finally {
            nvgGlState.restore()
        }
    }
}
