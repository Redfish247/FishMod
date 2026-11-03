package fishmod.mixin;

import fishmod.utils.SkyblockLocation;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBar.class)
public class ExperienceBarRendererMixin {

    @Inject(method = {"extractRenderState", "extractBackground"}, at = @At("HEAD"), cancellable = true)
    private void fishmod$hideXpBar(GuiGraphicsExtractor extractor, DeltaTracker delta, CallbackInfo ci) {
        if (FishSettings.actionBarEnabled && FishSettings.abHideXpBar && SkyblockLocation.inSkyblock()) ci.cancel();
    }
}
