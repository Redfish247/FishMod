package blade.addon;

import blade.addon.features.warpmap.WarpMapFeature;
import blade.addon.utils.config.FolderUtility;
import blade.addon.utils.config.Config;
import blade.addon.utils.Keybinds;
import blade.addon.utils.events.CustomEvents;
import blade.addon.utils.debug.Debug;
import blade.addon.utils.Location;
import blade.addon.utils.Scheduler;
import blade.addon.features.dungeon.DungeonDeathMessage;
import blade.addon.features.dungeon.PartyCommandHandler;
import blade.addon.features.dungeon.PartyInviteAccepter;
import blade.addon.features.dungeon.FishEstTotal;
import blade.addon.features.dungeon.FishPuzzleDisplay;
import blade.addon.features.dungeon.LagTracker;
import blade.addon.utils.data.FishPartyTracker;
import blade.addon.features.dungeon.PuzzleDisplay;
import blade.addon.utils.config.components.Components;
import blade.addon.utils.data.EntityUtil;
import blade.addon.utils.data.PartyUtil;
import blade.addon.utils.dungeon.Phase;
import blade.addon.utils.dungeon.Section;
import blade.addon.utils.rendering.RenderingEvents;
import blade.addon.features.FishModScreen;
import blade.addon.utils.Constants;
import blade.addon.utils.Misc;
import blade.addon.utils.config.FishConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

public class FishModInit implements ModInitializer {
    @Override
    public void onInitialize() {
        // Load FishMod-specific config (always, separate from blade config)
        FishConfig.manager.load();

        // Always init FishMod-exclusive classes (always load from FishMod's jar)
        LagTracker.init();
        FishPuzzleDisplay.init();
        FishEstTotal.init();
        DungeonDeathMessage.init();
        FishPartyTracker.init();
        PartyCommandHandler.init();
        PartyInviteAccepter.init();

        // Always register /fm and /fmdbg regardless of whether blade is loaded
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("fm")
                .executes(context -> {
                    MinecraftClient.getInstance().send(() ->
                        MinecraftClient.getInstance().setScreen(new FishModScreen()));
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("fmdbg").executes(context -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.send(() -> {
                    Misc.addChatMessage(Text.literal("§b--- FishMod Debug ---"));
                    Misc.addChatMessage(Text.literal("§7Location: §f" + Location.getCurrentLocation()));
                    Misc.addChatMessage(Text.literal("§7inSkyblock: §f" + Location.inSkyblock()));
                    Misc.addChatMessage(Text.literal("§7inDungeon: §f" + Location.inDungeon()));
                    Misc.addChatMessage(Text.literal("§7showPuzzles: §f" + blade.addon.utils.config.values.FishSettings.showPuzzles));
                    Misc.addChatMessage(Text.literal("§7Puzzle list (" + FishPuzzleDisplay.getPuzzles().size() + "): §f" + FishPuzzleDisplay.getPuzzles()));
                    try { Misc.addChatMessage(Text.literal("§7Phase.runStarted: §f" + Phase.runStarted())); } catch (Throwable t) { Misc.addChatMessage(Text.literal("§cPhase.runStarted ERR: " + t.getMessage())); }
                    try { Misc.addChatMessage(Text.literal("§7Phase.enableSplits: §f" + Phase.enableSplits)); } catch (Throwable t) { Misc.addChatMessage(Text.literal("§cPhase.enableSplits ERR: " + t.getMessage())); }
                    try { Misc.addChatMessage(Text.literal("§7blade loaded: §f" + FabricLoader.getInstance().isModLoaded("blade-addons"))); } catch (Throwable t) { Misc.addChatMessage(Text.literal("§cloader ERR")); }
                    // Dump tab list
                    ClientPlayNetworkHandler handler = mc.getNetworkHandler();
                    if (handler == null) {
                        Misc.addChatMessage(Text.literal("§cNo network handler"));
                    } else {
                        int total = 0, nullName = 0;
                        for (PlayerListEntry e : handler.getPlayerList()) {
                            total++;
                            if (e.getDisplayName() == null) { nullName++; continue; }
                            String raw = e.getDisplayName().getString();
                            String clean = raw.replaceAll("§.", "").trim();
                            if (!clean.isEmpty())
                                Misc.addChatMessage(Text.literal("§8TAB: §7" + clean));
                        }
                        Misc.addChatMessage(Text.literal("§7Tab entries: §f" + total + " (§c" + nullName + " null§7)"));
                    }
                    Misc.addChatMessage(Text.literal("§b--- End Debug ---"));
                });
                return Constants.SUCCESS;
            }));
        });


        // ── Warp Map HUD — always active regardless of blade-addons ──────────
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> WarpMapFeature.renderHud(ctx, tickCounter));

        if (!FabricLoader.getInstance().isModLoaded("blade-addons")) {
            // Standalone — init the full shared framework
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
        }
    }
}
