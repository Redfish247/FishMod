package fishmod.features.achievements

import fishmod.features.FishHudEditor
import fishmod.utils.Scheduler
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
import fishmod.utils.sound.SoundManager
import net.minecraft.client.Minecraft
import fishmod.features.achievements.AchSettings as S

// Custom unlock banner drawn with the UI renderer, plus the layered unlock sounds
object AchPopup {

    val STYLES = arrayOf("Fanfare", "Chime", "Level Up", "Vanilla")

    private class Card(val title: String, val desc: String, val pts: Int, val section: String)

    private const val W = 240
    private const val H = 54
    private const val IN_MS = 250L
    private const val HOLD_MS = 4000L
    private const val OUT_MS = 400L
    private const val GOLD = 0xFFFFC83D.toInt()
    private const val TEAL = 0xFF24B6B0.toInt()
    private const val BG = 0xFF12151B.toInt()
    private const val TEXT = 0xFFEDF1F5.toInt()
    private const val SUB = 0xFF8A96A3.toInt()

    private val queue = ArrayDeque<Card>()
    private var current: Card? = null
    private var startMs = 0L

    @JvmStatic
    fun init() {
        fishmod.features.FishHudEditor.register("Achievement Popup", { posX() }, { S.achPopupX = it }, { S.achPopupY }, { S.achPopupY = it }, W, H,
            { S.achPopupScale }, { S.achPopupScale = it })
    }

    private fun posX(): Int {
        if (S.achPopupX >= 0) return S.achPopupX
        val gw = Minecraft.getInstance().window.guiScaledWidth
        return ((gw - W * S.achPopupScale) / 2).toInt()
    }

    fun show(title: String, desc: String, pts: Int, section: String) {
        queue.addLast(Card(title, desc, pts, section))
    }

    fun test() {
        show("Fish King", "Sample unlock - nothing is saved", 25, "Fishing")
        playSound()
    }

    @JvmStatic
    fun paintUiOverlay() {
        FishDiag.guard("AchPopup.1", "achievement popup paint") {
            if (FishHudEditor.isOpen()) return@guard
            val now = System.currentTimeMillis()
            var c = current
            if (c != null && now - startMs >= IN_MS + HOLD_MS + OUT_MS) { current = null; c = null }
            if (c == null) {
                c = queue.removeFirstOrNull() ?: return@guard
                current = c; startMs = now
            }
            val t = now - startMs
            val p: Float
            val slide: Float
            when {
                t < IN_MS -> { val k = t / IN_MS.toFloat(); val e = 1f - (1f - k) * (1f - k) * (1f - k); p = e; slide = (1f - e) * -26f }
                t < IN_MS + HOLD_MS -> { p = 1f; slide = 0f }
                else -> { val k = (t - IN_MS - HOLD_MS) / OUT_MS.toFloat(); p = 1f - k; slide = -k * 10f }
            }
            val sc = S.achPopupScale.toFloat().coerceIn(0.5f, 3f)
            val win = Minecraft.getInstance().window
            val x = posX() / sc
            val y = S.achPopupY / sc + slide
            UiRecorder.clear()
            draw(c, x, y, p)
            UiRenderer.paint(win.guiScaledWidth, win.guiScaledHeight, sc)
            UiRecorder.clear()
        }
    }

    private fun a(color: Int, p: Float): Int {
        val al = ((color ushr 24) * p).toInt().coerceIn(0, 255)
        return (al shl 24) or (color and 0x00FFFFFF)
    }

    private fun fit(s: String, size: Float, max: Float, bold: Boolean = false): String {
        fun w(t: String) = if (bold) UiRecorder.textWidthBold(t, size) else UiRecorder.textWidth(t, size)
        if (w(s) <= max) return s
        var t = s
        while (t.length > 1 && w("$t...") > max) t = t.dropLast(1)
        return "$t..."
    }

