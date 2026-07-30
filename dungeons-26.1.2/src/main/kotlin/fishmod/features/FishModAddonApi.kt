package fishmod.features

import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Small public extension point so other mods (e.g. FishModAddons) can register a toggle into the
 * Dungeon column of /fm without FishMod depending on them, and without exposing FishModScreen's
 * private Feature/Column types across the mod boundary.
 */
object FishModAddonApi {

    class ExternalToggle(
        private val toggleName: String,
        private val toggleDescription: String,
        private val toggleGet: Supplier<Boolean>,
        private val toggleSet: Consumer<Boolean>
    ) {
        // Record-style accessors — matches how FishModScreen calls et.name()/et.description().
        fun name(): String = toggleName
        fun description(): String = toggleDescription
        fun get(): Supplier<Boolean> = toggleGet
        fun set(): Consumer<Boolean> = toggleSet
    }

    @JvmField
    val dungeonToggles: MutableList<ExternalToggle> = CopyOnWriteArrayList()

    /** Registers a toggle that appears in /fm's Dungeon column, e.g. for an addon's own on/off switch. */
    @JvmStatic
    fun registerDungeonToggle(name: String, description: String, get: Supplier<Boolean>, set: Consumer<Boolean>) {
        dungeonToggles.add(ExternalToggle(name, description, get, set))
    }
}
