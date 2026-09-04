package fishmod.mixin;

import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Action Bar" feature — Hide XP Bar: skip the contextual XP bar's extract passes on SkyBlock. */
@Mixin(ExperienceBarRenderer.class)
public class ExperienceBarRendererMixin {

    @Inject(method = {"extractRenderState", "extractBackground"}, at = @At("HEAD"), cancellable = true)
    private void fishmod$hideXpBar(GuiGraphicsExtractor extractor, DeltaTracker delta, CallbackInfo ci) {
        if (FishSettings.actionBarEnabled && FishSettings.abHideXpBar && Location.inSkyblock()) ci.cancel();
    }
}
