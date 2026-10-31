package fishmod.features

import fishmod.compat.showScreen
import fishmod.utils.debug.FishDiag
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.function.Supplier

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

        /** Sub-setting factories in registration order; they live on the toggle so removing it removes them. */
        val subSettings: MutableList<() -> FishModScreen.Setting> = CopyOnWriteArrayList()
    }

    /** 2 = addons may attach sub-settings to cheat toggles (addCheat*Setting). */
    @JvmField
    val API_VERSION: Int = 2

    @JvmField
    val dungeonToggles: MutableList<ExternalToggle> = CopyOnWriteArrayList()

    @JvmStatic
    fun registerDungeonToggle(name: String, description: String, get: Supplier<Boolean>, set: Consumer<Boolean>) {
        if (name.isBlank()) FishDiag.fail("FishModAddonApi.1", "addon dungeon toggle registered with blank name")
        if (dungeonToggles.any { it.name() == name }) FishDiag.fail("FishModAddonApi.2", "addon dungeon toggle '$name' registered twice")
        dungeonToggles.add(ExternalToggle(name, description, get, set))
    }

    @JvmField
    val cheatToggles: MutableList<ExternalToggle> = CopyOnWriteArrayList()

    @JvmStatic
    fun registerCheatToggle(name: String, description: String, get: Supplier<Boolean>, set: Consumer<Boolean>) {
        if (name.isBlank()) FishDiag.fail("FishModAddonApi.3", "addon cheat toggle registered with blank name")
        if (cheatToggles.any { it.name() == name }) FishDiag.fail("FishModAddonApi.4", "addon cheat toggle '$name' registered twice")
        cheatToggles.add(ExternalToggle(name, description, get, set))
    }

    private fun cheatParent(parent: String, name: String): ExternalToggle? {
        if (!FishDiag.check(name.isNotBlank(), "FishModAddonApi.5") { "addon cheat sub-setting for '$parent' registered with blank name" }) return null
        return FishDiag.notNull(cheatToggles.firstOrNull { it.name() == parent }, "FishModAddonApi.6") {
            "addon cheat sub-setting '$name' ignored: no cheat toggle named '$parent'"
        }
    }

    @JvmStatic
    fun addCheatToggleSetting(parent: String, name: String, description: String, get: Supplier<Boolean>, set: Consumer<Boolean>) {
        cheatParent(parent, name)?.subSettings?.add {
            FishModScreen.ToggleSetting(name, description, { get.get() }, { v -> set.accept(v) })
        }
    }

    @JvmStatic
    fun addCheatChoiceSetting(parent: String, name: String, description: String, options: java.util.List<String>, get: Supplier<String>, set: Consumer<String>) {
        val opts = options.toTypedArray<String>()
        // all legacy colour names -> swatches and "Dark Purple" style labels
        val rgbOf = { s: String -> fishmod.compat.Fmt.codeOfName(s)?.let { fishmod.compat.Fmt.colorOf(it) } }
        val isColors = opts.all { rgbOf(it) != null }
        cheatParent(parent, name)?.subSettings?.add {
            if (isColors) FishModScreen.DropdownSetting(name, description, opts, { get.get() }, { v -> set.accept(v) },
                { s -> s.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } } },
                { s -> (rgbOf(s) ?: 0xFFFFFF) or 0xFF000000.toInt() })
            else FishModScreen.DropdownSetting(name, description, opts, { get.get() }, { v -> set.accept(v) })
        }
    }

    @JvmStatic
    fun addCheatTextSetting(parent: String, name: String, description: String, hint: String, get: Supplier<String>, set: Consumer<String>) {
        cheatParent(parent, name)?.subSettings?.add {
            FishModScreen.InputSetting(name, if (hint.isBlank()) description else if (description.isBlank()) hint else "$description ($hint)", { get.get() }, { v -> set.accept(v) })
        }
    }

    @JvmStatic
    fun addCheatKeybindSetting(parent: String, name: String, description: String, key: Supplier<net.minecraft.client.KeyMapping>) {
        cheatParent(parent, name)?.subSettings?.add {
            FishModScreen.KeybindSetting(name, description, { key.get() })
        }
    }

    /** Opens the same screen /fm opens. */
    @JvmStatic
    fun openSettings() {
        val mc = net.minecraft.client.Minecraft.getInstance()
        mc.schedule { mc.showScreen(FishModScreen()) }
    }
}
