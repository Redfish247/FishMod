package fishmod.features.other

import fishmod.utils.Misc
import fishmod.utils.config.FolderUtility
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * User-defined "command keys": press a key in-game to run a slash command.
 *
 * Keys are stored as vanilla [InputUtil.Key] translation keys (not vanilla
 * `net.minecraft.client.option.KeyBinding`s) so entries can be freely added, rebound, and
 * removed at runtime from /fm commandkeys without touching the Controls screen, options.txt, or
 * the static KeyBinding registry (which Fabric API expects to be populated once at mod init).
 */
object CommandKeys {

    /** Ported from the original Java `record Entry(InputUtil.Key key, String command)`. Callers
     *  use the record-style accessors `.key()` / `.command()`, so this stays a plain class with
     *  explicit methods rather than a Kotlin data class. */
    class Entry(private val keyValue: InputUtil.Key, private val commandValue: String) {
        fun key(): InputUtil.Key = keyValue
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
    private val held: MutableSet<InputUtil.Key> = HashSet()
    private var loaded = false

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(CommandKeys::tick)
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

    private fun tick(client: MinecraftClient) {
        ensureLoaded()
        if (entries.isEmpty()) return

        // Only fire while actually playing — not in chat, inventory, or any other GUI, so typing
        // never accidentally triggers a bound command key.
        if (client.currentScreen != null || client.player == null) {
            held.clear()
            return
        }

        for (e in entries) {
            if (e.key() == InputUtil.UNKNOWN_KEY || e.command().isBlank()) continue

            val down = if (e.key().category == InputUtil.Type.MOUSE)
                GLFW.glfwGetMouseButton(client.window.handle, e.key().code) == GLFW.GLFW_PRESS
            else
                InputUtil.isKeyPressed(client.window, e.key().code)
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
                val key = InputUtil.fromTranslationKey(parts[0].trim())
                entries.add(Entry(key, parts[1].trim()))
            }
        } catch (ignored: IOException) {
        }
    }

    private fun save() {
        try {
            Files.createDirectories(FILE.parent)
            val sb = StringBuilder()
            for (e in entries) sb.append(e.key().translationKey).append('\t').append(e.command()).append('\n')
            Files.writeString(FILE, sb.toString())
        } catch (ignored: IOException) {
        }
    }
}
