package fishmod.features.dungeon.f7

import fishmod.features.FishHudEditor
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

object StormOverAlert {

    private const val NAME = "Storm Over Alert"
    private const val COUNTDOWN_TICKS = 5 * 20
    private val SERVER_COUNTDOWN = Regex("^[1-5]$")

    private var shownAt = 0L
    // Ticks left until Storm is over; -1 when not counting.
    private var ticksLeft = -1

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.stormOverHudX }, { v -> FishSettings.stormOverHudX = v },
            { FishSettings.stormOverHudY }, { v -> FishSettings.stormOverHudY = v },
            120, 14,
            { FishSettings.stormOverScale }, { v -> FishSettings.stormOverScale = v }
        )
        Events.ON_WORLD_CHANGE.register { shownAt = 0L; ticksLeft = -1; false }
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
            Minecraft.getInstance().player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f)
    }

    @JvmStatic
    fun shouldHideServerCountdown(text: Component): Boolean {
        if (!FishSettings.stormOverEnabled || !FishSettings.stormOverHideServerCountdown) return false
        if (!Location.inDungeon() || !Phase.inP2() || Phase.stormDead()) return false
        return SERVER_COUNTDOWN.matches(text.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim())
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.stormOverEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val text = when {
            FishSettings.stormOverCountdown && ticksLeft > 0 && Phase.inP2() && !Phase.stormDead() ->
                "%.2f".format(ticksLeft / 20.0)
            shownAt != 0L && System.currentTimeMillis() - shownAt <= FishSettings.stormOverDurationMs ->
                FishSettings.stormOverText.ifBlank { "Storm Over!" }
            else -> return
        }
        val sc = FishSettings.stormOverScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.stormOverHudX.toFloat(), FishSettings.stormOverHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, text, 0, 0, FishSettings.stormOverColor or 0xFF000000.toInt(), true)
        ctx.pose().popMatrix()
    }
}
