package fishmod.features.other;

import fishmod.utils.Misc;
import fishmod.utils.config.FolderUtility;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User-defined "command keys": press a key in-game to run a slash command.
 *
 * Keys are stored as vanilla {@link InputUtil.Key} translation keys (not vanilla
 * {@link net.minecraft.client.option.KeyBinding}s) so entries can be freely added, rebound, and
 * removed at runtime from /fm commandkeys without touching the Controls screen, options.txt, or
 * the static KeyBinding registry (which Fabric API expects to be populated once at mod init).
 */
public class CommandKeys {

    public record Entry(InputUtil.Key key, String command) {}

    private static final Path FILE = Paths.get(FolderUtility.CONFIG_PATH + "command_keys.txt");
    private static final List<Entry> entries = new ArrayList<>();
    private static final Set<InputUtil.Key> held = new HashSet<>();
    private static boolean loaded = false;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(CommandKeys::tick);
    }

    public static List<Entry> all() {
        ensureLoaded();
        return Collections.unmodifiableList(entries);
    }

    /** Replaces the whole list at once (used by the editor screen after add/remove/rebind). */
    public static void replaceAll(List<Entry> newEntries) {
        ensureLoaded();
        entries.clear();
        entries.addAll(newEntries);
        save();
    }

    private static void tick(MinecraftClient client) {
        ensureLoaded();
        if (entries.isEmpty()) return;

        // Only fire while actually playing — not in chat, inventory, or any other GUI, so typing
        // never accidentally triggers a bound command key.
        if (client.currentScreen != null || client.player == null) {
            held.clear();
            return;
        }

        for (Entry e : entries) {
            if (e.key().equals(InputUtil.UNKNOWN_KEY) || e.command().isBlank()) continue;

            boolean down = e.key().getCategory() == InputUtil.Type.MOUSE
                    ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().getHandle(), e.key().getCode())
                        == org.lwjgl.glfw.GLFW.GLFW_PRESS
                    : InputUtil.isKeyPressed(client.getWindow(), e.key().getCode());
            boolean wasDown = held.contains(e.key());

            if (down && !wasDown) Misc.executeCommand(e.command());
            if (down) held.add(e.key()); else held.remove(e.key());
        }
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
                if (parts.length != 2 || parts[0].isBlank()) continue;
                InputUtil.Key key = InputUtil.fromTranslationKey(parts[0].trim());
                entries.add(new Entry(key, parts[1].trim()));
            }
        } catch (IOException ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            StringBuilder sb = new StringBuilder();
            for (Entry e : entries) sb.append(e.key().getTranslationKey()).append('\t').append(e.command()).append('\n');
            Files.writeString(FILE, sb.toString());
        } catch (IOException ignored) {}
    }
}
