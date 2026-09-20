package fishmod.cosmetic

import net.minecraft.client.Minecraft
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Persists the raw cosmetic nick to <gameDir>/CosmeticNameChanger/nick.txt across sessions. */
object NickData {

    private fun file(): Path {
        val dir = Minecraft.getInstance().gameDirectory.toPath().resolve("CosmeticNameChanger")
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

    /**
     * When the current nick was last written, or 0 if none is set. Used to tell an admin/sweep
     * revoke (see nickClearedAt in InstallHeartbeat.kt) apart from a nick the player set afterward —
     * only a nick that predates the revoke gets cleared locally.
     */
    @JvmStatic
    fun lastSetAtMs(): Long {
        return try {
            val f = file()
            if (Files.exists(f)) Files.getLastModifiedTime(f).toMillis() else 0L
        } catch (ignored: IOException) {
            0L
        }
    }
}
