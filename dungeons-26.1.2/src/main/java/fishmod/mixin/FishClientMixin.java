package fishmod.mixin;

import fishmod.utils.events.FishEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class FishClientMixin {

    @Unique
    private ClientLevel fishmod$lastLevel;

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void fishmod$onWorldChange(ClientLevel level, CallbackInfo ci) {
        if (level == fishmod$lastLevel) return;
        fishmod$lastLevel = level;
        try {
            FishEvents.ON_WORLD_CHANGE.invoke(it -> it.onWorldSwap());
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("MinecraftClientMixin.1", "world change event failed", t);
        }
    }
}
