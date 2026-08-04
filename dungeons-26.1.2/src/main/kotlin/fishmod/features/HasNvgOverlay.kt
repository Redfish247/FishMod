package fishmod.features

/** Implemented by screens that record NanoVG draw commands in extractRenderState() and need
 *  GameRendererNvgMixin to replay them after the vanilla GUI flush each frame. */
interface HasNvgOverlay {
    fun paintNvgOverlay()
}
