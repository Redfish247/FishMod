package fishmod.compat

import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import com.mojang.renderpearl.api.vertex.VertexFormat
import fishmod.utils.rendering.UiFont
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.Optional

// Draws UiRenderer's SDF batch through Mojang's GPU API so it works on OpenGL and Vulkan (26.3 renderpearl API)
object UiGpu {
    private val ATTRS = listOf("PosLocal", "HalfUv", "Radius", "ColorA", "ColorB", "Params")

    private var pipeline: RenderPipeline? = null
    private var atlas: GpuTexture? = null
    private var atlasView: GpuTextureView? = null
    private val uni: ByteBuffer = MemoryUtil.memCalloc(16)
    private const val RING = 4
    private val vbufs = arrayOfNulls<GpuBuffer>(RING)
    private val ubufs = arrayOfNulls<GpuBuffer>(RING)
    private var ring = 0

    private fun pipeline(): RenderPipeline = pipeline ?: run {
        val fb = VertexFormat.builder(0)
        for (a in ATTRS) fb.addAttribute(a, GpuFormat.RGBA32_FLOAT)
        RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("fishmod", "pipeline/ui_sdf"))
            .withVertexShader(Identifier.fromNamespaceAndPath("fishmod", "core/ui_sdf"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("fishmod", "core/ui_sdf"))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("UiView", UniformType.UNIFORM_BUFFER).withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER).build())
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .withVertexBinding(0, fb.build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build().also { pipeline = it }
    }

    private fun target(last: Any?): GpuTextureView {
        val main = Minecraft.getInstance().mainTarget
        val v = last as? GpuTextureView
        if (v != null && !v.isClosed && v.getWidth(0) == main.width && v.getHeight(0) == main.height) return v
        return main.colorTextureView ?: error("main render target has no colour view")
    }

    @JvmStatic
    fun paint(last: Any?, data: ByteBuffer, verts: Int, viewW: Float, viewH: Float, atlasPixels: ByteBuffer?, segs: IntArray, nSeg: Int) {
        val dev = RenderSystem.getDevice()
        val p = pipeline()
        val enc = dev.createCommandEncoder()
        var tex = atlas
        if (tex == null || tex.isClosed) {
            tex = dev.createTexture("fishmod ui atlas", GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST, GpuFormat.R8_UNORM, UiFont.ATLAS, UiFont.ATLAS, 1, 1)
            atlas = tex; atlasView = dev.createTextureView(tex)
            if (atlasPixels == null) UiFont.markAtlasLost()
        }
        if (atlasPixels != null) enc.writeToTexture(tex, atlasPixels, 0, 0, 0, 0, UiFont.ATLAS, UiFont.ATLAS)
        // Ring of buffers: several paints per frame (screen, overlay, popup) never overwrite each other's data
        ring = (ring + 1) % RING
        val bytes = data.remaining().toLong()
        var v = vbufs[ring]
        if (v == null || v.isClosed || v.size() < bytes) {
            v?.close()
            v = dev.createBuffer({ "fishmod ui vertices" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, Math.max(bytes * 2, 1L shl 16))
            vbufs[ring] = v
        }
        enc.writeToBuffer(v.slice(0, bytes), data)
        var u = ubufs[ring]
        if (u == null || u.isClosed) {
            u = dev.createBuffer({ "fishmod ui view" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, 16L)
            ubufs[ring] = u
        }
        uni.putFloat(0, viewW).putFloat(4, viewH)
        enc.writeToBuffer(u.slice(), uni)
        val vb = v.slice(0, bytes)
        val ub = u.slice()
        val view = target(last)
        val tw = view.getWidth(0); val th = view.getHeight(0)
        enc.createRenderPass({ "fishmod ui" }, view, Optional.empty()).use { pass ->
            pass.setPipeline(RenderSystem.getCompiledPipeline(p))
            pass.setUniform("Sampler0", atlasView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            pass.setUniform("UiView", ub)
            pass.setVertexBuffer(0, vb)
            for (i in 0 until nSeg) {
                val o = i * 6
                if (segs[o + 4] < 0) pass.disableScissor() else scissor(pass, segs, o, tw, th)
                pass.draw(segs[o + 1], 1, segs[o], 0)
            }
        }
    }

    // Backends reject scissors past the target edge; clamp to the real target size
    private fun scissor(pass: com.mojang.renderpearl.api.commands.RenderPass, s: IntArray, o: Int, tw: Int, th: Int) {
        val x0 = s[o + 2].coerceIn(0, tw); val y0 = s[o + 3].coerceIn(0, th)
        val x1 = (s[o + 2] + s[o + 4]).coerceIn(x0, tw); val y1 = (s[o + 3] + s[o + 5]).coerceIn(y0, th)
        pass.enableScissor(x0, y0, x1 - x0, y1 - y0)
    }
}
