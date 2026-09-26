package fishmod.features.item

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.rendering.UiRecorder
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
import java.math.BigDecimal
import java.math.RoundingMode

class AuctionPriceScreen(
    private val sign: SignBlockEntity,
    private val originalLines: Array<String>,
    private val item: ItemStack,
    private val suggested: Long
) : Screen(Component.literal("Auction Price")), HasUiOverlay {

    private companion object {
        private const val SCRIM = 0xB3000000.toInt()
        private const val BG = 0xFF12151B.toInt()
        private const val BORDER = 0xFF2A2D38.toInt()
        private const val CARD = 0xFF181C23.toInt()
        private const val CARD_HOVER = 0xFF1E232C.toInt()
        private const val GOLD = 0xFFF2C14E.toInt()
        private const val GOLD_SOFT = 0x24F2C14E
        private const val GOOD = 0xFF5BD68A.toInt()
        private const val ON_ACCENT = 0xFF06302F.toInt()
        private const val ALLOWED = "0123456789,. kKmMbB"
        private val MAX_PRICE = BigDecimal("999999999999999")
    }

    private lateinit var priceField: EditBox
    private var panelX = 0
    private var panelY = 0
    private val panelW = 224
    private val panelH = 184
    private val pad = 12

    private var sugX = 0
    private var sugY = 0
    private var sugW = 0
    private var sugH = 0
    private var cancelX = 0
    private var confirmX = 0
    private var btnY = 0
    private val cancelW = 60
    private val confirmW = 72
    private val btnH = 20

    override fun isPauseScreen(): Boolean = false
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    private fun vw(): Int = (this.width / UiScale.factor()).toInt()
    private fun vh(): Int = (this.height / UiScale.factor()).toInt()

    override fun init() {
        panelX = (vw() - panelW) / 2
        panelY = (vh() - panelH) / 2

        priceField = EditBox(this.font, panelX + pad, panelY + 62, panelW - pad * 2, 18, Component.literal("Price"))
        priceField.setMaxLength(20)
        var filtering = false
        var lastValid = ""
        priceField.setResponder { s ->
            if (filtering) return@setResponder
            filtering = true
            if (s.all { it in ALLOWED }) lastValid = s else priceField.setValue(lastValid)
            filtering = false
        }
        priceField.value = if (suggested > 0) formatWithCommas(suggested) else ""
        lastValid = priceField.value
        priceField.moveCursorToEnd(false)
        priceField.isFocused = true

        sugX = panelX + pad
        sugY = panelY + 112
        sugH = 28
        sugW = if (suggested > 0) (panelW - pad * 2 - 6) / 2 else panelW - pad * 2
        btnY = panelY + panelH - pad - btnH
        confirmX = panelX + panelW - pad - confirmW
        cancelX = confirmX - 6 - cancelW
    }

    // Panel + icon box drawn vanilla so the item stack sits on top of them (the overlay paints last).
    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (minecraft?.player == null) return
        val scale = UiScale.factor()
        ctx.pose().pushMatrix()
        ctx.pose().scale(scale, scale)
        ctx.fill(0, 0, vw(), vh(), SCRIM)
        ScreenTheme.panel(ctx, panelX, panelY, panelX + panelW, panelY + panelH, 10, BG, BORDER)
        ScreenTheme.roundedRect(ctx, panelX + pad, panelY + 12, 24, 24, 6, GOLD_SOFT)
        val itemX = panelX + pad + 4
        val itemY = panelY + 16
        ctx.item(item, itemX, itemY)
        ctx.itemDecorations(font, item, itemX, itemY)
        ctx.pose().popMatrix()
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mouseX = UiScale.vx(mouseX)
        val mouseY = UiScale.vx(mouseY)
        UiRecorder.clear()
        val x = panelX
        val y = panelY
        val innerW = panelW - pad * 2

        UiRecorder.fillRect((x + 10).toFloat(), y.toFloat(), (panelW - 20).toFloat(), 2f, GOLD)
        val tx = x + pad + 32
        UiRecorder.textBold("Auction Price", tx.toFloat(), (y + 13).toFloat(), 9f, ScreenTheme.TEXT_COLOR)
        val sub = fit(item.hoverName.string, x + panelW - pad - tx - UiRecorder.textWidth(" · BIN", 6.5f).toInt(), 6.5f) + " · BIN"
        UiRecorder.text(sub, tx.toFloat(), (y + 26).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        ScreenTheme.nRect(x + pad, y + 44, innerW, 1, BORDER)

        UiRecorder.text("Price", (x + pad).toFloat(), (y + 51).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        ScreenTheme.nTextField(priceField, true, priceField.x, priceField.y, priceField.width, priceField.height)
        if (priceField.value.isEmpty()) {
            UiRecorder.text("e.g. 42.5m, 800k, 1.2b", (priceField.x + 3).toFloat(), (priceField.y + 5.5f), 7f, 0xFF5A6470.toInt())
        }

        val parsed = parsePrice(priceField.value)
        val big = parsed?.let { formatWithCommas(it) } ?: "—"
        val bigSize = 18f
        val bw = UiRecorder.textWidth(big, bigSize)
        UiRecorder.textBold(big, x + panelW / 2f - bw / 2f, (y + 86).toFloat(), bigSize, if (parsed != null) GOLD else ScreenTheme.SUBTEXT_COLOR)

        if (suggested > 0) {
            val sHov = inside(mouseX, mouseY, sugX, sugY, sugW, sugH)
            card(sugX, sugY, sugW, sugH, sHov)
            cardText("Suggested (-${FishSettingsPercent()}%)", fmtShort(suggested), sugX, sugY, sugW, GOLD)
            val youX = sugX + sugW + 6
            card(youX, sugY, sugW, sugH, false)
            val youColor = when {
                parsed == null -> ScreenTheme.SUBTEXT_COLOR
                parsed <= suggested -> GOOD
                else -> ScreenTheme.DANGER
            }
            cardText("You", parsed?.let { fmtShort(it) } ?: "—", youX, sugY, sugW, youColor)
        } else {
            card(sugX, sugY, sugW, sugH, false)
            val msg = "No known value — enter a price"
            val mw = UiRecorder.textWidth(msg, 6.5f)
            UiRecorder.text(msg, sugX + sugW / 2f - mw / 2f, sugY + (sugH - 6.5f) / 2f, 6.5f, ScreenTheme.SUBTEXT_COLOR)
        }

        ScreenTheme.nRect(x + pad, btnY - 8, innerW, 1, BORDER)

        val cHov = inside(mouseX, mouseY, cancelX, btnY, cancelW, btnH)
        ScreenTheme.nRoundedRectRing(cancelX, btnY, cancelW, btnH, btnH / 2, 1, if (cHov) CARD_HOVER else CARD, BORDER)
        centered("Cancel", cancelX + cancelW / 2, btnY, btnH, if (cHov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR)

        val enabled = parsed != null
        val okHov = enabled && inside(mouseX, mouseY, confirmX, btnY, confirmW, btnH)
        val okColor = when {
            !enabled -> 0x6624B6B0
            okHov -> ScreenTheme.ACCENT_HOVER
            else -> ScreenTheme.ACCENT
        }
        ScreenTheme.nRoundedRect(confirmX, btnY, confirmW, btnH, btnH / 2, okColor)
        centered("Confirm", confirmX + confirmW / 2, btnY, btnH, if (enabled) ON_ACCENT else 0x9906302F.toInt())

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun card(x: Int, y: Int, w: Int, h: Int, hov: Boolean) {
        ScreenTheme.nRoundedRectRing(x, y, w, h, 6, 1, if (hov) CARD_HOVER else CARD, BORDER)
    }

    private fun cardText(label: String, value: String, x: Int, y: Int, w: Int, valueColor: Int) {
        val lw = UiRecorder.textWidth(label, 6f)
        UiRecorder.text(label, x + w / 2f - lw / 2f, (y + 5).toFloat(), 6f, ScreenTheme.SUBTEXT_COLOR)
        val valW = UiRecorder.textWidth(value, 7.5f)
        UiRecorder.textBold(value, x + w / 2f - valW / 2f, (y + 15).toFloat(), 7.5f, valueColor)
    }

    private fun centered(s: String, cx: Int, y: Int, h: Int, color: Int) {
        val size = 7.5f
        UiRecorder.text(s, cx - UiRecorder.textWidth(s, size) / 2f, y + (h - size) / 2f, size, color)
    }

    private fun fit(s: String, maxW: Int, size: Float): String {
        if (UiRecorder.textWidth(s, size) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && UiRecorder.textWidth("$t...", size) > maxW) t = t.dropLast(1)
        return "$t..."
    }

    private fun FishSettingsPercent(): Int = fishmod.utils.config.values.FishSettings.auctionAutofillPercent

    // Accepts 42.5m / 800k / 1.2b / 12,500,000; null when unparseable.
    private fun parsePrice(s: String): Long? {
        val t = s.replace(",", "").replace(" ", "").lowercase()
        if (t.isEmpty()) return null
        val mult = when (t.last()) {
            'k' -> 1_000L
            'm' -> 1_000_000L
            'b' -> 1_000_000_000L
            else -> 1L
        }
        val num = if (mult != 1L) t.dropLast(1) else t
        if (!num.matches(Regex("\\d+(\\.\\d+)?|\\.\\d+"))) return null
        val value = BigDecimal(num).multiply(BigDecimal.valueOf(mult)).setScale(0, RoundingMode.DOWN)
        if (value.signum() <= 0 || value > MAX_PRICE) return null
        return value.toLong()
    }

    private fun formatWithCommas(v: Long): String = String.format(java.util.Locale.US, "%,d", v)

    private fun fmtShort(v: Long): String {
        fun trim(d: Double, unit: String): String {
            val s = String.format(java.util.Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')
            return s + unit
        }
        return when {
            v >= 1_000_000_000L -> trim(v / 1e9, "B")
            v >= 1_000_000L -> trim(v / 1e6, "M")
            v >= 1_000L -> trim(v / 1e3, "K")
            else -> v.toString()
        }
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
        mx >= x && mx <= x + w && my >= y && my <= y + h

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        if (inside(mx, my, confirmX, btnY, confirmW, btnH)) {
            if (parsePrice(priceField.value) != null) onClose()
            return true
        }
        if (inside(mx, my, cancelX, btnY, cancelW, btnH)) { cancel(); return true }
        if (suggested > 0 && inside(mx, my, sugX, sugY, sugW, sugH)) {
            priceField.value = formatWithCommas(suggested)
            priceField.moveCursorToEnd(false)
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val key = input.key()
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (parsePrice(priceField.value) != null || priceField.value.isBlank()) onClose()
            return true
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancel()
            return true
        }
        priceField.keyPressed(input)
        return true
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        priceField.charTyped(input)
        return true
    }

    // Writes the parsed price to line 1 of the sign (blank if unparseable), like before.
    override fun onClose() {
        val value = parsePrice(priceField.value)?.toString() ?: ""
        Minecraft.getInstance().connection?.send(
            ServerboundSignUpdatePacket(sign.blockPos, true, value, originalLines[1], originalLines[2], originalLines[3])
        )
        Minecraft.getInstance().setScreen(null)
    }

    // Sends the sign back untouched, same as closing the vanilla sign editor without typing.
    private fun cancel() {
        Minecraft.getInstance().connection?.send(
            ServerboundSignUpdatePacket(sign.blockPos, true, originalLines[0], originalLines[1], originalLines[2], originalLines[3])
        )
        Minecraft.getInstance().setScreen(null)
    }

    override fun paintUiOverlay() {
        fishmod.utils.rendering.UiRenderer.paint(this.width, this.height, fishmod.utils.rendering.UiScale.factor())
    }
}
