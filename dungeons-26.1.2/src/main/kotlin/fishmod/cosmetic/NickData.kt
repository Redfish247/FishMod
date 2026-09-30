package fishmod.cosmetic

import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object NickData {

    private val dirReady = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun file(): Path {
        val dir = Minecraft.getInstance().gameDirectory.toPath().resolve("CosmeticNameChanger")
        if (dirReady.compareAndSet(false, true)) {
            try {
                Files.createDirectories(dir)
            } catch (e: IOException) {
                FishDiag.fail("NickData.1", "could not create CosmeticNameChanger dir", e)
            }
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
        } catch (e: IOException) {
            FishDiag.fail("NickData.2", "nick.txt read failed", e)
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
        } catch (e: IOException) {
            FishDiag.fail("NickData.3", "nick.txt write failed", e)
        }
    }

    @JvmStatic
    fun lastSetAtMs(): Long {
        return try {
            val f = file()
            if (Files.exists(f)) Files.getLastModifiedTime(f).toMillis() else 0L
        } catch (e: IOException) {
            FishDiag.fail("NickData.4", "nick.txt mtime read failed", e)
            0L
        }
    }
}
