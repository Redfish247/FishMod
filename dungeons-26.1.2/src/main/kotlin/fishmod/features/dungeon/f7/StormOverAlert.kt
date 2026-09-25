package fishmod.features.dungeon.f7

import fishmod.features.FishHudEditor
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.sounds.SoundEvents

// Movable title once Storm has used both of his lightning procs.
object StormOverAlert {

    private const val NAME = "Storm Over Alert"
    private val PROC_LINES = setOf(
        "[BOSS] Storm: ENERGY HEED MY CALL!",
        "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!",
    )
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    private var procs = 0
    private var shownAt = 0L

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.stormOverHudX }, { v -> FishSettings.stormOverHudX = v },
            { FishSettings.stormOverHudY }, { v -> FishSettings.stormOverHudY = v },
            120, 14,
            { FishSettings.stormOverScale }, { v -> FishSettings.stormOverScale = v }
        )

        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.stormOverEnabled) return@register false
            val s = COLOR.replace(text.string, "").trim()
            if (s in PROC_LINES && ++procs == 2) {
                shownAt = System.currentTimeMillis()
                if (FishSettings.stormOverSound)
                    Minecraft.getInstance().player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f)
            } else if (s == "[BOSS] Storm: I should have known that I stood no chance.") {
                procs = 0
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { procs = 0; shownAt = 0L; false }
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.stormOverEnabled || shownAt == 0L) return
        if (System.currentTimeMillis() - shownAt > FishSettings.stormOverDurationMs) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val text = FishSettings.stormOverText.ifBlank { "Storm Over!" }
        val sc = FishSettings.stormOverScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.stormOverHudX.toFloat(), FishSettings.stormOverHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, text, 0, 0, FishSettings.stormOverColor or 0xFF000000.toInt(), true)
        ctx.pose().popMatrix()
    }
}
