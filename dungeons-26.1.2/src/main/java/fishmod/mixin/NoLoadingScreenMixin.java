package fishmod.mixin;

import fishmod.features.visuals.RenderOptimizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from NoFrills (GuiMixin / NoLoadingScreen)
@Mixin(Minecraft.class)
public abstract class NoLoadingScreenMixin {
    @Shadow public abstract void setScreen(Screen screen);

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void fishmod$skipLoadingScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof LevelLoadingScreen && RenderOptimizer.skipLoadingScreen((Minecraft) (Object) this)) {
            ci.cancel();
            this.setScreen(null);
        }
    }
}
