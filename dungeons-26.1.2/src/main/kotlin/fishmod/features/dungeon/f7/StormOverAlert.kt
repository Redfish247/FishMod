package fishmod.features.dungeon.f7
import fishmod.compat.hudHidden

import fishmod.features.FishHudEditor
import fishmod.features.HudExample
import fishmod.features.HudPreview
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import fishmod.utils.debug.FishDiag

object StormOverAlert {

    private const val NAME = "Storm Over Alert"
    private const val COUNTDOWN_TICKS = 5 * 20
    private const val HUD_W = 120
    private const val HUD_H = 14
    private val SERVER_COUNTDOWN = Regex("^[1-5]$")

    private var shownAt = 0L
    private var ticksLeft = -1

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.stormOverHudX }, { v -> FishSettings.stormOverHudX = v },
            { FishSettings.stormOverHudY }, { v -> FishSettings.stormOverHudY = v },
            HUD_W, HUD_H,
            { FishSettings.stormOverScale }, { v -> FishSettings.stormOverScale = v }
        )
        HudPreview.register(NAME) {
            val font = Minecraft.getInstance().font
            HudExample(HUD_W, HUD_H) { ctx -> drawAlert(ctx, font, FishSettings.stormOverText.ifBlank { "Storm Over!" }) }
        }
        FishEvents.worldChange.register { shownAt = 0L; ticksLeft = -1; false }
    }

    @JvmStatic
    fun onTick(tick: Int, overTick: Int) {
        val left = overTick - tick
        ticksLeft = if (left in 1..COUNTDOWN_TICKS) left else -1
        if (left == 0) trigger()
    }

    @JvmStatic
    fun trigger() {
        ticksLeft = -1
        if (!FishSettings.stormOverEnabled) return
        shownAt = System.currentTimeMillis()
        if (FishSettings.stormOverSound)
            FishDiag.guard("StormOverAlert.1", "storm over sound failed") { Minecraft.getInstance().player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f) }
    }

    @JvmStatic
    fun shouldHideServerCountdown(text: Component): Boolean {
        if (!FishSettings.stormOverEnabled || !FishSettings.stormOverHideServerCountdown) return false
        if (!SkyblockLocation.inDungeon() || !BossPhase.inP2() || BossPhase.stormDead()) return false
        return SERVER_COUNTDOWN.matches(text.string.replace(fishmod.utils.FishConstants.STRIP_COLOR_REGEX, "").trim())
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.stormOverEnabled) return
        try { renderHudInner(ctx) } catch (e: Exception) { FishDiag.fail("StormOverAlert.3", "storm over HUD render threw", e) }
    }

    private fun renderHudInner(ctx: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return
        val text = when {
            FishSettings.stormOverCountdown && ticksLeft > 0 && BossPhase.inP2() && !BossPhase.stormDead() ->
                "%.2f".format(ticksLeft / 20.0)
            shownAt != 0L && System.currentTimeMillis() - shownAt <= FishSettings.stormOverDurationMs ->
                FishSettings.stormOverText.ifBlank { "Storm Over!" }
            else -> return
        }
        val sc = FishSettings.stormOverScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.stormOverHudX.toFloat(), FishSettings.stormOverHudY.toFloat())
        ctx.pose().scale(sc, sc)
        drawAlert(ctx, mc.font, text)
        ctx.pose().popMatrix()
    }

    private fun drawAlert(ctx: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, text: String) =
        ctx.text(font, text, (HUD_W - font.width(text)) / 2, (HUD_H - font.lineHeight) / 2 + 1,
            FishSettings.stormOverColor or 0xFF000000.toInt(), true)
}
