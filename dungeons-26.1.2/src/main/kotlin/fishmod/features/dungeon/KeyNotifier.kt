package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.Dungeons
import fishmod.utils.events.Events
import fishmod.utils.sound.SoundManager
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * Revives blade-addons' `enableKeyNotifier`: a title + cue when a Wither or Blood key is obtained,
 * so the person routing doesn't have to watch chat. Purely reads chat — key *state* is still owned
 * by [fishmod.features.dungeon.map.DungeonState].
 */
object KeyNotifier {

    private val WITHER: Pattern = Pattern.compile("(?:\\[[A-Za-z+]+] )?([A-Za-z0-9_]+) has obtained Wither Key!")
    private val BLOOD: Pattern = Pattern.compile("(?:\\[[A-Za-z+]+] )?([A-Za-z0-9_]+) has obtained Blood Key!")

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (!Dungeons.enableKeyNotifier || !Location.inDungeon()) return@register false
            val s = text.string.replace(Regex("§."), "").trim()
            when {
                s == "A Wither Key was picked up!" || WITHER.matcher(s).matches() -> {
                    Misc.forceTitle(Component.literal("§8§lWITHER KEY"), Component.empty())
                    SoundManager.play(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.7f, "witherKey", 500)
                }
                s == "A Blood Key was picked up!" || BLOOD.matcher(s).matches() -> {
                    Misc.forceTitle(Component.literal("§c§lBLOOD KEY"), Component.empty())
                    SoundManager.play(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f, "bloodKey", 500)
                }
            }
            false
        }
    }
}
