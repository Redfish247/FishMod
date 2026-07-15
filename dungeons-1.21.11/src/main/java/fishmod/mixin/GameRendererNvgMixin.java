package fishmod.mixin;

import fishmod.features.FishModScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
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
 */
@Mixin(GameRenderer.class)
public class GameRendererNvgMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            shift = At.Shift.AFTER
        )
    )
    private void fishmod$paintNvgOverlay(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof FishModScreen screen) {
            screen.paintNvgOverlay();
        }
    }
}
