package fishmod.utils.rendering

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gl.UniformType
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderSetup

object RenderLayers {

    // Depth-tested layers: occluded by terrain (only drawn where the box is actually visible).
    @JvmField
    val FILLED_LAYER: RenderLayer = RenderLayer.of("fishmod_filled", RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).build())
    @JvmField
    val FILLED_ENTITY_LAYER: RenderLayer = RenderLayer.of("fishmod_filled_en", RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).build())

    // Through-walls layers: base pipeline rebuilt with depth testing disabled so boxes/lines aren't occluded by terrain.
    @JvmField
    val FILLED_LAYER_NO_DEPTH: RenderLayer = noDepth(RenderPipelines.DEBUG_FILLED_BOX, "fishmod/filled_no_depth", "fishmod_filled_nd")

    private val OUTLINE_LAYER: RenderLayer = RenderLayer.of("fishmod_lines", RenderSetup.builder(RenderPipelines.LINES).build())
    private val OUTLINE_LAYER_NO_DEPTH: RenderLayer = noDepth(RenderPipelines.LINES, "fishmod/lines_no_depth", "fishmod_lines_nd")

    /** Rebuilds `base` with depth testing off; copies fields via public getters since a built RenderPipeline doesn't retain its snippets (reflection broke on 1.21.11). */
    private fun noDepth(base: RenderPipeline, location: String, layerName: String): RenderLayer {
        val builder = RenderPipeline.builder()
            .withLocation(location)
            .withVertexShader(base.vertexShader)
            .withFragmentShader(base.fragmentShader)
            .withVertexFormat(base.vertexFormat, base.vertexFormatMode)
            .withCull(base.isCull)
            .withColorWrite(base.isWriteColor, base.isWriteAlpha)
            .withDepthWrite(base.isWriteDepth)
            .withColorLogic(base.colorLogic)
            .withPolygonMode(base.polygonMode)
            .withDepthBias(base.depthBiasScaleFactor, base.depthBiasConstant)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)

        val blend: BlendFunction? = base.blendFunction.orElse(null)
        if (blend != null) {
            builder.withBlend(blend)
        } else {
            builder.withoutBlend()
        }

        // Copy shader defines (builder only supports int/float keyed defines); base pipelines carry none, so normally a no-op.
        base.shaderDefines.flags().forEach { builder.withShaderDefine(it) }
        base.shaderDefines.values().forEach { name, value ->
            try {
                builder.withShaderDefine(name, Integer.parseInt(value))
            } catch (notInt: NumberFormatException) {
                try {
                    builder.withShaderDefine(name, java.lang.Float.parseFloat(value))
                } catch (notFloat: NumberFormatException) {
                    // non-numeric define can't be expressed via the builder API; skip it
                }
            }
        }

        base.samplers.forEach { builder.withSampler(it) }
        for (uniform in base.uniforms) {
            if (uniform.type() == UniformType.TEXEL_BUFFER) {
                builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat())
            } else {
                builder.withUniform(uniform.name(), uniform.type())
            }
        }

        return RenderLayer.of(layerName, RenderSetup.builder(builder.build()).build())
    }

    @JvmStatic
    fun getOutline(width: Int, depthCheck: Boolean): RenderLayer {
        return if (depthCheck) OUTLINE_LAYER else OUTLINE_LAYER_NO_DEPTH
    }
}
