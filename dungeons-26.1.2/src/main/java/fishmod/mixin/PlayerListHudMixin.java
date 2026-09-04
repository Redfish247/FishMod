package fishmod.mixin;

import fishmod.features.CompactTab;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {
    @Shadow private Component header;
    @Shadow private Component footer;

    private static boolean fishmod$loggedRenderError = false;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void fishmod$compactTab(GuiGraphicsExtractor context, int scaledWindowWidth, Scoreboard scoreboard,
                                    Objective objective, CallbackInfo ci) {
        if (!FishSettings.compactTabEnabled) return;
        // Only take over Hypixel lobby tabs using the !A-/!B- column encoding; others ship plain entries
        if (!CompactTab.shouldRender()) return;
        try {
            CompactTab.render(context, scaledWindowWidth,
                    header == null ? "" : header.getString(),
                    footer == null ? "" : footer.getString());
        } catch (Exception e) {
            if (!fishmod$loggedRenderError) {
                fishmod$loggedRenderError = true;
                fishmod.utils.debug.Debug.LOGGER.error("[FishMod] CompactTab.render failed; tab suppressed (logged once)", e);
            }
        }
        ci.cancel();
    }
}