    private fun draw(c: Card, x: Float, y: Float, p: Float) {
        UiRecorder.dropShadow(x, y, W.toFloat(), H.toFloat(), 10f, 18f, a(0x55FFC83D, p))
        UiRecorder.roundedRectRing(x, y, W.toFloat(), H.toFloat(), 10f, 1.2f, a(BG, p), a(GOLD, p * 0.8f))
        UiRecorder.fillRect(x + 12f, y + 1f, W - 24f, 2f, a(TEAL, p))

        val cx = x + 29f
        val cy = y + H / 2f + 1f
        UiRecorder.disc(cx, cy, 19f, a(0x30FFC83D, p))
        UiRecorder.disc(cx, cy, 16f, a(0xFF1C2028.toInt(), p))
        trophy(cx, cy, p)

        val tx = x + 56f
        val right = x + W - 10f
        UiRecorder.textBold("ACHIEVEMENT UNLOCKED", tx, y + 9f, 5.5f, a(TEAL, p))
        val pts = "+${c.pts} AP"
        UiRecorder.textBold(pts, right - UiRecorder.textWidthBold(pts, 7.5f), y + 8f, 7.5f, a(GOLD, p))
        UiRecorder.textBold(fit(c.title, 10f, right - tx, true), tx, y + 19f, 10f, a(TEXT, p))
        UiRecorder.text(fit(c.desc, 6.5f, right - tx - 4f), tx, y + 33f, 6.5f, a(SUB, p))
        if (c.section.isNotEmpty()) {
            val s = c.section.uppercase()
            UiRecorder.textBold(s, tx, y + 43f, 5.2f, a(0xFF5F6B78.toInt(), p))
        }
    }

    private fun trophy(cx: Float, cy: Float, p: Float) {
        val g = a(GOLD, p)
        UiRecorder.roundedRectRing(cx - 11f, cy - 8f, 6f, 7f, 3f, 1.3f, 0, g)
        UiRecorder.roundedRectRing(cx + 5f, cy - 8f, 6f, 7f, 3f, 1.3f, 0, g)
        UiRecorder.fillRoundedRectCorners(cx - 6.5f, cy - 10f, 13f, 12f, 1f, 1f, 6f, 6f, g)
        UiRecorder.fillRect(cx - 1.5f, cy + 2f, 3f, 5f, g)
        UiRecorder.fillRoundedRect(cx - 5.5f, cy + 7f, 11f, 3f, 1.5f, g)
        UiRecorder.fillRoundedRect(cx - 4f, cy - 8f, 2f, 7f, 1f, a(0x80FFFFFF.toInt(), p))
    }

    private fun note(id: String, vol: Float, pitch: Float, delay: Int) {
        val go = Runnable { SoundManager.play(SoundManager.preset(id), vol, pitch) }
        if (delay <= 0) go.run() else Scheduler.scheduleTask(go, delay)
    }

    @JvmStatic
    fun playSound() {
        if (!S.achSound) return
        val v = S.achSoundVolume / 100f
        val pt = S.achSoundPitch.toFloat()
        when (S.achSoundStyle) {
            "Chime" -> {
                note("block.note_block.bell", v, 1.0f * pt, 0)
                note("block.note_block.bell", v, 1.26f * pt, 2)
                note("block.note_block.bell", v, 1.5f * pt, 4)
                note("block.amethyst_block.chime", v, 1.4f * pt, 6)
            }
            "Level Up" -> {
                note("entity.player.levelup", v, pt, 0)
                note("entity.experience_orb.pickup", v, 1.3f * pt, 8)
            }
            "Vanilla" -> note(S.achSoundName, v, pt, 0)
            else -> {
                note("ui.toast.challenge_complete", v, pt, 0)
                note("block.note_block.pling", v * 0.7f, 1.0f * pt, 2)
                note("block.note_block.pling", v * 0.7f, 1.26f * pt, 4)
                note("block.note_block.pling", v * 0.7f, 1.5f * pt, 6)
                note("block.note_block.pling", v * 0.8f, 2.0f * pt, 8)
                note("block.amethyst_block.chime", v, 1.2f * pt, 10)
                note("entity.experience_orb.pickup", v * 0.8f, 1.5f * pt, 13)
            }
        }
    }
}
