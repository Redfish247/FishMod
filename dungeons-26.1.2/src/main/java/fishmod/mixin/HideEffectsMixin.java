package fishmod.mixin;

import fishmod.utils.config.values.VisualSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(EffectsInInventory.class)
public class HideEffectsMixin {

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void fishmod$skipEffectList(GuiGraphicsExtractor g, Collection<?> list, int x, int y, int w, int h, int maxH, CallbackInfo ci) {
        if (VisualSettings.hideEffectHud) ci.cancel();
    }
}
