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
