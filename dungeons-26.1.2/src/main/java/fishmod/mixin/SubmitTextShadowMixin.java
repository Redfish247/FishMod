package fishmod.mixin;

import fishmod.utils.SignTextState;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Text Shadow for floating world text, applied at submit time so signs can be skipped
@Mixin(SubmitNodeCollection.class)
public abstract class SubmitTextShadowMixin {

    @ModifyVariable(method = "submitText", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private boolean fishmod$shadow(boolean shadow) {
        return shadow || (!SignTextState.inSign && FishSettings.worldTextShadow(false));
    }
}
