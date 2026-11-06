package fishmod.mixin;

import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;

// 26.3: NameTagFeatureRenderer is gone and nametags share TextFeatureRenderer with signs; the submit-time SubmitTextShadowMixin covers world text, so this is an empty stub.
@Mixin(TextFeatureRenderer.class)
public abstract class WorldTextShadowMixin {
}
