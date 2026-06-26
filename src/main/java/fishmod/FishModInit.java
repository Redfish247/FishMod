package fishmod;

import fishmod.features.BossBarFeature;
import fishmod.features.wiki.WikiScreen;
import fishmod.features.BridgeBot;
import fishmod.features.croesus.LootTrackerOverlay;
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
        return runLocalLookup(cmd, arg1, arg2, null);
    }

    /** Three-arg variant (e.g. /crtc [name] [class] [level]). */
    private static int runLocalLookup(String cmd, String arg1, String arg2, String arg3) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String self = (mc.player != null) ? mc.player.getGameProfile().name() : null;
        if (self == null) return Constants.SUCCESS;
        fishmod.features.dungeon.PartyCommandHandler.onPartyCommand(
                self, cmd, arg1, arg2, arg3, fishmod.features.dungeon.PartyCommandHandler.LOCAL);
        return Constants.SUCCESS;
    }

    private static final java.util.regex.Pattern HELP_CMD_TOKEN =
            java.util.regex.Pattern.compile("[/.][a-zA-Z][a-zA-Z0-9]*");

    /**
     * Prints one command-help line. If the line names exactly one command (e.g. "/cata [player]"),
     * the whole line is made click-to-suggest so you can drop the command into chat with one click;
     * lines listing several commands, headers, and prose are printed plain.
     */
    private static void helpLine(String text) {
        java.util.regex.Matcher m = HELP_CMD_TOKEN.matcher(text);
        String cmd = null;
        if (m.find()) {
            cmd = m.group();
            if (m.find()) cmd = null; // more than one command on the line → leave it plain
        }
        if (cmd == null) {
            Misc.addChatMessage(Text.literal(text));
            return;
        }
        final String suggest = cmd;
        net.minecraft.text.MutableText t = Text.literal(text);
        t.setStyle(t.getStyle()
                .withClickEvent(new net.minecraft.text.ClickEvent.SuggestCommand(suggest))
                .withHoverEvent(new net.minecraft.text.HoverEvent.ShowText(
                        Text.literal("§7Click to put §f" + suggest + "§7 in chat"))));
        Misc.addChatMessage(t);
    }

    /** Prints a formatted reference of FishMod's commands and their argument formats to the player's chat. */
    private static void printCommandHelp() {
        java.util.function.Consumer<String> line = FishModInit::helpLine;
        line.accept("§b§m                    §r §3§lFishMod Commands §r§b§m                    ");
        line.accept("§7Args in §f<>§7 are required, §8[]§7 optional. Stats commands default to §fyou§7 if no name is given.");
        line.accept("§7All stats commands also work in party chat as §f.cmd§7 (toggle each in §f/fm §8> §7Party Commands).");

        line.accept("");
        line.accept("§3§lStats Lookups");
        line.accept("§e/cata §8[player] §7— Catacombs level");
        line.accept("§e/rtc §8[player] [level] §7— runs to a Cata level §8(default 50)");
        line.accept("§e/rtca §8[player] §7— runs to class 50 for all 5 classes");
        line.accept("§e/crtc §8[player] §f<class> §8[level] §7— XP + runs for one class to a level §8(default 50)");
        line.accept("§8        class = healer | mage | berserk | archer | tank §8(e.g. §7.crtc mage 60§8)");
        line.accept("§e/secrets §7or §e/sa §8[player] §7— total secrets / secret average");
        line.accept("§e/runs §8[player] [floor] §7— floor run count §8(default m7)");
        line.accept("§e/totalruns §8[player] §7— total dungeon runs");
        line.accept("§e/pb §8[player] [floor] §7— floor personal best §8(default m7)");
        line.accept("§e/mp §8[player] §7— Magical Power");
        line.accept("§e/nw §8[player] §7— networth");
        line.accept("§e/level §8[player] §7— Skyblock level");
        line.accept("§e/farming §8[player] §7— farming weight");
        line.accept("§e/nuc §8[player] §7— Crystal Nucleus runs");
        line.accept("§e/worm §7or §e/scatha §8[player] §7— Worm + Scatha bestiary");
        line.accept("§e/bank §8[player] §7— bank + purse");
        line.accept("§e/powder §8[player] §7— Mithril / Gemstone / Glacite powder");
        line.accept("§e/corpse §8[player] §7— Glacite corpses");
        line.accept("§8floor = e, f1-f7, m1-m7 §7(party-only §f.collection [floor]§7 also available)");

        line.accept("");
        line.accept("§3§lYour Stats");
        line.accept("§e/fps §8·§e /tps §8·§e /ping §8·§e /dprofit §7— FPS, server TPS, ping, Croesus profit/run");

        line.accept("");
        line.accept("§3§lDungeon / Kuudra Joins §8(party chat)");
        line.accept("§f.e §8·§f .f1-.f7 §8·§f .m1-.m7 §7— join Catacombs floor");
        line.accept("§f.t1-.t5 §7— join Kuudra tier");

        line.accept("");
        line.accept("§3§lParty Actions");
        line.accept("§e/pk §f<player> §7— kick  §8·§7  §e/pw §7— warp  §8·§7  §e/pt §f<player> §7— transfer  §8·§7  §e/pp §f<player> §7— promote");
        line.accept("§7In party chat: §f.ai §7(allinvite), §f.d §7(disband), §f.kick/.warp/.transfer/.promote");

        line.accept("");
        line.accept("§3§lScreens & Misc");
        line.accept("§e/fm §7— config GUI  §8·§7  §e/fm customize §7— item customizer  §8·§7  §e/fmloot §7— Croesus loot");
        line.accept("§e/po §7or §e/fm optimize §7— Profile Optimizer §8(net worth, skill roadmap, what to do next)");
        line.accept("§e/streams §8·§e /wiki §8<query> §8·§e /nick §8<name>|reset");
        line.accept("§e/fm commandhelp §7— this list  §8·§7  party chat: §f.help §7lists enabled party commands");
        line.accept("§b§m                                                                          ");
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
        fishmod.features.ItemCustomizer.init();
        // Shared item cosmetics: fetch + render other mod users' custom items/armor (after ItemCustomizer.init)
        fishmod.cosmetic.RemoteItems.init();
        // Shared player sizes: publish ours on join + render other mod users' shared sizes
        fishmod.cosmetic.PlayerSize.init();
        // Combined version-gated poller that drives RemoteNicks + RemoteItems + RemoteScales (after .init())
        fishmod.cosmetic.RemoteSync.init();
        fishmod.features.WelcomeMessage.init();
        LagTracker.init();
        SessionStats.init();
        FishPuzzleDisplay.init();
        FishEstTotal.init();
        DungeonDeathMessage.init();
        fishmod.features.ExplosiveShot.init();
        FishPartyTracker.init();
        PartyCommandHandler.init();
        SoulflowHud.init();
        PetHud.init();
        CooldownOverlay.init();
        ItemRarityHotbar.init();   // rarity background: inventory-slot coverage (hotbar via HudRenderCallback)
        MayorApi.init();
        BridgeBot.init();
        SlayerXpTracker.init();
        fishmod.features.SkillTracker.init();
        fishmod.features.FireFreezeTimer.init();
        PowderTracker.init();
        fishmod.features.dungeon.SimonSaysTracker.init();
        fishmod.features.dungeon.M7LeverWaypoints.init();
        // Floor 7 boss timers (ported from blade-addons): Maxor/Storm/Goldor tick timers, crystal
        // spawn, term start, section progress, storm-crushed. HUDs auto-render via the practical
        // config system (F7Huds registered with FishConfig); register each for the Edit-HUD dragger.
        fishmod.features.dungeon.f7.F7Huds.init();
        // Inventory command buttons (ported 1:1 from blade-addons) — touch the class so its 7 buttons
        // self-register; commands are edited in /fm → General → Inventory Buttons.
        fishmod.utils.config.values.Buttons.init();
        FishHudEditor.register("Maxor Tick Timer",  fishmod.features.dungeon.f7.F7Huds.maxorTickTimer);
        FishHudEditor.register("Crystal Spawn Time", fishmod.features.dungeon.f7.F7Huds.crystalSpawnTime);
        FishHudEditor.register("Crystal Reminder",  fishmod.features.dungeon.f7.F7Huds.crystalReminder);
        FishHudEditor.register("Storm Tick Timer",  fishmod.features.dungeon.f7.F7Huds.stormTickTimer);
        FishHudEditor.register("Storm Death Time",  fishmod.features.dungeon.f7.F7Huds.stormDeathTime);
        FishHudEditor.register("Storm Crushed",     fishmod.features.dungeon.f7.F7Huds.stormCrush);
        FishHudEditor.register("Goldor Tick Timer", fishmod.features.dungeon.f7.F7Huds.goldorTickTimer);
        FishHudEditor.register("Term Start Timer",  fishmod.features.dungeon.f7.F7Huds.termStartTimer);
        FishHudEditor.register("Section Progress",  fishmod.features.dungeon.f7.F7Huds.sectionProgress);
        // Dungeon class detection (own class from the "stats are doubled" message + tab list) and the
        // class-colored boots feature that depends on it. Boots init AFTER ItemCustomizer.init (above)
        // so the class color wins over per-item boot dye while enabled.
        fishmod.utils.dungeon.DungeonClass.init();
        fishmod.features.ClassColoredBoots.init();

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
                .then(ClientCommandManager.literal("customize").executes(context -> {
                    MinecraftClient.getInstance().send(() ->
                        MinecraftClient.getInstance().setScreen(new fishmod.features.ItemCustomizeScreen()));
                    return Constants.SUCCESS;
                }))
                .then(ClientCommandManager.literal("optimize").executes(context -> {
                    MinecraftClient.getInstance().send(() ->
                        MinecraftClient.getInstance().setScreen(new fishmod.features.optimizer.OptimizerScreen(null)));
                    return Constants.SUCCESS;
                }))
                .then(ClientCommandManager.literal("commandhelp").executes(context -> {
                    printCommandHelp();
                    return Constants.SUCCESS;
                }))
                .then(ClientCommandManager.literal("help").executes(context -> {
                    printCommandHelp();
                    return Constants.SUCCESS;
                }))
                .executes(context -> {
                    MinecraftClient.getInstance().send(() ->
                        MinecraftClient.getInstance().setScreen(new fishmod.features.FishModScreen()));
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("fmloot")
                .executes(ctx -> {
                    boolean on = !fishmod.utils.config.values.FishSettings.lootTrackerEnabled;
                    fishmod.utils.config.values.FishSettings.lootTrackerEnabled = on;
                    fishmod.utils.config.FishConfig.manager.save();
                    Misc.addChatMessage(Text.literal("§7[FM] Loot tracker "
                            + (on ? "§aenabled §7— open your inventory in the Dungeon Hub"
                                  : "§cdisabled")));
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("po")
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new fishmod.features.optimizer.OptimizerScreen(mc.currentScreen)));
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("fmnicktest")
                .executes(ctx -> {
                    if (fishmod.utils.DevOnly.deny(ctx.getSource())) return Constants.SUCCESS;
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null || mc.getNetworkHandler() == null) {
                        Misc.addChatMessage(Text.literal("§cNot in a world."));
                        return Constants.SUCCESS;
                    }
                    boolean remoteOn = fishmod.utils.config.values.FishSettings.remoteNicksEnabled;
                    Misc.addChatMessage(Text.literal("§b[fmnicktest] §7See Others: §f" + remoteOn
                            + " §8|§7 own nick active: §f" + fishmod.cosmetic.NickState.isActive()
                            + " §8|§7 raw: §f" + (fishmod.cosmetic.NickState.getRaw() == null ? "(none)" : fishmod.cosmetic.NickState.getRaw())));

                    // Re-upload own nick
                    fishmod.cosmetic.RemoteNicks.uploadOwn();
                    Misc.addChatMessage(Text.literal("§b[fmnicktest] §7re-uploaded own nick."));

                    // Force an immediate refresh so styledByName is up to date.
                    fishmod.cosmetic.RemoteNicks.forceRefresh();
                    Misc.addChatMessage(Text.literal("§b[fmnicktest] §7triggered RemoteNicks.refresh()…"));

                    // Re-dump the cache shortly after so the async fetch finishes first.
                    mc.send(() -> new Thread(() -> {
                        try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                        mc.send(() -> {
                            var cache = fishmod.cosmetic.RemoteNicks.snapshot();
                            Misc.addChatMessage(Text.literal("§b[fmnicktest] §7styledByName cache: §f"
                                    + cache.size() + " §7entries"));
                            int count = 0;
                            for (var e : cache.entrySet()) {
                                net.minecraft.text.MutableText line = net.minecraft.text.Text.literal("§7  " + e.getKey() + " §8→ ").copy();
                                line.append(e.getValue());
                                Misc.addChatMessage(line);
                                if (++count > 10) { Misc.addChatMessage(Text.literal("§8  (…more)")); break; }
                            }
                            if (cache.isEmpty()) {
                                Misc.addChatMessage(Text.literal("§c[fmnicktest] cache is empty — chat rewrite has nothing to apply. Check See Others toggle."));
                            } else {
                                Misc.addChatMessage(Text.literal("§a[fmnicktest] cache populated. If chat still shows IGNs, the mixin path isn't covering Hypixel's chat handler — paste a chat screenshot."));
                            }
                        });
                    }, "fmnicktest-dump").start());
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("fmitems")
                .executes(ctx -> {
                    if (fishmod.utils.DevOnly.deny(ctx.getSource())) return Constants.SUCCESS;
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null || mc.world == null) {
                        Misc.addChatMessage(Text.literal("§cNot in a world."));
                        return Constants.SUCCESS;
                    }
                    boolean on = fishmod.utils.config.values.FishSettings.remoteItemsEnabled;
                    var ownKeys = fishmod.features.ItemCustomizer.debugKeys();
                    Misc.addChatMessage(Text.literal("§b[fmitems] §7See Others' Items: §f" + on
                            + " §8|§7 your customs: §f" + ownKeys.size()));
                    for (String k : ownKeys) Misc.addChatMessage(Text.literal("§7  your key §8→ §f" + k));

                    fishmod.features.ItemCustomizer.uploadOwn();
                    fishmod.cosmetic.RemoteItems.forceRefresh();
                    Misc.addChatMessage(Text.literal("§b[fmitems] §7re-uploaded own + forced sync…"));

                    new Thread(() -> {
                        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                        mc.send(() -> {
                            var loaded = fishmod.cosmetic.RemoteItems.snapshotKeys();
                            Misc.addChatMessage(Text.literal("§b[fmitems] §7remote payloads loaded: §f"
                                    + loaded.size() + " §7player(s)"));
                            int shown = 0;
                            for (net.minecraft.entity.player.PlayerEntity p : mc.world.getPlayers()) {
                                if (p == mc.player) continue;
                                String u = p.getUuid().toString().replace("-", "");
                                java.util.Set<String> customs = loaded.get(u);
                                net.minecraft.item.ItemStack held = p.getEquippedStack(net.minecraft.entity.EquipmentSlot.MAINHAND);
                                String heldVanilla = fishmod.features.ItemCustomizer.vanillaId(held);
                                boolean match = customs != null && heldVanilla != null && customs.contains(heldVanilla);
                                Misc.addChatMessage(Text.literal("§7  " + p.getGameProfile().name()
                                        + " §8| customs:§f" + (customs == null ? 0 : customs.size())
                                        + " §8| held:§f" + heldVanilla
                                        + " §8| match:" + (match ? "§a✔" : "§c✘")));
                                if (++shown >= 8) { Misc.addChatMessage(Text.literal("§8  (…more)")); break; }
                            }
                            if (loaded.isEmpty())
                                Misc.addChatMessage(Text.literal("§c[fmitems] no remote customs fetched — nobody nearby has uploaded (their \"See Others' Items\" may be off, or they haven't customized anything)."));
                        });
                    }, "fmitems-dump").start();
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("fmchping")
                .executes(ctx -> {
                    if (fishmod.utils.DevOnly.deny(ctx.getSource())) return Constants.SUCCESS;
                    MinecraftClient mc = MinecraftClient.getInstance();
                    Misc.addChatMessage(Text.literal("§b[fmchping] §7Pinging worker…"));
                    fishmod.features.challenges.ChallengeApi.pingWorker((status, body) -> mc.send(() -> {
                        if (status == 200) Misc.addChatMessage(Text.literal("§a[fmchping] §7status=§a200 §7body=§f" + body));
                        else if (status == -1) Misc.addChatMessage(Text.literal("§c[fmchping] network error: §7" + body));
                        else Misc.addChatMessage(Text.literal("§c[fmchping] §7status=§c" + status + " §7body=§f" + body));
                    }));
                    return Constants.SUCCESS;
                })
            );
            dispatcher.register(ClientCommandManager.literal("fmchworker")
                .executes(ctx -> {
                    if (fishmod.utils.DevOnly.deny(ctx.getSource())) return Constants.SUCCESS;
                    String cur = fishmod.utils.config.values.FishSettings.challengeWorkerOverride;
                    Misc.addChatMessage(Text.literal("§b[fmchworker] §7current: §f"
                            + (cur == null || cur.isBlank() ? "(default)" : cur)));
                    Misc.addChatMessage(Text.literal("§7Usage: §f/fmchworker <url> §7| §f/fmchworker reset"));
                    return Constants.SUCCESS;
                })
                .then(ClientCommandManager.literal("reset").executes(ctx -> {
                    if (fishmod.utils.DevOnly.deny(ctx.getSource())) return Constants.SUCCESS;
                    fishmod.utils.config.values.FishSettings.challengeWorkerOverride = "";
                    fishmod.utils.config.FishConfig.manager.save();
                    Misc.addChatMessage(Text.literal("§a[fmchworker] reset — using default worker."));
                    return Constants.SUCCESS;
                }))
                .then(ClientCommandManager.argument("url", StringArgumentType.greedyString()).executes(ctx -> {
                    if (fishmod.utils.DevOnly.deny(ctx.getSource())) return Constants.SUCCESS;
                    String url = StringArgumentType.getString(ctx, "url").trim();
                    fishmod.utils.config.values.FishSettings.challengeWorkerOverride = url;
                    fishmod.utils.config.FishConfig.manager.save();
                    Misc.addChatMessage(Text.literal("§a[fmchworker] override set: §f" + url));
                    return Constants.SUCCESS;
                }))
            );
            dispatcher.register(ClientCommandManager.literal("fmlbtest")
                .executes(ctx -> {
                    if (fishmod.utils.DevOnly.deny(ctx.getSource())) return Constants.SUCCESS;
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null) return Constants.SUCCESS;
                    String uuid = mc.player.getUuid().toString().replace("-", "");
                    String name = fishmod.features.challenges.ChallengeApi.displayName();
                    Misc.addChatMessage(Text.literal("§b[lbtest] §7Submitting test score…"));
                    fishmod.features.challenges.ChallengeApi.submitScore(
                            uuid, name, "test-" + System.currentTimeMillis(),
                            fishmod.features.challenges.Tier.DAILY, 1, 0,
                            (ok, total, rank) -> mc.send(() -> {
                                if (ok) Misc.addChatMessage(Text.literal("§a[lbtest] §7submit OK — newTotal=§a"
                                        + total + " §7rank=§e#" + rank));
                                else    Misc.addChatMessage(Text.literal("§c[lbtest] submit FAILED — §7"
                                        + (fishmod.features.challenges.ChallengeApi.lastSubmitError.isEmpty()
                                            ? "unknown" : fishmod.features.challenges.ChallengeApi.lastSubmitError)));
                                Misc.addChatMessage(Text.literal("§b[lbtest] §7Fetching leaderboard…"));
                                fishmod.features.challenges.ChallengeApi.fetchLeaderboard(10, entries -> mc.send(() -> {
                                    if (entries.isEmpty()) {
                                        Misc.addChatMessage(Text.literal("§c[lbtest] leaderboard empty (or fetch failed — likely worker endpoint not deployed)"));
                                    } else {
                                        Misc.addChatMessage(Text.literal("§a[lbtest] leaderboard returned §f" + entries.size() + " §7entries:"));
                                        int i = 1;
                                        for (var e : entries) {
                                            Misc.addChatMessage(Text.literal("§7  #" + i++ + " §f" + e.name + " §8— §a" + e.totalPoints + " §7pts"));
                                            if (i > 10) break;
                                        }
                                    }
                                }));
                            }));
                    return Constants.SUCCESS;
                })
            );
            // /fmchallenge is disabled — feature shelved pending redesign. Code still present
            // in fishmod.features.challenges.* and gated by FishSettings.challengesEnabled.
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
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
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
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
                PetHud.debugDumpPetLines = !PetHud.debugDumpPetLines;
                Misc.addChatMessage(Text.literal("§b[fmpet] dump pet-related chat lines: §f" + PetHud.debugDumpPetLines));
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmcddump").executes(context -> {
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
                CooldownOverlay.debugDumpSound = !CooldownOverlay.debugDumpSound;
                Misc.addChatMessage(Text.literal("§b[fmcd] dump cooldown sound events: §f" + CooldownOverlay.debugDumpSound));
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmblocks").executes(context -> {
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
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
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
                fishmod.features.dungeon.SimonSaysTracker.debug = !fishmod.features.dungeon.SimonSaysTracker.debug;
                Misc.addChatMessage(Text.literal("§b[ssdbg] log Simon Says block transitions: §f" + fishmod.features.dungeon.SimonSaysTracker.debug));
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmseadump").executes(context -> {
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
                fishmod.features.fishing.SeaCreatureTracker.debugDump = !fishmod.features.fishing.SeaCreatureTracker.debugDump;
                Misc.addChatMessage(Text.literal("§b[fmsea] dump unmatched fishing chat lines: §f" + fishmod.features.fishing.SeaCreatureTracker.debugDump));
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmslayerdump").executes(context -> {
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
                fishmod.features.slayer.SlayerAlerts.debugDump = !fishmod.features.slayer.SlayerAlerts.debugDump;
                Misc.addChatMessage(Text.literal("§b[fmslayer] dump slayer chat lines: §f" + fishmod.features.slayer.SlayerAlerts.debugDump));
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmnuc").executes(context -> {
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
                fishmod.utils.HypixelApi.dumpNucleus(MinecraftClient.getInstance());
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmgarden").executes(context -> {
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
                fishmod.utils.HypixelApi.dumpGarden(MinecraftClient.getInstance());
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmprofile").executes(context -> {
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
                fishmod.utils.HypixelApi.dumpEconomy(MinecraftClient.getInstance());
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmtabdump").executes(context -> {
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
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
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
                fishmod.features.SkillTracker.debugDump = !fishmod.features.SkillTracker.debugDump;
                Misc.addChatMessage(Text.literal("§b[skill] dump raw action bar: §f" + fishmod.features.SkillTracker.debugDump));
                return Constants.SUCCESS;
            }));

            dispatcher.register(ClientCommandManager.literal("fmdbg").executes(context -> {
                if (fishmod.utils.DevOnly.deny(context.getSource())) return Constants.SUCCESS;
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
                    "worm","scatha","kick","transfer","promote"}) {
                dispatcher.register(ClientCommandManager.literal(name)
                    .executes(c -> runLocalLookup(name, null, null))
                    .then(ClientCommandManager.argument("player", StringArgumentType.word()).suggests(playerSuggest)
                        .executes(c -> runLocalLookup(name, StringArgumentType.getString(c, "player"), null))));
            }
            // NOTE: no "collection" here — Hypixel already owns /collection. The party-chat
            // ".collection" command still works via the chat handler.
            for (String name : new String[]{"pb","runs"}) {
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
            // /crtc [name] [class] [level] — XP for one class to reach a level (default 50).
            // Smart-parses in PartyCommandHandler: a leading class arg means "self".
            SuggestionProvider<FabricClientCommandSource> classSuggest = (c, b) -> {
                String rem = b.getRemaining().toLowerCase();
                for (String cl : new String[]{"healer","mage","berserk","archer","tank"})
                    if (cl.startsWith(rem)) b.suggest(cl);
                return b.buildFuture();
            };
            dispatcher.register(ClientCommandManager.literal("crtc")
                .executes(c -> runLocalLookup("crtc", null, null, null))
                .then(ClientCommandManager.argument("a1", StringArgumentType.word()).suggests(classSuggest)
                    .executes(c -> runLocalLookup("crtc", StringArgumentType.getString(c, "a1"), null, null))
                    .then(ClientCommandManager.argument("a2", StringArgumentType.word()).suggests(classSuggest)
                        .executes(c -> runLocalLookup("crtc", StringArgumentType.getString(c, "a1"), StringArgumentType.getString(c, "a2"), null))
                        .then(ClientCommandManager.argument("a3", StringArgumentType.word())
                            .executes(c -> runLocalLookup("crtc", StringArgumentType.getString(c, "a1"), StringArgumentType.getString(c, "a2"), StringArgumentType.getString(c, "a3")))))));
            // No-arg commands: self metrics, party actions, and join-floor/Kuudra shortcuts.
            for (String name : new String[]{"fps","tps","ping","dprofit","ai","allinv","d",
                    "e","f1","f2","f3","f4","f5","f6","f7","m1","m2","m3","m4","m5","m6","m7",
                    "t1","t2","t3","t4","t5"}) {
                dispatcher.register(ClientCommandManager.literal(name).executes(c -> runLocalLookup(name, null, null)));
            }
            // /warp — bare-form runs the local party action; with an argument, forward to
            // Hypixel's server-side /warp <dest> so the client command doesn't shadow it
            // with "Incorrect argument for command at position 5: warp <--[HERE]".
            dispatcher.register(ClientCommandManager.literal("warp")
                .executes(c -> runLocalLookup("warp", null, null))
                .then(ClientCommandManager.argument("dest", StringArgumentType.greedyString())
                    .executes(c -> {
                        String dest = StringArgumentType.getString(c, "dest");
                        MinecraftClient mc = MinecraftClient.getInstance();
                        // Send the command packet DIRECTLY, bypassing Fabric's client command
                        // dispatcher — otherwise it re-matches our /warp literal and infinitely
                        // recurses into this same lambda, blowing the stack.
                        if (mc.player != null && mc.player.networkHandler != null)
                            mc.player.networkHandler.sendPacket(
                                new net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket("warp " + dest));
                        return Constants.SUCCESS;
                    })));
        });

        // ── Cosmetic name changer: /nick <name> | /nick reset ────────────────
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                fishmod.cosmetic.command.NickCommand.register(dispatcher));

        // ── Override OdinClient's /cata ───────────────────────────────────────
        // Both mods register a client-side /cata; Brigadier hands the executes() to whoever
        // registers LAST, which isn't deterministic at init. Re-register ours on each server
        // join — that runs after every mod's init-time registration, so ours wins.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> d = ClientCommandManager.getActiveDispatcher();
            if (d == null) return;
            try {
                d.register(ClientCommandManager.literal("cata")
                    .executes(c -> runLocalLookup("cata", null, null))
                    .then(ClientCommandManager.argument("player", StringArgumentType.word())
                        .executes(c -> runLocalLookup("cata", StringArgumentType.getString(c, "player"), null))));
            } catch (Exception ignored) {}
        });


        // ── Warp Map HUD + click detection ───────────────────────────────────
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> WarpMapFeature.renderHud(ctx, tickCounter));
        ClientTickEvents.END_CLIENT_TICK.register(WarpMapFeature::tickClickDetection);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> SoulflowHud.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> PetHud.renderHud(ctx, tickCounter));
        // Rarity background is drawn behind items via DrawContextMixin (hotbar) + INVENTORY_SLOT_BEFORE
        // (inventory) — see ItemRarityHotbar.init(). No HudRenderCallback (that draws over the items).
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> CooldownOverlay.renderHotbar(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> SlayerXpTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.SkillTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> PowderTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> BossBarFeature.renderHud(ctx));
        // Splits panel + Maxor/Storm/Terminals split-time HUDs. Rendered here (not via practical-config's
        // HudElementRegistry auto-render, which doesn't fire reliably) — their condition-suppliers are
        // forced false in Phase so this is the single render path.
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> Phase.renderHud(ctx));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.dungeon.f7.F7Huds.renderHud(ctx));
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
        fishmod.features.TrophyFrogTracker.init();
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.TrophyFrogTracker.renderHud(ctx, tickCounter));

        // ── Fishing ──────────────────────────────────────────────────────────
        fishmod.features.fishing.FishingTimer.init();
        fishmod.features.fishing.SeaCreatureTracker.init();
        fishmod.features.fishing.TrophyFishTracker.init();
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.fishing.FishingTimer.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.fishing.SeaCreatureTracker.renderHud(ctx, tickCounter));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.fishing.TrophyFishTracker.renderHud(ctx, tickCounter));
        FishHudEditor.register("Bobber Reminder",
                () -> fishmod.utils.config.values.FishSettings.fishingTimerHudX,
                v  -> fishmod.utils.config.values.FishSettings.fishingTimerHudX = v,
                () -> fishmod.utils.config.values.FishSettings.fishingTimerHudY,
                v  -> fishmod.utils.config.values.FishSettings.fishingTimerHudY = v, 90, 14,
                () -> fishmod.utils.config.values.FishSettings.fishingTimerScale,
                v  -> fishmod.utils.config.values.FishSettings.fishingTimerScale = v,
                () -> fishmod.utils.config.values.FishSettings.fishingTimerEnabled);
        FishHudEditor.register("Sea Creatures",
                () -> fishmod.utils.config.values.FishSettings.seaCreatureHudX,
                v  -> fishmod.utils.config.values.FishSettings.seaCreatureHudX = v,
                () -> fishmod.utils.config.values.FishSettings.seaCreatureHudY,
                v  -> fishmod.utils.config.values.FishSettings.seaCreatureHudY = v, 150, 14 * 5,
                () -> fishmod.utils.config.values.FishSettings.seaCreatureScale,
                v  -> fishmod.utils.config.values.FishSettings.seaCreatureScale = v,
                () -> fishmod.features.fishing.SeaCreatureTracker.isVisible());
        FishHudEditor.register("Trophy Fish",
                () -> fishmod.utils.config.values.FishSettings.trophyFishHudX,
                v  -> fishmod.utils.config.values.FishSettings.trophyFishHudX = v,
                () -> fishmod.utils.config.values.FishSettings.trophyFishHudY,
                v  -> fishmod.utils.config.values.FishSettings.trophyFishHudY = v, 160, 14 * 6,
                () -> fishmod.utils.config.values.FishSettings.trophyFishHudScale,
                v  -> fishmod.utils.config.values.FishSettings.trophyFishHudScale = v,
                () -> fishmod.features.fishing.TrophyFishTracker.isVisible());

        // ── Slayer ───────────────────────────────────────────────────────────
        fishmod.features.slayer.SlayerAlerts.init();
        fishmod.features.slayer.SlayerDropTracker.init();
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> fishmod.features.slayer.SlayerDropTracker.renderHud(ctx, tickCounter));
        FishHudEditor.register("Slayer Drops",
                () -> fishmod.utils.config.values.FishSettings.slayerDropsHudX,
                v  -> fishmod.utils.config.values.FishSettings.slayerDropsHudX = v,
                () -> fishmod.utils.config.values.FishSettings.slayerDropsHudY,
                v  -> fishmod.utils.config.values.FishSettings.slayerDropsHudY = v, 110, 14 * 4,
                () -> fishmod.utils.config.values.FishSettings.slayerDropsScale,
                v  -> fishmod.utils.config.values.FishSettings.slayerDropsScale = v,
                () -> fishmod.utils.config.values.FishSettings.slayerDropsEnabled);

        // ── Daily/Weekly/Monthly Challenges ──────────────────────────────────
        fishmod.features.challenges.ChallengeManager.init();
        fishmod.features.challenges.LeaderboardRenderer.init();
        HudRenderCallback.EVENT.register((ctx, tickCounter) ->
                fishmod.features.challenges.ChallengeHud.renderHud(ctx, tickCounter));
        FishHudEditor.register("Challenges",
                () -> fishmod.utils.config.values.FishSettings.challengeHudX,
                v -> fishmod.utils.config.values.FishSettings.challengeHudX = v,
                () -> fishmod.utils.config.values.FishSettings.challengeHudY,
                v -> fishmod.utils.config.values.FishSettings.challengeHudY = v, 200, 64,
                () -> fishmod.utils.config.values.FishSettings.challengeHudScale,
                v -> fishmod.utils.config.values.FishSettings.challengeHudScale = v,
                () -> fishmod.utils.config.values.FishSettings.challengesEnabled
                        && fishmod.utils.config.values.FishSettings.challengeHudEnabled);

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
                fishmod.features.fishing.SeaCreatureTracker.renderInScreen(ctx, mx, my);
                fishmod.features.fishing.TrophyFishTracker.renderInScreen(ctx);
                fishmod.features.slayer.SlayerDropTracker.renderInScreen(ctx, mx, my);
                LootTrackerOverlay.renderInScreen(ctx, mx, my);
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
                if (fishmod.features.fishing.SeaCreatureTracker.handleScreenClick(mx, my)) return false;
                if (fishmod.features.slayer.SlayerDropTracker.handleScreenClick(mx, my)) return false;
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
