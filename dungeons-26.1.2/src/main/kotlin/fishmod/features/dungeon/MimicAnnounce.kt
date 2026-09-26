package fishmod.features.dungeon

import fishmod.features.dungeon.map.DungeonScore
import fishmod.features.dungeon.map.DungeonState
import fishmod.utils.ChatQueue
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.world.entity.monster.zombie.Zombie

private val FORMAT_CODE_RE = Regex("[&§][0-9A-FK-ORa-fk-or]")

object MimicAnnounce {

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val PRINCE = Regex("^A Prince falls\\. \\+1 Bonus Score$")
    private val BAT = Regex("^A Bat has been slain\\. \\+1 Bonus Score$")

    @Volatile private var mimicSent = false
    @Volatile private var princeSent = false
    @Volatile private var batSent = false

    @JvmStatic
    fun init() {
        Events.ON_WORLD_CHANGE.register { mimicSent = false; princeSent = false; batSent = false; false }

        Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.mimicAnnounceEnabled) {
                val s = COLOR.replace(text.string, "").trim()
                if (PRINCE.matches(s)) princeKilled(false)
                else if (BAT.matches(s)) batKilled(false)
                else when {
                    s.contains("Mimic Killed", true) || s.contains("Mimic Dead", true) -> mimicSent = true
                    s.contains("Prince Killed", true) -> princeSent = true
                    s.contains("Bat Killed", true) -> batSent = true
                }
            }
            false
        }

        Events.ON_PACKET.register { packet ->
            if (FishSettings.mimicAnnounceEnabled && !mimicSent && packet is ClientboundEntityEventPacket
                && packet.eventId.toInt() == 3 && isFloor67() && inClear()
            ) {
                val e = Minecraft.getInstance().level?.let { packet.getEntity(it) }
                if (e is Zombie && e.isBaby) mimicKilled(false)
            }
            false
        }
    }

    private fun isFloor67(): Boolean = DungeonState.floorNumber() in 6..7
    private fun inClear(): Boolean = DungeonState.isInDungeon() && !DungeonState.isInBoss()

    @JvmStatic
    fun mimicKilled(manual: Boolean) {
        if (!manual && (mimicSent || !inClear())) return
        mimicSent = true
        DungeonScore.mimicKilled = true
        if (FishSettings.mimicMsgEnabled) send(FishSettings.mimicMsgText)
    }

    @JvmStatic
    fun princeKilled(manual: Boolean) {
        if (!manual && (princeSent || !inClear())) return
        princeSent = true
        DungeonScore.princeKilled = true
        if (FishSettings.princeMsgEnabled) send(FishSettings.princeMsgText)
    }

    @JvmStatic
    fun batKilled(manual: Boolean) {
        if (!manual && (batSent || !inClear())) return
        batSent = true
        if (FishSettings.batMsgEnabled) send(FishSettings.batMsgText)
    }

    private fun send(raw: String) {
        val msg = raw.replace(FORMAT_CODE_RE, "").trim()
        if (msg.isNotEmpty()) ChatQueue.enqueue("pc $msg")
    }
}
