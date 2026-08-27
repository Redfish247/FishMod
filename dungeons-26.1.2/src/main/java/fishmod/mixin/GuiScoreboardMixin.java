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

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void fishmod$customScoreboard(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (!FishSettings.customScoreboardEnabled) return;
        // Optionally leave dungeons to vanilla's sidebar — the dungeon map info HUD already carries score.
        if (FishSettings.customScoreboardHideInDungeon && Location.inDungeon()) return;
        try {
            int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            CustomScoreboard.render(context, screenW);
        } catch (Exception ignored) {}
        ci.cancel();
    }
}
