package fishmod.utils.data

import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket

/** FishMod-exclusive party tracker; queries Hypixel's Mod API with no delay, unlike blade's PartyUtil which caches for 1 minute. */
object FishPartyTracker {

    private var inParty = false

    @JvmStatic
    fun init() {
        HypixelModAPI.getInstance().createHandler(
            ClientboundPartyInfoPacket::class.java
        ) { packet -> inParty = packet.isInParty }
    }

    /** Sends a fresh party-info request then returns the last known state. */
    @JvmStatic
    fun isInParty(): Boolean {
        try {
            HypixelModAPI.getInstance().sendPacket(ServerboundPartyInfoPacket())
        } catch (_: Throwable) {
        }
        return inParty
    }
}
