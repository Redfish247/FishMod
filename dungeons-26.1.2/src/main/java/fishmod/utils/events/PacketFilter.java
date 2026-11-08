package fishmod.utils.events;

import fishmod.utils.events.interfaces.PacketHook;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

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

    private record Sub(Class<?>[] types, BooleanSupplier gate) {
        boolean matches(Class<?> c) {
            for (Class<?> t : types) if (t.isAssignableFrom(c)) return true;
            return false;
        }
    }

    private static final CopyOnWriteArrayList<Sub> SUBS = new CopyOnWriteArrayList<>();

    private PacketFilter() {}

    /** Typed listener; gate must be a cheap thread-safe read (runs on the netty thread). Listeners are never unregistered. */
    public static void register(Class<?>[] types, BooleanSupplier gate, PacketHook hook) {
        SUBS.add(new Sub(types, gate));
        FishEvents.packet.register(hook);
    }

    public static boolean wanted(Object packet) {
        int total = FishEvents.packet.size();
        if (total == 0) return false;
        Class<?> c = packet.getClass();
        if (!WANTED.get(c)) return false;
        if (total > SUBS.size()) return true; // untyped listener present
        for (Sub s : SUBS) {
            if (s.matches(c) && (s.gate == null || s.gate.getAsBoolean())) return true;
        }
        return false;
    }
}
