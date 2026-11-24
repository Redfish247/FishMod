package fishmod.features.visuals

import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.FilePackResources
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.RepositorySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Optional
import java.util.function.Consumer

/**
 * Loads Hypixel's SkyBlock resource pack from a local cache instead of letting the server apply it on every join
 * (Pack Disabler port). The server's pack push is answered with "loaded" and dropped; the pack is downloaded once
 * per version and registered as a normal client pack. Also picks which text-colour shaders win (OG text colours).
 */
object ClassicPack {
    const val PACK_ID = "hypixel_skyblock"

    private val dir: Path = FabricLoader.getInstance().configDir.resolve("fishmod/skyblock-pack")
    private val fileA = dir.resolve("pack-a.zip")
    private val fileB = dir.resolve("pack-b.zip")
    private val urlFile = dir.resolve("url.txt")

    @Volatile private var active: Path? = null
    @Volatile private var servedToRepo = false
    @Volatile private var busy = false
    private val http: HttpClient by lazy { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build() }

    private val TEXT_SHADERS = setOf(
        "shaders/core/text.vsh",
        "shaders/core/rendertype_text.vsh",
        "shaders/core/rendertype_text_see_through.vsh",
        "shaders/core/rendertype_text_intensity.vsh",
        "shaders/core/rendertype_text_intensity_see_through.vsh",
        "shaders/include/modify_vanilla_color.glsl",
        "shaders/include/color_util.glsl",
    )

    @JvmStatic fun localEnabled(): Boolean = FishSettings.classicItemsEnabled && FishSettings.classicLocalPack
    @JvmStatic fun ogColors(): Boolean = FishSettings.classicItemsEnabled && FishSettings.classicOgColors
    @JvmStatic fun isTextShader(id: Identifier): Boolean = id.namespace == "minecraft" && id.path in TEXT_SHADERS

    /** Pack sources that carry Hypixel's SkyBlock colours: our local copy or a server-pushed pack. */
    @JvmStatic fun isSkyblockPackSource(sourceId: String?): Boolean = sourceId != null && (sourceId == PACK_ID || sourceId.startsWith("server/"))

    private fun current(): Path? {
        active?.let { if (Files.exists(it)) return it }
        if (!Files.exists(urlFile)) return null
        val newest = listOf(fileA, fileB).filter { Files.exists(it) }.maxByOrNull { Files.getLastModifiedTime(it) } ?: return null
        active = newest
        return newest
    }

    private fun build(path: Path): Pack = Pack.readMetaAndCreate(
        PackLocationInfo(PACK_ID, Component.literal("FishMod: SkyBlock Pack"), PackSource.BUILT_IN, Optional.empty()),
        FilePackResources.FileResourcesSupplier(path),
        PackType.CLIENT_RESOURCES,
        PackSelectionConfig(true, Pack.Position.BOTTOM, true),
    ) ?: error("could not read pack metadata for $path")

    class Source : RepositorySource {
        override fun loadPacks(onLoad: Consumer<Pack>) {
            try {
                if (!localEnabled()) { servedToRepo = false; return }
                val f = current() ?: run { servedToRepo = false; return }
                onLoad.accept(build(f))
                servedToRepo = true
            } catch (t: Throwable) {
                servedToRepo = false
                FishDiag.fail("ClassicPack.1", "could not register cached SkyBlock pack", t)
            }
        }
    }

    /** Returns true when the server pack push was handled locally and must be dropped. */
    @JvmStatic
    fun onServerPack(url: String): Boolean {
        if (!localEnabled() || !url.contains("hypixel.net") || !url.contains("SkyBlock")) return false
        try { update(url) } catch (t: Throwable) { FishDiag.fail("ClassicPack.2", "pack update failed", t) }
        return true
    }

    private fun update(url: String) {
        val cached = current()
        val same = try { Files.readString(urlFile).trim() == url } catch (_: Throwable) { false }
        if (cached != null && same) {
            if (!servedToRepo) reload()
            return
        }
        if (busy) return
        busy = true
        val t = Thread({
            try {
                Files.createDirectories(dir)
                val target = if (active == fileA) fileB else fileA
                val tmp = Files.createTempFile(dir, "pack", ".tmp")
                val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90)).build()
                val resp = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp))
                if (resp.statusCode() !in 200..299 || Files.size(tmp) < 1000) {
                    Files.deleteIfExists(tmp)
                    error("download failed: HTTP ${resp.statusCode()}")
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                Files.writeString(urlFile, url)
                active = target
                servedToRepo = false
                reload()
            } catch (e: Throwable) {
                FishDiag.fail("ClassicPack.3", "SkyBlock pack download failed", e)
            } finally {
                busy = false
            }
        }, "FishMod-SkyblockPack")
        t.isDaemon = true
        t.start()
    }

    @JvmStatic
    fun reload() {
        val mc = Minecraft.getInstance()
        mc.execute { mc.reloadResourcePacks() }
    }
}
