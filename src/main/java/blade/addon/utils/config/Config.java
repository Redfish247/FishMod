package blade.addon.utils.config;

import blade.addon.features.dungeon.PuzzleDisplay;
import blade.addon.utils.config.components.Components;
import blade.addon.utils.config.values.Dungeons;
import blade.addon.utils.config.values.ExtraOptions;
import blade.addon.utils.dungeon.Phase;
import blade.addon.utils.dungeon.Section;
import blade.addon.utils.dungeon.Split;
import config.practical.manager.ConfigManager;

import java.util.List;

public class Config {

    public static final ConfigManager manager = new ConfigManager(
            FolderUtility.OLD_PATH + FolderUtility.ADDONS_NAME,
            List.of(Phase.class, Section.class, Split.class, ExtraOptions.class,
                    Components.class, Dungeons.class, PuzzleDisplay.class));
}
