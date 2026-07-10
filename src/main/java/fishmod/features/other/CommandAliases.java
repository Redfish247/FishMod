package fishmod.features.other;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import fishmod.utils.Constants;
import fishmod.utils.Misc;
import fishmod.utils.config.FolderUtility;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User-defined command aliases: typing "/dh" (say) runs "/warp dh" instead.
 *
 * <p>Registered as real Brigadier client commands (not a chat interceptor) so aliases get normal
 * tab-completion and work exactly like any other /fm command. {@link #registerAll} is called from
 * FishModInit's existing ClientCommandRegistrationCallback, which Fabric re-fires on every (re)join
 * — {@link #replaceAll} additionally re-registers onto the last-seen dispatcher so edits/adds made
 * from the editor screen take effect immediately without reconnecting. Brigadier has no clean way
 * to unregister a node, so a removed/renamed alias's old literal lingers (still runs the old
 * mapping) until the next join, when it's simply not re-registered.
 */
public class CommandAliases {

    public record Entry(String alias, String command) {}

    private static final Path FILE = Paths.get(FolderUtility.CONFIG_PATH + "command_aliases.txt");
    private static final List<Entry> entries = new ArrayList<>();
    private static boolean loaded = false;

    private static CommandDispatcher<FabricClientCommandSource> liveDispatcher;

    public static List<Entry> all() {
        ensureLoaded();
        return Collections.unmodifiableList(entries);
    }

    public static void registerAll(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        ensureLoaded();
        liveDispatcher = dispatcher;
        for (Entry e : entries) registerNode(dispatcher, e);
    }

    /** Replaces the whole list (used by the editor screen after add/remove/edit) and, if a
     *  dispatcher is already live, re-registers immediately so the change takes effect now. */
    public static void replaceAll(List<Entry> newEntries) {
        ensureLoaded();
        entries.clear();
        entries.addAll(newEntries);
        save();
        if (liveDispatcher != null) {
            for (Entry e : entries) registerNode(liveDispatcher, e);
        }
    }

    private static void registerNode(CommandDispatcher<FabricClientCommandSource> dispatcher, Entry e) {
        // Brigadier literals can't contain a slash, so the alias (not the target command — that's
        // handled by Misc.executeCommand) needs it stripped.
        String alias = e.alias().trim();
        if (alias.startsWith("/")) alias = alias.substring(1);
        String target = e.command().trim();
        if (alias.isBlank() || target.isBlank()) return;

        dispatcher.register(ClientCommandManager.literal(alias)
                .executes(ctx -> { Misc.executeCommand(target); return Constants.SUCCESS; })
                .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            Misc.executeCommand(target + " " + StringArgumentType.getString(ctx, "args"));
                            return Constants.SUCCESS;
                        })));
    }

    private static void ensureLoaded() {
        if (!loaded) { load(); loaded = true; }
    }

    private static void load() {
        entries.clear();
        if (!Files.exists(FILE)) return;
        try {
            for (String line : Files.readAllLines(FILE)) {
                String[] parts = line.split("\t", 2);
                if (parts.length == 2 && !parts[0].isBlank()) entries.add(new Entry(parts[0].trim(), parts[1].trim()));
            }
        } catch (IOException ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            StringBuilder sb = new StringBuilder();
            for (Entry e : entries) sb.append(e.alias()).append('\t').append(e.command()).append('\n');
            Files.writeString(FILE, sb.toString());
        } catch (IOException ignored) {}
    }
}
