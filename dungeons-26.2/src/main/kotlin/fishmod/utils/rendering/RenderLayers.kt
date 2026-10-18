package fishmod.utils.rendering

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import fishmod.utils.debug.FishDiag
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier

// 26.2 side: pipelines copy the shaders and bind groups of the vanilla debug quad pipeline (depth is reversed-Z there).
object RenderLayers {

    private fun pipeline(name: String, topology: PrimitiveTopology, depth: CompareOp = CompareOp.ALWAYS_PASS): RenderPipeline {
        val t = RenderPipelines.DEBUG_QUADS
        val b = RenderPipeline.builder()
            .withVertexShader(t.vertexShader)
            .withFragmentShader(t.fragmentShader)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState(depth, false))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(topology)
            .withLocation(Identifier.fromNamespaceAndPath("fishmod", name))
        t.bindGroupLayouts.forEach { b.withBindGroupLayout(it) }
        val p = b.build()
        try {
            val m = RenderPipelines::class.java.getDeclaredMethod("register", RenderPipeline::class.java)
            m.isAccessible = true
            m.invoke(null, p)
        } catch (e: Throwable) {
            FishDiag.fail("RenderLayers.1", "could not register pipeline $name", e)
        }
        return p
    }

    private fun renderType(name: String, p: RenderPipeline): RenderType {
        val setup = RenderSetup.builder(p).setOutputTarget(OutputTarget.MAIN_TARGET).createRenderSetup()
        val m = RenderType::class.java.getDeclaredMethod("create", String::class.java, RenderSetup::class.java)
        m.isAccessible = true
        return m.invoke(null, name, setup) as RenderType
    }

    @JvmField val FILL: RenderType = renderType("fishmod:esp_fill", pipeline("pipeline/esp_fill", PrimitiveTopology.QUADS, CompareOp.GREATER_THAN_OR_EQUAL))
    @JvmField val FILL_ND: RenderType = renderType("fishmod:esp_fill_nd", pipeline("pipeline/esp_fill_nd", PrimitiveTopology.QUADS))
    @JvmField val LINE_ND: RenderType = renderType("fishmod:esp_line_nd", pipeline("pipeline/esp_line_nd", PrimitiveTopology.DEBUG_LINES))
}
