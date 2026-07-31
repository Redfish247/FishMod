package fishmod.utils

import net.fabricmc.loader.api.FabricLoader

/** Detects optional companion mods (e.g. FishModAddons) that unlock non-legit/cheat-adjacent options. */
object Addons {
    @JvmStatic
    val fishModAddonsInstalled: Boolean by lazy { FabricLoader.getInstance().isModLoaded("fishmodaddons") }
}
