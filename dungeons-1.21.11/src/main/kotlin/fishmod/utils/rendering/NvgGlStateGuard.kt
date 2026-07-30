package fishmod.utils.rendering

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33

/**
 * Captures every piece of raw GL state NanoVG's GL3 backend touches during a flush, then puts
 * back exactly what was there before — not "assumed defaults". NanoVG changes the bound VAO,
 * shader program, array-buffer binding, active-texture-unit-0 binding, blend/scissor/depth/
 * stencil/cull state, viewport, and unpack alignment; leaving any of these behind is the classic
 * "everything renders black/glitched after opening this screen" bug in this category of mod.
 * Always paired: [.capture] immediately before nvgBeginFrame, [.restore]
 * immediately after nvgEndFrame, every frame this runs.
 */
class NvgGlStateGuard {

    private var vao = 0
    private var program = 0
    private var arrayBuffer = 0
    private var texture2d = 0
    private var blendEnabled = false
    private var scissorEnabled = false
    private var depthEnabled = false
    private var stencilEnabled = false
    private var cullFaceEnabled = false
    private var blendSrcRgb = 0
    private var blendDstRgb = 0
    private var blendSrcAlpha = 0
    private var blendDstAlpha = 0
    private var blendEqRgb = 0
    private var blendEqAlpha = 0
    private val scissorBox = IntArray(4)
    private val viewport = IntArray(4)
    private var depthWriteMask = false
    private var unpackAlignment = 0
    private var samplerBinding = 0

    fun capture() {
        vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        texture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)

        // A sampler object bound to unit 0 (GL 3.3 sampler objects) overrides the *texture's own*
        // filtering/wrap parameters for every fetch on this unit. Minecraft's world rendering binds
        // mipmapped samplers here; NanoVG's tiny non-mipmapped font atlas / images would then be
        // sampled through that sampler's mipmap filtering, which sees a texture-incomplete object
        // and returns transparent black for every fetch — invisible text/images, unaffected solid
        // fills, and no GL error, matching exactly what we're seeing. Unbind it before drawing.
        samplerBinding = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING)
        GL33.glBindSampler(0, 0)

        blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        blendEqRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)
        blendEqAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA)

        scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox)

        depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        depthWriteMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        stencilEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
        cullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)

        // NanoVG's font-atlas texture upload needs GL_UNPACK_ALIGNMENT=1 (the atlas is a
        // single-channel bitmap with arbitrary row widths, not guaranteed 4-byte aligned).
        // We never previously *set* this before drawing — only reset it afterward — so
        // whatever Minecraft's own renderer last left it at silently corrupted every glyph
        // upload while leaving shape/fill rendering (which doesn't upload any texture)
        // completely unaffected. Capture the real prior value here so restore() can put it
        // back exactly, then force 1 immediately so NanoVG's own upload is always safe.
        unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
    }

    fun restore() {
        GL30.glBindVertexArray(vao)
        GL20.glUseProgram(program)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2d)
        GL33.glBindSampler(0, samplerBinding)

        setEnabled(GL11.GL_BLEND, blendEnabled)
        GL20.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
        GL20.glBlendEquationSeparate(blendEqRgb, blendEqAlpha)

        setEnabled(GL11.GL_SCISSOR_TEST, scissorEnabled)
        GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3])

        setEnabled(GL11.GL_DEPTH_TEST, depthEnabled)
        GL11.glDepthMask(depthWriteMask)
        setEnabled(GL11.GL_STENCIL_TEST, stencilEnabled)
        setEnabled(GL11.GL_CULL_FACE, cullFaceEnabled)

        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment)
    }

    companion object {
        private fun setEnabled(cap: Int, enabled: Boolean) {
            if (enabled) GL11.glEnable(cap) else GL11.glDisable(cap)
        }
    }
}
