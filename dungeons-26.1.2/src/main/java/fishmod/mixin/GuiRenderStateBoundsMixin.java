package fishmod.mixin;

import fishmod.utils.rendering.FishHudLayer;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Records screen bounds of mod HUD layers and the tab list (for Hide HUDs Under Tab)
@Mixin(GuiRenderState.class)
public class GuiRenderStateBoundsMixin {
    @Inject(method = "addGuiElement", at = @At("HEAD"))
    private void fishmod$el(GuiElementRenderState s, CallbackInfo ci) { if (FishHudLayer.inModHud || FishHudLayer.inTab) FishHudLayer.onElement(s.bounds()); }

    @Inject(method = "addText", at = @At("HEAD"))
    private void fishmod$text(GuiTextRenderState s, CallbackInfo ci) { if (FishHudLayer.inModHud || FishHudLayer.inTab) FishHudLayer.onElement(s.bounds()); }

    @Inject(method = "addItem", at = @At("HEAD"))
    private void fishmod$item(GuiItemRenderState s, CallbackInfo ci) { if (FishHudLayer.inModHud || FishHudLayer.inTab) FishHudLayer.onElement(s.bounds()); }

    @Inject(method = "addBlitToCurrentLayer", at = @At("HEAD"))
    private void fishmod$blit(BlitRenderState s, CallbackInfo ci) { if (FishHudLayer.inModHud || FishHudLayer.inTab) FishHudLayer.onElement(s.bounds()); }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
    private void fishmod$pip(PictureInPictureRenderState s, CallbackInfo ci) { if (FishHudLayer.inModHud || FishHudLayer.inTab) FishHudLayer.onElement(s.bounds()); }
}
