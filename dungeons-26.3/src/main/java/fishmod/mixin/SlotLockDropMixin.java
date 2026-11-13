package fishmod.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class SlotLockDropMixin {

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void fishmod$slotLockDrop(LocalPlayer player, boolean all, CallbackInfo ci) {
        try {
            if (fishmod.features.SlotLocking.onDrop()) ci.cancel();
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("SlotLockDropMixin.1", "slot lock drop check failed", t);
        }
    }
}
