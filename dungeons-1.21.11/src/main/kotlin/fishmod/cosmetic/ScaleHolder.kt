package fishmod.cosmetic

/**
 * Duck interface stamped onto `EntityRenderState` (via `EntityRenderStateMixin`) so a
 * per-player render size can ride along on the render state. `EntityRendererMixin` sets it each
 * frame from [PlayerSize.scaleFor]; `PlayerEntityRendererScaleMixin` reads it inside the
 * renderer's `scale()` and applies a (non-uniform) matrix scale — render-only, no hitbox change.
 *
 * Lives in the cosmetic package (not `fishmod.mixin`) so it never gets pulled into Mixin's init.
 */
interface ScaleHolder {
    fun `fishmod$getScaleX`(): Float
    fun `fishmod$getScaleY`(): Float
    fun `fishmod$getScaleZ`(): Float
    fun `fishmod$setScale`(x: Float, y: Float, z: Float)
}
