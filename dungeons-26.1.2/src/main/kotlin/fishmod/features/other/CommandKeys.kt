package fishmod.features.other

import com.mojang.blaze3d.platform.InputConstants
import fishmod.utils.Misc
import fishmod.utils.config.FolderUtility
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * User-defined "command keys": press a key to run a slash command. Stored as raw
 * [InputConstants.Key]s rather than `KeyMapping`s so entries can be freely added/rebound at
 * runtime without touching the static KeyMapping registry (populated once at mod init).
 */
object CommandKeys {

    /** Kept as a plain class (not a data class) so Java callers keep the record-style `.key()`/`.command()` accessors. */
    class Entry(private val keyValue: InputConstants.Key, private val commandValue: String) {
        fun key(): InputConstants.Key = keyValue
        fun command(): String = commandValue

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entry) return false
            return keyValue == other.keyValue && commandValue == other.commandValue
        }

        override fun hashCode(): Int = 31 * keyValue.hashCode() + commandValue.hashCode()

        override fun toString(): String = "Entry[key=$keyValue, command=$commandValue]"
    }

    private val FILE: Path = Paths.get(FolderUtility.CONFIG_PATH + "command_keys.txt")
    private val entries: MutableList<Entry> = ArrayList()
    private val held: MutableSet<InputConstants.Key> = HashSet()
    private var loaded = false

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc -> tick(mc) })
    }

    @JvmStatic
    fun all(): List<Entry> {
        ensureLoaded()
        return java.util.Collections.unmodifiableList(entries)
    }

    /** Replaces the whole list at once (used by the editor screen after add/remove/rebind). */
    @JvmStatic
    fun replaceAll(newEntries: List<Entry>) {
        ensureLoaded()
        entries.clear()
        entries.addAll(newEntries)
        save()
    }

    private fun tick(client: Minecraft) {
        ensureLoaded()
        if (entries.isEmpty()) return

        // Don't fire while any GUI (chat, inventory) is open.
        if (client.screen != null || client.player == null) {
            held.clear()
            return
        }

        for (e in entries) {
            if (e.key() == InputConstants.UNKNOWN || e.command().isBlank()) continue

            val down = if (e.key().type == InputConstants.Type.MOUSE)
                GLFW.glfwGetMouseButton(client.window.handle(), e.key().value) == GLFW.GLFW_PRESS
            else
                InputConstants.isKeyDown(client.window, e.key().value)
            val wasDown = held.contains(e.key())

            if (down && !wasDown) Misc.executeCommand(e.command())
            if (down) held.add(e.key()) else held.remove(e.key())
        }
    }

    private fun ensureLoaded() {
        if (!loaded) { load(); loaded = true }
    }

    private fun load() {
        entries.clear()
        if (!Files.exists(FILE)) return
        try {
            for (line in Files.readAllLines(FILE)) {
                val parts = line.split("\t", limit = 2)
                if (parts.size != 2 || parts[0].isBlank()) continue
                val key = InputConstants.getKey(parts[0].trim())
                entries.add(Entry(key, parts[1].trim()))
            }
        } catch (ignored: IOException) {
        }
    }

    private fun save() {
        try {
            Files.createDirectories(FILE.parent)
            val sb = StringBuilder()
            for (e in entries) sb.append(e.key().name).append('\t').append(e.command()).append('\n')
            Files.writeString(FILE, sb.toString())
        } catch (ignored: IOException) {
        }
    }
}
