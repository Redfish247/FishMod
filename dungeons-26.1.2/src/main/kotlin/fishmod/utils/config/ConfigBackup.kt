package fishmod.utils.config

import com.google.gson.JsonParser
import fishmod.utils.debug.FishDebug
import fishmod.utils.debug.FishDiag
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// The config library overwrites in place, so a full disk can truncate settings: keep a last-good copy.
object ConfigBackup {

    @JvmStatic
    fun run(path: String) {
        try {
            val file = File(path)
            if (!file.isFile) return
            val backup = File("$path.bak")
            if (isValid(file)) {
                writeAtomic(backup, file.readBytes())
            } else if (backup.isFile && isValid(backup)) {
                Files.copy(file.toPath(), File("$path.corrupt").toPath(), StandardCopyOption.REPLACE_EXISTING)
                writeAtomic(file, backup.readBytes())
                FishDebug.LOGGER.warn("[ConfigBackup] {} was corrupt, restored from backup", path)
            } else {
                FishDebug.LOGGER.warn("[ConfigBackup] {} is corrupt and has no usable backup", path)
            }
        } catch (e: Exception) {
            FishDiag.fail("ConfigBackup.1", "config backup failed for $path", e)
        }
    }

    // Temp then move: a crash or full disk mid-write keeps the old file.
    @JvmStatic
    fun writeAtomic(file: File, bytes: ByteArray) {
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File("${file.path}.tmp")
        try {
            tmp.writeBytes(bytes)
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            tmp.delete()
        }
    }

    private fun isValid(file: File): Boolean = try {
        JsonParser.parseString(file.readText(Charsets.UTF_8)).isJsonObject
    } catch (_: Exception) {
        false
    }
}
