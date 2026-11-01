package fishmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fishmod.features.extras.StatusBars;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Feeds the fancy status bars and hides the vanilla hearts/armor/food and XP level when they're on
@Mixin(Gui.class)
public class StatusBarsMixin {

    @Inject(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
    private void fishmod$statusBarsFeed(Component message, boolean tinted, CallbackInfo ci) {
        try {
            StatusBars.onActionBar(message);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("StatusBarsMixin.1", "status bars action bar feed failed", t);
        }
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void fishmod$statusBarsHideHealth(CallbackInfo ci) {
        if (StatusBars.hideVanilla()) ci.cancel();
    }

    @WrapOperation(method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
    private void fishmod$statusBarsHideXp(GuiGraphicsExtractor extractor, Font font, int level, Operation<Void> original) {
        if (!StatusBars.hideVanilla()) original.call(extractor, font, level);
    }
}
