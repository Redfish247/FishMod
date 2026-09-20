package fishmod.mixin;

import fishmod.features.CustomCrosshair;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class CrosshairMixin {

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void fishmod$hideVanillaCrosshair(GuiGraphicsExtractor extractor, DeltaTracker delta, CallbackInfo ci) {
        if (CustomCrosshair.active(Minecraft.getInstance())) ci.cancel();
    }
}
