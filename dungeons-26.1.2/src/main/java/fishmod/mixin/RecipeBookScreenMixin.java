package fishmod.mixin;

import fishmod.features.other.SearchBar;
import fishmod.utils.config.values.ExtraOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeBookScreen.class)
public class RecipeBookScreenMixin {
    @Inject(method = "initButton", at=@At("HEAD"), cancellable = true)
    private void addRecipeBook(CallbackInfo ci) {
        if (ExtraOptions.disableRecipeBook) {
            ci.cancel();
        }
    }

    // recipe-book screens (player inv, crafting, furnace) skip AbstractContainerScreen.extractRenderState
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fishmod$renderSearchBar(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        SearchBar.render(context, mouseX, mouseY, deltaTicks);
    }
}
