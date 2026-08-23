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
 * Paints FishModScreen's NanoVG overlay right after the vanilla GuiRenderer flush — the one
 * point per frame where the entire accumulated GuiRenderState (HUD, screen, tooltips) actually
 * becomes GPU draw calls. Anything drawn earlier (inside Screen.render/extractRenderState) only
 * populates a descriptor that gets flushed here, so NanoVG calls made there would submit before
 * this frame's vanilla content actually reaches the framebuffer — painting under it, not over.
 * Injecting after this call instead makes correct z-ordering (NanoVG always on top) automatic.
 *
 * Confirmed (2026-08-22 diagnostic logging) this timing also matters for which framebuffer is
 * actually bound: on this version's GPU-buffer-based render pipeline, whatever's bound switches
 * back to the default framebuffer (0, which nothing later blits from) at some point after the GUI
 * flush unless something keeps Minecraft's real render-target FBO bound longer — e.g. the
 * ImmediatelyFast mod's "avoid redundant framebuffer switching" optimization, which is exactly why
 * NanoVG only ever rendered for users with that mod installed. A TAIL injection (tried briefly)
 * runs after that switch-back happens and is worse, not more robust — the framebuffer must be
 * captured as close as possible to the GUI flush, before vanilla's own cleanup can switch it away.
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
