package fishmod.cosmetic;

/**
 * Duck interface stamped onto {@code EntityRenderState} (via {@code EntityRenderStateMixin}) so a
 * per-player render size can ride along on the render state. {@code EntityRendererMixin} sets it each
 * frame from {@link PlayerSize#scaleFor}; {@code PlayerEntityRendererScaleMixin} reads it inside the
 * renderer's {@code scale()} and applies a (non-uniform) matrix scale — render-only, no hitbox change.
 *
 * Lives in the cosmetic package (not {@code fishmod.mixin}) so it never gets pulled into Mixin's init.
 */
public interface ScaleHolder {
    float fishmod$getScaleX();
    float fishmod$getScaleY();
    float fishmod$getScaleZ();
    void fishmod$setScale(float x, float y, float z);
}
