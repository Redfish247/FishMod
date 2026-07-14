package fishmod.utils.rendering;

import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Owns the single NanoVG context used by FishModScreen. Created lazily on first use (not at
 * mod init) since GL-context timing relative to Fabric's ModInitializer entrypoint isn't
 * guaranteed, whereas FishModScreen's only construction site always runs post-boot on the
 * render thread. Lives for the process lifetime — Minecraft never recreates its GL context,
 * so there's nothing to tear down.
 */
public final class NvgContext {

    public static final String FONT_NAME = "inter";

    private static long handle = 0L;

    private NvgContext() {}

    public static long get() {
        if (handle == 0L) {
            handle = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);
            if (handle == 0L) throw new IllegalStateException("Failed to create NanoVG context");
            loadFont(handle);
        }
        return handle;
    }

    private static void loadFont(long ctx) {
        try (InputStream in = NvgContext.class.getResourceAsStream("/assets/fishmod/fonts/Inter-Regular.ttf")) {
            if (in == null) throw new IOException("font resource not found on classpath");
            byte[] bytes = in.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            // freeData=true: NanoVG/stb takes ownership of `buffer` and frees it itself later.
            int font = org.lwjgl.nanovg.NanoVG.nvgCreateFontMem(ctx, FONT_NAME, buffer, true);
            if (font == -1) throw new IllegalStateException("NanoVG failed to load bundled font");
            fishmod.utils.debug.Debug.LOGGER.info("[NanoVG] font '{}' loaded OK, handle={}, bytes={}", FONT_NAME, font, bytes.length);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load NanoVG font", e);
        }
    }
}
