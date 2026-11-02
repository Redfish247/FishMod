package fishmod.pv

import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ProfileViewer {
	const val NAMESPACE = "fishmod"
	/** Classpath root of the PV assets. */
	const val ASSETS = "/assets/fishmod/pv/"
	@JvmField
	val LOGGER: Logger = LoggerFactory.getLogger("FishPV")
	/** Disk caches (NEU repo, SkyBlock pack, responses). */
	@JvmField
	val DIR: Path = configDir().resolve("fishmod").resolve("pv")

	private fun configDir(): Path =
		try {
			FabricLoader.getInstance().configDir
		} catch (_: Throwable) {
			// unit tests run without a loader
			Path.of("config")
		}

	@JvmStatic
	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(NAMESPACE, "pv/$path")

	/** True when a standalone profile viewer mod owns /pv. */
	@JvmStatic
	fun standaloneLoaded(): Boolean {
		val loader = FabricLoader.getInstance()
		return loader.isModLoaded("betterpv") || loader.isModLoaded("fishpv")
	}
}
