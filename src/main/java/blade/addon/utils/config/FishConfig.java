package blade.addon.utils.config;

import blade.addon.features.dungeon.FishEstTotal;
import blade.addon.features.dungeon.FishPuzzleDisplay;
import blade.addon.utils.config.values.FishSettings;
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
                    blade.addon.utils.dungeon.Phase.class,
                    blade.addon.utils.dungeon.Section.class,
                    blade.addon.utils.dungeon.Split.class,
                    blade.addon.utils.config.values.Dungeons.class,
                    blade.addon.features.dungeon.PuzzleDisplay.class));
}
