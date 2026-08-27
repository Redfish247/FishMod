package fishmod

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import fishmod.features.BossBarFeature
import fishmod.features.CooldownOverlay
import fishmod.features.FishHudEditor
import fishmod.features.ItemRarityHotbar
import fishmod.features.PetHud
import fishmod.features.SoulflowHud
import fishmod.features.dungeon.DungeonDeathMessage
import fishmod.features.dungeon.FishEstTotal
import fishmod.features.dungeon.FishPuzzleDisplay
import fishmod.features.dungeon.LagTracker
import fishmod.features.dungeon.PartyCommandHandler
import fishmod.features.dungeon.PuzzleDisplay
import fishmod.features.dungeon.SessionStats
import fishmod.mixin.accessors.ChatScreenAccessor
import fishmod.utils.Constants
import fishmod.utils.Keybinds
import fishmod.utils.Location
import fishmod.utils.MayorApi
import fishmod.utils.Misc
import fishmod.utils.Scheduler
import fishmod.utils.config.Config
import fishmod.utils.config.FishConfig
import fishmod.utils.config.FolderUtility
import fishmod.utils.config.components.Components
import fishmod.utils.data.EntityUtil
import fishmod.utils.data.FishPartyTracker
import fishmod.utils.data.PartyUtil
import fishmod.utils.debug.Debug
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.events.CustomEvents
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard
import java.util.function.Consumer
import java.util.regex.Matcher
import java.util.regex.Pattern

class FishModInit : ModInitializer {

