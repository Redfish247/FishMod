package fishmod.utils.events

import fishmod.utils.Location
import fishmod.utils.debug.Debug
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import java.util.regex.Pattern

object CustomEvents {

    private val PARTY_PATTERN: Pattern = Pattern.compile("^§9Party §8>")
    private val LEAP_PATTERN: Pattern = Pattern.compile("^You have teleported to .*!$")

    private const val PARTY_MSG_OFFSET = 11

    @JvmStatic
    fun init() {
        // party event
        ClientReceiveMessageEvents.GAME.register { message, _ ->
            val string = message.string

            val matcher = PARTY_PATTERN.matcher(string)
            if (!matcher.find()) return@register

            var index = string.indexOf(":")
            if (index < PARTY_MSG_OFFSET) {
                Debug.LOGGER.error("{} had bad index", string)
                return@register
            }

            var tempUsername = string.substring(PARTY_MSG_OFFSET, index).replace(Regex("§."), "")
            if (index + 2 >= string.length) return@register
            val sentMessage = string.substring(index + 2).trim()

            index = tempUsername.indexOf("]") + 2
            if (index > -1 && index < tempUsername.length) {
                tempUsername = tempUsername.substring(index)
            }

            val username = tempUsername
            Events.ON_PARTY_MESSAGE.invoke { partyMessageEvent -> partyMessageEvent.sentMessage(username, sentMessage) }
        }

        // leap event
        ClientReceiveMessageEvents.GAME.register { message, _ ->
            if (Location.inDungeon()) {
                val string = message.string

                val matcher = LEAP_PATTERN.matcher(string)
                if (!matcher.find()) return@register

                Events.ON_LEAP.invoke { leapEvent -> leapEvent.onLeap(message) }
            }
        }
    }
}
