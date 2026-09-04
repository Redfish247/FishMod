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
 * World-overlay render layers: plain
 * `core/position_color` pipelines drawn straight onto the main target from
 * [LevelRenderEvents.END_MAIN][net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN]
 * with an immediate `endBatch`, instead of the old deferred `submitCustomGeometry` path (which
 * dropped geometry a flush too late and needed the elaborate DEBUG_FILLED_BOX pipeline surgery).
 *
 * - [FILL_ND]  — POSITION_COLOR, QUADS       (6 quads per box), ALWAYS_PASS (through walls).
 * - [LINE_ND]  — POSITION_COLOR, DEBUG_LINES (2 verts per edge), ALWAYS_PASS (through walls).
 *
 * Both disable depth *writes* and use `ALWAYS_PASS` — pure paint-on-top, matching the reference's
 * `renderEsp`. Occluded (depth-tested) highlights do NOT live here anymore: they go through vanilla
 * `net.minecraft.gizmos.Gizmos` (see [RenderingEvents.GIZMO]), which the hand-rolled
 * `LESS_THAN_OR_EQUAL` pipeline never rendered correctly from the `END_MAIN` pass.
 */
object RenderLayers {

    private fun pipeline(name: String, mode: VertexFormat.Mode): RenderPipeline =
        RenderPipelines.register(
            RenderPipeline.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                .withLocation(Identifier.fromNamespaceAndPath("fishmod", name))
                .build()
        )

    private fun renderType(name: String, p: RenderPipeline): RenderType =
        RenderType.create(name, RenderSetup.builder(p).setOutputTarget(OutputTarget.MAIN_TARGET).createRenderSetup())

    @JvmField val FILL_ND: RenderType = renderType("fishmod:esp_fill_nd", pipeline("pipeline/esp_fill_nd", VertexFormat.Mode.QUADS))
    @JvmField val LINE_ND: RenderType = renderType("fishmod:esp_line_nd", pipeline("pipeline/esp_line_nd", VertexFormat.Mode.DEBUG_LINES))
}
