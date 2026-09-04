package fishmod.features

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * Ragnarock Axe state alerts.
 *
 * A successful cast is confirmed by Hypixel's sound packet: the wolf-howl cue at the one magic
 * pitch `1.4920635` (1.8 "mob.wolf.howl") while a Ragnarock Axe is in hand. The wolf-howl SoundEvent
 * constant was dropped in modern mappings and the id Hypixel's 1.8→modern translation lands on is
 * unreliable, so we match on the pitch + held item + skyblock, which is already a unique-enough
 * signature. Cancellation is caught from the chat line.
 */
object Ragnarock {

    private const val CAST_PITCH = 1.4920635f
    private const val BUFF_TICKS = 200 // the strength buff lasts 10s
    private val CANCELLED: Pattern =
        Pattern.compile("Ragnarock was cancelled due to (?:being hit|taking damage)!")

    private const val NAME = "Rag Timer"

    @Volatile private var ticksLeft = 0

    private fun holdingAxe(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return ItemUtil.getId(p.mainHandItem) == "RAGNAROCK_AXE"
    }

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.ragnarockTimerHudX }, { v -> FishSettings.ragnarockTimerHudX = v },
            { FishSettings.ragnarockTimerHudY }, { v -> FishSettings.ragnarockTimerHudY = v },
            54, 12,
            { FishSettings.ragnarockTimerScale }, { v -> FishSettings.ragnarockTimerScale = v },
            { FishSettings.ragnarockEnabled && FishSettings.ragnarockTimer },
        )

        Events.ON_SOUND.register { event, _, pitch ->
            // require an actual wolf sound — pitch + held-axe alone mis-fired on unrelated sounds near the cooldown, announcing phantom casts
            if (FishSettings.ragnarockEnabled
                && pitch == CAST_PITCH && "wolf" in event.location.path && Location.inSkyblock() && holdingAxe()
            ) {
                if (FishSettings.ragnarockCastAlert) {
                    Misc.forceTitle(Component.literal("§aCasted Ragnarock"), Component.empty())
                    if (FishSettings.ragnarockAnnounceParty) fishmod.utils.ChatQueue.enqueue("pc Casted Ragnarock")
                }
                if (FishSettings.ragnarockTimer) ticksLeft = BUFF_TICKS
            }
            false
        }

        Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.ragnarockEnabled && CANCELLED.matcher(text.string).find()) {
                if (FishSettings.ragnarockCancelAlert) Misc.forceTitle(Component.literal("§cRagnarock Cancelled"), Component.empty())
                ticksLeft = 0
            }
            false
        }

        Events.ON_SERVER_TICK.register { if (ticksLeft > 0) ticksLeft--; false }
        Events.ON_WORLD_CHANGE.register { ticksLeft = 0; false }
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.ragnarockEnabled || !FishSettings.ragnarockTimer || ticksLeft <= 0) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val secs = ticksLeft / 20.0
        val color = if (secs > 5) "§a" else if (secs > 2) "§e" else "§c"
        val txt = "§5Rag: $color${"%.1f".format(secs)}s"
        val sc = FishSettings.ragnarockTimerScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.ragnarockTimerHudX.toFloat(), FishSettings.ragnarockTimerHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, txt, 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
