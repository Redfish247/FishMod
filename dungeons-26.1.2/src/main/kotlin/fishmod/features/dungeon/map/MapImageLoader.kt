package fishmod.features.dungeon.map

import com.mojang.blaze3d.platform.NativeImage
import fishmod.utils.Misc
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Loads user-supplied PNGs from config/FishMod/map_images/ as selectable dungeon-map HUD backgrounds, hot-reloaded via a directory watch. */
object MapImageLoader {

    const val NO_IMAGE = "No image"

    private val IMAGES_PATH: Path = FabricLoader.getInstance().configDir.resolve("FishMod").resolve("map_images")
    private val LOADED = ConcurrentHashMap<String, ImageData>()
    private var watchService: WatchService? = null
    private var started = false

    @JvmStatic
    fun getImagesPath(): Path = IMAGES_PATH

    @JvmStatic
    fun init() {
        if (started) return
        started = true

        try {
            Files.createDirectories(IMAGES_PATH)
            Files.list(IMAGES_PATH).use { stream ->
                stream.filter { it.toString().lowercase(Locale.ROOT).endsWith(".png") }.forEach { loadImage(it) }
            }

            val ws = IMAGES_PATH.fileSystem.newWatchService()
            watchService = ws
            IMAGES_PATH.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE)
            val t = Thread(::watchLoop, "FishMod-MapImageLoader")
            t.isDaemon = true
            t.start()
        } catch (e: Exception) {
        }
    }

    private fun watchLoop() {
        val ws = watchService ?: return
        while (true) {
            val key = try {
                ws.poll(1L, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                return
            }

            if (key != null) {
                for (event in key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue
                    val name = event.context() as Path
                    val file = IMAGES_PATH.resolve(name)
                    val base = nameWithoutExt(name.fileName.toString())

                    try {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            Thread.sleep(200L)
                            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && file.toString().lowercase(Locale.ROOT).endsWith(".png")) {
                                loadImage(file)
                            }
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE && LOADED.containsKey(base)) {
                            unloadImage(base)
                        }
                    } catch (e: Exception) {
                    }
                }
                key.reset()
            }
        }
    }

    private fun loadImage(path: Path) {
        val fileName = nameWithoutExt(path.fileName.toString())
        if (!path.toString().lowercase(Locale.ROOT).endsWith(".png")) return

        try {
            Files.newInputStream(path).use { input ->
                val img = NativeImage.read(input)
                val w = img.width
                val h = img.height
                val suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 5)
                val safe = fileName.lowercase(Locale.ROOT).replace(" ", "_")
                val id = Identifier.fromNamespaceAndPath("fishmod", "map_bg_$safe${suffix}f")

                Minecraft.getInstance().execute {
                    val tex = DynamicTexture({ id.toString() }, img)
                    Minecraft.getInstance().textureManager.register(id, tex)
                    LOADED[fileName] = ImageData(id, tex)
                    Misc.addChatMessage(Component.literal("§a[Map] Loaded image: §f$fileName §7(${w}x$h)"))
                }
            }
        } catch (e: Exception) {
            Misc.addChatMessage(Component.literal("§c[Map] Failed to load image '$fileName': ${e.message}"))
        }
    }

    private fun unloadImage(fileName: String) {
        val data = LOADED.remove(fileName) ?: return
        Minecraft.getInstance().execute {
            try {
                Minecraft.getInstance().textureManager.release(data.id)
            } catch (e: Exception) {
            }
        }
    }

    @JvmStatic
    fun getImageId(name: String): Identifier? = LOADED[name]?.id

    @JvmStatic
    fun getImageNames(): List<String> {
        val names = ArrayList<String>()
        names.add(NO_IMAGE)
        val sorted = ArrayList(LOADED.keys)
        sorted.sortWith(String.CASE_INSENSITIVE_ORDER)
        names.addAll(sorted)
        return names
    }

    private fun nameWithoutExt(s: String): String {
        val dot = s.lastIndexOf('.')
        return if (dot < 0) s else s.substring(0, dot)
    }

    private data class ImageData(val id: Identifier, val texture: DynamicTexture)
}
