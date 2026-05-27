package fishmod;

import fishmod.features.dungeon.PuzzleDisplay;
import fishmod.features.other.SearchBar;
import fishmod.utils.Keybinds;
import fishmod.utils.Location;
import fishmod.utils.Scheduler;
import fishmod.utils.config.Config;
import fishmod.utils.config.FolderUtility;
import fishmod.utils.config.components.Components;
import fishmod.utils.data.EntityUtil;
import fishmod.utils.data.PartyUtil;
import fishmod.utils.debug.Debug;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.dungeon.Section;
import fishmod.utils.events.CustomEvents;
import fishmod.utils.rendering.RenderingEvents;
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
