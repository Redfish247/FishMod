package fishmod.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignEditScreen.class)
public class AbstractSignEditScreenMixin {
    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void fishmod$skipSend(CallbackInfo ci) {
        if (fishmod.features.item.AuctionPriceAutofill.suppressSignSend) ci.cancel();
    }
}
