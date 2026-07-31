package fishmod.features

import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Supplier

/** Lets other mods (e.g. FishModAddons) register a Dungeon-column toggle in /fm without depending on FishMod's internal types. */
object FishModAddonApi {

    class ExternalToggle(
        private val toggleName: String,
        private val toggleDescription: String,
        private val toggleGet: Supplier<Boolean>,
        private val toggleSet: Consumer<Boolean>
    ) {
        fun name(): String = toggleName
        fun description(): String = toggleDescription
        fun get(): Supplier<Boolean> = toggleGet
        fun set(): Consumer<Boolean> = toggleSet
    }

    @JvmField
    val dungeonToggles: MutableList<ExternalToggle> = CopyOnWriteArrayList()

    @JvmStatic
    fun registerDungeonToggle(name: String, description: String, get: Supplier<Boolean>, set: Consumer<Boolean>) {
        dungeonToggles.add(ExternalToggle(name, description, get, set))
    }
}
