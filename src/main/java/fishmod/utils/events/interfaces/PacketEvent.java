package fishmod.utils.events.interfaces;

import net.minecraft.network.protocol.Packet;

public interface PacketEvent {

    boolean onPacket(Packet<?> packet);
}
