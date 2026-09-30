package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

object DungeonDeathMessage {

    private val DEATH_PATTERN: Pattern = Pattern.compile(
        "☠ (\\S+) (?:was|were) killed by|☠ (\\S+) (?:died|quit)"
    )

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { message ->
            try { onMessage(message) } catch (e: Exception) { FishDiag.fail("DungeonDeathMessage.1", "death message handling failed", e); false }
        }
    }

    private fun onMessage(message: Component): Boolean {
        if (!FishSettings.deathMessageEnabled) return false
        if (Location.getCurrentLocation() != Location.DUNGEON) return false

        val raw = message.string

        val m = DEATH_PATTERN.matcher(raw)
        if (!m.find()) return false

        val playerName: String = m.group(1) ?: m.group(2) ?: run { FishDiag.fail("DungeonDeathMessage.2", "death line matched without a name: '$raw'"); return false }

        val mc = Minecraft.getInstance()
        val localName = mc.user.name
        if (playerName.equals("You", ignoreCase = true) || playerName.equals(localName, ignoreCase = true)) return false

        if (FishSettings.deathMessageToParty && mc.connection != null) {
            fishmod.utils.ChatQueue.enqueue("pc " + FishSettings.deathMessageTemplate.replace("{name}", playerName))
        }

        return false
    }
}
