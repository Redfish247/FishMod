package fishmod.mixin;

import fishmod.features.FishModScreen;
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
 * becomes GPU draw calls. Anything drawn earlier (inside Screen.render) only populates a
 * descriptor that gets flushed here, so NanoVG calls made during render() would submit before
 * this frame's vanilla content actually reaches the framebuffer — painting under it, not over.
 * Injecting after this call instead makes correct z-ordering (NanoVG always on top) automatic.
 *
 * Mojang-mapped equivalent of dungeons-1.21.11's GameRendererNvgMixin (which targets Yarn's
 * GameRenderer.render(RenderTickCounter, boolean) / GuiRenderer.render(GpuBufferSlice)). Verified
 * via javap against the mapped 26.1.2 client jar (~/.gradle/caches/fabric-loom/26.1.2/minecraft-merged.jar):
 *   public void render(net.minecraft.client.DeltaTracker, boolean)  -- descriptor (Lnet/minecraft/client/DeltaTracker;Z)V
 *   public void render(com.mojang.blaze3d.buffers.GpuBufferSlice)   -- descriptor (Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V
 * and the bytecode of GameRenderer.render(DeltaTracker, boolean) does invoke
 * guiRenderer.render(GpuBufferSlice) directly (confirmed at the instruction calling
 * GuiRenderer.render:(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V), so this is a like-for-like
 * injection point, not a fallback.
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
        if (Minecraft.getInstance().screen instanceof FishModScreen screen) {
            screen.paintNvgOverlay();
        }
    }
}
