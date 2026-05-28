package fishmod.mixin;

import fishmod.features.CompactTab;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Shadow private Text header;
    @Shadow private Text footer;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fishmod$compactTab(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard,
                                    ScoreboardObjective objective, CallbackInfo ci) {
        if (!FishSettings.compactTabEnabled) return;
        try {
            CompactTab.render(context, scaledWindowWidth,
                    header == null ? "" : header.getString(),
                    footer == null ? "" : footer.getString());
        } catch (Exception ignored) {}
        ci.cancel();
    }
}
