package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.regex.Pattern

/**
 * Architect's First Draft auto-refill. When *you* fail a dungeon puzzle, pull one Architect's First
 * Draft from your sacks so the next attempt has one ready.
 */
object ArchitectDraft {

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val PUZZLE_FAIL = Pattern.compile("PUZZLE FAIL! (\\w{1,16}) .+")
    private val ORUO_WRONG = Pattern.compile("\\[STATUE] Oruo the Omniscient: (\\w{1,16}) chose the wrong answer!.*")

    private var pending = -1

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.architectDraftRefill || !Location.inDungeon()) return@register false
            val s = text.string.replace(COLOR, "").trim()
            val self = Minecraft.getInstance().player?.gameProfile?.name ?: return@register false
            val m1 = PUZZLE_FAIL.matcher(s)
            val m2 = ORUO_WRONG.matcher(s)
            if ((m1.matches() && m1.group(1) == self) || (m2.matches() && m2.group(1) == self)) pending = 30 // ~1.5s
            false
        }

        Events.ON_WORLD_CHANGE.register { pending = -1; false }

        ClientTickEvents.END_CLIENT_TICK.register {
            if (pending < 0) return@register
            if (pending == 0) Minecraft.getInstance().connection?.sendCommand("gfs ARCHITECT_FIRST_DRAFT 1")
            pending--
        }
    }
}
