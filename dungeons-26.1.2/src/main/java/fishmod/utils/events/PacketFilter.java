package fishmod.utils.events;

/** Packet types ON_PACKET listeners handle; add new ones here or the listener never sees them. */
public final class PacketFilter {
    private static final Class<?>[] WANTED_TYPES = {
        net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket.class,
        net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket.class,
        net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket.class,
        net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.class,
        net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket.class,
        net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket.class,
        net.minecraft.network.protocol.game.ClientboundAddEntityPacket.class,
        net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket.class,
        net.minecraft.network.protocol.game.ClientboundSoundPacket.class,
        net.minecraft.network.protocol.game.ClientboundOpenScreenPacket.class,
        net.minecraft.network.protocol.game.ClientboundMapItemDataPacket.class,
        net.minecraft.network.protocol.game.ClientboundEntityEventPacket.class,
        net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket.class,
        net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket.class,
        net.minecraft.network.protocol.game.ClientboundSystemChatPacket.class,
    };
    private static final ClassValue<Boolean> WANTED = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> c) {
            for (Class<?> w : WANTED_TYPES) if (w.isAssignableFrom(c)) return true;
            return false;
        }
    };

    private PacketFilter() {}

    public static boolean wanted(Object packet) {
        return !Events.ON_PACKET.isEmpty() && WANTED.get(packet.getClass());
    }
}