    companion object {
        /** True when text matches "/pc .x", "/gc .x", etc. — a channel prefix followed by a dot-command. */
        @JvmStatic
        private fun looksLikeChannelDot(text: String): Boolean {
            val sp = text.indexOf(' ')
            if (sp <= 0 || sp + 1 >= text.length || text[sp + 1] != '.') return false
            val head = text.substring(0, sp).lowercase()
            return head == "/pc" || head == "/gc" || head == "/ac" || head == "/oc" || head == "/msg" || head == "/r"
        }

        /** Runs a party-command lookup locally and prints the result in your own chat (no party message). */
        @JvmStatic
        private fun runLocalLookup(cmd: String, arg1: String?, arg2: String?): Int =
            runLocalLookup(cmd, arg1, arg2, null)

        /** Three-arg variant (e.g. /crtc [name] [class] [level]). */
        @JvmStatic
        private fun runLocalLookup(cmd: String, arg1: String?, arg2: String?, arg3: String?): Int {
            val mc = Minecraft.getInstance()
            val self = mc.player?.gameProfile?.name ?: return Constants.SUCCESS
            fishmod.features.dungeon.PartyCommandHandler.onPartyCommand(
                self, cmd, arg1, arg2, arg3, fishmod.features.dungeon.PartyCommandHandler.LOCAL
            )
            return Constants.SUCCESS
        }

        /** Prints a party-action whitelist/blacklist to your own chat, e.g. from /fmcmd whitelist. */
        @JvmStatic
        private fun printNameList(label: String, csv: String) {
            val names = fishmod.utils.NameList.toList(csv)
            Misc.addChatMessage(
                Component.literal(
                    "§b[FM] Party-Action $label §7(${names.size}): §f" +
                        (if (names.isEmpty()) "(empty)" else names.joinToString(", "))
                )
            )
        }

        private val HELP_CMD_TOKEN: Pattern = Pattern.compile("[/.][a-zA-Z][a-zA-Z0-9]*")

        /** Lines naming exactly one command become click-to-suggest; multi-command/header lines print plain. */
        @JvmStatic
        private fun helpLine(text: String) {
            val m: Matcher = HELP_CMD_TOKEN.matcher(text)
            var cmd: String? = null
            if (m.find()) {
                cmd = m.group()
                if (m.find()) cmd = null // more than one command on the line → leave it plain
            }
            if (cmd == null) {
                Misc.addChatMessage(Component.literal(text))
                return
            }
            val suggest = cmd
            val t: MutableComponent = Component.literal(text)
            t.setStyle(
                t.style
                    .withClickEvent(ClickEvent.SuggestCommand(suggest))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("§7Click to put §f$suggest§7 in chat")))
            )
            Misc.addChatMessage(t)
        }

        /** Builds the /fm wp (and /fm waypoint, /fm waypoints) subtree — waypoint editor, see [fishmod.features.dungeon.DungeonWaypoints]. */
        @JvmStatic
        private fun waypointSubcommand(name: String): com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> {
            val tree = ClientCommands.literal(name)
                .executes { fishmod.features.dungeon.DungeonWaypoints.toggleEdit(); Constants.SUCCESS }
                .then(ClientCommands.literal("edit").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleEdit(); Constants.SUCCESS
                })
                .then(ClientCommands.literal("fill").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleFill(); Constants.SUCCESS
                })
                .then(
                    ClientCommands.literal("size")
                        .then(
                            ClientCommands.argument("value", DoubleArgumentType.doubleArg(0.1, 1.0))
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setSize(
                                        DoubleArgumentType.getDouble(ctx, "value")
                                    )
                                    Constants.SUCCESS
                                }
                        )
                )
                .then(
                    ClientCommands.literal("distance")
                        .then(
                            ClientCommands.argument("value", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setDistance(
                                        IntegerArgumentType.getInteger(ctx, "value")
                                    )
                                    Constants.SUCCESS
                                }
                        )
                )
                .then(ClientCommands.literal("resetsecrets").executes {
                    fishmod.features.dungeon.DungeonWaypoints.resetSecrets(); Constants.SUCCESS
                })
                .then(
                    ClientCommands.literal("type")
                        .then(
                            ClientCommands.argument("value", StringArgumentType.word())
                                .suggests { _, builder ->
                                    for (s in arrayOf("none", "normal", "secret", "etherwarp", "move", "blocketherwarp")) builder.suggest(s)
                                    builder.buildFuture()
                                }
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setType(StringArgumentType.getString(ctx, "value"))
                                    Constants.SUCCESS
                                }
                        )
                )
                .then(
                    ClientCommands.literal("timer")
                        .then(
                            ClientCommands.argument("value", StringArgumentType.word())
                                .suggests { _, builder ->
                                    for (s in arrayOf("none", "start", "checkpoint", "end")) builder.suggest(s)
                                    builder.buildFuture()
                                }
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setTimer(StringArgumentType.getString(ctx, "value"))
                                    Constants.SUCCESS
                                }
                        )
                )
                .then(ClientCommands.literal("useblocksize").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleUseBlockSize(); Constants.SUCCESS
                })
                .then(
                    ClientCommands.literal("offset")
                        .then(
                            ClientCommands.argument("x", DoubleArgumentType.doubleArg())
                                .then(
                                    ClientCommands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(
                                            ClientCommands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes { ctx ->
                                                    fishmod.features.dungeon.DungeonWaypoints.setOffset(
                                                        DoubleArgumentType.getDouble(ctx, "x"),
                                                        DoubleArgumentType.getDouble(ctx, "y"),
                                                        DoubleArgumentType.getDouble(ctx, "z")
                                                    )
                                                    Constants.SUCCESS
                                                }
                                        )
                                )
                        )
                )
                .then(ClientCommands.literal("through").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleThrough(); Constants.SUCCESS
                })
                .then(
                    ClientCommands.literal("linesize")
                        .then(
                            ClientCommands.argument("value", DoubleArgumentType.doubleArg(0.01, 0.5))
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setLineWidth(
                                        DoubleArgumentType.getDouble(ctx, "value")
                                    )
                                    Constants.SUCCESS
                                }
                        )
                )
                .then(
                    ClientCommands.literal("color")
                        .then(
                            ClientCommands.argument("hex", StringArgumentType.word())
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setColor(StringArgumentType.getString(ctx, "hex"))
                                    Constants.SUCCESS
                                }
                        )
                )
                .then(ClientCommands.literal("export").executes {
                    fishmod.features.dungeon.DungeonWaypoints.exportToClipboard(); Constants.SUCCESS
                })
                .then(ClientCommands.literal("import").executes {
                    fishmod.features.dungeon.DungeonWaypoints.importFromClipboard(); Constants.SUCCESS
                })
                .then(ClientCommands.literal("reset").executes {
                    fishmod.features.dungeon.DungeonWaypoints.resetCurrentArea(); Constants.SUCCESS
                })
                .then(
                    ClientCommands.literal("route")
                        .executes {
                            fishmod.features.dungeon.DungeonWaypoints.toggleRoute(null); Constants.SUCCESS
                        }
                        .then(
                            ClientCommands.argument("name", StringArgumentType.word())
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.toggleRoute(StringArgumentType.getString(ctx, "name"))
                                    Constants.SUCCESS
                                }
                        )
                        .then(
                            ClientCommands.literal("reset")
                                .executes {
                                    fishmod.features.dungeon.DungeonWaypoints.endRoute(null); Constants.SUCCESS
                                }
                                .then(
                                    ClientCommands.argument("name", StringArgumentType.word())
                                        .executes { ctx ->
                                            fishmod.features.dungeon.DungeonWaypoints.endRoute(StringArgumentType.getString(ctx, "name"))
                                            Constants.SUCCESS
                                        }
                                )
                        )
                        .then(
                            ClientCommands.literal("delete")
                                .then(
                                    ClientCommands.argument("name", StringArgumentType.word())
                                        .executes { ctx ->
                                            fishmod.features.dungeon.DungeonWaypoints.deleteRoute(StringArgumentType.getString(ctx, "name"))
                                            Constants.SUCCESS
                                        }
                                )
                        )
                )
            return tree
        }

        private fun chatNotificationsSubcommand(name: String): com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> {
            return ClientCommands.literal(name)
                .executes {
                    Minecraft.getInstance().schedule {
                        Minecraft.getInstance().setScreen(fishmod.features.chat.ChatNotificationsScreen())
                    }
                    Constants.SUCCESS
                }
                .then(ClientCommands.literal("on").executes {
                    fishmod.features.chat.ChatRuleStore.setMasterEnabled(true)
                    Misc.addChatMessage(Component.literal("§b[FM] Chat Notifications: §aON"))
                    Constants.SUCCESS
                })
                .then(ClientCommands.literal("off").executes {
                    fishmod.features.chat.ChatRuleStore.setMasterEnabled(false)
                    Misc.addChatMessage(Component.literal("§b[FM] Chat Notifications: §cOFF"))
                    Constants.SUCCESS
                })
        }

        /** Prints a formatted reference of FishMod's commands and their argument formats to the player's chat. */
        @JvmStatic
        private fun printCommandHelp() {
            val line: Consumer<String> = Consumer { helpLine(it) }
            line.accept("§b§m                    §r §3§lFishMod Commands §r§b§m                    ")
            line.accept("§7Args in §f<>§7 are required, §8[]§7 optional. Stats commands default to §fyou§7 if no name is given.")
            line.accept("§7All stats commands also work in party chat as §f.cmd§7 (toggle each in §f/fm §8> §7Party Commands).")

            line.accept("")
            line.accept("§3§lStats Lookups")
            line.accept("§e/cata §8[player] §7— Catacombs level")
            line.accept("§e/rtc §8[player] [level] §7— runs to a Cata level §8(default 50)")
            line.accept("§e/rtca §8[player] §7— runs to class 50 for all 5 classes")
            line.accept("§e/crtc §8[player] §f<class> §8[level] §7— XP + runs for one class to a level §8(default 50)")
            line.accept("§8        class = healer | mage | berserk | archer | tank §8(e.g. §7.crtc mage 60§8)")
            line.accept("§e/secrets §7or §e/sa §8[player] §7— total secrets / secret average")
            line.accept("§e/runs §8[player] [floor] §7— floor run count §8(default m7)")
            line.accept("§e/totalruns §8[player] §7— total dungeon runs")
            line.accept("§e/pb §8[player] [floor] §7— floor personal best §8(default m7)")
            line.accept("§e/mp §8[player] §7— Magical Power")
            line.accept("§e/nw §8[player] §7— networth")
            line.accept("§e/level §8[player] §7— Skyblock level")
            line.accept("§e/farming §8[player] §7— farming weight")
            line.accept("§e/nuc §8[player] §7— Crystal Nucleus runs")
            line.accept("§e/worm §7or §e/scatha §8[player] §7— Worm + Scatha bestiary")
            line.accept("§e/bank §8[player] §7— bank + purse")
            line.accept("§e/powder §8[player] §7— Mithril / Gemstone / Glacite powder")
            line.accept("§e/corpse §8[player] §7— Glacite corpses")
            line.accept("§8floor = e, f1-f7, m1-m7 §7(party-only §f.collection [floor]§7 also available)")

            line.accept("")
            line.accept("§3§lYour Stats")
            line.accept("§e/fps §8·§e /tps §8·§e /ping §8·§e /dprofit §7— FPS, server TPS, ping, Croesus profit/run")

            line.accept("")
            line.accept("§3§lDungeon / Kuudra Joins §8(party chat)")
            line.accept("§f.e §8·§f .f1-.f7 §8·§f .m1-.m7 §7— join Catacombs floor")
            line.accept("§f.t1-.t5 §7— join Kuudra tier")

            line.accept("")
            line.accept("§3§lParty Actions")
            line.accept("§e/pk §f<player> §7— kick  §8·§7  §e/pw §7— warp  §8·§7  §e/pt §f<player> §7— transfer  §8·§7  §e/pp §f<player> §7— promote  §8·§7  §e/pd §f<player> §7— demote")
            line.accept("§7In party chat: §f.ai §7(allinvite), §f.d §7(disband), §f.kick/.warp(.w)/.transfer(.pt/.ptme)/.promote/.demote")
            line.accept("§7Control who else can trigger them: §f/fm §8> §7Party §8> §7Party Commands, and §f/fmcmd whitelist|blacklist add|remove|list")

            line.accept("")
            line.accept("§3§lScreens & Misc")
            line.accept("§e/fm §7— config GUI  §8·§7  §e/fmloot §7— Croesus loot")
            line.accept("§e/fm commandkeys §7— bind keys/mouse buttons to run slash commands")
            line.accept("§e/fm aliases §7— make short commands (e.g. §f/dh§7) run longer ones (e.g. §f/warp dh§7)")
            line.accept("§e/nick §8<name>|reset")
            line.accept("§e/fm commandhelp §7— this list  §8·§7  party chat: §f.help §7lists enabled party commands")
            line.accept("§b§m                                                                          ")
        }

        @JvmStatic
        private fun safeInit(name: String, init: () -> Unit) {
            try {
                init()
            } catch (t: Throwable) {
                println("[FishMod] init failed for $name: $t")
            }
        }
    }

    override fun onInitialize() {
        // Load FishMod-specific config (always, separate from blade config)
        FishConfig.manager.load()

        // Cosmetic name changer — restore persisted /nick across sessions, then sync with other mod users
        fishmod.cosmetic.NickData.load()
        fishmod.cosmetic.RemoteNicks.init()
        fishmod.cosmetic.PlayerSize.init()
        fishmod.cosmetic.RemoteSync.init()
        fishmod.utils.InstallHeartbeat.init()

        LagTracker.init()
        SessionStats.init()
        FishPuzzleDisplay.init()
        FishEstTotal.init()
        DungeonDeathMessage.init()
        fishmod.features.ExplosiveShot.init()
        fishmod.features.CritTracker.init()
        FishPartyTracker.init()
        PartyCommandHandler.init()
        SoulflowHud.init()
        PetHud.init()
        CooldownOverlay.init()
        fishmod.features.croesus.CroesusLootDetector.init()
        fishmod.features.CatacombsOverflowOverlay.init()
        fishmod.features.scoreboard.SkillLevels.init()
        fishmod.features.scoreboard.BestiaryProgress.init()
        fishmod.features.scoreboard.CollectionsProgress.init()
        fishmod.features.scoreboard.ElectionInfo.init()
        fishmod.features.scoreboard.FireSaleInfo.init()
        fishmod.features.other.CommandKeys.init()
        fishmod.features.other.WardrobeHotkeys.init()
        // ItemRarityHotbar.init();   // rarity background: inventory-slot coverage (hotbar via HudRenderCallback)
        MayorApi.init()
        // SlayerXpTracker.init();
        // fishmod.features.SkillTracker.init();
        fishmod.features.FireFreezeTimer.init()
        fishmod.features.LoadoutTitle.init()
        fishmod.features.AutoSprint.init()
        fishmod.features.WarpCooldown.init()
        fishmod.features.dungeon.LeapAnnounce.init()
        fishmod.features.dungeon.KeyNotifier.init()
        fishmod.features.dungeon.AutoRequeue.init()
        fishmod.features.dungeon.Blessings.init()
        fishmod.features.dungeon.InvincibilityTracker.init()
        fishmod.features.dungeon.puzzles.PuzzleSolvers.init()
        // PowderTracker.init();
        fishmod.features.dungeon.SimonSaysTracker.init()
        fishmod.features.chat.ChatRuleHandler.init()
        fishmod.features.dungeon.M7LeverWaypoints.init()
        fishmod.features.dungeon.DungeonWaypoints.init()
        fishmod.features.dungeon.StarredMobHighlight.init()
        // Floor 7 boss timers (Maxor/Storm/Goldor); registered here for the Edit-HUD dragger.
        fishmod.features.dungeon.f7.F7Huds.init()
        // Touching Buttons registers its 7 inventory command buttons (self-registering).
        fishmod.utils.config.values.Buttons.init()
        FishHudEditor.register("Tick Timer", fishmod.features.dungeon.f7.F7Huds.tickTimer)
        FishHudEditor.register("Crystal Spawn Time", fishmod.features.dungeon.f7.F7Huds.crystalSpawnTime)
        FishHudEditor.register("Crystal Reminder", fishmod.features.dungeon.f7.F7Huds.crystalReminder)
        FishHudEditor.register("Storm Death Time", fishmod.features.dungeon.f7.F7Huds.stormDeathTime)
        FishHudEditor.register("LB Release Timer", fishmod.features.dungeon.f7.F7Huds.lbReleaseTimer)
        FishHudEditor.register("Storm Crushed", fishmod.features.dungeon.f7.F7Huds.stormCrush)
        FishHudEditor.register("Term Start Timer", fishmod.features.dungeon.f7.F7Huds.termStartTimer)
        FishHudEditor.register("Section Progress", fishmod.features.dungeon.f7.F7Huds.sectionProgress)
        FishHudEditor.register("Current Section", fishmod.features.dungeon.f7.F7Huds.currentSection)
        FishHudEditor.register("Device Completed", fishmod.features.dungeon.f7.F7Huds.deviceNotifier)
        FishHudEditor.register("Melody Warning", fishmod.features.dungeon.f7.F7Huds.melodyWarning)
        FishHudEditor.register("Section Completion", fishmod.features.dungeon.f7.F7Huds.sectionCompletion)
        FishHudEditor.register("S4 Alert", fishmod.features.dungeon.f7.F7Huds.s4Alert)
        FishHudEditor.register("S4 Debug", fishmod.features.dungeon.f7.F7Huds.s4DebugHud)
        FishHudEditor.register("Goldor Splits", fishmod.utils.dungeon.Section.terminalSplits)
        // Own-class detection (from "stats are doubled" message + tab list); boots feature depends on it.
        fishmod.utils.dungeon.DungeonClass.init()
        fishmod.features.ClassColoredBoots.init()
        fishmod.features.dungeon.DupeClassDetector.init()

        // Register all HUD elements in FishHudEditor (position drag editor)
        FishHudEditor.register("Splits", Phase.splitTimer)
        FishHudEditor.registerLocked(
            "Est. Total (follows Splits)",
            { try { Phase.splitTimer.scaledX } catch (t: Throwable) { 0 } },
            {
                try {
                    Phase.splitTimer.scaledY + Constants.TEXT_HEIGHT * Phase.getVisibleRowCount() + 8
                } catch (t: Throwable) {
                    Phase.splitTimer.scaledY + 20
                }
            },
            Phase.SPLIT_LENGTH, Constants.TEXT_HEIGHT + 4
        )
        FishHudEditor.register("Puzzles", FishPuzzleDisplay.puzzleHud)

        // Always register /fm and /fmdbg regardless of whether blade is loaded
        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, _ ->
            fishmod.features.other.CommandAliases.registerAll(dispatcher)
            dispatcher.register(
                ClientCommands.literal("fm")
                    .then(ClientCommands.literal("commandkeys").executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().setScreen(fishmod.features.CommandKeysScreen())
                        }
                        Constants.SUCCESS
                    })
                    .then(ClientCommands.literal("customize").executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().setScreen(fishmod.features.item.ItemCustomizeScreen())
                        }
                        Constants.SUCCESS
                    })
                    .then(ClientCommands.literal("aliases").executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().setScreen(fishmod.features.CommandAliasesScreen())
                        }
                        Constants.SUCCESS
                    })
                    .then(ClientCommands.literal("commandhelp").executes {
                        printCommandHelp()
                        Constants.SUCCESS
                    })
                    .then(ClientCommands.literal("help").executes {
                        printCommandHelp()
                        Constants.SUCCESS
                    })
                    .then(waypointSubcommand("wp"))
                    .then(waypointSubcommand("waypoint"))
                    .then(waypointSubcommand("waypoints"))
                    .then(chatNotificationsSubcommand("chatnotifications"))
                    .then(chatNotificationsSubcommand("cn"))
                    .executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().setScreen(fishmod.features.FishModScreen())
                        }
                        Constants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("fmloot")
                    .executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().setScreen(fishmod.features.croesus.LootTrackerScreen())
                        }
                        Constants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("fmnicktest")
                    .executes { ctx ->
                        if (fishmod.utils.DevOnly.deny(ctx.source)) return@executes Constants.SUCCESS
                        val mc = Minecraft.getInstance()
                        if (mc.player == null || mc.connection == null) {
                            Misc.addChatMessage(Component.literal("§cNot in a world."))
                            return@executes Constants.SUCCESS
                        }
                        val remoteOn = fishmod.utils.config.values.FishSettings.remoteNicksEnabled
                        Misc.addChatMessage(
                            Component.literal(
                                "§b[fmnicktest] §7See Others: §f$remoteOn" +
                                    " §8|§7 own nick active: §f" + fishmod.cosmetic.NickState.isActive() +
                                    " §8|§7 raw: §f" + (fishmod.cosmetic.NickState.getRaw() ?: "(none)")
                            )
                        )
                        fishmod.cosmetic.RemoteNicks.uploadOwn()
                        Misc.addChatMessage(Component.literal("§b[fmnicktest] §7re-uploaded own nick."))
                        fishmod.cosmetic.RemoteNicks.forceRefresh()
                        Misc.addChatMessage(Component.literal("§b[fmnicktest] §7triggered RemoteNicks.refresh()…"))
                        mc.schedule {
                            Thread({
                                try {
                                    Thread.sleep(1200)
                                } catch (ignored: InterruptedException) {
                                }
                                mc.schedule {
                                    val cache = fishmod.cosmetic.RemoteNicks.snapshot()
                                    Misc.addChatMessage(Component.literal("§b[fmnicktest] §7styledByName cache: §f" + cache.size + " §7entries"))
                                    var count = 0
                                    for (e in cache.entries) {
                                        val line: MutableComponent = Component.literal("§7  " + e.key + " §8→ ").copy()
                                        line.append(e.value)
                                        Misc.addChatMessage(line)
                                        if (++count > 10) {
                                            Misc.addChatMessage(Component.literal("§8  (…more)")); break
                                        }
                                    }
                                    if (cache.isEmpty()) {
                                        Misc.addChatMessage(Component.literal("§c[fmnicktest] cache is empty — chat rewrite has nothing to apply. Check See Others toggle."))
                                    } else {
                                        Misc.addChatMessage(Component.literal("§a[fmnicktest] cache populated. If chat still shows IGNs, the mixin path isn't covering Hypixel's chat handler — paste a chat screenshot."))
                                    }
                                }
                            }, "fmnicktest-dump").start()
                        }
                        Constants.SUCCESS
                    }
            )
            // ── Party alias commands ──────────────────────────────────────────
            dispatcher.register(
                ClientCommands.literal("pk")
                    .then(
                        ClientCommands.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val name = StringArgumentType.getString(ctx, "name")
                                val mc = Minecraft.getInstance()
                                if (mc.connection != null) mc.connection!!.sendCommand("p kick $name")
                                Constants.SUCCESS
                            }
                    )
            )
            dispatcher.register(
                ClientCommands.literal("pw")
                    .executes {
                        val mc = Minecraft.getInstance()
                        if (mc.connection != null) mc.connection!!.sendCommand("p warp")
                        Constants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("pt")
                    .then(
                        ClientCommands.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val name = StringArgumentType.getString(ctx, "name")
                                val mc = Minecraft.getInstance()
                                if (mc.connection != null) mc.connection!!.sendCommand("p transfer $name")
                                Constants.SUCCESS
                            }
                    )
            )
            dispatcher.register(
                ClientCommands.literal("pp")
                    .then(
                        ClientCommands.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val name = StringArgumentType.getString(ctx, "name")
                                val mc = Minecraft.getInstance()
                                if (mc.connection != null) mc.connection!!.sendCommand("p promote $name")
                                Constants.SUCCESS
                            }
                    )
            )
            dispatcher.register(
                ClientCommands.literal("pd")
                    .then(
                        ClientCommands.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val name = StringArgumentType.getString(ctx, "name")
                                val mc = Minecraft.getInstance()
                                if (mc.connection != null) mc.connection!!.sendCommand("p demote $name")
                                Constants.SUCCESS
                            }
                    )
            )
            // ─────────────────────────────────────────────────────────────────

            dispatcher.register(
                ClientCommands.literal("fmpet").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    val mc = Minecraft.getInstance()
                    mc.schedule {
                        Misc.addChatMessage(Component.literal("§b--- Pet HUD ---"))
                        Misc.addChatMessage(Component.literal("§7" + PetHud.debugState()))
                        Misc.addChatMessage(Component.literal("§b--- Cooldown Overlay ---"))
                        Misc.addChatMessage(Component.literal("§7" + CooldownOverlay.debugState()))
                    }
                    Constants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmpetdump").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    PetHud.debugDumpPetLines = !PetHud.debugDumpPetLines
                    Misc.addChatMessage(Component.literal("§b[fmpet] dump pet-related chat lines: §f" + PetHud.debugDumpPetLines))
                    Constants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmcddump").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    CooldownOverlay.debugDumpSound = !CooldownOverlay.debugDumpSound
                    Misc.addChatMessage(Component.literal("§b[fmcd] dump cooldown sound events: §f" + CooldownOverlay.debugDumpSound))
                    Constants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmcatadump").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    fishmod.features.CatacombsOverflowOverlay.debugDumpLines = !fishmod.features.CatacombsOverflowOverlay.debugDumpLines
                    Misc.addChatMessage(Component.literal("§b[fmcata] dump Catacombs/class menu item lines: §f" + fishmod.features.CatacombsOverflowOverlay.debugDumpLines))
                    Constants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmblocks").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    val mc = Minecraft.getInstance()
                    mc.schedule {
                        if (mc.player == null || mc.level == null) {
                            Misc.addChatMessage(Component.literal("§cNo world")); return@schedule
                        }
                        val c = mc.player!!.blockPosition()
                        val counts = HashMap<String, Int>()
                        val R = 7
                        val m = BlockPos.MutableBlockPos()
                        for (dx in -R..R) for (dy in -R..R) for (dz in -R..R) {
                            m.set(c.x + dx, c.y + dy, c.z + dz)
                            val b: Block = mc.level!!.getBlockState(m).block
                            if (b == Blocks.AIR) continue
                            val id = BuiltInRegistries.BLOCK.getKey(b).toString()
                            counts.merge(id, 1, Integer::sum)
                        }
                        Misc.addChatMessage(Component.literal("§b--- Blocks within $R (top 20) ---"))
                        counts.entries.sortedByDescending { it.value }.take(20)
                            .forEach { e -> Misc.addChatMessage(Component.literal("§7" + e.value + "x §f" + e.key)) }
                    }
                    Constants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmssdebug").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    fishmod.features.dungeon.SimonSaysTracker.debug = !fishmod.features.dungeon.SimonSaysTracker.debug
                    Misc.addChatMessage(Component.literal("§b[ssdbg] log Simon Says block transitions: §f" + fishmod.features.dungeon.SimonSaysTracker.debug))
                    Constants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmnuc").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    fishmod.utils.HypixelApi.dumpNucleus(Minecraft.getInstance())
                    Constants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmgarden").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    fishmod.utils.HypixelApi.dumpGarden(Minecraft.getInstance())
                    Constants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmprofile").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    fishmod.utils.HypixelApi.dumpEconomy(Minecraft.getInstance())
                    Constants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmtabdump").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    val mc = Minecraft.getInstance()
                    mc.schedule {
                        if (mc.connection == null) {
                            Misc.addChatMessage(Component.literal("§cNo network")); return@schedule
                        }
                        Misc.addChatMessage(Component.literal("§b--- Tab entries (non-empty) ---"))
                        var n = 0
                        for (e: PlayerInfo in mc.connection!!.onlinePlayers) {
                            if (e.tabListDisplayName == null) continue
                            val s = e.tabListDisplayName!!.string.replace(Regex("§."), "").trim()
                            if (s.isEmpty()) continue
                            if (s.lowercase().contains("pet") || s.contains("Lvl") || s.contains("XP") || s.contains("/")) {
                                Misc.addChatMessage(Component.literal("§7$s"))
                                if (++n > 30) break
                            }
                        }
                        Misc.addChatMessage(Component.literal("§b--- End ($n) ---"))
                    }
                    Constants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmdbg").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    val mc = Minecraft.getInstance()
                    mc.schedule {
                        Misc.addChatMessage(Component.literal("§b--- FishMod Debug ---"))
                        Misc.addChatMessage(Component.literal("§7Location: §f" + Location.getCurrentLocation()))
                        Misc.addChatMessage(Component.literal("§7inSkyblock: §f" + Location.inSkyblock()))
                        Misc.addChatMessage(Component.literal("§7inDungeon: §f" + Location.inDungeon()))
                        Misc.addChatMessage(Component.literal("§7showPuzzles: §f" + fishmod.utils.config.values.FishSettings.showPuzzles))
                        Misc.addChatMessage(Component.literal("§7Puzzle list (" + FishPuzzleDisplay.getPuzzles().size + "): §f" + FishPuzzleDisplay.getPuzzles()))
                        try {
                            Misc.addChatMessage(Component.literal("§7Phase.runStarted: §f" + Phase.runStarted()))
                        } catch (t: Throwable) {
                            Misc.addChatMessage(Component.literal("§cPhase.runStarted ERR: " + t.message))
                        }
                        try {
                            Misc.addChatMessage(Component.literal("§7Phase.enableSplits: §f" + Phase.enableSplits))
                        } catch (t: Throwable) {
                            Misc.addChatMessage(Component.literal("§cPhase.enableSplits ERR: " + t.message))
                        }
                        try {
                            Misc.addChatMessage(Component.literal("§7blade loaded: §f" + FabricLoader.getInstance().isModLoaded("blade-addons")))
                        } catch (t: Throwable) {
                            Misc.addChatMessage(Component.literal("§cloader ERR"))
                        }
                        // Dump tab list
                        val handler: ClientPacketListener? = mc.connection
                        if (handler == null) {
                            Misc.addChatMessage(Component.literal("§cNo network handler"))
                        } else {
                            var total = 0
                            var nullName = 0
                            for (e: PlayerInfo in handler.onlinePlayers) {
                                total++
                                if (e.tabListDisplayName == null) {
                                    nullName++; continue
                                }
                                val raw = e.tabListDisplayName!!.string
                                val clean = raw.replace(Regex("§."), "").trim()
                                if (clean.isNotEmpty())
                                    Misc.addChatMessage(Component.literal("§8TAB: §7$clean"))
                            }
                            Misc.addChatMessage(Component.literal("§7Tab entries: §f$total (§c$nullName null§7)"))
                        }
                        // Dump scoreboard sidebar
                        if (mc.level != null) {
                            val sb: Scoreboard = mc.level!!.scoreboard
                            val sidebar: Objective? = sb.getDisplayObjective(DisplaySlot.SIDEBAR)
                            if (sidebar == null) {
                                Misc.addChatMessage(Component.literal("§7Sidebar: §cnone"))
                            } else {
                                Misc.addChatMessage(Component.literal("§7Sidebar obj: §f" + sidebar.displayName.string))
                                for (entry: PlayerScoreEntry in sb.listPlayerScores(sidebar)) {
                                    val owner = entry.owner
                                    val team: PlayerTeam? = sb.getPlayersTeam(owner)
                                    val line = if (team != null)
                                        team.playerPrefix.string + owner + team.playerSuffix.string
                                    else
                                        entry.ownerName().string
                                    val clean = line.replace(Regex("§."), "").trim()
                                    if (clean.isNotEmpty())
                                        Misc.addChatMessage(Component.literal("§8SB: §7$clean"))
                                }
                            }
                        }
                        Misc.addChatMessage(Component.literal("§b--- End Debug ---"))
                    }
                    Constants.SUCCESS
                }.then(
                    ClientCommands.argument("sub", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val arg = StringArgumentType.getString(ctx, "sub")
                            val mc = Minecraft.getInstance()
                            val parts = arg.trim().split(Regex("\\s+"), 2)
                            if (parts[0] == "cprice") {
                                if (parts.size < 2) {
                                    mc.schedule { Misc.addChatMessage(Component.literal("§cUsage: /fmdbg cprice <ITEM_ID>")) }
                                    return@executes Constants.SUCCESS
                                }
                                val pid = parts[1].trim().uppercase()
                                fishmod.features.croesus.CroesusPrices.refreshIfStale().whenComplete { _, _ ->
                                    mc.schedule {
                                        Misc.addChatMessage(
                                            Component.literal(
                                                "§b$pid §7→ §f" + fishmod.features.croesus.CroesusPrices.debugSource(pid)
                                            )
                                        )
                                    }
                                }
                                return@executes Constants.SUCCESS
                            }
                            if (parts[0] == "mp") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.schedule { Misc.addChatMessage(Component.literal("§cUsage: /fmdbg mp <ign>")) }
                                    return@executes Constants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.getByName(mc, ign) { data ->
                                    mc.schedule { Misc.addChatMessage(Component.literal("§b$ign magicalPower=§f" + data.magicalPower)) }
                                }
                                return@executes Constants.SUCCESS
                            }
                            if (parts[0] == "mpraw") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.schedule { Misc.addChatMessage(Component.literal("§cUsage: /fmdbg mpraw <ign>")) }
                                    return@executes Constants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.dumpMemberKeys(mc, ign)
                                return@executes Constants.SUCCESS
                            }
                            if (parts[0] == "col") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.schedule { Misc.addChatMessage(Component.literal("§cUsage: /fmdbg col <ign>")) }
                                    return@executes Constants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.getByName(mc, ign) { data ->
                                    mc.schedule {
                                        var cataTotal = 0L
                                        for (t in data.cataTimes) cataTotal += t
                                        var masterTotal = 0L
                                        for (i in 1..7) masterTotal += data.masterTimes[i]
                                        val col = cataTotal + masterTotal * 2
                                        Misc.addChatMessage(Component.literal("§b--- Collection debug: $ign ---"))
                                        val cata = StringBuilder("§7cata: ")
                                        for (i in 0..7) cata.append(if (i == 0) "E" else "F$i").append("=").append(data.cataTimes[i]).append(" ")
                                        Misc.addChatMessage(Component.literal(cata.toString()))
                                        val master = StringBuilder("§7master: ")
                                        for (i in 1..7) master.append("M").append(i).append("=").append(data.masterTimes[i]).append(" ")
                                        Misc.addChatMessage(Component.literal(master.toString()))
                                        Misc.addChatMessage(Component.literal("§7cataTotal=§f$cataTotal §7masterTotal=§f$masterTotal"))
                                        Misc.addChatMessage(Component.literal("§7computed col=§f$col §7(cata×1 + master×2)"))
                                    }
                                }
                                return@executes Constants.SUCCESS
                            }
                            if (parts[0] == "runs") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.schedule { Misc.addChatMessage(Component.literal("§cUsage: /fmdbg runs <ign>")) }
                                    return@executes Constants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.getByName(mc, ign) { data ->
                                    mc.schedule {
                                        Misc.addChatMessage(Component.literal("§b--- Runs debug: $ign ---"))
                                        Misc.addChatMessage(Component.literal("§7totalRuns: §f" + data.totalRuns))
                                        val cata = StringBuilder("§7cataTimes: ")
                                        for (i in 0..7) cata.append("F").append(if (i == 0) "E" else i.toString()).append("=").append(data.cataTimes[i]).append(" ")
                                        Misc.addChatMessage(Component.literal(cata.toString()))
                                        val master = StringBuilder("§7masterTimes: ")
                                        for (i in 1..7) master.append("M").append(i).append("=").append(data.masterTimes[i]).append(" ")
                                        Misc.addChatMessage(Component.literal(master.toString()))
                                        Misc.addChatMessage(Component.literal("§b--- End ---"))
                                    }
                                }
                            }
                            Constants.SUCCESS
                        }
                )
            )

            // ── Local lookup /commands (native tab-complete; result shown in your own chat) ──
            val playerSuggest = SuggestionProvider<FabricClientCommandSource> { _, b ->
                val mc = Minecraft.getInstance()
                if (mc.connection != null) {
                    val rem = b.remaining.lowercase()
                    val seen = HashSet<String>()
                    for (e: PlayerInfo in mc.connection!!.onlinePlayers) {
                        val n = e.profile.name
                        if (n.isBlank() || !seen.add(n.lowercase())) continue
                        if (n.lowercase().startsWith(rem)) b.suggest(n)
                    }
                }
                b.buildFuture()
            }
            val floors = arrayOf("e", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "m1", "m2", "m3", "m4", "m5", "m6", "m7")
            val floorSuggest = SuggestionProvider<FabricClientCommandSource> { _, b ->
                val rem = b.remaining.lowercase()
                for (f in floors) if (f.startsWith(rem)) b.suggest(f)
                b.buildFuture()
            }

            // /fmcmd whitelist|blacklist — manages the name lists the "Who Can Trigger" dropdown reads.
            dispatcher.register(
                ClientCommands.literal("fmcmd")
                    .then(
                        ClientCommands.literal("whitelist")
                            .executes { printNameList("Whitelist", fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist); Constants.SUCCESS }
                            .then(ClientCommands.literal("list").executes { printNameList("Whitelist", fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist); Constants.SUCCESS })
                            .then(
                                ClientCommands.literal("add").then(
                                    ClientCommands.argument("name", StringArgumentType.word()).suggests(playerSuggest)
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist =
                                                fishmod.utils.NameList.add(fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            Misc.addChatMessage(Component.literal("§7[FM] Added §f$name §7to the party-action whitelist."))
                                            Constants.SUCCESS
                                        }
                                )
                            )
                            .then(
                                ClientCommands.literal("remove").then(
                                    ClientCommands.argument("name", StringArgumentType.word())
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist =
                                                fishmod.utils.NameList.remove(fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            Misc.addChatMessage(Component.literal("§7[FM] Removed §f$name §7from the party-action whitelist."))
                                            Constants.SUCCESS
                                        }
                                )
                            )
                    )
                    .then(
                        ClientCommands.literal("blacklist")
                            .executes { printNameList("Blacklist", fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist); Constants.SUCCESS }
                            .then(ClientCommands.literal("list").executes { printNameList("Blacklist", fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist); Constants.SUCCESS })
                            .then(
                                ClientCommands.literal("add").then(
                                    ClientCommands.argument("name", StringArgumentType.word()).suggests(playerSuggest)
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist =
                                                fishmod.utils.NameList.add(fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            Misc.addChatMessage(Component.literal("§7[FM] Added §f$name §7to the party-action blacklist."))
                                            Constants.SUCCESS
                                        }
                                )
                            )
                            .then(
                                ClientCommands.literal("remove").then(
                                    ClientCommands.argument("name", StringArgumentType.word())
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist =
                                                fishmod.utils.NameList.remove(fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            Misc.addChatMessage(Component.literal("§7[FM] Removed §f$name §7from the party-action blacklist."))
                                            Constants.SUCCESS
                                        }
                                )
                            )
                    )
            )

            // Lookups + player-arg party actions (kick/transfer/promote/demote take a player name).
            for (name in arrayOf(
                "cata", "rtca", "secrets", "sa", "totalruns", "mp", "nw", "networth",
                "bank", "corpse", "corpses", "level", "sblvl", "farming", "nuc", "nucleus", "powder",
                "worm", "scatha", "kick", "transfer", "promote", "demote"
            )) {
                dispatcher.register(
                    ClientCommands.literal(name)
                        .executes { runLocalLookup(name, null, null) }
                        .then(
                            ClientCommands.argument("player", StringArgumentType.word()).suggests(playerSuggest)
                                .executes { c -> runLocalLookup(name, StringArgumentType.getString(c, "player"), null) }
                        )
                )
            }
            // NOTE: no "collection" here — Hypixel already owns /collection. The party-chat
            // ".collection" command still works via the chat handler.
            for (name in arrayOf("pb", "runs")) {
                dispatcher.register(
                    ClientCommands.literal(name)
                        .executes { runLocalLookup(name, null, null) }
                        .then(
                            ClientCommands.argument("player", StringArgumentType.word()).suggests(playerSuggest)
                                .executes { c -> runLocalLookup(name, StringArgumentType.getString(c, "player"), null) }
                                .then(
                                    ClientCommands.argument("floor", StringArgumentType.word()).suggests(floorSuggest)
                                        .executes { c -> runLocalLookup(name, StringArgumentType.getString(c, "player"), StringArgumentType.getString(c, "floor")) }
                                )
                        )
                )
            }
            dispatcher.register(
                ClientCommands.literal("rtc")
                    .executes { runLocalLookup("rtc", null, null) }
                    .then(
                        ClientCommands.argument("player", StringArgumentType.word()).suggests(playerSuggest)
                            .executes { c -> runLocalLookup("rtc", StringArgumentType.getString(c, "player"), null) }
                            .then(
                                ClientCommands.argument("level", StringArgumentType.word())
                                    .executes { c -> runLocalLookup("rtc", StringArgumentType.getString(c, "player"), StringArgumentType.getString(c, "level")) }
                            )
                    )
            )
            // /crtc [name] [class] [level]; PartyCommandHandler treats a leading class arg as "self".
            val classSuggest = SuggestionProvider<FabricClientCommandSource> { _, b ->
                val rem = b.remaining.lowercase()
                for (cl in arrayOf("healer", "mage", "berserk", "archer", "tank"))
                    if (cl.startsWith(rem)) b.suggest(cl)
                b.buildFuture()
            }
            dispatcher.register(
                ClientCommands.literal("crtc")
                    .executes { runLocalLookup("crtc", null, null, null) }
                    .then(
                        ClientCommands.argument("a1", StringArgumentType.word()).suggests(classSuggest)
                            .executes { c -> runLocalLookup("crtc", StringArgumentType.getString(c, "a1"), null, null) }
                            .then(
                                ClientCommands.argument("a2", StringArgumentType.word()).suggests(classSuggest)
                                    .executes { c -> runLocalLookup("crtc", StringArgumentType.getString(c, "a1"), StringArgumentType.getString(c, "a2"), null) }
                                    .then(
                                        ClientCommands.argument("a3", StringArgumentType.word())
                                            .executes { c -> runLocalLookup("crtc", StringArgumentType.getString(c, "a1"), StringArgumentType.getString(c, "a2"), StringArgumentType.getString(c, "a3")) }
                                    )
                            )
                    )
            )
            // No-arg commands: self metrics, party actions, and join-floor/Kuudra shortcuts.
            for (name in arrayOf(
                "fps", "tps", "ping", "dprofit", "crit", "ai", "allinv", "d",
                "e", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "m1", "m2", "m3", "m4", "m5", "m6", "m7",
                "t1", "t2", "t3", "t4", "t5"
            )) {
                dispatcher.register(ClientCommands.literal(name).executes { c -> runLocalLookup(name, null, null) })
            }
            // Bare /warp runs the local party action; with an arg, forward to Hypixel's server-side /warp <dest>.
            dispatcher.register(
                ClientCommands.literal("warp")
                    .executes { runLocalLookup("warp", null, null) }
                    .then(
                        ClientCommands.argument("dest", StringArgumentType.greedyString())
                            .executes { c ->
                                val dest = StringArgumentType.getString(c, "dest")
                                val mc = Minecraft.getInstance()
                                // Send the packet directly, bypassing Fabric's dispatcher — otherwise it re-matches
                                // our /warp literal and recurses into this lambda, blowing the stack.
                                if (mc.player != null && mc.player!!.connection != null)
                                    mc.player!!.connection.send(ServerboundChatCommandPacket("warp $dest"))
                                Constants.SUCCESS
                            }
                    )
            )
        })

        // Legit-mode map settings are only ever toggled off by FishModAddons; they must never stay
        // off silently across a relog/update, so force them back to their safe (legit) defaults on
        // every server join. FishModAddons re-applies whatever it wants after this fires.
        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, _ ->
            fishmod.utils.config.values.DungeonMapSettings.mapLegitMode = true
            fishmod.utils.config.values.DungeonMapSettings.mapInsightLegit = false
        })

        // Both mods register /cata; Brigadier honors whichever executes() registered last, which isn't
        // deterministic at init — so we re-register ours on every server join to win.
        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, _ ->
            val d: CommandDispatcher<FabricClientCommandSource>? = ClientCommands.getActiveDispatcher()
            if (d == null) return@Join
            try {
                d.register(
                    ClientCommands.literal("cata")
                        .executes { c -> runLocalLookup("cata", null, null) }
                        .then(
                            ClientCommands.argument("player", StringArgumentType.word())
                                .executes { c -> runLocalLookup("cata", StringArgumentType.getString(c, "player"), null) }
                        )
                )
            } catch (ignored: Exception) {
            }
        })

        // ── Warp Map HUD + click detection ───────────────────────────────────
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "soulflow_hud")) { ctx, tickCounter -> SoulflowHud.renderHud(ctx, tickCounter) }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "pet_hud")) { ctx, tickCounter -> PetHud.renderHud(ctx, tickCounter) }
        // Rarity background is drawn behind items via DrawContextMixin + INVENTORY_SLOT_BEFORE, not HudRenderCallback (which would draw over items).
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "cooldown_overlay_hotbar")) { ctx, tickCounter -> CooldownOverlay.renderHotbar(ctx, tickCounter) }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "boss_bar_feature")) { ctx, _ -> BossBarFeature.renderHud(ctx) }
        // Rendered manually here, not via practical-config's auto-render (unreliable); Phase forces its condition-suppliers false so this is the single render path.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "phase_splits")) { ctx, _ -> Phase.renderHud(ctx) }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "f7_huds")) { ctx, _ -> fishmod.features.dungeon.f7.F7Huds.renderHud(ctx) }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "dungeon_waypoints_overlay")) { ctx, _ -> fishmod.features.dungeon.DungeonWaypoints.renderOverlay(ctx) }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "session_stats")) { ctx, tickCounter -> SessionStats.renderHud(ctx, tickCounter) }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "warp_cooldown")) { ctx, tickCounter -> fishmod.features.WarpCooldown.renderHud(ctx, tickCounter) }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "blessings")) { ctx, tickCounter -> fishmod.features.dungeon.Blessings.renderHud(ctx, tickCounter) }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "invincibility")) { ctx, tickCounter -> fishmod.features.dungeon.InvincibilityTracker.renderHud(ctx, tickCounter) }
        fishmod.utils.SkyblockItems.initAsync()

        // ── PB Pace (live delta vs personal-best splits) ─────────────────────
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "pb_pace_hud")) { ctx, tickCounter -> fishmod.features.PbPaceHud.renderHud(ctx, tickCounter) }
        FishHudEditor.register(
            "PB Pace",
            { fishmod.utils.config.values.FishSettings.pbPaceHudX },
            { v -> fishmod.utils.config.values.FishSettings.pbPaceHudX = v },
            { fishmod.utils.config.values.FishSettings.pbPaceHudY },
            { v -> fishmod.utils.config.values.FishSettings.pbPaceHudY = v }, 130, 14 * 3,
            { fishmod.utils.config.values.FishSettings.pbPaceScale },
            { v -> fishmod.utils.config.values.FishSettings.pbPaceScale = v },
            { fishmod.features.PbPaceHud.isVisible() }
        )

        // ── Dungeon Map (ported from System22) ───────────────────────────────
        fishmod.features.dungeon.map.MapColors.init()
        fishmod.features.dungeon.map.DungeonMap.init()
        fishmod.features.dungeon.map.Scan.register()
        fishmod.features.dungeon.map.Mimic.register()
        fishmod.features.dungeon.map.MapHud.register()
        fishmod.features.dungeon.map.MapInfoHud.register()
        fishmod.features.dungeon.map.MapImageLoader.init()
        fishmod.features.dungeon.map.DungeonScore.register()
        fishmod.features.dungeon.map.DoorHighlight.init()
        fishmod.utils.events.Events.ON_GAME_MESSAGE.register { message ->
            fishmod.features.dungeon.map.DungeonState.onChatMessage(message.string)
            fishmod.features.dungeon.map.DungeonScore.onChatMessage(message.string)
            false
        }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "dungeon_map_score_messages")) { ctx, tickCounter -> fishmod.features.dungeon.map.ScoreMessages.renderHud(ctx, tickCounter) }

        FishHudEditor.register(
            "Dungeon Map",
            java.util.function.IntSupplier { fishmod.utils.config.values.DungeonMapSettings.mapX.toInt() },
            java.util.function.IntConsumer { v -> fishmod.utils.config.values.DungeonMapSettings.mapX = v.toFloat() },
            java.util.function.IntSupplier { fishmod.utils.config.values.DungeonMapSettings.mapY.toInt() },
            java.util.function.IntConsumer { v -> fishmod.utils.config.values.DungeonMapSettings.mapY = v.toFloat() },
            // Match MapHud's actual render size (a hardcoded 160x160 box here made the editor
            // preview noticeably bigger than the real in-game map, which is only ~116px + the
            // background-size setting on each side for the default room grid).
            fishmod.features.dungeon.map.MapHud.baseWidth(net.minecraft.client.Minecraft.getInstance()),
            fishmod.features.dungeon.map.MapHud.baseHeight(net.minecraft.client.Minecraft.getInstance()),
            java.util.function.DoubleSupplier { fishmod.utils.config.values.DungeonMapSettings.mapScale.toDouble() },
            java.util.function.DoubleConsumer { v -> fishmod.utils.config.values.DungeonMapSettings.mapScale = v.toFloat() }
        )
        FishHudEditor.register(
            "Dungeon Map Info",
            java.util.function.IntSupplier { fishmod.utils.config.values.DungeonMapSettings.mapInfoX.toInt() },
            java.util.function.IntConsumer { v -> fishmod.utils.config.values.DungeonMapSettings.mapInfoX = v.toFloat() },
            java.util.function.IntSupplier { fishmod.utils.config.values.DungeonMapSettings.mapInfoY.toInt() },
            java.util.function.IntConsumer { v -> fishmod.utils.config.values.DungeonMapSettings.mapInfoY = v.toFloat() },
            160, 40,
            java.util.function.DoubleSupplier { fishmod.utils.config.values.DungeonMapSettings.mapInfoScale.toDouble() },
            java.util.function.DoubleConsumer { v -> fishmod.utils.config.values.DungeonMapSettings.mapInfoScale = v.toFloat() },
            java.util.function.BooleanSupplier { fishmod.features.dungeon.map.MapInfoHud.enabled() }
        )

        // Tracker overlay (reset button) for HandledScreens — fires after full render chain
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) return@AfterInit
            fishmod.features.croesus.CroesusLootDetector.onScreenInit(screen)
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, mx, my, _ ->
                SessionStats.renderInScreen(ctx, mx, my)
            })
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click ->
                if (click.button() != 0) return@AllowMouseClick true // only left click resets
                val mx = click.x()
                val my = click.y()
                if (SessionStats.handleScreenClick(mx, my)) return@AllowMouseClick false
                true
            })
        })

        // Always init FishMod's own framework — since the fishmod.* rename it's separate from blade-addons's,
        // so this must run regardless. Each init is guarded so one clash can't take down the entrypoint.
        safeInit("FolderUtility") { FolderUtility.init() }
        safeInit("Components") { Components.init() }
        safeInit("Config") { Config.manager.load() }
        safeInit("Keybinds") { Keybinds.init() }
        safeInit("CustomEvents") { CustomEvents.init() }
        safeInit("Debug") { Debug.init() }
        safeInit("Location") { Location.init() }
        safeInit("Phase") { Phase.init() }
        safeInit("Section") { Section.init() }
        safeInit("PartyUtil") { PartyUtil.init() }
        safeInit("EntityUtil") { EntityUtil.init() }
        safeInit("RenderingEvents") { RenderingEvents.init() }
        safeInit("Scheduler") { Scheduler.init() }
        // Location Ping needs the world render passes (RenderingEvents) registered first.
        // safeInit("PingFeature", fishmod.features.PingFeature::init);
    }
}
