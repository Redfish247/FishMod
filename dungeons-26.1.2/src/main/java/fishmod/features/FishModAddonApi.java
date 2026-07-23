package fishmod.features;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Small public extension point so other mods (e.g. FishModAddons) can register a toggle into the
 * Dungeon column of /fm without FishMod depending on them, and without exposing FishModScreen's
 * private Feature/Column types across the mod boundary.
 */
public final class FishModAddonApi {
    private FishModAddonApi() {}

    public record ExternalToggle(String name, String description, Supplier<Boolean> get, Consumer<Boolean> set) {}

    public static final List<ExternalToggle> dungeonToggles = new CopyOnWriteArrayList<>();

    /** Registers a toggle that appears in /fm's Dungeon column, e.g. for an addon's own on/off switch. */
    public static void registerDungeonToggle(String name, String description, Supplier<Boolean> get, Consumer<Boolean> set) {
        dungeonToggles.add(new ExternalToggle(name, description, get, set));
    }
}
