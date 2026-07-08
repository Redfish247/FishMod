package fishmod.mixin;

import fishmod.utils.events.Events;
import fishmod.utils.events.interfaces.ServerTickEvent;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ClientConnectionMixin {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"), order = 0, cancellable = true)
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientboundPingPacket common) {
            //admins send these packets too for inventory changes
            //thankfully they all have the param 0 for some reason
            if (common.getId() == 0) return;
            Events.ON_SERVER_TICK.invoke(ServerTickEvent::onServerTick);
        }

        // Vanilla's PingMeasurer drives ping requests during play; the server echoes our startTime in
        // the pong, so now - startTime is a true round trip. Same source Odin reads for live ping.
        if (packet instanceof ClientboundPongResponsePacket pong) {
            fishmod.utils.PingTracker.pushRtt(Util.getMillis() - pong.time());
        }

        if (packet instanceof ClientboundSystemChatPacket(Component content, boolean overlay) && !overlay) {
            if (Events.ON_GAME_MESSAGE.invoke(gameMessageEvent -> gameMessageEvent.onGameMessage(content))) {
               ci.cancel();
            }
        }

        Events.ON_PACKET.invoke(packetEvent -> packetEvent.onPacket(packet));
    }

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void sendImmediately(Packet<?> packet, ChannelFutureListener channelFutureListener, boolean flush, CallbackInfo ci) {
        // for some reason this just being here
        // fixes player tracking and I don't know why
        // will hopefully look into it later
    }
}
