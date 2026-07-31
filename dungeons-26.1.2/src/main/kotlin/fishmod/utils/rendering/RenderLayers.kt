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
    @JvmField
    val FILLED_LAYER: RenderType = RenderType.create("fishmod_filled", RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).createRenderSetup())
    @JvmField
    val FILLED_ENTITY_LAYER: RenderType = RenderType.create("fishmod_filled_en", RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).createRenderSetup())

    // Through-walls layers: clones of the base pipeline with depth testing disabled, since the
    // vanilla DEBUG_FILLED_BOX/LINES pipelines depth-test and would let walls occlude the highlight.
    @JvmField
    val FILLED_LAYER_NO_DEPTH: RenderType = noDepth(RenderPipelines.DEBUG_FILLED_BOX, "fishmod/filled_no_depth", "fishmod_filled_nd")

    private val OUTLINE_LAYER: RenderType = RenderType.create("fishmod_lines", RenderSetup.builder(RenderPipelines.LINES).createRenderSetup())
    private val OUTLINE_LAYER_NO_DEPTH: RenderType = noDepth(RenderPipelines.LINES, "fishmod/lines_no_depth", "fishmod_lines_nd")

    /** 26.1.2's RenderPipeline builder API is transitional (flat samplers/uniforms/vertex format, no BindGroupLayout yet) — don't reuse the 26.2 branch's version of this function as-is. */
    private fun noDepth(base: RenderPipeline, location: String, layerName: String): RenderType {
        val baseDepth = base.depthStencilState!!
        val noDepthTest = DepthStencilState(
            CompareOp.ALWAYS_PASS, baseDepth.writeDepth(), baseDepth.depthBiasScaleFactor(), baseDepth.depthBiasConstant()
        )

        val builder = RenderPipeline.builder()
            .withLocation(location)
            .withVertexShader(base.vertexShader)
            .withFragmentShader(base.fragmentShader)
            .withVertexFormat(base.vertexFormat, base.vertexFormatMode)
            .withCull(base.isCull)
            .withPolygonMode(base.polygonMode)
            .withColorTargetState(base.colorTargetState)
            .withDepthStencilState(noDepthTest)

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

    @JvmStatic
    fun getOutline(width: Int, depthCheck: Boolean): RenderType {
        return if (depthCheck) OUTLINE_LAYER else OUTLINE_LAYER_NO_DEPTH
    }
}
