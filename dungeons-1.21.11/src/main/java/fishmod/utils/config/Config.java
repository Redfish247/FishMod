package fishmod.utils.config;

import fishmod.features.dungeon.PuzzleDisplay;
import fishmod.utils.config.components.Components;
import fishmod.utils.config.values.Dungeons;
import fishmod.utils.config.values.ExtraOptions;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.dungeon.Section;
import fishmod.utils.dungeon.Split;
import config.practical.manager.ConfigManager;

import java.util.List;

public class Config {

    public static final ConfigManager manager = new ConfigManager(
            FolderUtility.OLD_PATH + FolderUtility.ADDONS_NAME,
            List.of(Phase.class, Section.class, Split.class, ExtraOptions.class,
                    Components.class, Dungeons.class, PuzzleDisplay.class));
}
