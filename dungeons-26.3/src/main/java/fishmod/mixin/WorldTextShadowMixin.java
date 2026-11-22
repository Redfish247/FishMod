package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SubmitNodeCollection.class)
public abstract class WorldTextShadowMixin {

    @ModifyArg(method = "nameTag", require = 0, index = 3, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"))
    private static boolean fishmod$shadowNameTag(boolean shadow) {
        return shadow || FishSettings.worldTextShadow(true);
    }
}
