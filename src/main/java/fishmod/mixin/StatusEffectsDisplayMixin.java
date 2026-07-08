package fishmod.mixin;

import fishmod.utils.config.values.Visual;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectsInInventory.class)
public class StatusEffectsDisplayMixin {

    @Inject(method = "renderEffects", at=@At("HEAD"), cancellable = true)
    public void drawEffects(GuiGraphics context, java.util.Collection<?> effects, int x, int y, int width, int height, int maxHeight, CallbackInfo ci) {
        if (Visual.hideStatusOverLay) {
            ci.cancel();
        }
    }

    @Inject(method = "drawStatusEffectTooltip", at=@At("HEAD"), cancellable = true, require = 0)
    public void drawToolTip(GuiGraphics context, int mouseX, int mouseY, CallbackInfo ci) {
        if (Visual.hideStatusOverLay) {
            ci.cancel();
        }
    }
}
