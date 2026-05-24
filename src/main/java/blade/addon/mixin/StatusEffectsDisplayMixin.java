package blade.addon.mixin;

import blade.addon.utils.config.values.Visual;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.StatusEffectsDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatusEffectsDisplay.class)
public class StatusEffectsDisplayMixin {

    @Inject(method = "drawStatusEffects", at=@At("HEAD"), cancellable = true)
    public void drawEffects(DrawContext context, java.util.Collection<?> effects, int x, int y, int width, int height, int maxHeight, CallbackInfo ci) {
        if (Visual.hideStatusOverLay) {
            ci.cancel();
        }
    }

    @Inject(method = "drawStatusEffectTooltip", at=@At("HEAD"), cancellable = true, require = 0)
    public void drawToolTip(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (Visual.hideStatusOverLay) {
            ci.cancel();
        }
    }
}
