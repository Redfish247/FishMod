package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.sound.SoundManager
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * A title + cue when a Wither or Blood key is obtained, so the person routing doesn't have to watch
 * chat. Purely reads chat — key *state* is still owned by [fishmod.features.dungeon.map.DungeonState].
 */
object KeyNotifier {

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val WITHER: Pattern = Pattern.compile("(?:\\[[A-Za-z+]+] )?([A-Za-z0-9_]+) has obtained Wither Key!")
    private val BLOOD: Pattern = Pattern.compile("(?:\\[[A-Za-z+]+] )?([A-Za-z0-9_]+) has obtained Blood Key!")

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (!Dungeons.enableKeyNotifier || !Location.inDungeon()) return@register false
            val s = text.string.replace(COLOR, "").trim()
            when {
                s == "A Wither Key was picked up!" || WITHER.matcher(s).matches() ->
                    notify("§8§lWITHER KEY", "§8Wither key picked up", 0.7f, "witherKey")
                s == "A Blood Key was picked up!" || BLOOD.matcher(s).matches() ->
                    notify("§c§lBLOOD KEY", "§cBlood key picked up", 1.2f, "bloodKey")
            }
            false
        }
    }

    private fun notify(title: String, chat: String, pitch: Float, key: String) {
        if (FishSettings.keyNotifierTitle)
            Misc.forceTitle(Component.literal(title), Component.empty(), FishSettings.keyNotifierDurationMs)
        if (FishSettings.keyNotifierChat) Misc.addChatMessage(Component.literal(chat))
        if (FishSettings.keyNotifierSound) {
            SoundManager.play(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, pitch, key, 500)
        }
    }
}
