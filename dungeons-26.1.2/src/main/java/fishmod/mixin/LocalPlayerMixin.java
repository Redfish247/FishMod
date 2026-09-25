package fishmod.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void fishmod$slotLockDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
        if (fishmod.features.SlotLocking.onDrop()) cir.setReturnValue(false);
    }
}
