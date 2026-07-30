package fishmod.utils.config

import fishmod.utils.Constants
import fishmod.utils.debug.Debug
import java.io.File

object FolderUtility {

    @JvmField
    val OLD_PATH: String = "./config/"

    @JvmField
    val CONFIG_PATH: String = "config/fishmod/"

    @JvmField
    val ADDONS_NAME: String = Constants.NAMESPACE + ".json"

    @JvmStatic
    fun init() {
        val file = File(CONFIG_PATH)
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Debug.LOGGER.error("Failed to create fishmod directory")
            }
        }
    }

}
