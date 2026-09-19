package fishmod.mixin;

import fishmod.features.HasNvgOverlay;
import fishmod.features.item.AnimatedDyeAnimator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints FishModScreen's NanoVG overlay right after the vanilla GuiRenderer flush, the one point
 * per frame where the accumulated GuiRenderState becomes GPU draw calls, so z-ordering is correct
 * and the render-target FBO is still bound (a TAIL injection is too late, after it switches back).
 */
@Mixin(GameRenderer.class)
public class GameRendererNvgMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            shift = At.Shift.AFTER
        )
    )
    private void fishmod$paintNvgOverlay(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        AnimatedDyeAnimator.tickFrame();
        if (Minecraft.getInstance().screen instanceof HasNvgOverlay screen) {
            screen.paintNvgOverlay();
        }
    }
}
