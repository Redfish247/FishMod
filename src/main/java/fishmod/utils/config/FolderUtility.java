package fishmod.utils.config;

import fishmod.utils.Constants;
import fishmod.utils.debug.Debug;

import java.io.File;

public class FolderUtility {

    public static final String OLD_PATH = "./config/";
    public static final String CONFIG_PATH = "config/fishmod/";

    public static final String ADDONS_NAME = Constants.NAMESPACE + ".json";

    public static void init() {
        File file = new File(CONFIG_PATH);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Debug.LOGGER.error("Failed to create fishmod directory");
            }
        }
    }

}
