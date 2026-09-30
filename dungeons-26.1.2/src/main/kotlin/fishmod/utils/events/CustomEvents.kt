package fishmod.utils.events

import fishmod.utils.Location
import fishmod.utils.debug.Debug
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import java.util.regex.Pattern

object CustomEvents {

    private val PARTY_PATTERN: Pattern = Pattern.compile("^§9Party §8>")
    private val LEAP_PATTERN: Pattern = Pattern.compile("^You have teleported to .*!$")

    private const val PARTY_MSG_OFFSET = 11

    @JvmStatic
    fun init() {
        ClientReceiveMessageEvents.GAME.register { message, _ ->
            try {
                onPartyChat(message.string)
            } catch (t: Throwable) {
                FishDiag.fail("CustomEvents.1", "party chat parse", t)
            }
        }

        ClientReceiveMessageEvents.GAME.register { message, _ ->
            try {
                if (Location.inDungeon() && LEAP_PATTERN.matcher(message.string).find()) {
                    Events.ON_LEAP.invoke { leapEvent -> leapEvent.onLeap(message) }
                }
            } catch (t: Throwable) {
                FishDiag.fail("CustomEvents.2", "leap message dispatch", t)
            }
        }
    }

    private fun onPartyChat(string: String) {
            val matcher = PARTY_PATTERN.matcher(string)
            if (!matcher.find()) return

            val index = string.indexOf(":")
            if (index < PARTY_MSG_OFFSET) {
                Debug.LOGGER.error("{} had bad index", string)
                FishDiag.fail("CustomEvents.3", "party message without ':' after prefix: '$string'")
                return
            }

            var tempUsername = string.substring(PARTY_MSG_OFFSET, index).replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")
            if (index + 2 >= string.length) return
            val sentMessage = string.substring(index + 2).trim()

            val b = tempUsername.indexOf("]")
            if (b >= 0 && b + 2 <= tempUsername.length) tempUsername = tempUsername.substring(b + 2)

            val username = tempUsername
            if (username.isBlank()) FishDiag.fail("CustomEvents.4", "odd party sender '$username' parsed from '$string'")
            Events.ON_PARTY_MESSAGE.invoke { partyMessageEvent -> partyMessageEvent.sentMessage(username, sentMessage) }
    }
}
