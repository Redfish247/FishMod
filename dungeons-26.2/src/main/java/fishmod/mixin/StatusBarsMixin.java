package fishmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fishmod.features.extras.StatusBars;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Feeds the fancy status bars and hides the vanilla hearts/armor/food and XP level when they're on
@Mixin(Hud.class)
public class StatusBarsMixin {

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void fishmod$statusBarsHideHealth(CallbackInfo ci) {
        if (StatusBars.hideVanilla()) ci.cancel();
    }

    @WrapOperation(method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
    private void fishmod$statusBarsHideXp(GuiGraphicsExtractor extractor, Font font, int level, Operation<Void> original) {
        if (!StatusBars.hideVanillaXp()) original.call(extractor, font, level);
    }

    @WrapOperation(method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void fishmod$statusBarsHideXpBg(net.minecraft.client.gui.contextualbar.ContextualBar bar, GuiGraphicsExtractor extractor, net.minecraft.client.DeltaTracker dt, Operation<Void> original) {
        if (!StatusBars.hideVanillaXp()) original.call(bar, extractor, dt);
    }

    @WrapOperation(method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void fishmod$statusBarsHideXpFill(net.minecraft.client.gui.contextualbar.ContextualBar bar, GuiGraphicsExtractor extractor, net.minecraft.client.DeltaTracker dt, Operation<Void> original) {
        if (!StatusBars.hideVanillaXp()) original.call(bar, extractor, dt);
    }
}
