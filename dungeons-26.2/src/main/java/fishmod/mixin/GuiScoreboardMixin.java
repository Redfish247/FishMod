package fishmod.mixin;

import fishmod.features.scoreboard.CustomScoreboard;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiScoreboardMixin {

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void fishmod$customScoreboard(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (!FishSettings.customScoreboardEnabled) return;
        if (FishSettings.customScoreboardHideInDungeon && Location.inDungeon()) return;
        fishmod.utils.debug.MixinGuard.run("GuiScoreboardMixin.1", "custom scoreboard render failed",
                () -> CustomScoreboard.render(context, Minecraft.getInstance().getWindow().getGuiScaledWidth()));
        ci.cancel();
    }
}
