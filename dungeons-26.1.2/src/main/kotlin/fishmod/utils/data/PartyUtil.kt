package fishmod.utils.data

import fishmod.utils.debug.Debug
import fishmod.utils.debug.FishDiag
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
    @Volatile
    var lastReceived: Long = 0
        private set

    @JvmStatic
    fun init() {
        INSTANCE.createHandler(ClientboundPartyInfoPacket::class.java) { packet ->
            try {
                if (Debug.termInfo) Debug.LOGGER.info("Received party info packet")
                lastReceived = System.currentTimeMillis()
                memberMap = packet.memberMap
                inParty = packet.isInParty
                leaderUuid = packet.memberMap.entries
                    .firstOrNull { it.value.role == ClientboundPartyInfoPacket.PartyRole.LEADER }?.key
                if (inParty && leaderUuid == null) FishDiag.fail("PartyUtil.1", "party info packet has no leader among ${packet.memberMap.size} members")
                if (inParty && packet.memberMap.isEmpty()) FishDiag.fail("PartyUtil.2", "party info packet says in party but member map is empty")
            } catch (t: Throwable) {
                FishDiag.fail("PartyUtil.3", "handle party info packet", t)
            }
        }
    }

    @JvmStatic
    fun isInParty(): Boolean = inParty

    @JvmStatic
    fun amLeader(): Boolean {
        sendPacket()
        if (!inParty) return false
        val self = Minecraft.getInstance().player?.gameProfile?.id ?: return false
        return leaderUuid == self
    }

    private var retryDelay = 10_000L
    private var warned = false

    private fun onHypixel(): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.isLocalServer) return false
        val host = mc.currentServer?.ip?.substringBefore(':')?.trim()?.trimEnd('.')?.lowercase() ?: return false
        return host == "hypixel.net" || host.endsWith(".hypixel.net")
    }

    @JvmStatic
    fun sendPacket() {
        if (System.currentTimeMillis() - grabbedTime < MIN_DELAY) return
        // off Hypixel the Mod API can't answer; just wait quietly
        if (!onHypixel()) { grabbedTime = System.currentTimeMillis() - MIN_DELAY + 5_000; return }
        if (INSTANCE.sendPacket(ServerboundPartyInfoPacket())) {
            grabbedTime = System.currentTimeMillis()
            retryDelay = 10_000L
            warned = false
        } else {
            // not registered yet (handshake pending): back off, log once, never an error report
            if (!warned) { Debug.LOGGER.info("Hypixel Mod API not ready for party info, retrying with backoff"); warned = true }
            grabbedTime = System.currentTimeMillis() - MIN_DELAY + retryDelay
            retryDelay = minOf(retryDelay * 2, MIN_DELAY.toLong())
        }
    }

    @JvmStatic
    fun forceRefresh() {
        grabbedTime = 0
        retryDelay = 10_000L
        sendPacket()
    }

    @JvmStatic
    fun getMemberUuids(): Set<UUID> {
        sendPacket()
        if (!inParty) return emptySet()
        return memberMap?.keys ?: emptySet()
    }
}
