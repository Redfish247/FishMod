package fishmod.mixin;

import fishmod.utils.events.Events;
import fishmod.utils.events.interfaces.ServerTickEvent;
import io.netty.channel.ChannelHandlerContext;
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

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"), order = 0)
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientboundPingPacket common) {
            if (common.getId() == 0) return;
            Events.ON_SERVER_TICK.invoke(ServerTickEvent::onServerTick);
        }

        if (packet instanceof ClientboundPongResponsePacket pong) {
            fishmod.utils.PingTracker.pushRtt(Util.getMillis() - pong.time());
        }

        Events.ON_PACKET.invoke(packetEvent -> packetEvent.onPacket(packet));
    }
}
