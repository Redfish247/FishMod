package fishmod.mixin;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class ClientCommonNetworkHandlerMixin {

    @Inject(method = "onKeepAlive", at = @At("HEAD"))
    private void fishmod$timeKeepAlive(KeepAliveS2CPacket packet, CallbackInfo ci) {
        try {
            long est = System.currentTimeMillis() - packet.getId();
            fishmod.utils.PingTracker.pushOneWay(est);
        } catch (Exception ignored) {}
    }
}
