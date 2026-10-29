package fishmod.mixin;

import fishmod.utils.debug.MixinGuard;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Packet handler throws become "Network Protocol Error"; swallow the ones FishMod code caused.
@Mixin(ClientCommonPacketListenerImpl.class)
public class PacketErrorGuardMixin {

    @Inject(method = "onPacketError", at = @At("HEAD"), cancellable = true)
    private void fishmod$keepConnectionOnOwnError(Packet<?> packet, Exception exception, CallbackInfo ci) {
        try {
            if (!fishmod$isOurs(exception)) return;
            String name = packet == null ? "null" : packet.getClass().getSimpleName();
            MixinGuard.fail("PacketErrorGuardMixin." + name, "FishMod threw while handling " + name + ", kept the connection", exception);
            ci.cancel();
        } catch (Throwable ignored) {
            // never make the vanilla error path worse
        }
    }

    @Unique
    private static boolean fishmod$isOurs(Throwable t) {
        for (int depth = 0; t != null && depth < 8; depth++, t = t.getCause()) {
            for (StackTraceElement e : t.getStackTrace()) {
                String cls = e.getClassName();
                if (cls.startsWith("fishmod.") || cls.startsWith("twitchbridge.") || e.getMethodName().contains("fishmod")) return true;
            }
        }
        return false;
    }
}
