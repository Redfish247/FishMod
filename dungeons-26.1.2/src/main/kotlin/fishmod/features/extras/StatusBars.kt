package fishmod.features.extras

import fishmod.features.FishHudEditor
import fishmod.features.extras.ExtrasSettings as S
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

// Skyblocker-style defense bar + health/mana bars above the hotbar, fed from the action bar
object StatusBars {
    private val HEALTH = Regex("""(\d[\d,.  ]*)/(\d[\d,.  ]*)\s*❤""")
    private val MANA = Regex("""(\d[\d,.  ]*)/(\d[\d,.  ]*)\s*✎""")
    private val DEFENSE = Regex("""(\d[\d,.  ]*)\s*❈""")

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

    // Skyblocker-style: same parts it strips, colour codes allowed anywhere
    private val STRIP_HEALTH = Regex("""(?:§.)*[\d,]+/[\d,]+❤(?:\+(?:§.)*[\d,]+.)?""")
    private val STRIP_MANA = Regex("""(?:§.)*[\d,]+/[\d,]+✎(?: (?:§.)*Mana| (?:§.)*[\d,]+(?:§.)*ʬ)?""")
    private val STRIP_DEF = Regex("""(?:§.)*[\d,]+(?:§.)*❈(?: (?:§.)*Defense)?""")

    // Reads health/mana/defense from the action bar; returns it with those parts removed when vanilla is hidden
    @JvmStatic
    fun onActionBar(msg: Component): Component {
        if (!S.statusBars || !SkyblockLocation.inSkyblock()) return msg
        val raw = msg.string
        read(raw)
        if (!hideVanilla()) return msg
        var out = raw
        if (S.statusBarsHealth) out = STRIP_HEALTH.replace(out, "")
        if (S.statusBarsMana) out = STRIP_MANA.replace(out, "")
        if (S.statusBarsDefense) out = STRIP_DEF.replace(out, "")
        if (out == raw) return msg
        out = out.replace(Regex(" {3,}"), "     ").trim()
        return Component.literal(out.ifEmpty { " " })
    }

    private var loggedMiss = false

    private fun read(raw: String) {
        val s = fishmod.utils.FishConstants.STRIP_COLOR_REGEX.replace(raw, "")
        val h = HEALTH.find(s)
        h?.let { hp = num(it.groupValues[1]); hpMax = num(it.groupValues[2]) }
        MANA.find(s)?.let { mana = num(it.groupValues[1]); manaMax = num(it.groupValues[2]) }
        DEFENSE.find(s)?.let { def = num(it.groupValues[1]) }
        // log one unparsed bar so the real format can be fixed
        if (h == null && !loggedMiss && s.contains('/')) { loggedMiss = true; FishDiag.fail("StatusBars.2", "no health in action bar '${s.take(120)}'") }
    }

    // nbsp/thin-space separators too, not just commas
    private fun num(s: String) = s.filter { it.isDigit() }.toLongOrNull() ?: -1L

    @JvmStatic
    fun hideVanilla(): Boolean = S.statusBars && S.statusBarsHideVanilla && SkyblockLocation.inSkyblock()

    @JvmStatic
    fun hideVanillaXp(): Boolean = hideVanilla() && S.statusBarsXp

    private fun render(ctx: GuiGraphicsExtractor) {
        if (!S.statusBars || !SkyblockLocation.inSkyblock()) return
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        // no action bar read yet: fall back to vanilla health so the bars still show
        if (hpMax <= 0) { hp = p.health.toLong(); hpMax = p.maxHealth.toLong().coerceAtLeast(1) }
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
