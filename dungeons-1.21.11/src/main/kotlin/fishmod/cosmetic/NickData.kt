package fishmod.cosmetic

import net.minecraft.client.MinecraftClient
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Persists the raw cosmetic nick to <gameDir>/CosmeticNameChanger/nick.txt across sessions. */
object NickData {

    private fun file(): Path {
        val dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("CosmeticNameChanger")
        try {
            Files.createDirectories(dir)
        } catch (ignored: IOException) {
        }
        return dir.resolve("nick.txt")
    }

    @JvmStatic
    fun load() {
        try {
            val f = file()
            if (Files.exists(f)) {
                val raw = Files.readString(f).trim()
                if (raw.isNotEmpty()) NickState.applyFromDisk(raw)
            }
        } catch (ignored: IOException) {
        }
    }

    @JvmStatic
    fun save(raw: String?) {
        try {
            val f = file()
            if (raw != null && raw.isNotEmpty()) {
                Files.writeString(f, raw)
            } else {
                Files.deleteIfExists(f)
            }
        } catch (ignored: IOException) {
        }
    }
}
