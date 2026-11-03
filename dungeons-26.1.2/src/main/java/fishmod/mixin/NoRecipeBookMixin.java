package fishmod.mixin;

import fishmod.utils.config.values.MiscSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// With the recipe book hidden, the panel never draws, never reports open and ignores toggles.
@Mixin(RecipeBookComponent.class)
public class NoRecipeBookMixin {

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void fishmod$neverOpen(CallbackInfoReturnable<Boolean> cir) {
        if (MiscSettings.hideRecipeBook) cir.setReturnValue(false);
    }

    @Inject(method = {"extractRenderState", "setVisible"}, at = @At("HEAD"), cancellable = true)
    private void fishmod$blockPanel(CallbackInfo ci) {
        if (MiscSettings.hideRecipeBook) ci.cancel();
    }
}
