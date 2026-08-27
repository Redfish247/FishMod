package fishmod.features

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * Ragnarock Axe state alerts, ported from Odin's `Ragnarock`.
 *
 * A successful cast is confirmed by Hypixel's sound packet: the wolf-howl cue at the one magic
 * pitch `1.4920635` (1.8 "mob.wolf.howl") while a Ragnarock Axe is in hand — Odin keys off the same
 * pitch. The wolf-howl SoundEvent constant was dropped in modern mappings and the id Hypixel's
 * 1.8→modern translation lands on is unreliable, so we match on the pitch + held item + skyblock,
 * which is already a unique-enough signature. Cancellation is caught from the chat line.
 */
object Ragnarock {

    private const val CAST_PITCH = 1.4920635f
    private val CANCELLED: Pattern =
        Pattern.compile("Ragnarock was cancelled due to (?:being hit|taking damage)!")

    private fun holdingAxe(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return ItemUtil.getId(p.mainHandItem) == "RAGNAROCK_AXE"
    }

    @JvmStatic
    fun init() {
        Events.ON_SOUND.register { _, _, pitch ->
            if (FishSettings.ragnarockEnabled && FishSettings.ragnarockCastAlert
                && pitch == CAST_PITCH && Location.inSkyblock() && holdingAxe()
            ) {
                Misc.forceTitle(Component.literal("§aCasted Ragnarock"), Component.empty())
                if (FishSettings.ragnarockAnnounceParty) Misc.executeCommand("pc Casted Ragnarock")
            }
            false
        }

        Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.ragnarockEnabled && FishSettings.ragnarockCancelAlert
                && CANCELLED.matcher(text.string).find()
            ) {
                Misc.forceTitle(Component.literal("§cRagnarock Cancelled"), Component.empty())
            }
            false
        }
    }
}
