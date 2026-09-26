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

object CommandKeys {

    data class Entry(
        private val keyValue: InputConstants.Key,
        private val commandValue: String,
        private val enabledValue: Boolean = true,
    ) {
        fun key(): InputConstants.Key = keyValue
        fun command(): String = commandValue
        fun enabled(): Boolean = enabledValue
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

        if (client.screen != null || client.player == null) {
            held.clear()
            return
        }

        for (e in entries) {
            if (e.key() == InputConstants.UNKNOWN || e.command().isBlank()) continue
            if (!e.enabled()) { held.remove(e.key()); continue }

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
                val parts = line.split("\t", limit = 3)
                if (parts.size < 2 || parts[0].isBlank()) continue
                val key = InputConstants.getKey(parts[0].trim())
                if (parts.size == 3 && (parts[1] == "0" || parts[1] == "1")) {
                    entries.add(Entry(key, parts[2].trim(), parts[1] == "1"))
                } else {
                    entries.add(Entry(key, line.substringAfter('\t').trim()))
                }
            }
        } catch (ignored: IOException) {
        }
    }

    private fun save() {
        try {
            Files.createDirectories(FILE.parent)
            val sb = StringBuilder()
            for (e in entries) sb.append(e.key().name).append('\t').append(if (e.enabled()) '1' else '0').append('\t').append(e.command()).append('\n')
            Files.writeString(FILE, sb.toString())
        } catch (ignored: IOException) {
        }
    }
}
