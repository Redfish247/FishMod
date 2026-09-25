package fishmod.features.dungeon.f7

import fishmod.features.FishHudEditor
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.sounds.SoundEvents

// Movable title at 28.5s on the Storm tick timer.
object StormOverAlert {

    private const val NAME = "Storm Over Alert"

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
        Events.ON_WORLD_CHANGE.register { shownAt = 0L; false }
    }

    @JvmStatic
    fun trigger() {
        if (!FishSettings.stormOverEnabled) return
        shownAt = System.currentTimeMillis()
        if (FishSettings.stormOverSound)
            Minecraft.getInstance().player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f)
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
