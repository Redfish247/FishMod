package fishmod.utils.data;

import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;

/**
 * FishMod-exclusive party tracker. Queries Hypixel's Mod API directly with
 * no minimum delay, so the state is always fresh — unlike blade's PartyUtil
 * which caches for 1 minute and returns stale data after leaving a party.
 */
public class FishPartyTracker {

    private static boolean inParty = false;

    public static void init() {
        HypixelModAPI.getInstance().createHandler(
                ClientboundPartyInfoPacket.class,
                packet -> inParty = packet.isInParty()
        );
    }

    /** Sends a fresh party-info request then returns the last known state. */
    public static boolean isInParty() {
        try {
            HypixelModAPI.getInstance().sendPacket(new ServerboundPartyInfoPacket());
        } catch (Throwable ignored) {}
        return inParty;
    }
}
