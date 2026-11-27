package fishmod.mixin;

//? if >=26.3 {
/*import net.minecraft.client.multiplayer.MultiPlayerGameMode;
*///?}
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if <26.3 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

//? if <26.3 {
@Mixin(LocalPlayer.class)
//?} else {
/*@Mixin(MultiPlayerGameMode.class)
*///?}
public class SlotLockDropMixin {

    //? if <26.3 {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void fishmod$slotLockDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
    //?} else {
    /*@Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void fishmod$slotLockDrop(LocalPlayer player, boolean all, CallbackInfo ci) {
    *///?}
        try {
            //? if <26.3 {
            if (fishmod.features.inventory.SlotLocking.onDrop()) cir.setReturnValue(false);
            //?} else {
            /*if (fishmod.features.inventory.SlotLocking.onDrop()) ci.cancel();
            *///?}
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("SlotLockDropMixin.1", "slot lock drop check failed", t);
        }
    }
}
