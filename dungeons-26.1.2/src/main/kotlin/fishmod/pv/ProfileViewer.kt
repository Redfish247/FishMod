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
	// NEU repo folder; -Dfishmod.neuRepoDir lets the test kit share one copy instead of downloading per run
	@JvmField
	val NEU_DIR: Path = System.getProperty("fishmod.neuRepoDir")?.let { Path.of(it) } ?: DIR.resolve("neu-repo")

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
