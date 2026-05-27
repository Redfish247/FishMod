package fishmod.utils.rendering;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

public class RenderLayers {

    public static final RenderLayer FILLED_LAYER          = RenderLayer.of("fishmod_filled",    RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).build());
    public static final RenderLayer FILLED_LAYER_NO_DEPTH = RenderLayer.of("fishmod_filled_nd", RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).build());
    public static final RenderLayer FILLED_ENTITY_LAYER   = RenderLayer.of("fishmod_filled_en", RenderSetup.builder(RenderPipelines.DEBUG_FILLED_BOX).build());

    private static final RenderLayer OUTLINE_LAYER          = RenderLayer.of("fishmod_lines",    RenderSetup.builder(RenderPipelines.LINES).build());
    private static final RenderLayer OUTLINE_LAYER_NO_DEPTH = RenderLayer.of("fishmod_lines_nd", RenderSetup.builder(RenderPipelines.LINES).build());

    public static RenderLayer getOutline(int width, boolean depthCheck) {
        return depthCheck ? OUTLINE_LAYER : OUTLINE_LAYER_NO_DEPTH;
    }
}
