package fishmod.cosmetic

import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import fishmod.compat.hud
import fishmod.utils.HypixelApi
import fishmod.mixin.FishChatInvoker
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink
import org.w3c.dom.Node
import java.awt.AlphaComposite
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

/**
 * Hover an [Image] link in the chat screen to see the picture / GIF next to the cursor.
 *
 * Only a short allowlist of image hosts is ever fetched (Discord CDN, Imgur, Tenor, Giphy). Fetching
 * an arbitrary URL on hover would let anyone in FishChat log the IP of whoever hovers their link.
 */
object ChatImagePreview {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_PIXELS = 16_000_000L
    private const val TEX_MAX_DIM = 720
    private const val MAX_FRAMES = 40
    private const val MAX_CACHED = 8
    private const val SHOW_MAX_W = 340
    private const val SHOW_MAX_H = 280

    private val TRUSTED_HOSTS = arrayOf("discordapp.com", "discordapp.net", "imgur.com", "tenor.com", "giphy.com", "klipy.com")
    private val IMAGE_EXT = Regex("(?i)\\.(png|jpe?g|gif)$")
    private val OG_IMAGE_A = Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val OG_IMAGE_B = Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", RegexOption.IGNORE_CASE)

    private val http: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(8))
        .build()
    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "FishMod-ChatImage").also { it.isDaemon = true } }
    private val counter = AtomicInteger()

    private class Frame(val id: Identifier, val delayMs: Int)

    private class Entry {
        @Volatile var state = 0 // 0 loading, 1 ready, 2 failed
        @Volatile var frames: List<Frame> = emptyList()
        @Volatile var w = 0
        @Volatile var h = 0
        @Volatile var error = ""
    }

    // Touched on the render thread only; loader threads only mutate the Entry they were handed.
    private val cache = LinkedHashMap<String, Entry>(16, 0.75f, true)

    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is ChatScreen) return@AfterInit
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, mx, my, _ ->
                try { render(ctx, mx, my) } catch (t: Throwable) { FishDiag.fail("ChatImagePreview.1", "chat image preview render failed", t) }
            })
        })
    }

    private fun hostTrusted(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return TRUSTED_HOSTS.any { h == it || h.endsWith(".$it") }
    }

    private fun isKlipyPage(uri: URI): Boolean {
        val h = uri.host?.lowercase() ?: return false
        return (h == "klipy.com" || h == "www.klipy.com") && (uri.path ?: "").startsWith("/gifs/")
    }

    private fun isPage(uri: URI): Boolean {
        val h = uri.host?.lowercase() ?: return false
        val p = uri.path ?: ""
        return ((h == "tenor.com" || h == "www.tenor.com") && p.startsWith("/view/")) ||
            ((h == "giphy.com" || h == "www.giphy.com") && p.startsWith("/gifs/")) ||
            isKlipyPage(uri)
    }

    /** True for https links to an allowlisted host that are an image file or a Tenor/Giphy page. */
    @JvmStatic
    fun isPreviewable(url: String): Boolean {
        val uri = try { URI.create(url) } catch (_: Throwable) { return false }
        if (!"https".equals(uri.scheme, true) || !hostTrusted(uri.host)) return false
        return isPage(uri) || IMAGE_EXT.containsMatchIn(uri.path ?: "")
    }

    // ---- hover detection ----

    private fun styleAt(ctx: GuiGraphicsExtractor, mc: Minecraft, mx: Int, my: Int): Style? {
        val chat = mc.hud.chat ?: return null
        val inv = chat as FishChatInvoker
        val scale = inv.invokeChatScale()
        val chatX = mx / scale - 4.0
        val chatY = (ctx.guiHeight() - 40 - my) / scale
        if (chatX < 0.0 || chatX > inv.invokeWidth() || chatY < 0.0) return null
        val lines = inv.visibleMessages ?: return null
        val lh = inv.invokeLineHeight()
        val visible = minOf(chat.linesPerPage, lines.size)
        if (chatY >= (lh * visible + lh).toDouble()) return null
        val idx = (chatY / lh).toInt() + inv.scrolledLines
        if (idx < 0 || idx >= lines.size) return null
        val font = mc.font
        var cx = 0f
        var found: Style? = null
        lines[idx].content().accept(FormattedCharSink { _, style, cp ->
            val w = font.width(FormattedCharSequence.codepoint(cp, style)).toFloat()
            if (chatX >= cx && chatX < cx + w) { found = style; false } else { cx += w; true }
        })
        return found
    }

    // ---- render ----

    private fun render(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
        if (!FishSettings.fishChatEnabled) return
        val mc = Minecraft.getInstance()
        val style = styleAt(ctx, mc, mx, my) ?: return
        val click = style.clickEvent as? ClickEvent.OpenUrl ?: return
        val url = click.uri().toString()
        if (!isPreviewable(url)) return

        val entry = cache.getOrPut(url) { Entry().also { evictIfNeeded(url); start(url, it) } }
        val font = mc.font
        val sw = ctx.guiWidth()
        val sh = ctx.guiHeight()

        if (entry.state != 1 || entry.frames.isEmpty()) {
            val msg = if (entry.state == 2) "§cCan't preview: ${entry.error}" else "§7Loading image..."
            val tw = font.width(msg)
            val bx = if (mx + 14 + tw + 6 > sw) mx - tw - 14 else mx + 12
            val by = (my - 16).coerceAtLeast(4)
            ctx.fill(bx - 3, by - 3, bx + tw + 3, by + 11, 0xE0100010.toInt())
            ctx.text(font, msg, bx, by, -1, true)
            return
        }

        val maxW = minOf(SHOW_MAX_W, sw / 2)
        val maxH = minOf(SHOW_MAX_H, sh / 2)
        val ratio = minOf(maxW.toDouble() / entry.w, maxH.toDouble() / entry.h)
        val dw = (entry.w * ratio).toInt().coerceAtLeast(1)
        val dh = (entry.h * ratio).toInt().coerceAtLeast(1)
        var x = mx + 14
        if (x + dw + 4 > sw) x = (mx - dw - 14).coerceAtLeast(4)
        val y = (my - dh - 8).coerceIn(4, (sh - dh - 4).coerceAtLeast(4))

        ctx.fill(x - 3, y - 3, x + dw + 3, y + dh + 3, 0xE0100010.toInt())
        ctx.outline(x - 3, y - 3, dw + 6, dh + 6, 0xFF5599FF.toInt())
        ctx.blit(RenderPipelines.GUI_TEXTURED, frameFor(entry).id, x, y, 0.0f, 0.0f, dw, dh, dw, dh, -1)
    }

    private fun frameFor(e: Entry): Frame {
        val fs = e.frames
        if (fs.size == 1) return fs[0]
        val total = fs.sumOf { it.delayMs }.coerceAtLeast(1)
        var t = (System.currentTimeMillis() % total).toInt()
        for (f in fs) { if (t < f.delayMs) return f; t -= f.delayMs }
        return fs[0]
    }

    private fun evictIfNeeded(keep: String) {
        while (cache.size >= MAX_CACHED) {
            val victim = cache.entries.firstOrNull { it.key != keep && it.value.state != 0 } ?: return
            cache.remove(victim.key)
            val tm = Minecraft.getInstance().textureManager
            for (f in victim.value.frames) try { tm.release(f.id) } catch (_: Throwable) {}
        }
    }

    // ---- loading (worker threads) ----

    private fun start(url: String, e: Entry) {
        val caller = Minecraft.getInstance().player?.uuid?.toString()?.replace("-", "") ?: ""
        pool.execute {
            try { load(url, e, caller) } catch (t: Throwable) {
                e.error = (t.message ?: t.javaClass.simpleName).take(40)
                e.state = 2
                FishDiag.fail("ChatImagePreview.2", "chat image load failed: ${describe(url)}", t)
            }
        }
    }

    /** Host, path and query shape only (never the signed query values), for debug reports. */
    private fun describe(url: String): String = try {
        val u = URI.create(url)
        val q = u.rawQuery ?: ""
        val keys = q.split('&').filter { it.isNotEmpty() }.joinToString(",") { it.substringBefore('=') }
        "${u.host}${u.rawPath} (url ${url.length} chars, query keys [$keys], endsWith& ${url.endsWith("&")})"
    } catch (_: Throwable) { "unparseable url (${url.length} chars)" }

    private fun fetch(url: String, maxBytes: Int): ByteArray {
        val uri = URI.create(url)
        if (!"https".equals(uri.scheme, true) || !hostTrusted(uri.host)) throw IOException("host not allowed")
        val req = HttpRequest.newBuilder(uri).header("User-Agent", "Mozilla/5.0").timeout(Duration.ofSeconds(10)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofInputStream())
        res.body().use { body ->
            if (res.statusCode() != 200) throw IOException("HTTP ${res.statusCode()}")
            val bytes = body.readNBytes(maxBytes + 1)
            if (bytes.size > maxBytes) throw IOException("too large")
            return bytes
        }
    }

    /** Klipy pages are behind a bot challenge; the FishMod worker looks the slug up with Klipy's official API. */
    private fun resolveKlipy(uri: URI, caller: String): String {
        val slug = (uri.path ?: "").removePrefix("/gifs/").trim('/').substringBefore('/').lowercase()
        if (!Regex("[a-z0-9][a-z0-9-]{0,119}").matches(slug)) throw IOException("bad Klipy link")
        val req = HttpRequest.newBuilder(URI.create("https://fishmod.dev/klipy?slug=$slug"))
            .header("X-FishMod-Token", HypixelApi.MOD_TOKEN).header("X-FishMod-Caller", caller)
            .header("User-Agent", "Mozilla/5.0").timeout(Duration.ofSeconds(10)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        val root = try { JsonParser.parseString(res.body()).asJsonObject } catch (_: Throwable) { throw IOException("HTTP ${res.statusCode()}") }
        if (res.statusCode() != 200) throw IOException(root.get("cause")?.takeIf { !it.isJsonNull }?.asString ?: "HTTP ${res.statusCode()}")
        return root.get("url")?.takeIf { !it.isJsonNull }?.asString ?: throw IOException("no gif found")
    }

    /** Forwarded Discord attachments arrive without the signed query and 404; the worker asks Discord to sign them. */
    private fun needsDiscordSign(uri: URI): Boolean {
        val h = uri.host?.lowercase() ?: return false
        return (h == "cdn.discordapp.com" || h == "media.discordapp.net" || h == "cdn.discordapp.net") &&
            (uri.path ?: "").startsWith("/attachments/") && uri.rawQuery == null
    }

    private fun signDiscord(url: String, caller: String): String {
        val req = HttpRequest.newBuilder(URI.create("https://fishmod.dev/discord-sign?u=" + java.net.URLEncoder.encode(url, Charsets.UTF_8)))
            .header("X-FishMod-Token", HypixelApi.MOD_TOKEN).header("X-FishMod-Caller", caller)
            .header("User-Agent", "Mozilla/5.0").timeout(Duration.ofSeconds(10)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        val root = try { JsonParser.parseString(res.body()).asJsonObject } catch (_: Throwable) { throw IOException("HTTP ${res.statusCode()}") }
        if (res.statusCode() != 200) throw IOException(root.get("cause")?.takeIf { !it.isJsonNull }?.asString ?: "HTTP ${res.statusCode()}")
        return root.get("url")?.takeIf { !it.isJsonNull }?.asString ?: throw IOException("could not sign link")
    }

    private fun load(url: String, e: Entry, caller: String) {
        var imageUrl = url
        val pageUri = URI.create(url)
        if (needsDiscordSign(pageUri)) imageUrl = signDiscord(url, caller)
        if (isKlipyPage(pageUri)) {
            imageUrl = resolveKlipy(pageUri, caller)
            if (!isPreviewable(imageUrl)) throw IOException("unsupported image")
        } else if (isPage(pageUri)) {
            val html = String(fetch(url, 1_500_000), Charsets.UTF_8)
            val found = (OG_IMAGE_A.find(html) ?: OG_IMAGE_B.find(html))?.groupValues?.get(1)?.replace("&amp;", "&")
                ?: throw IOException("no image on page")
            imageUrl = found
            if (!isPreviewable(imageUrl)) throw IOException("unsupported image")
        }
        val bytes = fetch(imageUrl, MAX_BYTES)
        val decoded = decode(bytes)
        val images = decoded.map { toNative(it.first) }
        val w = images[0].width
        val h = images[0].height
        val delays = decoded.map { it.second }
        val base = Integer.toHexString(url.hashCode()) + "_" + counter.incrementAndGet()
        Minecraft.getInstance().execute {
            try {
                val tm = Minecraft.getInstance().textureManager
                val frames = images.mapIndexed { i, img ->
                    val id = Identifier.fromNamespaceAndPath("fishmod", "chatimg/${base}_$i")
                    tm.register(id, DynamicTexture({ id.toString() }, img))
                    Frame(id, delays[i])
                }
                e.w = w; e.h = h; e.frames = frames; e.state = 1
            } catch (t: Throwable) {
                images.forEach { try { it.close() } catch (_: Throwable) {} }
                e.error = "texture"; e.state = 2
                FishDiag.fail("ChatImagePreview.3", "chat image texture register failed", t)
            }
        }
    }

    /** Frames with their delay in ms (0 for a still image). GIFs are composited frame by frame. */
    private fun decode(bytes: ByteArray): List<Pair<BufferedImage, Int>> {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { iis ->
            val readers = ImageIO.getImageReaders(iis)
            if (!readers.hasNext()) throw IOException("unsupported format")
            val r = readers.next()
            try {
                r.setInput(iis, false, false)
                if (r.getWidth(0).toLong() * r.getHeight(0) > MAX_PIXELS) throw IOException("image too big")
                if (!r.formatName.equals("gif", true)) return listOf(r.read(0) to 0)

                var sw = 0
                var sh = 0
                try {
                    val lsd = child(r.streamMetadata.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor")
                    sw = attr(lsd, "logicalScreenWidth"); sh = attr(lsd, "logicalScreenHeight")
                } catch (_: Throwable) {}
                if (sw <= 0 || sh <= 0) { sw = r.getWidth(0); sh = r.getHeight(0) }

                val canvas = BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB)
                val g = canvas.createGraphics()
                val out = ArrayList<Pair<BufferedImage, Int>>()
                val n = minOf(r.getNumImages(true), MAX_FRAMES)
                for (i in 0 until n) {
                    val fr = r.read(i)
                    val tree = r.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0")
                    val desc = child(tree, "ImageDescriptor")
                    val gce = child(tree, "GraphicControlExtension")
                    val left = attr(desc, "imageLeftPosition")
                    val top = attr(desc, "imageTopPosition")
                    val delay = attr(gce, "delayTime") * 10
                    val disposal = (gce?.attributes?.getNamedItem("disposalMethod")?.nodeValue) ?: "none"
                    val before = if (disposal == "restoreToPrevious") copy(canvas) else null
                    g.drawImage(fr, left, top, null)
                    out.add(copy(canvas) to (if (delay < 20) 100 else delay))
                    when (disposal) {
                        "restoreToBackgroundColor" -> {
                            g.composite = AlphaComposite.Clear
                            g.fillRect(left, top, fr.width, fr.height)
                            g.composite = AlphaComposite.SrcOver
                        }
                        "restoreToPrevious" -> if (before != null) {
                            g.composite = AlphaComposite.Src
                            g.drawImage(before, 0, 0, null)
                            g.composite = AlphaComposite.SrcOver
                        }
                    }
                }
                g.dispose()
                if (out.isEmpty()) throw IOException("empty gif")
                return out
            } finally {
                r.dispose()
            }
        }
    }

    private fun child(n: Node?, name: String): Node? {
        var c = n?.firstChild
        while (c != null) { if (c.nodeName == name) return c; c = c.nextSibling }
        return null
    }

    private fun attr(n: Node?, name: String): Int = n?.attributes?.getNamedItem(name)?.nodeValue?.toIntOrNull() ?: 0

    private fun copy(src: BufferedImage): BufferedImage {
        val d = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val g = d.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return d
    }

    /** Downscale to at most TEX_MAX_DIM and go through PNG so NativeImage can read any decoded format. */
    private fun toNative(src: BufferedImage): NativeImage {
        val s = minOf(1.0, TEX_MAX_DIM.toDouble() / maxOf(src.width, src.height))
        val tw = maxOf(1, (src.width * s).toInt())
        val th = maxOf(1, (src.height * s).toInt())
        val dst = BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, tw, th, null)
        g.dispose()
        val bos = ByteArrayOutputStream()
        ImageIO.write(dst, "png", bos)
        return NativeImage.read(ByteArrayInputStream(bos.toByteArray()))
    }
}
