package fishmod.pv

import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ProfileViewer {
	const val NAMESPACE = "fishmod"
	const val ASSETS = "/assets/fishmod/pv/"
	@JvmField
	val LOGGER: Logger = LoggerFactory.getLogger("FishPV")
	@JvmField
	val DIR: Path = configDir().resolve("fishmod").resolve("pv")

	private fun configDir(): Path =
		try {
			FabricLoader.getInstance().configDir
		} catch (_: Throwable) {
			Path.of("config")
		}

	@JvmStatic
	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(NAMESPACE, "pv/$path")

	@JvmStatic
	fun standaloneLoaded(): Boolean {
		val loader = FabricLoader.getInstance()
		return loader.isModLoaded("betterpv") || loader.isModLoaded("fishpv")
	}
}
