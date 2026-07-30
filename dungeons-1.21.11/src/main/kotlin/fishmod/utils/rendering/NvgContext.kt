package fishmod.utils.rendering

import org.lwjgl.nanovg.NanoVG
import org.lwjgl.nanovg.NanoVGGL3
import org.lwjgl.system.MemoryUtil
import java.io.IOException
import java.nio.ByteBuffer

/** Owns FishModScreen's single NanoVG context, created lazily on first use since GL-context timing at mod init isn't guaranteed. Lives for the process lifetime. */
object NvgContext {

    const val FONT_NAME: String = "inter"

    private var handle: Long = 0L

    @JvmStatic
    fun get(): Long {
        if (handle == 0L) {
            handle = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS or NanoVGGL3.NVG_STENCIL_STROKES)
            if (handle == 0L) throw IllegalStateException("Failed to create NanoVG context")
            loadFont(handle)
        }
        return handle
    }

    private fun loadFont(ctx: Long) {
        try {
            NvgContext::class.java.getResourceAsStream("/assets/fishmod/fonts/Inter-Regular.ttf").use { input ->
                if (input == null) throw IOException("font resource not found on classpath")
                val bytes = input.readAllBytes()
                val buffer: ByteBuffer = MemoryUtil.memAlloc(bytes.size)
                buffer.put(bytes).flip()
                // freeData=true: NanoVG/stb takes ownership of `buffer` and frees it itself later.
                val font = NanoVG.nvgCreateFontMem(ctx, FONT_NAME, buffer, true)
                if (font == -1) throw IllegalStateException("NanoVG failed to load bundled font")
                fishmod.utils.debug.Debug.LOGGER.info("[NanoVG] font '{}' loaded OK, handle={}, bytes={}", FONT_NAME, font, bytes.size)
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to load NanoVG font", e)
        }
    }
}
