package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import java.util.regex.Pattern

/**
 * Architect's First Draft QoL (ported from NoammAddons' ArchitectDraft, announce half only). When
 * you use the Draft to reset a puzzle, tell the party which puzzle you reset so nobody re-does it.
 */
object ArchitectDraft {

    private val RESET: Pattern = Pattern.compile("You used the Architect's First Draft to reset (.+)!")

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.architectDraftAnnounce || !Location.inDungeon()) return@register false
            val m = RESET.matcher(text.string.replace(Regex("§."), "").trim())
            if (m.matches()) {
                Minecraft.getInstance().connection?.sendCommand("pc Used Draft to reset ${m.group(1)}")
            }
            false
        }
    }
}
