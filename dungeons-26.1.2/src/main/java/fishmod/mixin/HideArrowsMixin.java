package fishmod.mixin;

import fishmod.utils.config.values.VisualSettings;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArrowLayer.class)
public class HideArrowsMixin {

    @Inject(method = "numStuck", at=@At("TAIL"), cancellable = true)
    public void shouldRender(CallbackInfoReturnable<Integer> cir) {
        if (VisualSettings.hideArrowsInBody) {
            cir.setReturnValue(0);
        }

    }
}
