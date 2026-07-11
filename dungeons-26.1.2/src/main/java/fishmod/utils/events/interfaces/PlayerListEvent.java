package fishmod.utils.events.interfaces;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;

public interface PlayerListEvent {
    boolean onNewPlayerEntry(ClientboundPlayerInfoUpdatePacket.Entry receivedEntry);
}
