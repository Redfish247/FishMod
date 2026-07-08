package fishmod.mixin;

import fishmod.cosmetic.ScaleHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries a per-player non-uniform render size on the render state (see {@link ScaleHolder}). */
@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements ScaleHolder {
    @Unique private float fishmod$scaleX = 1.0f;
    @Unique private float fishmod$scaleY = 1.0f;
    @Unique private float fishmod$scaleZ = 1.0f;

    @Override public float fishmod$getScaleX() { return fishmod$scaleX; }
    @Override public float fishmod$getScaleY() { return fishmod$scaleY; }
    @Override public float fishmod$getScaleZ() { return fishmod$scaleZ; }
    @Override public void fishmod$setScale(float x, float y, float z) {
        fishmod$scaleX = x; fishmod$scaleY = y; fishmod$scaleZ = z;
    }
}
