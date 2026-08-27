package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.regex.Pattern

/**
 * Leap Counter: tallies how many Spirit Leaps you've taken this run. Increments on the same
 * "You have teleported to X!" line [LeapAnnounce] keys off; resets on run end / world change.
 */
object LeapCounter {

    private const val NAME = "Leap Counter"
    private val TARGET: Pattern = Pattern.compile("You have teleported to (.+?)!")

    @Volatile private var count = 0

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.leapCounterHudX }, { v -> FishSettings.leapCounterHudX = v },
            { FishSettings.leapCounterHudY }, { v -> FishSettings.leapCounterHudY = v },
            70, 12,
            { FishSettings.leapCounterScale }, { v -> FishSettings.leapCounterScale = v }
        )

        Events.ON_LEAP.register { message ->
            if (FishSettings.leapCounterEnabled && TARGET.matcher(message.string.replace(Regex("§."), "")).find()) count++
            false
        }
        Events.ON_RUN_END.register { count = 0; false }
        Events.ON_WORLD_CHANGE.register { count = 0; false }
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.leapCounterEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val sc = FishSettings.leapCounterScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.leapCounterHudX.toFloat(), FishSettings.leapCounterHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, "§bLeaps: §f$count", 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
