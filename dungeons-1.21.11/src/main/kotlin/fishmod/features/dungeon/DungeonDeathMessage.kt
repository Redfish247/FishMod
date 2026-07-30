package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import java.util.regex.Pattern

/** When a player dies in a dungeon, sends a customisable message with their name substituted into the template via {name}. */
object DungeonDeathMessage {

    // Captures the player name from Hypixel dungeon death messages
    private val DEATH_PATTERN: Pattern = Pattern.compile(
        "☠ (\\S+) (?:was|were) killed by|☠ (\\S+) (?:died|quit)"
    )

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { message -> onMessage(message) }
    }

    private fun onMessage(message: Text): Boolean {
        if (!FishSettings.deathMessageEnabled) return false
        if (Location.getCurrentLocation() != Location.DUNGEON) return false

        val raw = message.string

        val m = DEATH_PATTERN.matcher(raw)
        if (!m.find()) return false

        val playerName = m.group(1) ?: m.group(2)

        // Skip the local player's own death. Hypixel writes "☠ You died/were killed..." for
        // yourself (literally "You"), so match that as well as the actual username.
        val mc = MinecraftClient.getInstance()
        val localName = mc.session.username
        if (playerName.equals("You", ignoreCase = true) || playerName.equals(localName, ignoreCase = true)) return false

        if (FishSettings.deathMessageToParty && mc.networkHandler != null) {
            mc.networkHandler!!.sendChatCommand("pc " + FishSettings.deathMessageTemplate.replace("{name}", playerName))
        }

        return false
    }
}
