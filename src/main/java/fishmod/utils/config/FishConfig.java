package fishmod.utils.config;

import fishmod.features.dungeon.FishEstTotal;
import fishmod.features.dungeon.FishPuzzleDisplay;
import fishmod.utils.config.values.FishSettings;
import config.practical.manager.ConfigManager;

import java.util.List;

/**
 * Separate config manager for FishMod-specific settings.
 * Stored in config/fishmod-settings.json, independent of blade-addons config.
 */
public class FishConfig {

    public static final ConfigManager manager = new ConfigManager(
            "config/fishmod-settings.json",
            List.of(FishSettings.class, FishPuzzleDisplay.class, FishEstTotal.class,
                    fishmod.utils.dungeon.Phase.class,
                    fishmod.utils.dungeon.Section.class,
                    fishmod.utils.dungeon.Split.class,
                    fishmod.utils.config.values.Dungeons.class));
}
