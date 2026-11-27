package fishmod.utils.rendering
//? if <26.2 {

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

object FishRenderLayers {

    private fun pipeline(name: String, mode: VertexFormat.Mode, depth: CompareOp = CompareOp.ALWAYS_PASS): RenderPipeline =
        RenderPipelines.register(
            RenderPipeline.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(DepthStencilState(depth, false))
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                .withLocation(Identifier.fromNamespaceAndPath("fishmod", name))
                .build()
        )

    private fun renderType(name: String, p: RenderPipeline): RenderType =
        RenderType.create(name, RenderSetup.builder(p).setOutputTarget(OutputTarget.MAIN_TARGET).createRenderSetup())

    @JvmField val FILL: RenderType = renderType("fishmod:esp_fill", pipeline("pipeline/esp_fill", VertexFormat.Mode.QUADS, CompareOp.LESS_THAN_OR_EQUAL))
    @JvmField val FILL_ND: RenderType = renderType("fishmod:esp_fill_nd", pipeline("pipeline/esp_fill_nd", VertexFormat.Mode.QUADS))
    @JvmField val LINE_ND: RenderType = renderType("fishmod:esp_line_nd", pipeline("pipeline/esp_line_nd", VertexFormat.Mode.DEBUG_LINES))
}
//?} else if <26.3 {

/*import com.mojang.blaze3d.PrimitiveTopology
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

object FishRenderLayers {

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
            FishDiag.fail("FishRenderLayers.1", "could not register pipeline $name", e)
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
*///?} else {

/*import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.ShaderType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import fishmod.utils.debug.FishDiag
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier

object FishRenderLayers {

    private fun pipeline(name: String, topology: PrimitiveTopology, depth: CompareOp = CompareOp.ALWAYS_PASS): RenderPipeline {
        val t = RenderPipelines.DEBUG_QUADS
        val b = RenderPipeline.builder()
            .withVertexShader(t.shaders[ShaderType.VERTEX]!!)
            .withFragmentShader(t.shaders[ShaderType.FRAGMENT]!!)
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
            FishDiag.fail("FishRenderLayers.1", "could not register pipeline $name", e)
        }
        return p
    }

    private fun renderType(name: String, p: RenderPipeline): RenderType {
        val setup = RenderSetup.builder(p).createRenderSetup()
        val m = RenderType::class.java.getDeclaredMethod("create", String::class.java, RenderSetup::class.java)
        m.isAccessible = true
        return m.invoke(null, name, setup) as RenderType
    }

    @JvmField val FILL: RenderType = renderType("fishmod:esp_fill", pipeline("pipeline/esp_fill", PrimitiveTopology.QUADS, CompareOp.GREATER_THAN_OR_EQUAL))
    @JvmField val FILL_ND: RenderType = renderType("fishmod:esp_fill_nd", pipeline("pipeline/esp_fill_nd", PrimitiveTopology.QUADS))
    @JvmField val LINE_ND: RenderType = renderType("fishmod:esp_line_nd", pipeline("pipeline/esp_line_nd", PrimitiveTopology.DEBUG_LINES))
}
*///?}
