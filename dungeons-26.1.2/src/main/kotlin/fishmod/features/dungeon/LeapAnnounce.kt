package fishmod.features.dungeon

import fishmod.utils.Misc
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.events.Events
import fishmod.utils.sound.SoundManager
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * On a Spirit-Leap teleport, flash the destination player's name as a title (the vanilla
 * "You have teleported to X!" chat line scrolls away fast mid-fight). Fires off [Events.ON_LEAP],
 * which `CustomEvents` already gates to dungeons only.
 */
object LeapAnnounce {

    private val TARGET: Pattern = Pattern.compile("You have teleported to (.+?)!")

    @JvmStatic
    fun init() {
        Events.ON_LEAP.register { message ->
            if (!Dungeons.enableLeapMessages) return@register false
            val s = message.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")
            val m = TARGET.matcher(s)
            if (m.find()) {
                val target = m.group(1).trim()
                val clazz = DungeonClass.getClass(target)
                val className = clazz?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "?"
                val classLetter = DungeonClass.getChar(clazz)
                fun fill(t: String) = t.replace("{name}", target).replace("{class}", className).replace("{c}", classLetter)
                if (FishSettings.leapMessagesTitle) {
                    val msg = fill(FishSettings.leapMessagesText.replace("&", "§"))
                    Misc.forceTitle(Component.literal(msg), Component.empty())
                }
                if (FishSettings.leapMessagesParty) {
                    // Party chat can't carry formatting codes — strip only real colour codes
                    // ([&§] + a code char) so a lone "&" in prose survives, then fill placeholders.
                    val plain = fill(
                        FishSettings.leapMessagesText.replace(Regex("[&§][0-9A-FK-ORa-fk-or]"), "")
                    ).trim()
                    if (plain.isNotEmpty()) {
                        fishmod.utils.ChatQueue.enqueue("pc $plain")
                    }
                }
                if (FishSettings.leapMessagesSound) {
                    SoundManager.play(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.4f, "leap", 250)
                }
            }
            false
        }
    }
}
