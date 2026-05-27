package fishmod;

import fishmod.features.BossBarFeature;
import fishmod.features.wiki.WikiScreen;
import fishmod.features.BridgeBot;
import fishmod.features.croesus.CroesusLootScreen;
import fishmod.features.croesus.CroesusOverlay;
import fishmod.features.croesus.CroesusTracker;
import fishmod.features.FishHudEditor;
import fishmod.features.PowderTracker;
import fishmod.features.SlayerXpTracker;
import fishmod.features.SoulflowHud;
import fishmod.features.PetHud;
import fishmod.features.CooldownOverlay;
import fishmod.features.ItemRarityHotbar;
import fishmod.mixin.accessors.ChatScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import fishmod.features.dungeon.SessionStats;
import fishmod.features.warpmap.WarpMapFeature;
import fishmod.utils.config.FolderUtility;
import fishmod.utils.config.Config;
import fishmod.utils.Keybinds;
import fishmod.utils.events.CustomEvents;
import fishmod.utils.debug.Debug;
import fishmod.utils.Location;
import fishmod.utils.MayorApi;
import fishmod.utils.Scheduler;
import fishmod.features.dungeon.DungeonDeathMessage;
import fishmod.features.dungeon.PartyCommandHandler;
import fishmod.features.dungeon.FishEstTotal;
import fishmod.features.dungeon.FishPuzzleDisplay;
import fishmod.features.dungeon.LagTracker;
import fishmod.utils.data.FishPartyTracker;
import fishmod.features.dungeon.PuzzleDisplay;
import fishmod.utils.config.components.Components;
import fishmod.utils.data.EntityUtil;
import fishmod.utils.data.PartyUtil;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.dungeon.Section;
import fishmod.utils.rendering.RenderingEvents;
import fishmod.features.FishModScreen;
import fishmod.utils.Constants;
import fishmod.utils.Misc;
import fishmod.utils.config.FishConfig;
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

    /** Runs a party-command lookup locally and prints the result in your own chat (no party message). */
    private static int runLocalLookup(String cmd, String arg1, String arg2) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String self = (mc.player != null) ? mc.player.getGameProfile().name() : null;
        if (self == null) return Constants.SUCCESS;
        fishmod.features.dungeon.PartyCommandHandler.onPartyCommand(
                self, cmd, arg1, arg2, fishmod.features.dungeon.PartyCommandHandler.LOCAL);
        return Constants.SUCCESS;
    }

    /** True if MCEF isn't installed — /wiki needs it, and constructing WikiScreen without it crashes
     *  (NoClassDefFoundError on the MCEF-typed fields). Guard the command before touching WikiScreen. */
    private static boolean wikiUnavailable(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mcef")) return false;
        source.sendFeedback(net.minecraft.text.Text.literal(
                "§c[FishMod] §7/wiki needs the §fMCEF §7mod — install it from Modrinth (MC 1.21.11)."));
        return true;
    }

    @Override
    public void onInitialize() {
        // Load FishMod-specific config (always, separate from blade config)
        FishConfig.manager.load();

        // Cosmetic name changer — restore persisted /nick across sessions
        fishmod.cosmetic.NickData.load();
        // Shared nicks: publish ours + fetch other mod users' nicks
        fishmod.cosmetic.RemoteNicks.init();

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
        fishmod.features.SkillTracker.init();
        fishmod.features.FireFreezeTimer.init();
        PowderTracker.init();
        CroesusTracker.init();
        CroesusOverlay.init();
        fishmod.features.dungeon.SimonSaysTracker.init();

        // Register all HUD elements in FishHudEditor (position drag editor)
        FishHudEditor.register("Splits",    Phase.splitTimer);
        FishHudEditor.registerLocked("Est. Total (follows Splits)",
                () -> { try { return Phase.splitTimer.getScaledX(); } catch (Throwable t) { return 0; } },
                () -> { try { return Phase.splitTimer.getScaledY() + fishmod.utils.Constants.TEXT_HEIGHT * Phase.getVisibleRowCount() + 8; } catch (Throwable t) { return Phase.splitTimer.getScaledY() + 20; } },
                Phase.SPLIT_LENGTH, fishmod.utils.Constants.TEXT_HEIGHT + 4);
        FishHudEditor.register("Puzzles",   FishPuzzleDisplay.puzzleHud);
        FishHudEditor.register("Slayer XP",
                () -> fishmod.utils.config.values.FishSettings.slayerXpHudX,
                v  -> fishmod.utils.config.values.FishSettings.slayerXpHudX = v,
                () -> fishmod.utils.config.values.FishSettings.slayerXpHudY,
                v  -> fishmod.utils.config.values.FishSettings.slayerXpHudY = v, 160, 30,
                () -> fishmod.utils.config.values.FishSettings.slayerXpScale,
                v  -> fishmod.utils.config.values.FishSettings.slayerXpScale = v,
                () -> fishmod.features.SlayerXpTracker.isBossActive());
        FishHudEditor.register("Skill XP",
                () -> fishmod.utils.config.values.FishSettings.skillTrackerHudX,
                v  -> fishmod.utils.config.values.FishSettings.skillTrackerHudX = v,
                () -> fishmod.utils.config.values.FishSettings.skillTrackerHudY,
                v  -> fishmod.utils.config.values.FishSettings.skillTrackerHudY = v, 160, 60,
                () -> fishmod.utils.config.values.FishSettings.skillTrackerScale,
                v  -> fishmod.utils.config.values.FishSettings.skillTrackerScale = v,
                () -> fishmod.features.SkillTracker.hasData());
        FishHudEditor.register("Powder",
                () -> fishmod.utils.config.values.FishSettings.powderTrackerHudX,
                v  -> fishmod.utils.config.values.FishSettings.powderTrackerHudX = v,
                () -> fishmod.utils.config.values.FishSettings.powderTrackerHudY,
                v  -> fishmod.utils.config.values.FishSettings.powderTrackerHudY = v, 160, 60,
                () -> fishmod.utils.config.values.FishSettings.powderTrackerScale,
                v  -> fishmod.utils.config.values.FishSettings.powderTrackerScale = v,
                () -> fishmod.features.PowderTracker.isInMiningArea() && fishmod.utils.config.values.FishSettings.powderTrackerEnabled);

        // Always register /fm and /fmdbg regardless of whether blade is loaded
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("fm")
                .executes(context -> {
                    MinecraftClient.getInstance().send(() ->
                        MinecraftClient.getInstance().setScreen(new fishmod.features.FishModScreen()));
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
                    mc.send(() -> mc.setScreen(new fishmod.features.streams.StreamsScreen(mc.currentScreen)));
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("wiki")
                .executes(ctx -> {
                    if (wikiUnavailable(ctx.getSource())) return Constants.SUCCESS;
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new WikiScreen(mc.currentScreen, "")));
                    return Constants.SUCCESS;
                })
                .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (wikiUnavailable(ctx.getSource())) return Constants.SUCCESS;
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

            dispatcher.register(ClientCommandManager.literal("fmssdebug").executes(context -> {
                fishmod.features.dungeon.SimonSaysTracker.debug = !fishmod.features.dungeon.SimonSaysTracker.debug;
                Misc.addChatMessage(Text.literal("§b[ssdbg] log Simon Says block transitions: §f" + fishmod.features.dungeon.SimonSaysTracker.debug));
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmnuc").executes(context -> {
                fishmod.utils.HypixelApi.dumpNucleus(MinecraftClient.getInstance());
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmgarden").executes(context -> {
                fishmod.utils.HypixelApi.dumpGarden(MinecraftClient.getInstance());
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmprofile").executes(context -> {
                fishmod.utils.HypixelApi.dumpEconomy(MinecraftClient.getInstance());
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
                fishmod.features.SkillTracker.debugDump = !fishmod.features.SkillTracker.debugDump;
                Misc.addChatMessage(Text.literal("§b[skill] dump raw action bar: §f" + fishmod.features.SkillTracker.debugDump));
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmdbg").executes(context -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.send(() -> {
                    Misc.addChatMessage(Text.literal("§b--- FishMod Debug ---"));
                    Misc.addChatMessage(Text.literal("§7Location: §f" + Location.getCurrentLocation()));
                    Misc.addChatMessage(Text.literal("§7inSkyblock: §f" + Location.inSkyblock()));
                    Misc.addChatMessage(Text.literal("§7inDungeon: §f" + Location.inDungeon()));
                    Misc.addChatMessage(Text.literal("§7showPuzzles: §f" + fishmod.utils.config.values.FishSettings.showPuzzles));
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
                        fishmod.features.croesus.CroesusPrices.refreshIfStale().whenComplete((v, t) ->
                            mc.send(() -> Misc.addChatMessage(Text.literal("§b" + pid + " §7→ §f"
                                + fishmod.features.croesus.CroesusPrices.debugSource(pid)))));
                        return Constants.SUCCESS;
                    }
                    if (parts[0].equals("clast")) {
                        java.util.List<fishmod.features.croesus.CroesusStore.Entry> all =
                                fishmod.features.croesus.CroesusStore.all();
                        if (all.isEmpty()) { mc.send(() -> Misc.addChatMessage(Text.literal("§7No claims recorded."))); return Constants.SUCCESS; }
                        fishmod.features.croesus.CroesusStore.Entry e = all.get(all.size() - 1);
                        mc.send(() -> {
                            Misc.addChatMessage(Text.literal("§b--- Last claim: " + e.floor + " " + e.chestType + " ---"));
                            for (fishmod.features.croesus.CroesusStore.Item it : e.items)
                                Misc.addChatMessage(Text.literal("§7 • §f" + it.name + " §8[" + it.id + "] §7x" + it.count
                                    + " §e@" + (long)it.priceAtClaim));
                        });
                        return Constants.SUCCESS;
                    }
                    if (parts[0].equals("mp")) {
                        String ign = parts.length > 1 ? parts[1] : (mc.player != null ? mc.player.getName().getString() : null);
                        if (ign == null) { mc.send(() -> Misc.addChatMessage(Text.literal("§cUsage: /fmdbg mp <ign>"))); return Constants.SUCCESS; }
                        final String finalIgn = ign;
                        fishmod.utils.HypixelApi.getByName(mc, ign, data ->
                            mc.send(() -> Misc.addChatMessage(Text.literal("§b" + finalIgn + " magicalPower=§f" + data.magicalPower))));
                        return Constants.SUCCESS;
                    }
                    if (parts[0].equals("mpraw")) {
                        String ign = parts.length > 1 ? parts[1] : (mc.player != null ? mc.player.getName().getString() : null);
                        if (ign == null) { mc.send(() -> Misc.addChatMessage(Text.literal("§cUsage: /fmdbg mpraw <ign>"))); return Constants.SUCCESS; }
                        fishmod.utils.HypixelApi.dumpMemberKeys(mc, ign);
                        return Constants.SUCCESS;
                    }
                    if (parts[0].equals("col")) {
                        String ign = parts.length > 1 ? parts[1] : (mc.player != null ? mc.player.getName().getString() : null);
                        if (ign == null) { mc.send(() -> Misc.addChatMessage(Text.literal("§cUsage: /fmdbg col <ign>"))); return Constants.SUCCESS; }
                        final String finalIgn = ign;
                        fishmod.utils.HypixelApi.getByName(mc, ign, data -> mc.send(() -> {
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
                        fishmod.utils.HypixelApi.getByName(mc, ign, data -> mc.send(() -> {
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

            // ── Local lookup /commands (native tab-complete; result shown in your own chat) ──
            SuggestionProvider<FabricClientCommandSource> playerSuggest = (c, b) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.getNetworkHandler() != null) {
                    String rem = b.getRemaining().toLowerCase();
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                        String n = e.getProfile().name();
                        if (n == null || n.isBlank() || !seen.add(n.toLowerCase())) continue;
                        if (n.toLowerCase().startsWith(rem)) b.suggest(n);
                    }
                }
                return b.buildFuture();
            };
            String[] floors = {"e","f1","f2","f3","f4","f5","f6","f7","m1","m2","m3","m4","m5","m6","m7"};
            SuggestionProvider<FabricClientCommandSource> floorSuggest = (c, b) -> {
                String rem = b.getRemaining().toLowerCase();
                for (String f : floors) if (f.startsWith(rem)) b.suggest(f);
                return b.buildFuture();
            };

            // Lookups + player-arg party actions (kick/transfer/promote take a player name).
            for (String name : new String[]{"cata","rtca","secrets","sa","totalruns","mp","nw","networth",
                    "bank","corpse","corpses","level","sblvl","farming","nuc","nucleus","powder",
                    "kick","transfer","promote"}) {
                dispatcher.register(ClientCommandManager.literal(name)
                    .executes(c -> runLocalLookup(name, null, null))
                    .then(ClientCommandManager.argument("player", StringArgumentType.word()).suggests(playerSuggest)
                        .executes(c -> runLocalLookup(name, StringArgumentType.getString(c, "player"), null))));
            }
            for (String name : new String[]{"pb","runs","collection"}) {
                dispatcher.register(ClientCommandManager.literal(name)
                    .executes(c -> runLocalLookup(name, null, null))
                    .then(ClientCommandManager.argument("player", StringArgumentType.word()).suggests(playerSuggest)
                        .executes(c -> runLocalLookup(name, StringArgumentType.getString(c, "player"), null))
                        .then(ClientCommandManager.argument("floor", StringArgumentType.word()).suggests(floorSuggest)
                            .executes(c -> runLocalLookup(name, StringArgumentType.getString(c, "player"), StringArgumentType.getString(c, "floor"))))));
            }
            dispatcher.register(ClientCommandManager.literal("rtc")
                .executes(c -> runLocalLookup("rtc", null, null))
                .then(ClientCommandManager.argument("player", StringArgumentType.word()).suggests(playerSuggest)
                    .executes(c -> runLocalLookup("rtc", StringArgumentType.getString(c, "player"), null))
                    .then(ClientCommandManager.argument("level", StringArgumentType.word())
                        .executes(c -> runLocalLookup("rtc", StringArgumentType.getString(c, "player"), StringArgumentType.getString(c, "level"))))));
            // No-arg commands: self metrics, party actions, and join-floor/Kuudra shortcuts.
            for (String name : new String[]{"fps","tps","ping","dprofit","ai","allinv","d","warp",
                    "e","f1","f2","f3","f4","f5","f6","f7","m1","m2","m3","m4","m5","m6","m7",
                    "t1","t2","t3","t4","t5"}) {
                dispatcher.register(ClientCommandManager.literal(name).executes(c -> runLocalLookup(name, null, null)));
            }
        });

        // ── Cosmetic name changer: /nick <name> | /nick reset ────────────────
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                fishmod.cosmetic.command.NickCommand.register(dispatcher));


        // ── Warp Map HUD + click detection ───────────────────────────────────
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> WarpMapFeature.renderHud(ctx, tickCounter));
        ClientTickEvents.END_CLIENT_TICK.register(WarpMapFeature::tickClickDetection);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> SoulflowHud.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> PetHud.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> ItemRarityHotbar.renderHotbar(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> CooldownOverlay.renderHotbar(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> SlayerXpTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.SkillTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> PowderTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> BossBarFeature.renderHud(ctx));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> SessionStats.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.dungeon.DungeonScore.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.FarmingTracker.renderHud(ctx, tickCounter));
        fishmod.features.dungeon.DungeonScore.init();
        fishmod.utils.SkyblockItems.initAsync();
        fishmod.features.FarmingTracker.init();
        fishmod.features.HarvestFeastTracker.init();
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.HarvestFeastTracker.renderHud(ctx, tickCounter));
        fishmod.features.MiningTracker.init();
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.MiningTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> CroesusOverlay.renderHud(ctx, tickCounter));
        fishmod.features.TrophyFrogTracker.init();
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.TrophyFrogTracker.renderHud(ctx, tickCounter));

        // Tracker overlay (reset button) for HandledScreens — fires after full render chain
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>)) return;
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                SessionStats.renderInScreen(ctx, mx, my);
                PowderTracker.renderInScreen(ctx, mx, my);
                SlayerXpTracker.renderInScreen(ctx, mx, my);
                fishmod.features.SkillTracker.renderInScreen(ctx, mx, my);
                fishmod.features.FarmingTracker.renderInScreen(ctx, mx, my);
                fishmod.features.HarvestFeastTracker.renderInScreen(ctx, mx, my);
                fishmod.features.MiningTracker.renderInScreen(ctx, mx, my);
                fishmod.features.TrophyFrogTracker.renderInScreen(ctx);
            });
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
                if (click.button() != 0) return true; // only left click resets
                double mx = click.x(), my = click.y();
                if (SessionStats.handleScreenClick(mx, my)) return false;
                if (PowderTracker.handleScreenClick(mx, my)) return false;
                if (SlayerXpTracker.handleScreenClick(mx, my)) return false;
                if (fishmod.features.FarmingTracker.handleScreenClick(mx, my)) return false;
                if (fishmod.features.HarvestFeastTracker.handleScreenClick(mx, my)) return false;
                if (fishmod.features.MiningTracker.handleScreenClick(mx, my)) return false;
                return true;
            });
        });

        // Always init FishMod's own framework. (Pre-rename this was skipped when blade-addons was
        // present because the classes were shared as blade.addon.*; after renaming to fishmod.* they
        // are separate, so FishMod must initialize its own — otherwise Location/Config/Keybinds/etc.
        // never run and features like the warp map silently break.) Each init is guarded so a single
        // duplicate-registration clash with blade-addons can't take down the whole entrypoint.
        safeInit("FolderUtility", FolderUtility::init);
        safeInit("Components", Components::init);
        safeInit("Config", () -> Config.manager.load());
        safeInit("Keybinds", Keybinds::init);
        safeInit("CustomEvents", CustomEvents::init);
        safeInit("Debug", Debug::init);
        safeInit("Location", Location::init);
        safeInit("Phase", Phase::init);
        safeInit("Section", Section::init);
        safeInit("PartyUtil", PartyUtil::init);
        safeInit("EntityUtil", EntityUtil::init);
        safeInit("RenderingEvents", RenderingEvents::init);
        safeInit("Scheduler", Scheduler::init);
    }

    private static void safeInit(String name, Runnable init) {
        try { init.run(); }
        catch (Throwable t) { System.out.println("[FishMod] init failed for " + name + ": " + t); }
    }
}
