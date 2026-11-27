package fishmod.mixin;

import fishmod.utils.debug.MixinGuard;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        }
    }

    @Unique
    private static boolean fishmod$isOurs(Throwable t) {
        for (int depth = 0; t != null && depth < 8; depth++, t = t.getCause()) {
            for (StackTraceElement e : t.getStackTrace()) {
                String cls = e.getClassName();
                String m = e.getMethodName();
                if (cls.startsWith("fishmod.") || cls.startsWith("twitchbridge.")) return true;
                boolean mixinFrame = m.contains("$") && (m.startsWith("handler$") || m.startsWith("wrapOperation$") || m.startsWith("redirect$") || m.startsWith("modify"));
                if (mixinFrame) { if (m.contains("fishmod")) return true; if (!m.contains("mixinextras$bridge")) return false; continue; }
                if (cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.") || cls.startsWith("net.minecraft.")
                        || cls.startsWith("com.mojang.") || cls.startsWith("com.google.") || cls.startsWith("io.netty.")
                        || cls.startsWith("org.spongepowered.") || cls.startsWith("net.fabricmc.") || cls.startsWith("it.unimi.")) continue;
                return false;
            }
        }
        return false;
    }
}
