package blade.addon;

import blade.addon.features.BossBarFeature;
import blade.addon.features.wiki.WikiScreen;
import blade.addon.features.BridgeBot;
import blade.addon.features.croesus.CroesusLootScreen;
import blade.addon.features.croesus.CroesusOverlay;
import blade.addon.features.croesus.CroesusTracker;
import blade.addon.features.FishHudEditor;
import blade.addon.features.PowderTracker;
import blade.addon.features.SlayerXpTracker;
import blade.addon.features.SoulflowHud;
import blade.addon.features.PetHud;
import blade.addon.features.CooldownOverlay;
import blade.addon.features.ItemRarityHotbar;
import blade.addon.features.DotCompletion;
import blade.addon.mixin.accessors.ChatScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import blade.addon.features.dungeon.SessionStats;
import blade.addon.features.warpmap.WarpMapFeature;
import blade.addon.utils.config.FolderUtility;
import blade.addon.utils.config.Config;
import blade.addon.utils.Keybinds;
import blade.addon.utils.events.CustomEvents;
import blade.addon.utils.debug.Debug;
import blade.addon.utils.Location;
import blade.addon.utils.MayorApi;
import blade.addon.utils.Scheduler;
import blade.addon.features.dungeon.DungeonDeathMessage;
import blade.addon.features.dungeon.PartyCommandHandler;
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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

public class FishModInit implements ModInitializer {

    /** True when text matches "/pc .x", "/gc .x", etc. — a channel prefix followed by a dot-command. */
    private static boolean looksLikeChannelDot(String text) {
        int sp = text.indexOf(' ');
        if (sp <= 0 || sp + 1 >= text.length() || text.charAt(sp + 1) != '.') return false;
        String head = text.substring(0, sp).toLowerCase();
        return head.equals("/pc") || head.equals("/gc") || head.equals("/ac")
                || head.equals("/oc") || head.equals("/msg") || head.equals("/r");
    }

