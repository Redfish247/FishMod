package fishmod.mixin;

import fishmod.features.visuals.ClassicPack;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClassicPackPushMixin {
    @Shadow @Final protected Connection connection;

    @Inject(
        method = "handleResourcePackPush",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true,
        require = 0
    )
    private void fishmod$localSkyblockPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        try {
            if (ClassicPack.onServerPack(packet.url())) {
                connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
                connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                ci.cancel();
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ClassicPackPushMixin.1", "pack push handling failed", t);
        }
    }
}
