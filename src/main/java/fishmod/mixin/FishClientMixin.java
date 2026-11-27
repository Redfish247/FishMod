package fishmod.mixin;

import fishmod.utils.events.WorldReset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class FishClientMixin {

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void fishmod$onWorldChange(ClientLevel level, CallbackInfo ci) {
        WorldReset.onSetLevel(level);
    }
}