    @Override
    public void onInitialize() {
        // Load FishMod-specific config (always, separate from blade config)
        FishConfig.manager.load();

        // Always init FishMod-exclusive classes (always load from FishMod's jar)
        LagTracker.init();
SessionStats.init();
        FishPuzzleDisplay.init();
        FishEstTotal.init();
        DungeonDeathMessage.init();
        FishPartyTracker.init();
        PartyCommandHandler.init();
        SoulflowHud.init();
        PetHud.init();
        CooldownOverlay.init();
        MayorApi.init();
        BridgeBot.init();
        SlayerXpTracker.init();
        blade.addon.features.SkillTracker.init();
        blade.addon.features.FireFreezeTimer.init();
        PowderTracker.init();
        CroesusTracker.init();
        CroesusOverlay.init();
        blade.addon.features.dungeon.SimonSaysTracker.init();

        // Register all HUD elements in FishHudEditor (position drag editor)
        FishHudEditor.register("Splits",    Phase.splitTimer);
        FishHudEditor.registerLocked("Est. Total (follows Splits)",
                () -> { try { return Phase.splitTimer.getScaledX(); } catch (Throwable t) { return 0; } },
                () -> { try { return Phase.splitTimer.getScaledY() + blade.addon.utils.Constants.TEXT_HEIGHT * Phase.getVisibleRowCount() + 8; } catch (Throwable t) { return Phase.splitTimer.getScaledY() + 20; } },
                Phase.SPLIT_LENGTH, blade.addon.utils.Constants.TEXT_HEIGHT + 4);
        FishHudEditor.register("Puzzles",   FishPuzzleDisplay.puzzleHud);
        FishHudEditor.register("Slayer XP",
                () -> blade.addon.utils.config.values.FishSettings.slayerXpHudX,
                v  -> blade.addon.utils.config.values.FishSettings.slayerXpHudX = v,
                () -> blade.addon.utils.config.values.FishSettings.slayerXpHudY,
                v  -> blade.addon.utils.config.values.FishSettings.slayerXpHudY = v, 160, 30,
                () -> blade.addon.utils.config.values.FishSettings.slayerXpScale,
                v  -> blade.addon.utils.config.values.FishSettings.slayerXpScale = v,
                () -> blade.addon.features.SlayerXpTracker.isBossActive());
        FishHudEditor.register("Skill XP",
                () -> blade.addon.utils.config.values.FishSettings.skillTrackerHudX,
                v  -> blade.addon.utils.config.values.FishSettings.skillTrackerHudX = v,
                () -> blade.addon.utils.config.values.FishSettings.skillTrackerHudY,
                v  -> blade.addon.utils.config.values.FishSettings.skillTrackerHudY = v, 160, 60,
                () -> blade.addon.utils.config.values.FishSettings.skillTrackerScale,
                v  -> blade.addon.utils.config.values.FishSettings.skillTrackerScale = v,
                () -> blade.addon.features.SkillTracker.hasData());
        FishHudEditor.register("Powder",
                () -> blade.addon.utils.config.values.FishSettings.powderTrackerHudX,
                v  -> blade.addon.utils.config.values.FishSettings.powderTrackerHudX = v,
                () -> blade.addon.utils.config.values.FishSettings.powderTrackerHudY,
                v  -> blade.addon.utils.config.values.FishSettings.powderTrackerHudY = v, 160, 60,
                () -> blade.addon.utils.config.values.FishSettings.powderTrackerScale,
                v  -> blade.addon.utils.config.values.FishSettings.powderTrackerScale = v,
                () -> blade.addon.features.PowderTracker.isInMiningArea() && blade.addon.utils.config.values.FishSettings.powderTrackerEnabled);

        // Always register /fm and /fmdbg regardless of whether blade is loaded
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("fm")
                .executes(context -> {
                    MinecraftClient.getInstance().send(() ->
                        MinecraftClient.getInstance().setScreen(new FishModScreen()));
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("fmloot")
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new CroesusLootScreen(mc.currentScreen)));
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("streams")
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new blade.addon.features.streams.StreamsScreen(mc.currentScreen)));
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("wiki")
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new WikiScreen(mc.currentScreen, "")));
                    return Constants.SUCCESS;
                })
                .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String query = StringArgumentType.getString(ctx, "query");
                        MinecraftClient mc = MinecraftClient.getInstance();
                        mc.send(() -> mc.setScreen(new WikiScreen(mc.currentScreen, query)));
                        return Constants.SUCCESS;
                    })
                )
            );
            // ── Party alias commands ──────────────────────────────────────────
            dispatcher.register(ClientCommandManager.literal("pk")
                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("p kick " + name);
                        return Constants.SUCCESS;
                    })
                )
            );
            dispatcher.register(ClientCommandManager.literal("pw")
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("p warp");
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("pt")
                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("p transfer " + name);
                        return Constants.SUCCESS;
                    })
                )
            );
            dispatcher.register(ClientCommandManager.literal("pp")
                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("p promote " + name);
                        return Constants.SUCCESS;
                    })
                )
            );
            // ─────────────────────────────────────────────────────────────────

            dispatcher.register(ClientCommandManager.literal("fmpet").executes(context -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.send(() -> {
                    Misc.addChatMessage(Text.literal("§b--- Pet HUD ---"));
                    Misc.addChatMessage(Text.literal("§7" + PetHud.debugState()));
                    Misc.addChatMessage(Text.literal("§b--- Cooldown Overlay ---"));
                    Misc.addChatMessage(Text.literal("§7" + CooldownOverlay.debugState()));
                });
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmpetdump").executes(context -> {
                PetHud.debugDumpPetLines = !PetHud.debugDumpPetLines;
                Misc.addChatMessage(Text.literal("§b[fmpet] dump pet-related chat lines: §f" + PetHud.debugDumpPetLines));
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmcddump").executes(context -> {
                CooldownOverlay.debugDumpSound = !CooldownOverlay.debugDumpSound;
                Misc.addChatMessage(Text.literal("§b[fmcd] dump cooldown sound events: §f" + CooldownOverlay.debugDumpSound));
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmblocks").executes(context -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.send(() -> {
                    if (mc.player == null || mc.world == null) { Misc.addChatMessage(Text.literal("§cNo world")); return; }
                    net.minecraft.util.math.BlockPos c = mc.player.getBlockPos();
                    java.util.Map<String, Integer> counts = new java.util.HashMap<>();
                    int R = 7;
                    net.minecraft.util.math.BlockPos.Mutable m = new net.minecraft.util.math.BlockPos.Mutable();
                    for (int dx = -R; dx <= R; dx++) for (int dy = -R; dy <= R; dy++) for (int dz = -R; dz <= R; dz++) {
                        m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        net.minecraft.block.Block b = mc.world.getBlockState(m).getBlock();
                        if (b == net.minecraft.block.Blocks.AIR) continue;
                        String id = net.minecraft.registry.Registries.BLOCK.getId(b).toString();
                        counts.merge(id, 1, Integer::sum);
                    }
                    Misc.addChatMessage(Text.literal("§b--- Blocks within " + R + " (top 20) ---"));
                    counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(20)
                        .forEach(e -> Misc.addChatMessage(Text.literal("§7" + e.getValue() + "x §f" + e.getKey())));
                });
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmprofile").executes(context -> {
                blade.addon.utils.HypixelApi.dumpEconomy(MinecraftClient.getInstance());
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmtabdump").executes(context -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.send(() -> {
                    if (mc.getNetworkHandler() == null) { Misc.addChatMessage(Text.literal("§cNo network")); return; }
                    Misc.addChatMessage(Text.literal("§b--- Tab entries (non-empty) ---"));
                    int n = 0;
                    for (net.minecraft.client.network.PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                        if (e.getDisplayName() == null) continue;
                        String s = e.getDisplayName().getString().replaceAll("§.", "").trim();
                        if (s.isEmpty()) continue;
                        if (s.toLowerCase().contains("pet") || s.contains("Lvl") || s.contains("XP") || s.contains("/")) {
                            Misc.addChatMessage(Text.literal("§7" + s));
                            if (++n > 30) break;
                        }
                    }
                    Misc.addChatMessage(Text.literal("§b--- End (" + n + ") ---"));
                });
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmskilldump").executes(context -> {
                blade.addon.features.SkillTracker.debugDump = !blade.addon.features.SkillTracker.debugDump;
                Misc.addChatMessage(Text.literal("§b[skill] dump raw action bar: §f" + blade.addon.features.SkillTracker.debugDump));
                return Constants.SUCCESS;
            }));

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
                    // Dump scoreboard sidebar
                    if (mc.world != null) {
                        net.minecraft.scoreboard.Scoreboard sb = mc.world.getScoreboard();
                        net.minecraft.scoreboard.ScoreboardObjective sidebar = sb.getObjectiveForSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
                        if (sidebar == null) {
                            Misc.addChatMessage(Text.literal("§7Sidebar: §cnone"));
                        } else {
                            Misc.addChatMessage(Text.literal("§7Sidebar obj: §f" + sidebar.getDisplayName().getString()));
                            for (net.minecraft.scoreboard.ScoreboardEntry entry : sb.getScoreboardEntries(sidebar)) {
                                String owner = entry.owner();
                                net.minecraft.scoreboard.Team team = sb.getScoreHolderTeam(owner);
                                String line = team != null
                                    ? team.getPrefix().getString() + owner + team.getSuffix().getString()
                                    : entry.name().getString();
                                String clean = line.replaceAll("§.", "").trim();
                                if (!clean.isEmpty())
                                    Misc.addChatMessage(Text.literal("§8SB: §7" + clean));
                            }
                        }
                    }
                    Misc.addChatMessage(Text.literal("§b--- End Debug ---"));
                });
                return Constants.SUCCESS;
            }).then(ClientCommandManager.argument("sub", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String arg = StringArgumentType.getString(ctx, "sub");
                    MinecraftClient mc = MinecraftClient.getInstance();
                    String[] parts = arg.trim().split("\\s+", 2);
                    if (parts[0].equals("cprice")) {
                        if (parts.length < 2) {
                            mc.send(() -> Misc.addChatMessage(Text.literal("§cUsage: /fmdbg cprice <ITEM_ID>")));
                            return Constants.SUCCESS;
                        }
                        String pid = parts[1].trim().toUpperCase();
                        blade.addon.features.croesus.CroesusPrices.refreshIfStale().whenComplete((v, t) ->
                            mc.send(() -> Misc.addChatMessage(Text.literal("§b" + pid + " §7→ §f"
                                + blade.addon.features.croesus.CroesusPrices.debugSource(pid)))));
                        return Constants.SUCCESS;
                    }
                    if (parts[0].equals("clast")) {
                        java.util.List<blade.addon.features.croesus.CroesusStore.Entry> all =
                                blade.addon.features.croesus.CroesusStore.all();
                        if (all.isEmpty()) { mc.send(() -> Misc.addChatMessage(Text.literal("§7No claims recorded."))); return Constants.SUCCESS; }
                        blade.addon.features.croesus.CroesusStore.Entry e = all.get(all.size() - 1);
                        mc.send(() -> {
                            Misc.addChatMessage(Text.literal("§b--- Last claim: " + e.floor + " " + e.chestType + " ---"));
                            for (blade.addon.features.croesus.CroesusStore.Item it : e.items)
                                Misc.addChatMessage(Text.literal("§7 • §f" + it.name + " §8[" + it.id + "] §7x" + it.count
                                    + " §e@" + (long)it.priceAtClaim));
                        });
                        return Constants.SUCCESS;
                    }
                    if (parts[0].equals("mp")) {
                        String ign = parts.length > 1 ? parts[1] : (mc.player != null ? mc.player.getName().getString() : null);
                        if (ign == null) { mc.send(() -> Misc.addChatMessage(Text.literal("§cUsage: /fmdbg mp <ign>"))); return Constants.SUCCESS; }
                        final String finalIgn = ign;
                        blade.addon.utils.HypixelApi.getByName(mc, ign, data ->
                            mc.send(() -> Misc.addChatMessage(Text.literal("§b" + finalIgn + " magicalPower=§f" + data.magicalPower))));
                        return Constants.SUCCESS;
                    }
                    if (parts[0].equals("mpraw")) {
                        String ign = parts.length > 1 ? parts[1] : (mc.player != null ? mc.player.getName().getString() : null);
                        if (ign == null) { mc.send(() -> Misc.addChatMessage(Text.literal("§cUsage: /fmdbg mpraw <ign>"))); return Constants.SUCCESS; }
                        blade.addon.utils.HypixelApi.dumpMemberKeys(mc, ign);
                        return Constants.SUCCESS;
                    }
                    if (parts[0].equals("col")) {
                        String ign = parts.length > 1 ? parts[1] : (mc.player != null ? mc.player.getName().getString() : null);
                        if (ign == null) { mc.send(() -> Misc.addChatMessage(Text.literal("§cUsage: /fmdbg col <ign>"))); return Constants.SUCCESS; }
                        final String finalIgn = ign;
                        blade.addon.utils.HypixelApi.getByName(mc, ign, data -> mc.send(() -> {
                            long cataTotal = 0; for (long t : data.cataTimes) cataTotal += t;
                            long masterTotal = 0; for (int i = 1; i <= 7; i++) masterTotal += data.masterTimes[i];
                            long col = cataTotal + masterTotal * 2;
                            Misc.addChatMessage(Text.literal("§b--- Collection debug: " + finalIgn + " ---"));
                            StringBuilder cata = new StringBuilder("§7cata: ");
                            for (int i = 0; i <= 7; i++) cata.append(i == 0 ? "E" : "F" + i).append("=").append(data.cataTimes[i]).append(" ");
                            Misc.addChatMessage(Text.literal(cata.toString()));
                            StringBuilder master = new StringBuilder("§7master: ");
                            for (int i = 1; i <= 7; i++) master.append("M").append(i).append("=").append(data.masterTimes[i]).append(" ");
                            Misc.addChatMessage(Text.literal(master.toString()));
                            Misc.addChatMessage(Text.literal("§7cataTotal=§f" + cataTotal + " §7masterTotal=§f" + masterTotal));
                            Misc.addChatMessage(Text.literal("§7computed col=§f" + col + " §7(cata×1 + master×2)"));
                        }));
                        return Constants.SUCCESS;
                    }
                    if (parts[0].equals("runs")) {
                        String ign = parts.length > 1 ? parts[1] : (mc.player != null ? mc.player.getName().getString() : null);
                        if (ign == null) { mc.send(() -> Misc.addChatMessage(Text.literal("§cUsage: /fmdbg runs <ign>"))); return Constants.SUCCESS; }
                        final String finalIgn = ign;
                        blade.addon.utils.HypixelApi.getByName(mc, ign, data -> mc.send(() -> {
                            Misc.addChatMessage(Text.literal("§b--- Runs debug: " + finalIgn + " ---"));
                            Misc.addChatMessage(Text.literal("§7totalRuns: §f" + data.totalRuns));
                            StringBuilder cata = new StringBuilder("§7cataTimes: ");
                            for (int i = 0; i <= 7; i++) cata.append("F").append(i == 0 ? "E" : String.valueOf(i)).append("=").append(data.cataTimes[i]).append(" ");
                            Misc.addChatMessage(Text.literal(cata.toString()));
                            StringBuilder master = new StringBuilder("§7masterTimes: ");
                            for (int i = 1; i <= 7; i++) master.append("M").append(i).append("=").append(data.masterTimes[i]).append(" ");
                            Misc.addChatMessage(Text.literal(master.toString()));
                            Misc.addChatMessage(Text.literal("§b--- End ---"));
                        }));
                    }
                    return Constants.SUCCESS;
                })
            ));
        });


        // ── Warp Map HUD + click detection ───────────────────────────────────
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> WarpMapFeature.renderHud(ctx, tickCounter));
        ClientTickEvents.END_CLIENT_TICK.register(WarpMapFeature::tickClickDetection);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> SoulflowHud.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> PetHud.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> ItemRarityHotbar.renderHotbar(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> CooldownOverlay.renderHotbar(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> SlayerXpTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> blade.addon.features.SkillTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> PowderTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> BossBarFeature.renderHud(ctx));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> SessionStats.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> blade.addon.features.dungeon.DungeonScore.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> blade.addon.features.FarmingTracker.renderHud(ctx, tickCounter));
        blade.addon.features.dungeon.DungeonScore.init();
        blade.addon.utils.SkyblockItems.initAsync();
        blade.addon.features.FarmingTracker.init();
        blade.addon.features.HarvestFeastTracker.init();
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> blade.addon.features.HarvestFeastTracker.renderHud(ctx, tickCounter));
        blade.addon.features.MiningTracker.init();
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> blade.addon.features.MiningTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> CroesusOverlay.renderHud(ctx, tickCounter));

        // Tracker overlay (reset button) for HandledScreens — fires after full render chain
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>)) return;
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                SessionStats.renderInScreen(ctx, mx, my);
                PowderTracker.renderInScreen(ctx, mx, my);
                SlayerXpTracker.renderInScreen(ctx, mx, my);
                blade.addon.features.SkillTracker.renderInScreen(ctx, mx, my);
                blade.addon.features.FarmingTracker.renderInScreen(ctx, mx, my);
                blade.addon.features.HarvestFeastTracker.renderInScreen(ctx, mx, my);
                blade.addon.features.MiningTracker.renderInScreen(ctx, mx, my);
            });
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
                if (click.button() != 0) return true; // only left click resets
                double mx = click.x(), my = click.y();
                if (SessionStats.handleScreenClick(mx, my)) return false;
                if (PowderTracker.handleScreenClick(mx, my)) return false;
                if (SlayerXpTracker.handleScreenClick(mx, my)) return false;
                if (blade.addon.features.FarmingTracker.handleScreenClick(mx, my)) return false;
                if (blade.addon.features.HarvestFeastTracker.handleScreenClick(mx, my)) return false;
                if (blade.addon.features.MiningTracker.handleScreenClick(mx, my)) return false;
                return true;
            });
        });

        // Tab-complete for dot-prefix commands in chat (any chat — works in party chat too).
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen cs)) return;
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, input) -> {
                if (input.key() != GLFW.GLFW_KEY_TAB) return true;
                TextFieldWidget cf = ((ChatScreenAccessor) cs).getChatField();
                if (cf == null) return true;
                String text = cf.getText();
                if (text == null) return true;
                // Let vanilla tab work for real slash commands; block it for plain chat to avoid noisy word suggestions.
                if (text.isEmpty()) return false;
                if (text.charAt(0) == '/' && !looksLikeChannelDot(text)) return true;

                int dotOffset = 0;
                if (text.charAt(0) != '.') {
                    if (!looksLikeChannelDot(text)) return false;
                    int sp = text.indexOf(' ');
                    dotOffset = sp + 1;
                }

                String dotText = text.substring(dotOffset);
                int dotCursor = Math.max(0, cf.getCursor() - dotOffset);
                DotCompletion.Result r = DotCompletion.complete(dotText, dotCursor);
                if (r == null) return false;
                String newText = text.substring(0, dotOffset) + r.newText();
                cf.setText(newText);
                cf.setCursor(dotOffset + r.newCursor(), false);
                return false;
            });

            // Suggestion list popup above the chat input.
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                TextFieldWidget cf = ((ChatScreenAccessor) cs).getChatField();
                if (cf == null) return;
                String text = cf.getText();
                if (text == null || text.isEmpty()) return;
                int dotOffset = 0;
                if (text.charAt(0) != '.') {
                    if (!looksLikeChannelDot(text)) return;
                    dotOffset = text.indexOf(' ') + 1;
                }
                String dotText = text.substring(dotOffset);
                int dotCursor = Math.max(0, cf.getCursor() - dotOffset);
                DotCompletion.Suggestions sug = DotCompletion.listCandidates(dotText, dotCursor);
                if (sug == null || sug.candidates().isEmpty()) return;

                var tr = MinecraftClient.getInstance().textRenderer;
                int max = Math.min(10, sug.candidates().size());
                int maxW = 0;
                for (int i = 0; i < max; i++) {
                    int wpx = tr.getWidth(sug.candidates().get(i));
                    if (wpx > maxW) maxW = wpx;
                }
                int lineH = tr.fontHeight + 2;
                int boxW = maxW + 4;
                int boxH = max * lineH;
                int x = 4;
                int chatBottomY = cs.height - 14;
                int boxY = chatBottomY - boxH - 1;
                ctx.fill(x, boxY, x + boxW, boxY + boxH, 0xD0000000);
                for (int i = 0; i < max; i++) {
                    ctx.drawText(tr, sug.candidates().get(i), x + 2, boxY + i * lineH + 1, 0xFFFFFF55, false);
                }
            });
        });

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
            PartyUtil.init();
            EntityUtil.init();
            RenderingEvents.init();
            Scheduler.init();

        }
    }
}
