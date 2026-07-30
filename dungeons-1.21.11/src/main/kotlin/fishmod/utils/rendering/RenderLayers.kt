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

    // Through-walls layers: a clone of the base pipeline with depth testing disabled, so boxes/lines
    // (e.g. the M7 lever waypoints) show through terrain. The vanilla DEBUG_FILLED_BOX / LINES
    // pipelines depth-test, so reusing them here would let walls occlude the highlight — which is
    // exactly the "doesn't render through walls" bug. We rebuild the pipeline from its own snippets
    // and override only the depth-test function.
    @JvmField
    val FILLED_LAYER_NO_DEPTH: RenderLayer = noDepth(RenderPipelines.DEBUG_FILLED_BOX, "fishmod/filled_no_depth", "fishmod_filled_nd")

    private val OUTLINE_LAYER: RenderLayer = RenderLayer.of("fishmod_lines", RenderSetup.builder(RenderPipelines.LINES).build())
    private val OUTLINE_LAYER_NO_DEPTH: RenderLayer = noDepth(RenderPipelines.LINES, "fishmod/lines_no_depth", "fishmod_lines_nd")

    /**
     * Builds a render layer whose pipeline is `base` with depth testing turned off, so geometry
     * drawn through it renders on top of (through) the world instead of being occluded by it.
     *
     * A built [RenderPipeline] does *not* retain the [RenderPipeline.Snippet]s it
     * was assembled from (they are consumed at build time), so there is no snippet list to re-derive a
     * builder from. Instead we start from an empty builder and copy every property off the base via its
     * public getters, overriding only the depth-test function (and location/name). This is mapping-stable
     * across Minecraft versions — it relies on the public `RenderPipeline` API rather than the
     * private internals, which is what broke the previous reflective approach on 1.21.11.
     */
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
            // The whole point of this layer: render through walls.
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)

        val blend: BlendFunction? = base.blendFunction.orElse(null)
        if (blend != null) {
            builder.withBlend(blend)
        } else {
            builder.withoutBlend()
        }

        // Copy shader defines: bare flags directly, keyed values numerically (the builder only exposes
        // int/float keyed defines). The base debug/line pipelines carry no defines, so this is normally
        // a no-op — it just keeps the copy faithful if that ever changes.
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

        // Copy samplers and uniforms so the shader still has everything it expects.
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
