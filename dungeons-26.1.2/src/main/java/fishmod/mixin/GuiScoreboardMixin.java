package fishmod.mixin;

import fishmod.features.scoreboard.CustomScoreboard;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiScoreboardMixin {

    private static boolean fishmod$loggedRenderError = false;

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void fishmod$customScoreboard(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (!FishSettings.customScoreboardEnabled) return;
        // dungeon map HUD already carries score
        if (FishSettings.customScoreboardHideInDungeon && Location.inDungeon()) return;
        try {
            int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            CustomScoreboard.render(context, screenW);
        } catch (Exception e) {
            if (!fishmod$loggedRenderError) {
                fishmod$loggedRenderError = true;
                fishmod.utils.debug.Debug.LOGGER.error("[FishMod] CustomScoreboard.render failed; sidebar suppressed (logged once)", e);
            }
        }
        ci.cancel();
    }
}
