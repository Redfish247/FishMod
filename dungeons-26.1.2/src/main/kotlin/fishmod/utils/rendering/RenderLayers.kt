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
