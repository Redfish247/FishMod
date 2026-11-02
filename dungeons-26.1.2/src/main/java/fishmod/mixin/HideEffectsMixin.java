package fishmod.mixin;

import fishmod.utils.config.values.VisualSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectsInInventory.class)
public class HideEffectsMixin {

    @Inject(method = "extractEffects", at=@At("HEAD"), cancellable = true)
    public void drawEffects(GuiGraphicsExtractor context, java.util.Collection<?> effects, int x, int y, int width, int height, int maxHeight, CallbackInfo ci) {
        if (VisualSettings.hideStatusOverLay) {
            ci.cancel();
        }
    }
}
