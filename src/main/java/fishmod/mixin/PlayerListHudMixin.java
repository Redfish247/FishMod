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

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void fishmod$compactTab(GuiGraphicsExtractor context, int scaledWindowWidth, Scoreboard scoreboard,
                                    Objective objective, CallbackInfo ci) {
        if (!FishSettings.compactTabEnabled) return;
        // Only take over on Hypixel lobby-style tabs that use the !A-/!B- column encoding.
        // Dungeons, Kuudra, Rift, Garden, etc. ship plain tab entries — let vanilla draw
        // them verbatim instead of forcing them into a column layout that mangles the data.
        if (!CompactTab.shouldRender()) return;
        try {
            CompactTab.render(context, scaledWindowWidth,
                    header == null ? "" : header.getString(),
                    footer == null ? "" : footer.getString());
        } catch (Exception ignored) {}
        ci.cancel();
    }
}
