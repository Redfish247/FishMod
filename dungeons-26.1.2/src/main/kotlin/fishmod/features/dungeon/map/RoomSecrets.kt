package fishmod.features.dungeon.map

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object RoomSecrets {
    private val SECRETS = Regex("(\\d+)/(\\d+) Secrets")

    @JvmStatic
    fun onActionBar(message: Component) {
        if (!DungeonState.isInDungeon()) return
        val m = SECRETS.find(fishmod.utils.Constants.STRIP_COLOR_REGEX.replace(message.string, "")) ?: return
        fishmod.features.dungeon.SecretOverlay.onSecrets(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        val player = Minecraft.getInstance().player ?: return
        val idx = MapVec2i(player.blockX, player.blockZ).index()
        if (idx < 0) return
        val room = Scan.roomsList[idx].owner ?: return
        room.secretsFound = m.groupValues[1].toInt()
    }
}
