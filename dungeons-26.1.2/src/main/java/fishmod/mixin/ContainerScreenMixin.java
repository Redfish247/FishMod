package fishmod.mixin;

import fishmod.features.dungeon.LeapMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skip the vanilla chest texture while the custom Spirit Leap menu overlay is up. */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void fishmod$hideLeapMenuChest(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (LeapMenu.isActive((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }
}
