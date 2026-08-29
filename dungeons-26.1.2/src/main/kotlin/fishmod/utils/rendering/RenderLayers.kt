package fishmod.utils.rendering

import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType

object RenderLayers {

    // Depth-tested layers: occluded by terrain (only drawn where the box is actually visible).
    //
    // These must NOT bare-wrap the vanilla RenderPipelines.DEBUG_FILLED_BOX/LINES singletons via
    // RenderType.create(...) directly. Multiple fishmod RenderTypes (this file's *_LAYER and
    // *_LAYER_NO_DEPTH) would then all reference the exact same shared RenderPipeline instance,
    // and the no-depth variants prove that identity matters here: they only work because
    // noDepth() rebuilds a genuinely distinct RenderPipeline object (new withLocation(...)) rather
    // than reusing vanilla's static instance. A bare RenderType.create(vanillaPipeline) wrapper
    // rendered nothing in-world for the depth-tested box (through walls it worked fine once
    // depth-testing was disabled), consistent with the renderer's pipeline-switch/state-rebind
    // logic keying off pipeline identity and skipping a fresh depth-attachment bind when it
    // thinks nothing changed. Rebuilding (withDepth(), mirroring noDepth() but preserving the
    // original DepthStencilState instead of forcing ALWAYS_PASS) gives every fishmod RenderType
    // its own pipeline identity, matching the one pattern already proven to work.
    @JvmField
    val FILLED_LAYER: RenderType = withDepth(RenderPipelines.DEBUG_FILLED_BOX, "fishmod/filled", "fishmod_filled")
    @JvmField
    val FILLED_ENTITY_LAYER: RenderType = withDepth(RenderPipelines.DEBUG_FILLED_BOX, "fishmod/filled_en", "fishmod_filled_en")

    // Through-walls layers: clones of the base pipeline with depth testing disabled, since the
    // vanilla DEBUG_FILLED_BOX/LINES pipelines depth-test and would let walls occlude the highlight.
    @JvmField
    val FILLED_LAYER_NO_DEPTH: RenderType = noDepth(RenderPipelines.DEBUG_FILLED_BOX, "fishmod/filled_no_depth", "fishmod_filled_nd")

    private val OUTLINE_LAYER: RenderType = withDepth(RenderPipelines.LINES, "fishmod/lines", "fishmod_lines")
    private val OUTLINE_LAYER_NO_DEPTH: RenderType = noDepth(RenderPipelines.LINES, "fishmod/lines_no_depth", "fishmod_lines_nd")

    /** 26.1.2's RenderPipeline builder API is transitional (flat samplers/uniforms/vertex format, no BindGroupLayout yet) — don't reuse the 26.2 branch's version of this function as-is. */
    private fun rebuild(base: RenderPipeline, location: String, layerName: String, depth: DepthStencilState): RenderType {
        val builder = RenderPipeline.builder()
            .withLocation(location)
            .withVertexShader(base.vertexShader)
            .withFragmentShader(base.fragmentShader)
            .withVertexFormat(base.vertexFormat, base.vertexFormatMode)
            .withCull(base.isCull)
            .withPolygonMode(base.polygonMode)
            .withColorTargetState(base.colorTargetState)
            .withDepthStencilState(depth)

        // Builder only exposes int/float keyed defines; base pipelines carry none today so this is
        // normally a no-op, kept for fidelity if that changes.
        base.shaderDefines.flags().forEach { builder.withShaderDefine(it) }
        base.shaderDefines.values().forEach { name, value ->
            try {
                builder.withShaderDefine(name, value.toInt())
            } catch (notInt: NumberFormatException) {
                try {
                    builder.withShaderDefine(name, value.toFloat())
                } catch (notFloat: NumberFormatException) {
                    // non-numeric define can't be expressed via the builder API; skip it
                }
            }
        }

        base.samplers.forEach { builder.withSampler(it) }
        for (uniform: RenderPipeline.UniformDescription in base.uniforms) {
            if (uniform.type() == UniformType.TEXEL_BUFFER) {
                builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat()!!)
            } else {
                builder.withUniform(uniform.name(), uniform.type())
            }
        }

        return RenderType.create(layerName, RenderSetup.builder(builder.build()).createRenderSetup())
    }

    // writeDepth is forced false (not baseDepth.writeDepth(), which is true on the vanilla debug
    // pipelines): an ALWAYS_PASS layer that still WRITES depth leaves whatever it drew last as the
    // new depth-buffer value at those pixels, so later draws in the same frame — including this
    // same RenderType's own other faces/doors, or the next frame's terrain re-render — start
    // fighting over which write wins, seen as the highlight intermittently vanishing/reappearing
    // through walls. A pure paint-on-top overlay must neither read nor write depth.
    private fun noDepth(base: RenderPipeline, location: String, layerName: String): RenderType {
        val baseDepth = base.depthStencilState!!
        val noDepthTest = DepthStencilState(
            CompareOp.ALWAYS_PASS, false, baseDepth.depthBiasScaleFactor(), baseDepth.depthBiasConstant()
        )
        return rebuild(base, location, layerName, noDepthTest)
    }

    /** Sibling of [noDepth]: rebuilds `base` into its own distinct RenderPipeline object (so this
     * RenderType isn't sharing a vanilla singleton's pipeline identity with any other RenderType),
     * while preserving the base pipeline's own depth-tested DepthStencilState unchanged. */
    private fun withDepth(base: RenderPipeline, location: String, layerName: String): RenderType {
        return rebuild(base, location, layerName, base.depthStencilState!!)
    }

    @JvmStatic
    fun getOutline(width: Int, depthCheck: Boolean): RenderType {
        return if (depthCheck) OUTLINE_LAYER else OUTLINE_LAYER_NO_DEPTH
    }
}
