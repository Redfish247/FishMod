package fishmod.utils.config

import fishmod.utils.FishConstants
import fishmod.utils.debug.FishDebug
import fishmod.utils.debug.FishDiag
import java.io.File

object FishPaths {

    @JvmField
    val OLD_PATH: String = "./config/"

    @JvmField
    val CONFIG_PATH: String = "config/fishmod/"

    @JvmField
    val ADDONS_NAME: String = FishConstants.NAMESPACE + ".json"

    @JvmStatic
    fun init() {
        val file = File(CONFIG_PATH)
        if (!file.exists()) {
            if (!file.mkdirs()) {
                FishDiag.fail("FishPaths.1", "failed to create config directory ${file.absolutePath}")
                FishDebug.LOGGER.error("Failed to create fishmod directory")
            }
        }
    }

}
