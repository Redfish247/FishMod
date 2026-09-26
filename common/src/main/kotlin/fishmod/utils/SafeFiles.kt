package fishmod.utils

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object SafeFiles {
    private val LOGGER = LoggerFactory.getLogger("fishmod")

    @JvmStatic
    fun quarantine(file: Path, error: Throwable) {
        val backup = file.resolveSibling(file.fileName.toString() + ".bak")
        LOGGER.error("Failed to load {}, moving it to {} and starting empty", file, backup.fileName, error)
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            LOGGER.error("Could not back up {}", file, e)
        }
    }

    @JvmStatic
    fun quarantine(file: File, error: Throwable) = quarantine(file.toPath(), error)

    @JvmStatic
    fun writeAtomic(file: Path, text: String) {
        try {
            file.parent?.let { Files.createDirectories(it) }
            val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(tmp, text, StandardCharsets.UTF_8)
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to save {}", file, e)
        }
    }

    @JvmStatic
    fun writeAtomic(file: File, text: String) = writeAtomic(file.toPath(), text)
}
