package fishmod.mixin;

import fishmod.cosmetic.NametagStatsHolder;
import fishmod.cosmetic.ScaleHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/** Carries a per-player non-uniform render size on the render state (see {@link ScaleHolder}), plus
 *  the resolved networth / cata lines drawn under the nametag (see {@link NametagStatsHolder}). */
@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements ScaleHolder, NametagStatsHolder {
    @Unique private float fishmod$scaleX = 1.0f;
    @Unique private float fishmod$scaleY = 1.0f;
    @Unique private float fishmod$scaleZ = 1.0f;
    @Unique private List<Component> fishmod$nametagStats = null;

    @Override public float fishmod$getScaleX() { return fishmod$scaleX; }
    @Override public float fishmod$getScaleY() { return fishmod$scaleY; }
    @Override public float fishmod$getScaleZ() { return fishmod$scaleZ; }
    @Override public void fishmod$setScale(float x, float y, float z) {
        fishmod$scaleX = x; fishmod$scaleY = y; fishmod$scaleZ = z;
    }

    @Override public List<Component> fishmod$getNametagStats() { return fishmod$nametagStats; }
    @Override public void fishmod$setNametagStats(List<Component> lines) { fishmod$nametagStats = lines; }
}
