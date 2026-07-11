package fishmod.utils.events.interfaces;

import net.minecraft.network.packet.Packet;

public interface PacketEvent {

    boolean onPacket(Packet<?> packet);
}
