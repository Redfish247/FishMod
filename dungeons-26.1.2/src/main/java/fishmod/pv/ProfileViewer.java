package fishmod.pv;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProfileViewer {
	public static final String NAMESPACE = "fishmod";
	/** Classpath root of the PV assets. */
	public static final String ASSETS = "/assets/fishmod/pv/";
	public static final Logger LOGGER = LoggerFactory.getLogger("FishPV");
	/** Disk caches (NEU repo, SkyBlock pack, responses). */
	public static final Path DIR = configDir().resolve("fishmod").resolve("pv");

	private ProfileViewer() {}

	private static Path configDir() {
		try {
			return FabricLoader.getInstance().getConfigDir();
		} catch (Throwable t) {
			// unit tests run without a loader
			return Path.of("config");
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, "pv/" + path);
	}

	/** True when a standalone profile viewer mod owns /pv. */
	public static boolean standaloneLoaded() {
		FabricLoader loader = FabricLoader.getInstance();
		return loader.isModLoaded("betterpv") || loader.isModLoaded("fishpv");
	}
}
