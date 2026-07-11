package fishmod.utils.rendering;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

public class RenderLayers {

    // Depth-tested layers: occluded by terrain (only drawn where the box is actually visible).
    public static final RenderLayer FILLED_LAYER        = RenderLayer.of("fishmod_filled",    RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).build());
    public static final RenderLayer FILLED_ENTITY_LAYER = RenderLayer.of("fishmod_filled_en", RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).build());

    // Through-walls layers: a clone of the base pipeline with depth testing disabled, so boxes/lines
    // (e.g. the M7 lever waypoints) show through terrain. The vanilla DEBUG_FILLED_BOX / LINES
    // pipelines depth-test, so reusing them here would let walls occlude the highlight — which is
    // exactly the "doesn't render through walls" bug. We rebuild the pipeline from its own snippets
    // and override only the depth-test function.
    public static final RenderLayer FILLED_LAYER_NO_DEPTH = noDepth(RenderPipelines.DEBUG_FILLED_BOX, "fishmod/filled_no_depth", "fishmod_filled_nd");

    private static final RenderLayer OUTLINE_LAYER          = RenderLayer.of("fishmod_lines", RenderSetup.builder(RenderPipelines.LINES).build());
    private static final RenderLayer OUTLINE_LAYER_NO_DEPTH = noDepth(RenderPipelines.LINES, "fishmod/lines_no_depth", "fishmod_lines_nd");

    /**
     * Builds a render layer whose pipeline is {@code base} with depth testing turned off, so geometry
     * drawn through it renders on top of (through) the world instead of being occluded by it.
     *
     * <p>A built {@link RenderPipeline} does <em>not</em> retain the {@link RenderPipeline.Snippet}s it
     * was assembled from (they are consumed at build time), so there is no snippet list to re-derive a
     * builder from. Instead we start from an empty builder and copy every property off the base via its
     * public getters, overriding only the depth-test function (and location/name). This is mapping-stable
     * across Minecraft versions — it relies on the public {@code RenderPipeline} API rather than the
     * private internals, which is what broke the previous reflective approach on 1.21.11.
     */
    private static RenderLayer noDepth(RenderPipeline base, String location, String layerName) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(base.getVertexShader())
                .withFragmentShader(base.getFragmentShader())
                .withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
                .withCull(base.isCull())
                .withColorWrite(base.isWriteColor(), base.isWriteAlpha())
                .withDepthWrite(base.isWriteDepth())
                .withColorLogic(base.getColorLogic())
                .withPolygonMode(base.getPolygonMode())
                .withDepthBias(base.getDepthBiasScaleFactor(), base.getDepthBiasConstant())
                // The whole point of this layer: render through walls.
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);

        BlendFunction blend = base.getBlendFunction().orElse(null);
        if (blend != null) {
            builder.withBlend(blend);
        } else {
            builder.withoutBlend();
        }

        // Copy shader defines: bare flags directly, keyed values numerically (the builder only exposes
        // int/float keyed defines). The base debug/line pipelines carry no defines, so this is normally
        // a no-op — it just keeps the copy faithful if that ever changes.
        base.getShaderDefines().flags().forEach(builder::withShaderDefine);
        base.getShaderDefines().values().forEach((name, value) -> {
            try {
                builder.withShaderDefine(name, Integer.parseInt(value));
            } catch (NumberFormatException notInt) {
                try {
                    builder.withShaderDefine(name, Float.parseFloat(value));
                } catch (NumberFormatException notFloat) {
                    // non-numeric define can't be expressed via the builder API; skip it
                }
            }
        });

        // Copy samplers and uniforms so the shader still has everything it expects.
        base.getSamplers().forEach(builder::withSampler);
        for (RenderPipeline.UniformDescription uniform : base.getUniforms()) {
            if (uniform.type() == UniformType.TEXEL_BUFFER) {
                builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
            } else {
                builder.withUniform(uniform.name(), uniform.type());
            }
        }

        return RenderLayer.of(layerName, RenderSetup.builder(builder.build()).build());
    }

    public static RenderLayer getOutline(int width, boolean depthCheck) {
        return depthCheck ? OUTLINE_LAYER : OUTLINE_LAYER_NO_DEPTH;
    }
}
