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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererUiMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            shift = At.Shift.AFTER
        )
    )
    private void fishmod$paintUiOverlay(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        AnimatedDyeAnimator.tickFrame();
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof HasUiOverlay screen) {
            screen.paintUiOverlay();
        } else if (current instanceof AbstractContainerScreen<?> container && StorageOverlay.isActive(container)) {
            StorageOverlay.paintUiOverlay();
        }
    }
}
