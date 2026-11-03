package fishmod.mixin;

import fishmod.features.other.InvSearchBar;
import fishmod.utils.config.values.MiscSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeBookScreen.class)
public class NoRecipeBookScreenMixin {
    @Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
    private void fishmod$skipRecipeButton(CallbackInfo ci) {
        if (MiscSettings.hideRecipeBook) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fishmod$renderSearchBar(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        try {
            InvSearchBar.render(context, mouseX, mouseY, deltaTicks);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("NoRecipeBookScreenMixin.1", "search bar render (recipe screen) failed", t);
        }
    }
}
