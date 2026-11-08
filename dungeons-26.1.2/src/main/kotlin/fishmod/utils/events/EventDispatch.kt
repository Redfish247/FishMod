package fishmod.utils.events

import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents

// Turns raw chat into the party-chat and leap hooks.
object EventDispatch {

    private const val PARTY_PREFIX = "Party >"
    private val LEAP_LINE = Regex("^You have teleported to .*!$")
    // "[MVP+] Name" -> "Name"
    private val RANK_TAG = Regex("^\\[[^\\]]*] ")

    @JvmStatic
    fun init() {
        ClientReceiveMessageEvents.GAME.register { message, _ ->
            val raw = message.chatPlain
            FishDiag.guard("EventDispatch.1", "party chat parse") { if (raw.startsWith(PARTY_PREFIX)) firePartyChat(raw) }
            FishDiag.guard("EventDispatch.2", "leap message dispatch") {
                if (SkyblockLocation.inDungeon() && LEAP_LINE.containsMatchIn(raw)) FishEvents.leap.invoke { it.onLeap(message) }
            }
        }
    }

    private fun firePartyChat(raw: String) {
        val colon = raw.indexOf(':', PARTY_PREFIX.length)
        if (colon < 0) {
            FishDiag.fail("EventDispatch.3", "party message without ':' after prefix: '$raw'")
            return
        }
        if (colon + 2 >= raw.length) return
        val sender = raw.substring(PARTY_PREFIX.length, colon).replace(FishConstants.STRIP_COLOR_REGEX, "").trim().replace(RANK_TAG, "")
        val text = raw.substring(colon + 2).trim()
        if (sender.isBlank()) FishDiag.fail("EventDispatch.4", "odd party sender parsed from '$raw'")
        FishEvents.partyChat.invoke { it.sentMessage(sender, text) }
    }
}
