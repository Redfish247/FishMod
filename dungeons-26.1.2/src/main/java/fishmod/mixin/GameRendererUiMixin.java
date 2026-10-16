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

    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.client.gui.render.GuiRenderer guiRenderer;
    @org.spongepowered.asm.mixin.Unique private com.mojang.blaze3d.buffers.GpuBufferSlice fishmod$fogSlice;

    @org.spongepowered.asm.mixin.injection.ModifyArg(
        method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V")
    )
    private com.mojang.blaze3d.buffers.GpuBufferSlice fishmod$captureFog(com.mojang.blaze3d.buffers.GpuBufferSlice slice) {
        fishmod$fogSlice = slice;
        return slice;
    }

    @Inject(
        method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            shift = At.Shift.AFTER
        )
    )
    private void fishmod$paintUiOverlay(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        try {
            AnimatedDyeAnimator.tickFrame();
            Screen current = Minecraft.getInstance().screen;
            if (current instanceof HasUiOverlay screen) {
                screen.paintUiOverlay();
            } else if (current instanceof AbstractContainerScreen<?> container && StorageOverlay.isActive(container)) {
                StorageOverlay.paintUiOverlay();
                var state = ((GameRenderer) (Object) this).getGameRenderState().guiRenderState;
                if (StorageOverlay.extractCarriedLayer(state)) guiRenderer.render(fishmod$fogSlice);
            }
            fishmod.features.achievements.AchPopup.paintUiOverlay();
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("GameRendererUiMixin.1", "ui overlay paint failed", t);
        }
    }
}
