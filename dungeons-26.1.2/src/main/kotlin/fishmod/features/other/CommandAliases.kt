package fishmod.features.other

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import fishmod.utils.Constants
import fishmod.utils.Misc
import fishmod.utils.config.FolderUtility
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * User-defined command aliases (e.g. "/dh" runs "/warp dh"), registered as real Brigadier
 * commands for normal tab-completion. Brigadier has no clean way to unregister a node, so a
 * removed/renamed alias's old literal lingers until the next join, when it's not re-registered.
 */
object CommandAliases {

    /** Kept as a plain class (not a data class) so Java callers keep the record-style `.alias()`/`.command()` accessors. */
    class Entry(private val aliasValue: String, private val commandValue: String) {
        fun alias(): String = aliasValue
        fun command(): String = commandValue

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entry) return false
            return aliasValue == other.aliasValue && commandValue == other.commandValue
        }

        override fun hashCode(): Int = 31 * aliasValue.hashCode() + commandValue.hashCode()

        override fun toString(): String = "Entry[alias=$aliasValue, command=$commandValue]"
    }

    private val FILE: Path = Paths.get(FolderUtility.CONFIG_PATH + "command_aliases.txt")
    private val entries: MutableList<Entry> = ArrayList()
    private var loaded = false

    private var liveDispatcher: CommandDispatcher<FabricClientCommandSource>? = null

    @JvmStatic
    fun all(): List<Entry> {
        ensureLoaded()
        return java.util.Collections.unmodifiableList(entries)
    }

    @JvmStatic
    fun registerAll(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        ensureLoaded()
        liveDispatcher = dispatcher
        for (e in entries) registerNode(dispatcher, e)
    }

    /** Re-registers onto the live dispatcher (if any) so edits take effect without reconnecting. */
    @JvmStatic
    fun replaceAll(newEntries: List<Entry>) {
        ensureLoaded()
        entries.clear()
        entries.addAll(newEntries)
        save()
        val dispatcher = liveDispatcher
        if (dispatcher != null) {
            for (e in entries) registerNode(dispatcher, e)
        }
    }

    private fun registerNode(dispatcher: CommandDispatcher<FabricClientCommandSource>, e: Entry) {
        // Brigadier literals can't contain a slash, so strip it from the alias.
        var alias = e.alias().trim()
        if (alias.startsWith("/")) alias = alias.substring(1)
        val target = e.command().trim()
        if (alias.isBlank() || target.isBlank()) return

        dispatcher.register(
            ClientCommands.literal(alias)
                .executes { _ -> Misc.executeCommand(target); Constants.SUCCESS }
                .then(
                    ClientCommands.argument("args", StringArgumentType.greedyString())
                        .executes { ctx ->
                            Misc.executeCommand(target + " " + StringArgumentType.getString(ctx, "args"))
                            Constants.SUCCESS
                        }
                )
        )
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
                if (parts.size == 2 && parts[0].isNotBlank()) entries.add(Entry(parts[0].trim(), parts[1].trim()))
            }
        } catch (ignored: IOException) {
        }
    }

    private fun save() {
        try {
            Files.createDirectories(FILE.parent)
            val sb = StringBuilder()
            for (e in entries) sb.append(e.alias()).append('\t').append(e.command()).append('\n')
            Files.writeString(FILE, sb.toString())
        } catch (ignored: IOException) {
        }
    }
}
