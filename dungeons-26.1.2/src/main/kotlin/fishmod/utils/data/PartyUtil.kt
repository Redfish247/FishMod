package fishmod.utils.data

import fishmod.utils.debug.Debug
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket
import net.minecraft.client.Minecraft
import java.util.UUID

object PartyUtil {

    private const val MIN_DELAY = 1000 * 60
    private val INSTANCE: HypixelModAPI = HypixelModAPI.getInstance()

    private var grabbedTime: Long = 0
    private var memberMap: Map<UUID, ClientboundPartyInfoPacket.PartyMember>? = null
    private var inParty = false
    private var leaderUuid: UUID? = null

    @JvmStatic
    fun init() {
        INSTANCE.createHandler(ClientboundPartyInfoPacket::class.java) { packet ->
            if (Debug.termInfo) Debug.LOGGER.info("Received party info packet")
            memberMap = packet.memberMap
            inParty = packet.isInParty
            leaderUuid = packet.memberMap.entries
                .firstOrNull { it.value.role == ClientboundPartyInfoPacket.PartyRole.LEADER }?.key
        }
    }

    @JvmStatic
    fun amLeader(): Boolean {
        sendPacket()
        if (!inParty) return false
        val self = Minecraft.getInstance().player?.gameProfile?.id ?: return false
        return leaderUuid == self
    }

    @JvmStatic
    fun sendPacket() {
        if (System.currentTimeMillis() - grabbedTime < MIN_DELAY) return
        if (INSTANCE.sendPacket(ServerboundPartyInfoPacket())) {
            grabbedTime = System.currentTimeMillis()
        } else {
            Debug.LOGGER.warn("Server bound party info packet lost")
        }
    }

    @JvmStatic
    fun getPlayerCount(): Int {
        sendPacket()
        if (!inParty) return 0
        return memberMap?.size ?: 0
    }

    @JvmStatic
    fun isInParty(): Boolean {
        sendPacket()
        return inParty
    }

    @JvmStatic
    fun getMemberUuids(): Set<UUID> {
        sendPacket()
        if (!inParty) return emptySet()
        return memberMap?.keys ?: emptySet()
    }
}
