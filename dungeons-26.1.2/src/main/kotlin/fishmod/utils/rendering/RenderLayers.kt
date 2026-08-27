package fishmod.utils.rendering

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier

/**
 * World-overlay render layers, rebuilt on the System22 `WaypointTest` pattern: plain
 * `core/position_color` pipelines drawn straight onto the main target from
 * [LevelRenderEvents.END_MAIN][net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN]
 * with an immediate `endBatch`, instead of the old deferred `submitCustomGeometry` path (which
 * dropped geometry a flush too late and needed the elaborate DEBUG_FILLED_BOX pipeline surgery).
 *
 * - [FILL] / [FILL_ND]  — POSITION_COLOR, QUADS       (6 quads per box). ND = through walls.
 * - [LINE] / [LINE_ND]  — POSITION_COLOR, DEBUG_LINES (2 verts per edge). ND = through walls.
 *
 * All four disable depth *writes* (pure paint-on-top). The depth-tested pair reads depth with
 * LESS_THAN_OR_EQUAL so terrain occludes them; the ND pair uses ALWAYS_PASS.
 */
object RenderLayers {

    private fun pipeline(name: String, mode: VertexFormat.Mode, depthTested: Boolean): RenderPipeline =
        RenderPipelines.register(
            RenderPipeline.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(
                    DepthStencilState(if (depthTested) CompareOp.LESS_THAN_OR_EQUAL else CompareOp.ALWAYS_PASS, false)
                )
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                .withLocation(Identifier.fromNamespaceAndPath("fishmod", name))
                .build()
        )

    private fun renderType(name: String, p: RenderPipeline): RenderType =
        RenderType.create(name, RenderSetup.builder(p).setOutputTarget(OutputTarget.MAIN_TARGET).createRenderSetup())

    @JvmField val FILL: RenderType = renderType("fishmod:esp_fill", pipeline("pipeline/esp_fill", VertexFormat.Mode.QUADS, true))
    @JvmField val FILL_ND: RenderType = renderType("fishmod:esp_fill_nd", pipeline("pipeline/esp_fill_nd", VertexFormat.Mode.QUADS, false))
    @JvmField val LINE: RenderType = renderType("fishmod:esp_line", pipeline("pipeline/esp_line", VertexFormat.Mode.DEBUG_LINES, true))
    @JvmField val LINE_ND: RenderType = renderType("fishmod:esp_line_nd", pipeline("pipeline/esp_line_nd", VertexFormat.Mode.DEBUG_LINES, false))

    /** Back-compat helper used by [RenderingEvents]. */
    @JvmStatic
    fun getOutline(@Suppress("UNUSED_PARAMETER") width: Int, depthCheck: Boolean): RenderType = if (depthCheck) LINE else LINE_ND
}
