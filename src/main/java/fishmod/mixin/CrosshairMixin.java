package fishmod.mixin;

import fishmod.features.cosmetics.CustomCrosshair;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.gui.Gui;
//?} else {
/*import net.minecraft.client.gui.Hud;
*///?}
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if <26.2 {
@Mixin(Gui.class)
//?} else {
/*@Mixin(Hud.class)
*///?}
public class CrosshairMixin {

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void fishmod$hideVanillaCrosshair(GuiGraphicsExtractor extractor, DeltaTracker delta, CallbackInfo ci) {
        if (CustomCrosshair.active(Minecraft.getInstance())) ci.cancel();
    }
}
