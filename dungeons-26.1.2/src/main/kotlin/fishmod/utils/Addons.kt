package fishmod.utils

import net.fabricmc.loader.api.FabricLoader

object Addons {
    @JvmStatic
    val fishModAddonsInstalled: Boolean by lazy { FabricLoader.getInstance().isModLoaded("fishmodaddons") }
}
