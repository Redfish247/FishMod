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
            // Hypixel also sends these for admin inventory changes; those carry id 0
            if (common.getId() == 0) return;
            Events.ON_SERVER_TICK.invoke(ServerTickEvent::onServerTick);
        }

        // Server echoes our startTime in the pong, so now - startTime is a true round trip
        if (packet instanceof ClientboundPongResponsePacket pong) {
            fishmod.utils.PingTracker.pushRtt(Util.getMillis() - pong.time());
        }

        // ON_GAME_MESSAGE is fired on the main thread by ClientPlayNetworkHandlerMixin; don't re-fire from netty here
        // Not a double-fire with ClientPlayNetworkHandlerMixin#apply: this sees every packet as it arrives on the
        // netty thread, including a ClientboundBundlePacket itself (the wrapper). The bundle's real sub-packets
        // are unwrapped and re-dispatched only inside handleBundlePacket (main thread), which is what that other
        // invoke covers — so a given packet's *content* is only ever seen at one of the two sites, never both.
        Events.ON_PACKET.invoke(packetEvent -> packetEvent.onPacket(packet));
    }
}
