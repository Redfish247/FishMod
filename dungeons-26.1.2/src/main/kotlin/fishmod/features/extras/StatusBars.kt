package fishmod.features.extras

import fishmod.features.FishHudEditor
import fishmod.features.extras.ExtrasSettings as S
import fishmod.utils.Location
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

// Skyblocker-style health / mana / defense / XP bars fed from the action bar
object StatusBars {
    private val HEALTH = Regex("""([\d,]+)/([\d,]+)❤""")
    private val MANA = Regex("""([\d,]+)/([\d,]+)✎""")
    private val DEFENSE = Regex("""([\d,]+)❈""")

    private const val W = 90
    private const val H = 9
    private const val GAP = 2

    private var hp = -1L; private var hpMax = -1L
    private var mana = -1L; private var manaMax = -1L
    private var def = -1L

    fun init() {
        FishHudEditor.register("Status Bars", { S.statusBarsX }, { S.statusBarsX = it }, { S.statusBarsY }, { S.statusBarsY = it },
            W, H * 4 + GAP * 3, { S.statusBarsScale }, { S.statusBarsScale = it })
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "status_bars")) { ctx, _ ->
            try { if (!FishHudEditor.isOpen()) render(ctx, false) } catch (t: Throwable) { FishDiag.fail("StatusBars.1", "status bars render failed", t) }
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

    private fun render(ctx: GuiGraphicsExtractor, preview: Boolean) {
        if (!S.statusBars) return
        if (!preview && !Location.inSkyblock()) return
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        val sc = S.statusBarsScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(S.statusBarsX.toFloat(), S.statusBarsY.toFloat())
            ctx.pose().scale(sc, sc)
            var y = 0
            if (S.statusBarsHealth && (hpMax > 0 || preview)) {
                val (c, m) = if (hpMax > 0) hp to hpMax else 80L to 100L
                bar(ctx, y, c.toFloat() / m, S.statusBarsHealthColor, if (c > m) (c - m).toFloat() / m else 0f, "$c/$m")
                y += H + GAP
            }
            if (S.statusBarsMana && (manaMax > 0 || preview)) {
                val (c, m) = if (manaMax > 0) mana to manaMax else 60L to 100L
                bar(ctx, y, c.toFloat() / m, S.statusBarsManaColor, 0f, "$c/$m")
                y += H + GAP
            }
            if (S.statusBarsDefense && (def >= 0 || preview)) {
                bar(ctx, y, 1f, S.statusBarsDefenseColor, 0f, "${if (def >= 0) def else 0}❈")
                y += H + GAP
            }
            if (S.statusBarsXp) bar(ctx, y, p.experienceProgress, S.statusBarsXpColor, 0f, "${p.experienceLevel}")
        } finally {
            ctx.pose().popMatrix()
        }
    }

    private fun bar(ctx: GuiGraphicsExtractor, y: Int, frac: Float, color: Int, over: Float, label: String) {
        val bg = (S.statusBarsBgOpacity * 255 / 100).coerceIn(0, 255) shl 24
        ctx.fill(0, y, W, y + H, bg)
        ctx.fill(1, y + 1, 1 + ((W - 2) * frac.coerceIn(0f, 1f)).toInt(), y + H - 1, color)
        if (over > 0f) ctx.fill(1, y + 1, 1 + ((W - 2) * over.coerceIn(0f, 1f)).toInt(), y + H - 1, S.statusBarsOverflowColor)
        val font = Minecraft.getInstance().font
        ctx.text(font, label, (W - font.width(label)) / 2, y + 1, S.statusBarsTextColor, S.statusBarsTextShadow)
    }
}
