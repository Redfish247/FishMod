package fishmod.mixin;

import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;

// 26.2 rewrote NameTagFeatureRenderer (no renderTranslucent); on-top nametags not ported yet
@Mixin(NameTagFeatureRenderer.class)
public class NameTagOnTopMixin {}
