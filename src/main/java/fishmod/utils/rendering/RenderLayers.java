package fishmod.utils.rendering;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public class RenderLayers {

    // Depth-tested layers: occluded by terrain (only drawn where the box is actually visible).
    public static final RenderType FILLED_LAYER        = RenderType.create("fishmod_filled",    RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).createRenderSetup());
    public static final RenderType FILLED_ENTITY_LAYER = RenderType.create("fishmod_filled_en", RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).createRenderSetup());

    // Through-walls layers: a clone of the base pipeline with depth testing disabled, so boxes/lines
    // (e.g. the M7 lever waypoints) show through terrain. The vanilla DEBUG_FILLED_BOX / LINES
    // pipelines depth-test, so reusing them here would let walls occlude the highlight — which is
    // exactly the "doesn't render through walls" bug. We rebuild the pipeline from its own snippets
    // and override only the depth-test function.
    public static final RenderType FILLED_LAYER_NO_DEPTH = noDepth(RenderPipelines.DEBUG_FILLED_BOX, "fishmod/filled_no_depth", "fishmod_filled_nd");

    private static final RenderType OUTLINE_LAYER          = RenderType.create("fishmod_lines", RenderSetup.builder(RenderPipelines.LINES).createRenderSetup());
    private static final RenderType OUTLINE_LAYER_NO_DEPTH = noDepth(RenderPipelines.LINES, "fishmod/lines_no_depth", "fishmod_lines_nd");

    /**
     * Builds a render layer whose pipeline is {@code base} with depth testing turned off, so geometry
     * drawn through it renders on top of (through) the world instead of being occluded by it.
     *
     * <p>26.1.2's {@link RenderPipeline} is a transitional shape: {@link com.mojang.blaze3d.pipeline.ColorTargetState}/
     * {@link DepthStencilState} already exist as composite objects, but samplers/uniforms/vertex format
     * are still flat properties (no {@code BindGroupLayout}/per-buffer vertex bindings yet — those are
     * 26.2-only). Don't reuse the 26.2 branch's version of this file as-is; the Builder API genuinely
     * differs between the two versions, not just renamed.
     */
    private static RenderType noDepth(RenderPipeline base, String location, String layerName) {
        DepthStencilState baseDepth = base.getDepthStencilState();
        DepthStencilState noDepthTest = new DepthStencilState(
                CompareOp.ALWAYS_PASS, baseDepth.writeDepth(), baseDepth.depthBiasScaleFactor(), baseDepth.depthBiasConstant());

        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(base.getVertexShader())
                .withFragmentShader(base.getFragmentShader())
                .withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
                .withCull(base.isCull())
                .withPolygonMode(base.getPolygonMode())
                .withColorTargetState(base.getColorTargetState())
                // The whole point of this layer: render through walls.
                .withDepthStencilState(noDepthTest);

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

        return RenderType.create(layerName, RenderSetup.builder(builder.build()).createRenderSetup());
    }

    public static RenderType getOutline(int width, boolean depthCheck) {
        return depthCheck ? OUTLINE_LAYER : OUTLINE_LAYER_NO_DEPTH;
    }
}
