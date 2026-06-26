package fishmod.utils.rendering;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

import java.lang.reflect.Field;
import java.util.List;

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
     * <p>A built {@link RenderPipeline} keeps the list of {@link RenderPipeline.Snippet}s it was made
     * from; {@code RenderPipeline.builder(Snippet...)} is the only way to re-derive a builder that
     * carries the base's shaders/vertex-format/blend. We grab that snippet list reflectively (its
     * field is private and unmapped at runtime), then override just the depth-test function.
     */
    private static RenderLayer noDepth(RenderPipeline base, String location, String layerName) {
        RenderPipeline.Snippet[] snippets = null;
        for (Field f : base.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object value = f.get(base);
                if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof RenderPipeline.Snippet) {
                    snippets = list.toArray(new RenderPipeline.Snippet[0]);
                    break;
                }
            } catch (ReflectiveOperationException ignored) {
                // try the next field
            }
        }
        if (snippets == null) {
            throw new IllegalStateException("Could not find pipeline snippets to derive a no-depth layer for " + location);
        }
        RenderPipeline pipeline = RenderPipeline.builder(snippets)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withLocation(location)
                .build();
        return RenderLayer.of(layerName, RenderSetup.builder(pipeline).build());
    }

    public static RenderLayer getOutline(int width, boolean depthCheck) {
        return depthCheck ? OUTLINE_LAYER : OUTLINE_LAYER_NO_DEPTH;
    }
}
