package fishmod.features.extras

import fishmod.features.FishHudEditor
import fishmod.features.extras.ExtrasSettings as S
import fishmod.utils.Location
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

// Skyblocker-style defense bar + health/mana bars above the hotbar, fed from the action bar
object StatusBars {
    private val HEALTH = Regex("""([\d,]+)/([\d,]+)❤""")
    private val MANA = Regex("""([\d,]+)/([\d,]+)✎""")
    private val DEFENSE = Regex("""([\d,]+)❈""")

    private const val W = 182
    private const val H = 22
    private const val HALF = 90

    private var hp = -1L; private var hpMax = -1L
    private var mana = -1L; private var manaMax = -1L
    private var def = -1L

    // -1 = default spot right above the hotbar
    private fun x(): Int = if (S.statusBarX >= 0) S.statusBarX else Minecraft.getInstance().window.guiScaledWidth / 2 - W / 2
    private fun y(): Int = if (S.statusBarY >= 0) S.statusBarY else Minecraft.getInstance().window.guiScaledHeight - 24 - H

    fun init() {
        FishHudEditor.register("Status Bars", { x() }, { S.statusBarX = it }, { y() }, { S.statusBarY = it },
            W, H, { S.statusBarsScale }, { S.statusBarsScale = it })
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "status_bars")) { ctx, _ ->
            try { if (!FishHudEditor.isOpen()) render(ctx) } catch (t: Throwable) { FishDiag.fail("StatusBars.1", "status bars render failed", t) }
        }
    }

    @JvmStatic
    fun onActionBar(msg: Component) {
        if (!S.statusBars) return
        val s = fishmod.utils.Constants.STRIP_COLOR_REGEX.replace(msg.string, "")
        HEALTH.find(s)?.let { hp = num(it.groupValues[1]); hpMax = num(it.groupValues[2]) }
        MANA.find(s)?.let { mana = num(it.groupValues[1]); manaMax = num(it.groupValues[2]) }
        DEFENSE.find(s)?.let { def = num(it.groupValues[1]) }
    }

    private fun num(s: String) = s.replace(",", "").toLongOrNull() ?: -1L

    @JvmStatic
    fun hideVanilla(): Boolean = S.statusBars && S.statusBarsHideVanilla && Location.inSkyblock()

    @JvmStatic
    fun hideVanillaXp(): Boolean = hideVanilla() && S.statusBarsXp

    private fun render(ctx: GuiGraphicsExtractor) {
        if (!S.statusBars || !Location.inSkyblock()) return
        val mc = Minecraft.getInstance()
        mc.player ?: return
        val sc = S.statusBarsScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(x().toFloat(), y().toFloat())
            ctx.pose().scale(sc, sc)
            // row 1: defense, fill = damage reduction def/(def+100)
            if (S.statusBarsDefense && def >= 0) {
                icon(ctx, "❈", 0, 3, S.statusBarsDefenseColor)
                bar(ctx, 9, 5, W - 9, 5, def.toFloat() / (def + 100), 0f, S.statusBarsDefenseColor)
                label(ctx, "$def", 9 + (W - 9) / 2, 3, S.statusBarsDefenseColor)
            }
            // row 2: health left, mana right
            if (S.statusBarsHealth && hpMax > 0) {
                icon(ctx, "❤", 0, 14, S.statusBarsHealthColor)
                bar(ctx, 9, 13, HALF - 10, 8, hp.toFloat() / hpMax, if (hp > hpMax) (hp - hpMax).toFloat() / hpMax else 0f, S.statusBarsHealthColor)
                label(ctx, "$hp", 9 + (HALF - 10) / 2, 13, S.statusBarsHealthColor)
            }
            if (S.statusBarsMana && manaMax > 0) {
                icon(ctx, "✎", HALF + 2, 14, S.statusBarsManaColor)
                bar(ctx, HALF + 11, 13, W - HALF - 11, 8, mana.toFloat() / manaMax, 0f, S.statusBarsManaColor)
                label(ctx, "$mana", HALF + 11 + (W - HALF - 11) / 2, 13, S.statusBarsManaColor)
            }
        } finally {
            ctx.pose().popMatrix()
        }
    }

    private fun bar(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, frac: Float, over: Float, color: Int) {
        val bg = ((S.statusBarsBgOpacity * 255 / 100).coerceIn(0, 255) shl 24) or 0x101010
        ctx.fill(x, y, x + w, y + h, bg)
        val inner = w - 2
        val fill = (inner * frac.coerceIn(0f, 1f)).toInt()
        ctx.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, color)
        // top highlight for a bit of depth
        ctx.fill(x + 1, y + 1, x + 1 + fill, y + 2, 0x40FFFFFF)
        if (over > 0f) ctx.fill(x + 1, y + 1, x + 1 + (inner * over.coerceIn(0f, 1f)).toInt(), y + h - 1, S.statusBarsOverflowColor)
    }

    private fun icon(ctx: GuiGraphicsExtractor, s: String, x: Int, y: Int, color: Int) {
        ctx.text(Minecraft.getInstance().font, s, x, y, color or 0xFF000000.toInt(), true)
    }

    // Number in the bar colour (white Text Color = follow bar) with optional black outline
    private fun label(ctx: GuiGraphicsExtractor, s: String, cx: Int, y: Int, color: Int) {
        val font = Minecraft.getInstance().font
        val x = cx - font.width(s) / 2
        val c = if (S.statusBarsTextColor != 0xFFFFFFFF.toInt()) S.statusBarsTextColor else color or 0xFF000000.toInt()
        if (S.statusBarsTextShadow) for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) ctx.text(font, s, x + dx, y + dy, 0xFF000000.toInt(), false)
        ctx.text(font, s, x, y, c, false)
    }
}
