package fishmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fishmod.features.visuals.RenderOptimizer;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?} else {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
*///?}
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    //? if <26.2 {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void fishmod$hideFireOverlay(PoseStack poseStack, MultiBufferSource bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void fishmod$hideFireOverlay(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
    *///?}
        if (RenderOptimizer.shouldDisableFireOverlay()) ci.cancel();
    }
}
