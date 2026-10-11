package fishmod.mixin;

import fishmod.utils.events.Events;
import fishmod.utils.events.interfaces.ServerTickEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ClientConnectionMixin {

    // packet types ON_PACKET listeners handle; add new ones here or the listener never sees them
    @org.spongepowered.asm.mixin.Unique
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
    @org.spongepowered.asm.mixin.Unique
    private static final ClassValue<Boolean> WANTED = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> c) {
            for (Class<?> w : WANTED_TYPES) if (w.isAssignableFrom(c)) return true;
            return false;
        }
    };

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"), order = 0)
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        try {
            if (packet instanceof ClientboundPongResponsePacket pong) {
                fishmod.utils.PingTracker.pushRtt(Util.getMillis() - pong.time());
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClientConnectionMixin.1", "ping rtt tracking failed", t);
        }

        boolean serverTick = packet instanceof ClientboundPingPacket ping && ping.getId() != 0;
        boolean wanted = !Events.ON_PACKET.isEmpty() && WANTED.get(packet.getClass());
        if (!serverTick && !wanted) return;

        Minecraft.getInstance().execute(() -> {
            try {
                if (serverTick) Events.ON_SERVER_TICK.invoke(ServerTickEvent::onServerTick);
                if (wanted) Events.ON_PACKET.invoke(packetEvent -> packetEvent.onPacket(packet));
            } catch (Throwable t) {
                fishmod.utils.debug.FishDiag.fail("ClientConnectionMixin.2", "packet/server-tick event dispatch failed packet=" + packet.getClass().getSimpleName(), t);
            }
        });
    }
}
