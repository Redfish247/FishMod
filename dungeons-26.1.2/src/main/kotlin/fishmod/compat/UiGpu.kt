package fishmod.compat

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexFormatElement
import fishmod.utils.rendering.UiFont
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.Optional
import java.util.OptionalInt

// Draws UiRenderer's SDF batch through Mojang's GPU API so it works on any graphics backend
object UiGpu {
    private val ATTRS = listOf("PosLocal", "HalfUv", "Radius", "ColorA", "ColorB", "Params")

    private var pipeline: RenderPipeline? = null
    private var vbuf: GpuBuffer? = null
    private var ubuf: GpuBuffer? = null
    private var atlas: GpuTexture? = null
    private var atlasView: GpuTextureView? = null
    private val uni: ByteBuffer = MemoryUtil.memCalloc(16)

    // 26.1.2 vertex elements are a global id table; take free high ids for our six vec4s
    private val elements by lazy { vec4Elements() }

    private fun vec4Elements(): List<VertexFormatElement> {
        val out = ArrayList<VertexFormatElement>()
        var id = VertexFormatElement.MAX_COUNT - 1
        while (out.size < ATTRS.size && id >= 0) {
            val taken = runCatching { VertexFormatElement.byId(id) }.getOrNull() != null
            if (!taken) out += VertexFormatElement.register(id, 0, VertexFormatElement.Type.FLOAT, false, 4)
            id--
        }
        check(out.size == ATTRS.size) { "no free vertex element ids for UI format" }
        return out
    }

    private fun pipeline(): RenderPipeline = pipeline ?: run {
        val fmt = VertexFormat.builder().also { b -> elements.forEachIndexed { i, e -> b.add(ATTRS[i], e) } }.build()
        RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("fishmod", "pipeline/ui_sdf"))
            .withVertexShader(Identifier.fromNamespaceAndPath("fishmod", "core/ui_sdf"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("fishmod", "core/ui_sdf"))
            .withSampler("Sampler0")
            .withUniform("UiView", UniformType.UNIFORM_BUFFER)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .withVertexFormat(fmt, VertexFormat.Mode.TRIANGLES)
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
            tex = dev.createTexture("fishmod ui atlas", GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST, TextureFormat.RED8, UiFont.ATLAS, UiFont.ATLAS, 1, 1)
            atlas = tex; atlasView = dev.createTextureView(tex)
            if (atlasPixels == null) UiFont.markAtlasLost()
        }
        if (atlasPixels != null) enc.writeToTexture(tex, atlasPixels, NativeImage.Format.LUMINANCE, 0, 0, 0, 0, UiFont.ATLAS, UiFont.ATLAS)
        val bytes = data.remaining().toLong()
        var vb = vbuf
        if (vb == null || vb.isClosed || vb.size() < bytes) {
            vb?.close()
            vb = dev.createBuffer({ "fishmod ui vertices" }, GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST, Math.max(bytes * 2, 1L shl 16))
            vbuf = vb
        }
        enc.writeToBuffer(vb.slice(0, bytes), data)
        var ub = ubuf
        if (ub == null || ub.isClosed) {
            ub = dev.createBuffer({ "fishmod ui view" }, GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST, 16L)
            ubuf = ub
        }
        uni.putFloat(0, viewW).putFloat(4, viewH)
        enc.writeToBuffer(ub.slice(), uni)
        val view = target(last)
        val tw = view.getWidth(0); val th = view.getHeight(0)
        enc.createRenderPass({ "fishmod ui" }, view, OptionalInt.empty()).use { pass ->
            pass.setPipeline(p)
            pass.bindTexture("Sampler0", atlasView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            pass.setUniform("UiView", ub)
            pass.setVertexBuffer(0, vb)
            for (i in 0 until nSeg) {
                val o = i * 6
                if (segs[o + 4] < 0) pass.disableScissor() else scissor(pass, segs, o, tw, th)
                pass.draw(segs[o], segs[o + 1])
            }
        }
    }

    // Backends reject scissors past the target edge; clamp to the real target size
    private fun scissor(pass: com.mojang.blaze3d.systems.RenderPass, s: IntArray, o: Int, tw: Int, th: Int) {
        val x0 = s[o + 2].coerceIn(0, tw); val y0 = s[o + 3].coerceIn(0, th)
        val x1 = (s[o + 2] + s[o + 4]).coerceIn(x0, tw); val y1 = (s[o + 3] + s[o + 5]).coerceIn(y0, th)
        pass.enableScissor(x0, y0, x1 - x0, y1 - y0)
    }
}
