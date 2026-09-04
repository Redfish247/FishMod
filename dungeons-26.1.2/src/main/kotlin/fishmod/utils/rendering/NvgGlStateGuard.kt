package fishmod.utils.rendering

import com.mojang.blaze3d.opengl.GlStateManager
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL33

/** Captures raw GL state NanoVG's GL3 backend touches and restores it after each flush, to avoid the classic "everything renders black after this screen" bug. */
class NvgGlStateGuard {

    private var vao = 0
    private var program = 0
    private var arrayBuffer = 0
    private var activeTexture = 0
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
    private var frameBuffer = 0
    private var frameBufferLogged = false

    // GL33 sampler objects can be missing on old contexts; a missing capability skips only that step
    private var samplerObjectsSupported = true

    /** Never throws — a partially-captured state is safe (restore() just no-ops the missing bits). */
    fun capture() {
        try {
            // capture whatever framebuffer is bound; MC's real target isn't always FBO 0, forcing 0 broke it
            frameBuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
            if (!frameBufferLogged) {
                frameBufferLogged = true
                fishmod.utils.debug.Debug.LOGGER.info("[NanoVG] framebuffer bound at paint time: {}", frameBuffer)
            }

            vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
            arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            texture2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)

            // Minecraft binds a mipmapped GL 3.3 sampler to unit 0, which makes NanoVG's non-mipmapped font atlas sample as transparent black; unbind before drawing.
            if (samplerObjectsSupported) {
                try {
                    samplerBinding = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING)
                    GL33.glBindSampler(0, 0)
                } catch (t: Throwable) {
                    samplerObjectsSupported = false
                    fishmod.utils.debug.Debug.LOGGER.warn("[NanoVG] GL33 sampler objects unsupported - skipping sampler unbind", t)
                }
            }

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

            // NanoVG's font atlas upload needs GL_UNPACK_ALIGNMENT=1 (arbitrary row widths); capture the prior value, then force 1 for the upload.
            unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
        } catch (t: Throwable) {
            fishmod.utils.debug.Debug.LOGGER.error("[NanoVG] NvgGlStateGuard.capture() failed", t)
        }
    }

    /** Never throws — best-effort restore; a failed step just leaves that bit of GL state as NanoVG left it. */
    fun restore() {
        try {
            // framebuffer + program via GlStateManager so blaze3d's state cache stays in sync
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer)
            GlStateManager._glUseProgram(program)
            // VAO + array buffer: no cache-syncing equivalent on the public surface, leave raw.
            GL30.glBindVertexArray(vao)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer)
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2d)
            if (samplerObjectsSupported) {
                try { GL33.glBindSampler(0, samplerBinding) } catch (_: Throwable) { samplerObjectsSupported = false }
            }

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

            // Last: put the active texture unit back (capture()/restore() force GL_TEXTURE0 above).
            GL13.glActiveTexture(activeTexture)
        } catch (t: Throwable) {
            fishmod.utils.debug.Debug.LOGGER.error("[NanoVG] NvgGlStateGuard.restore() failed", t)
        }
    }

    companion object {
        private fun setEnabled(cap: Int, enabled: Boolean) {
            if (enabled) GL11.glEnable(cap) else GL11.glDisable(cap)
        }
    }
}
