package fishmod.features.streams;

import com.mojang.blaze3d.platform.NativeImage;
import fishmod.utils.debug.Debug;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Async loader/cache that turns a remote image URL into a registered MC texture Identifier. */
public class TwitchThumbnails {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static final Map<String, Identifier> ready   = new ConcurrentHashMap<>();
    private static final Map<String, String>     failed  = new ConcurrentHashMap<>();
    private static final Set<String>             loading = ConcurrentHashMap.newKeySet();

    /** Returns the texture id if loaded, otherwise null and kicks off a background load. */
    public static Identifier get(String url) {
        if (url == null || url.isEmpty()) return null;
        Identifier id = ready.get(url);
        if (id != null) return id;
        if (failed.containsKey(url)) return null; // don't retry forever
        load(url);
        return null;
    }

    /** Error message for a url that failed to load, or null. */
    public static String error(String url) {
        return url == null ? null : failed.get(url);
    }

    private static void load(String url) {
        if (!loading.add(url)) return;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(12))
                .GET().build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(r -> {
                    if (r.statusCode() != 200) {
                        failed.put(url, "http " + r.statusCode());
                        loading.remove(url);
                        return;
                    }
                    try {
                        // MC's NativeImage.read only handles PNG; Twitch previews are JPEG,
                        // so decode via ImageIO here (off the render thread).
                        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(r.body()));
                        if (bi == null) {
                            failed.put(url, "ImageIO returned null");
                            loading.remove(url);
                            return;
                        }
                        int wd = bi.getWidth(), ht = bi.getHeight();
                        int[] argb = bi.getRGB(0, 0, wd, ht, null, 0, wd);
                        Minecraft mc = Minecraft.getInstance();
                        mc.execute(() -> {
                            try {
                                NativeImage img = new NativeImage(wd, ht, false);
                                for (int yy = 0; yy < ht; yy++)
                                    for (int xx = 0; xx < wd; xx++)
                                        img.setPixel(xx, yy, argb[yy * wd + xx]);
                                DynamicTexture tex =
                                        new DynamicTexture(() -> "twitch_thumb", img);
                                Identifier id = Identifier.fromNamespaceAndPath("fishmod", "twitch_thumb/" + hash(url));
                                mc.getTextureManager().register(id, tex);
                                ready.put(url, id);
                            } catch (Throwable e) {
                                failed.put(url, String.valueOf(e));
                                Debug.LOGGER.warn("[TwitchThumbnails] upload failed for {}: {}", url, e.toString());
                            } finally {
                                loading.remove(url);
                            }
                        });
                    } catch (Throwable e) {
                        failed.put(url, String.valueOf(e));
                        Debug.LOGGER.warn("[TwitchThumbnails] decode failed for {}: {}", url, e.toString());
                        loading.remove(url);
                    }
                })
                .exceptionally(t -> { failed.put(url, String.valueOf(t)); loading.remove(url); return null; });
    }

    private static String hash(String url) {
        return Integer.toHexString(url.hashCode());
    }
}
