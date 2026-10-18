package fishmod.mixin;

import fishmod.features.HasUiOverlay;
import fishmod.features.item.AnimatedDyeAnimator;
import fishmod.features.storage.StorageOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.2 side: GuiRenderer.render() no longer takes a fog slice and the render state getter was renamed.
@Mixin(GameRenderer.class)
public class GameRendererUiMixin {

    @Shadow @Final private net.minecraft.client.gui.render.GuiRenderer guiRenderer;

    @Inject(
        method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V",
            shift = At.Shift.AFTER
        )
    )
    private void fishmod$paintUiOverlay(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        try {
            AnimatedDyeAnimator.tickFrame();
            Screen current = fishmod.compat.McCompatKt.getCurScreen(Minecraft.getInstance());
            if (current instanceof HasUiOverlay screen) {
                screen.paintUiOverlay();
            } else if (current instanceof AbstractContainerScreen<?> container && StorageOverlay.isActive(container)) {
                StorageOverlay.paintUiOverlay();
                var state = ((GameRenderer) (Object) this).gameRenderState().guiRenderState;
                if (StorageOverlay.extractCarriedLayer(state)) guiRenderer.render();
            }
            fishmod.features.achievements.AchPopup.paintUiOverlay();
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("GameRendererUiMixin.1", "ui overlay paint failed", t);
        }
    }
}
