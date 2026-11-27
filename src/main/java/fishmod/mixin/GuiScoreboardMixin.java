package fishmod.mixin;

import fishmod.features.general.CustomScoreboard;
import fishmod.utils.SkyblockLocation;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.gui.Gui;
//?} else {
/*import net.minecraft.client.gui.Hud;
*///?}
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if <26.2 {
@Mixin(Gui.class)
//?} else {
/*@Mixin(Hud.class)
*///?}
public class GuiScoreboardMixin {

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void fishmod$customScoreboard(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (fishmod.features.hud.SkyItemList.panelActive()) { ci.cancel(); return; }
        if (!FishSettings.customScoreboardEnabled) return;
        if (FishSettings.customScoreboardHideInDungeon && SkyblockLocation.inDungeon()) return;
        fishmod.utils.debug.MixinGuard.run("GuiScoreboardMixin.1", "custom scoreboard render failed",
                () -> CustomScoreboard.render(context, Minecraft.getInstance().getWindow().getGuiScaledWidth()));
        ci.cancel();
    }
}
