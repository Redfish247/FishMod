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

object CommandAliases {

    data class Entry(private val aliasValue: String, private val commandValue: String) {
        fun alias(): String = aliasValue
        fun command(): String = commandValue
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
