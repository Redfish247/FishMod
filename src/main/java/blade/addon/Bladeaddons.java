package blade.addon;

import blade.addon.features.dungeon.PuzzleDisplay;
import blade.addon.features.other.SearchBar;
import blade.addon.utils.Keybinds;
import blade.addon.utils.Location;
import blade.addon.utils.Scheduler;
import blade.addon.utils.config.Config;
import blade.addon.utils.config.FolderUtility;
import blade.addon.utils.config.components.Components;
import blade.addon.utils.data.EntityUtil;
import blade.addon.utils.data.PartyUtil;
import blade.addon.utils.debug.Debug;
import blade.addon.utils.dungeon.Phase;
import blade.addon.utils.dungeon.Section;
import blade.addon.utils.events.CustomEvents;
import blade.addon.utils.rendering.RenderingEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class Bladeaddons implements ModInitializer {
    @Override
    public void onInitialize() {
        boolean bladePresent = FabricLoader.getInstance().isModLoaded("blade-addons");

        if (!bladePresent) {
            // Standalone mode — init the full framework
            FolderUtility.init();
            Components.init();
            Config.manager.load();
            Keybinds.init();
            CustomEvents.init();
            Debug.init();
            Location.init();
            Phase.init();
            Section.init();
            PuzzleDisplay.init();
            PartyUtil.init();
            EntityUtil.init();
            RenderingEvents.init();
            Scheduler.init();
            SearchBar.init();
        }
        // FishMod-specific features — always init regardless of blade presence
    }
}
