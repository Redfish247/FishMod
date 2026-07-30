package fishmod

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import fishmod.features.CatacombsOverflowOverlay
import fishmod.features.CooldownOverlay
import fishmod.features.FishHudEditor
import fishmod.features.FishModScreen
import fishmod.features.PetHud
import fishmod.features.SoulflowHud
import fishmod.features.dungeon.DungeonDeathMessage
import fishmod.features.dungeon.FishEstTotal
import fishmod.features.dungeon.FishPuzzleDisplay
import fishmod.features.dungeon.LagTracker
import fishmod.features.dungeon.PartyCommandHandler
import fishmod.features.dungeon.PuzzleDisplay
import fishmod.features.dungeon.SessionStats
import fishmod.features.croesus.LootTrackerOverlay
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
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.registry.Registries
import net.minecraft.scoreboard.Scoreboard
import net.minecraft.scoreboard.ScoreboardDisplaySlot
import net.minecraft.scoreboard.ScoreboardEntry
import net.minecraft.scoreboard.ScoreboardObjective
import net.minecraft.scoreboard.Team
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
            val mc = MinecraftClient.getInstance()
            val self = mc.player?.gameProfile?.name ?: return Constants.SUCCESS
            PartyCommandHandler.onPartyCommand(self, cmd, arg1, arg2, arg3, PartyCommandHandler.LOCAL)
            return Constants.SUCCESS
        }

        /** Prints a party-action whitelist/blacklist to your own chat, e.g. from /fmcmd whitelist. */
        @JvmStatic
        private fun printNameList(label: String, csv: String) {
            val names = fishmod.utils.NameList.toList(csv)
            Misc.addChatMessage(
                Text.literal(
                    "§b[FM] Party-Action $label §7(${names.size}): §f" +
                        (if (names.isEmpty()) "(empty)" else names.joinToString(", "))
                )
            )
        }

        private val HELP_CMD_TOKEN: Pattern = Pattern.compile("[/.][a-zA-Z][a-zA-Z0-9]*")

        /**
         * Prints one command-help line. If the line names exactly one command (e.g. "/cata [player]"),
         * the whole line is made click-to-suggest so you can drop the command into chat with one click;
         * lines listing several commands, headers, and prose are printed plain.
         */
        @JvmStatic
        private fun helpLine(text: String) {
            val m: Matcher = HELP_CMD_TOKEN.matcher(text)
            var cmd: String? = null
            if (m.find()) {
                cmd = m.group()
                if (m.find()) cmd = null // more than one command on the line → leave it plain
            }
            if (cmd == null) {
                Misc.addChatMessage(Text.literal(text))
                return
            }
            val suggest = cmd
            val t: MutableText = Text.literal(text)
            t.setStyle(
                t.style
                    .withClickEvent(ClickEvent.SuggestCommand(suggest))
                    .withHoverEvent(HoverEvent.ShowText(Text.literal("§7Click to put §f$suggest§7 in chat")))
            )
            Misc.addChatMessage(t)
        }

        /** Registers /dwp and its /dungeonwaypoints alias — dungeon waypoint editor, see [fishmod.features.dungeon.DungeonWaypoints]. */
        @JvmStatic
        private fun registerDungeonWaypointCommand(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
            val tree = ClientCommandManager.literal("dwp")
                .executes { fishmod.features.dungeon.DungeonWaypoints.toggleEdit(); Constants.SUCCESS }
                .then(ClientCommandManager.literal("edit").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleEdit(); Constants.SUCCESS
                })
                .then(ClientCommandManager.literal("fill").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleFill(); Constants.SUCCESS
                })
                .then(
                    ClientCommandManager.literal("size")
                        .then(
                            ClientCommandManager.argument("value", DoubleArgumentType.doubleArg(0.1, 1.0))
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setSize(
                                        DoubleArgumentType.getDouble(ctx, "value")
                                    )
                                    Constants.SUCCESS
                                }
                        )
                )
                .then(
                    ClientCommandManager.literal("distance")
                        .then(
                            ClientCommandManager.argument("value", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setDistance(
                                        IntegerArgumentType.getInteger(ctx, "value")
                                    )
                                    Constants.SUCCESS
                                }
                        )
                )
                .then(ClientCommandManager.literal("resetsecrets").executes {
                    fishmod.features.dungeon.DungeonWaypoints.resetSecrets(); Constants.SUCCESS
                })
                .then(
                    ClientCommandManager.literal("type")
                        .then(
                            ClientCommandManager.argument("value", StringArgumentType.word())
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
                    ClientCommandManager.literal("timer")
                        .then(
                            ClientCommandManager.argument("value", StringArgumentType.word())
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
                .then(ClientCommandManager.literal("useblocksize").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleUseBlockSize(); Constants.SUCCESS
                })
                .then(
                    ClientCommandManager.literal("offset")
                        .then(
                            ClientCommandManager.argument("x", DoubleArgumentType.doubleArg())
                                .then(
                                    ClientCommandManager.argument("y", DoubleArgumentType.doubleArg())
                                        .then(
                                            ClientCommandManager.argument("z", DoubleArgumentType.doubleArg())
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
                .then(ClientCommandManager.literal("through").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleThrough(); Constants.SUCCESS
                })
                .then(
                    ClientCommandManager.literal("color")
                        .then(
                            ClientCommandManager.argument("hex", StringArgumentType.word())
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setColor(StringArgumentType.getString(ctx, "hex"))
                                    Constants.SUCCESS
                                }
                        )
                )
                .then(ClientCommandManager.literal("export").executes {
                    fishmod.features.dungeon.DungeonWaypoints.exportToClipboard(); Constants.SUCCESS
                })
                .then(ClientCommandManager.literal("import").executes {
                    fishmod.features.dungeon.DungeonWaypoints.importFromClipboard(); Constants.SUCCESS
                })
                .then(ClientCommandManager.literal("reset").executes {
                    fishmod.features.dungeon.DungeonWaypoints.resetCurrentRoom(); Constants.SUCCESS
                })
            val node = dispatcher.register(tree)
            dispatcher.register(ClientCommandManager.literal("dungeonwaypoints").redirect(node))
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

        LagTracker.init()
        SessionStats.init()
        FishPuzzleDisplay.init()
        FishEstTotal.init()
        DungeonDeathMessage.init()
        fishmod.features.ExplosiveShot.init()
        FishPartyTracker.init()
        PartyCommandHandler.init()
        SoulflowHud.init()
        PetHud.init()
        CooldownOverlay.init()
        fishmod.features.croesus.CroesusLootDetector.init()
        fishmod.features.CatacombsOverflowOverlay.init()
        fishmod.features.other.CommandKeys.init()
        fishmod.features.other.WardrobeHotkeys.init()
        // ItemRarityHotbar.init();   // rarity background: inventory-slot coverage (hotbar via HudRenderCallback)
        MayorApi.init()
        // SlayerXpTracker.init();
        // fishmod.features.SkillTracker.init();
        fishmod.features.FireFreezeTimer.init()
        // PowderTracker.init();
        fishmod.features.dungeon.SimonSaysTracker.init()
        fishmod.features.dungeon.M7LeverWaypoints.init()
        fishmod.features.dungeon.DungeonWaypoints.init()
        fishmod.features.dungeon.StarredMobHighlight.init()
        // Floor 7 boss timers (ported from blade-addons): Maxor/Storm/Goldor tick timers, crystal
        // spawn, term start, section progress, storm-crushed. HUDs auto-render via the practical
        // config system (F7Huds registered with FishConfig); register each for the Edit-HUD dragger.
        fishmod.features.dungeon.f7.F7Huds.init()
        // Inventory command buttons (ported 1:1 from blade-addons) — touch the class so its 7 buttons
        // self-register; commands are edited in /fm → General → Inventory Buttons.
        fishmod.utils.config.values.Buttons.init()
        FishHudEditor.register("Maxor Tick Timer", fishmod.features.dungeon.f7.F7Huds.maxorTickTimer)
        FishHudEditor.register("Crystal Spawn Time", fishmod.features.dungeon.f7.F7Huds.crystalSpawnTime)
        FishHudEditor.register("Crystal Reminder", fishmod.features.dungeon.f7.F7Huds.crystalReminder)
        FishHudEditor.register("Storm Tick Timer", fishmod.features.dungeon.f7.F7Huds.stormTickTimer)
        FishHudEditor.register("Storm Death Time", fishmod.features.dungeon.f7.F7Huds.stormDeathTime)
        FishHudEditor.register("LB Release Timer", fishmod.features.dungeon.f7.F7Huds.lbReleaseTimer)
        FishHudEditor.register("Storm Crushed", fishmod.features.dungeon.f7.F7Huds.stormCrush)
        FishHudEditor.register("Goldor Tick Timer", fishmod.features.dungeon.f7.F7Huds.goldorTickTimer)
        FishHudEditor.register("Goldor Leap Timer", fishmod.features.dungeon.f7.F7Huds.goldorLeapTimer)
        FishHudEditor.register("Term Start Timer", fishmod.features.dungeon.f7.F7Huds.termStartTimer)
        FishHudEditor.register("Section Progress", fishmod.features.dungeon.f7.F7Huds.sectionProgress)
        FishHudEditor.register("Goldor Splits", Section.terminalSplits)
        // Dungeon class detection (own class from the "stats are doubled" message + tab list) and the
        // class-colored boots feature that depends on it.
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
                ClientCommandManager.literal("fm")
                    .then(ClientCommandManager.literal("commandkeys").executes { context ->
                        MinecraftClient.getInstance().send { MinecraftClient.getInstance().setScreen(fishmod.features.CommandKeysScreen()) }
                        Constants.SUCCESS
                    })
                    .then(ClientCommandManager.literal("aliases").executes { context ->
                        MinecraftClient.getInstance().send { MinecraftClient.getInstance().setScreen(fishmod.features.CommandAliasesScreen()) }
                        Constants.SUCCESS
                    })
                    .then(ClientCommandManager.literal("commandhelp").executes { context ->
                        printCommandHelp()
                        Constants.SUCCESS
                    })
                    .then(ClientCommandManager.literal("help").executes { context ->
                        printCommandHelp()
                        Constants.SUCCESS
                    })
                    .executes { context ->
                        MinecraftClient.getInstance().send { MinecraftClient.getInstance().setScreen(FishModScreen()) }
                        Constants.SUCCESS
                    }
            )
            registerDungeonWaypointCommand(dispatcher)
            dispatcher.register(
                ClientCommandManager.literal("fmloot")
                    .executes { ctx ->
                        MinecraftClient.getInstance().send { MinecraftClient.getInstance().setScreen(fishmod.features.croesus.LootTrackerScreen()) }
                        Constants.SUCCESS
                    }
            )
            // ── Party alias commands ──────────────────────────────────────────
            dispatcher.register(
                ClientCommandManager.literal("pk")
                    .then(
                        ClientCommandManager.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val name = StringArgumentType.getString(ctx, "name")
                                val mc = MinecraftClient.getInstance()
                                mc.networkHandler?.sendChatCommand("p kick $name")
                                Constants.SUCCESS
                            }
                    )
            )
            dispatcher.register(
                ClientCommandManager.literal("pw")
                    .executes { ctx ->
                        val mc = MinecraftClient.getInstance()
                        mc.networkHandler?.sendChatCommand("p warp")
                        Constants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommandManager.literal("pt")
                    .then(
                        ClientCommandManager.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val name = StringArgumentType.getString(ctx, "name")
                                val mc = MinecraftClient.getInstance()
                                mc.networkHandler?.sendChatCommand("p transfer $name")
                                Constants.SUCCESS
                            }
                    )
            )
            dispatcher.register(
                ClientCommandManager.literal("pp")
                    .then(
                        ClientCommandManager.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val name = StringArgumentType.getString(ctx, "name")
                                val mc = MinecraftClient.getInstance()
                                mc.networkHandler?.sendChatCommand("p promote $name")
                                Constants.SUCCESS
                            }
                    )
            )
            dispatcher.register(
                ClientCommandManager.literal("pd")
                    .then(
                        ClientCommandManager.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val name = StringArgumentType.getString(ctx, "name")
                                val mc = MinecraftClient.getInstance()
                                mc.networkHandler?.sendChatCommand("p demote $name")
                                Constants.SUCCESS
                            }
                    )
            )
            // ─────────────────────────────────────────────────────────────────

            dispatcher.register(ClientCommandManager.literal("fmpet").executes { context ->
                if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                val mc = MinecraftClient.getInstance()
                mc.send {
                    Misc.addChatMessage(Text.literal("§b--- Pet HUD ---"))
                    Misc.addChatMessage(Text.literal("§7" + PetHud.debugState()))
                    Misc.addChatMessage(Text.literal("§b--- Cooldown Overlay ---"))
                    Misc.addChatMessage(Text.literal("§7" + CooldownOverlay.debugState()))
                }
                Constants.SUCCESS
            })


            dispatcher.register(ClientCommandManager.literal("fmpetdump").executes { context ->
                if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                PetHud.debugDumpPetLines = !PetHud.debugDumpPetLines
                Misc.addChatMessage(Text.literal("§b[fmpet] dump pet-related chat lines: §f" + PetHud.debugDumpPetLines))
                Constants.SUCCESS
            })

            dispatcher.register(ClientCommandManager.literal("fmcddump").executes { context ->
                if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                CooldownOverlay.debugDumpSound = !CooldownOverlay.debugDumpSound
                Misc.addChatMessage(Text.literal("§b[fmcd] dump cooldown sound events: §f" + CooldownOverlay.debugDumpSound))
                Constants.SUCCESS
            })

            dispatcher.register(ClientCommandManager.literal("fmcatadump").executes { context ->
                if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                CatacombsOverflowOverlay.debugDumpLines = !CatacombsOverflowOverlay.debugDumpLines
                Misc.addChatMessage(Text.literal("§b[fmcata] dump Catacombs/class menu item lines: §f" + CatacombsOverflowOverlay.debugDumpLines))
                Constants.SUCCESS
            })

            dispatcher.register(ClientCommandManager.literal("fmblocks").executes { context ->
                if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                val mc = MinecraftClient.getInstance()
                mc.send {
                    if (mc.player == null || mc.world == null) {
                        Misc.addChatMessage(Text.literal("§cNo world")); return@send
                    }
                    val c = mc.player!!.blockPos
                    val counts = HashMap<String, Int>()
                    val R = 7
                    val m = BlockPos.Mutable()
                    for (dx in -R..R) for (dy in -R..R) for (dz in -R..R) {
                        m.set(c.x + dx, c.y + dy, c.z + dz)
                        val b: Block = mc.world!!.getBlockState(m).block
                        if (b == Blocks.AIR) continue
                        val id = Registries.BLOCK.getId(b).toString()
                        counts.merge(id, 1, Integer::sum)
                    }
                    Misc.addChatMessage(Text.literal("§b--- Blocks within $R (top 20) ---"))
                    counts.entries.sortedByDescending { it.value }.take(20)
                        .forEach { e -> Misc.addChatMessage(Text.literal("§7" + e.value + "x §f" + e.key)) }
                }
                Constants.SUCCESS
            })

            dispatcher.register(ClientCommandManager.literal("fmssdebug").executes { context ->
                if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                fishmod.features.dungeon.SimonSaysTracker.debug = !fishmod.features.dungeon.SimonSaysTracker.debug
                Misc.addChatMessage(Text.literal("§b[ssdbg] log Simon Says block transitions: §f" + fishmod.features.dungeon.SimonSaysTracker.debug))
                Constants.SUCCESS
            })

            dispatcher.register(ClientCommandManager.literal("fmnuc").executes { context ->
                if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                fishmod.utils.HypixelApi.dumpNucleus(MinecraftClient.getInstance())
                Constants.SUCCESS
            })

            dispatcher.register(ClientCommandManager.literal("fmgarden").executes { context ->
                if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                fishmod.utils.HypixelApi.dumpGarden(MinecraftClient.getInstance())
                Constants.SUCCESS
            })

            dispatcher.register(ClientCommandManager.literal("fmprofile").executes { context ->
                if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                fishmod.utils.HypixelApi.dumpEconomy(MinecraftClient.getInstance())
                Constants.SUCCESS
            })

            dispatcher.register(ClientCommandManager.literal("fmtabdump").executes { context ->
                if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                val mc = MinecraftClient.getInstance()
                mc.send {
                    if (mc.networkHandler == null) {
                        Misc.addChatMessage(Text.literal("§cNo network")); return@send
                    }
                    Misc.addChatMessage(Text.literal("§b--- Tab entries (non-empty) ---"))
                    var n = 0
                    for (e: PlayerListEntry in mc.networkHandler!!.playerList) {
                        if (e.displayName == null) continue
                        val s = e.displayName!!.string.replace(Regex("§."), "").trim()
                        if (s.isEmpty()) continue
                        if (s.lowercase().contains("pet") || s.contains("Lvl") || s.contains("XP") || s.contains("/")) {
                            Misc.addChatMessage(Text.literal("§7$s"))
                            if (++n > 30) break
                        }
                    }
                    Misc.addChatMessage(Text.literal("§b--- End ($n) ---"))
                }
                Constants.SUCCESS
            })

            dispatcher.register(
                ClientCommandManager.literal("fmdbg").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes Constants.SUCCESS
                    val mc = MinecraftClient.getInstance()
                    mc.send {
                        Misc.addChatMessage(Text.literal("§b--- FishMod Debug ---"))
                        Misc.addChatMessage(Text.literal("§7Location: §f" + Location.getCurrentLocation()))
                        Misc.addChatMessage(Text.literal("§7inSkyblock: §f" + Location.inSkyblock()))
                        Misc.addChatMessage(Text.literal("§7inDungeon: §f" + Location.inDungeon()))
                        Misc.addChatMessage(Text.literal("§7showPuzzles: §f" + fishmod.utils.config.values.FishSettings.showPuzzles))
                        Misc.addChatMessage(Text.literal("§7Puzzle list (" + FishPuzzleDisplay.getPuzzles().size + "): §f" + FishPuzzleDisplay.getPuzzles()))
                        try {
                            Misc.addChatMessage(Text.literal("§7Phase.runStarted: §f" + Phase.runStarted()))
                        } catch (t: Throwable) {
                            Misc.addChatMessage(Text.literal("§cPhase.runStarted ERR: " + t.message))
                        }
                        try {
                            Misc.addChatMessage(Text.literal("§7Phase.enableSplits: §f" + Phase.enableSplits))
                        } catch (t: Throwable) {
                            Misc.addChatMessage(Text.literal("§cPhase.enableSplits ERR: " + t.message))
                        }
                        try {
                            Misc.addChatMessage(Text.literal("§7blade loaded: §f" + FabricLoader.getInstance().isModLoaded("blade-addons")))
                        } catch (t: Throwable) {
                            Misc.addChatMessage(Text.literal("§cloader ERR"))
                        }
                        // Dump tab list
                        val handler: ClientPlayNetworkHandler? = mc.networkHandler
                        if (handler == null) {
                            Misc.addChatMessage(Text.literal("§cNo network handler"))
                        } else {
                            var total = 0
                            var nullName = 0
                            for (e: PlayerListEntry in handler.playerList) {
                                total++
                                if (e.displayName == null) {
                                    nullName++; continue
                                }
                                val raw = e.displayName!!.string
                                val clean = raw.replace(Regex("§."), "").trim()
                                if (clean.isNotEmpty())
                                    Misc.addChatMessage(Text.literal("§8TAB: §7$clean"))
                            }
                            Misc.addChatMessage(Text.literal("§7Tab entries: §f$total (§c$nullName null§7)"))
                        }
                        // Dump scoreboard sidebar
                        if (mc.world != null) {
                            val sb: Scoreboard = mc.world!!.scoreboard
                            val sidebar: ScoreboardObjective? = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR)
                            if (sidebar == null) {
                                Misc.addChatMessage(Text.literal("§7Sidebar: §cnone"))
                            } else {
                                Misc.addChatMessage(Text.literal("§7Sidebar obj: §f" + sidebar.displayName.string))
                                for (entry: ScoreboardEntry in sb.getScoreboardEntries(sidebar)) {
                                    val owner = entry.owner()
                                    val team: Team? = sb.getScoreHolderTeam(owner)
                                    val line = if (team != null)
                                        team.prefix.string + owner + team.suffix.string
                                    else
                                        entry.name().string
                                    val clean = line.replace(Regex("§."), "").trim()
                                    if (clean.isNotEmpty())
                                        Misc.addChatMessage(Text.literal("§8SB: §7$clean"))
                                }
                            }
                        }
                        Misc.addChatMessage(Text.literal("§b--- End Debug ---"))
                    }
                    Constants.SUCCESS
                }.then(
                    ClientCommandManager.argument("sub", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val arg = StringArgumentType.getString(ctx, "sub")
                            val mc = MinecraftClient.getInstance()
                            val parts = arg.trim().split(Regex("\\s+"), 2)
                            if (parts[0] == "cprice") {
                                if (parts.size < 2) {
                                    mc.send { Misc.addChatMessage(Text.literal("§cUsage: /fmdbg cprice <ITEM_ID>")) }
                                    return@executes Constants.SUCCESS
                                }
                                val pid = parts[1].trim().uppercase()
                                fishmod.features.croesus.CroesusPrices.refreshIfStale().whenComplete { _, _ ->
                                    mc.send {
                                        Misc.addChatMessage(
                                            Text.literal("§b$pid §7→ §f" + fishmod.features.croesus.CroesusPrices.debugSource(pid))
                                        )
                                    }
                                }
                                return@executes Constants.SUCCESS
                            }
                            if (parts[0] == "mp") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.send { Misc.addChatMessage(Text.literal("§cUsage: /fmdbg mp <ign>")) }; return@executes Constants.SUCCESS
                                }
                                val finalIgn = ign
                                fishmod.utils.HypixelApi.getByName(mc, ign) { data ->
                                    mc.send { Misc.addChatMessage(Text.literal("§b$finalIgn magicalPower=§f" + data.magicalPower)) }
                                }
                                return@executes Constants.SUCCESS
                            }
                            if (parts[0] == "mpraw") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.send { Misc.addChatMessage(Text.literal("§cUsage: /fmdbg mpraw <ign>")) }; return@executes Constants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.dumpMemberKeys(mc, ign)
                                return@executes Constants.SUCCESS
                            }
                            if (parts[0] == "col") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.send { Misc.addChatMessage(Text.literal("§cUsage: /fmdbg col <ign>")) }; return@executes Constants.SUCCESS
                                }
                                val finalIgn = ign
                                fishmod.utils.HypixelApi.getByName(mc, ign) { data ->
                                    mc.send {
                                        var cataTotal: Long = 0
                                        for (t in data.cataTimes) cataTotal += t
                                        var masterTotal: Long = 0
                                        for (i in 1..7) masterTotal += data.masterTimes[i]
                                        val col = cataTotal + masterTotal * 2
                                        Misc.addChatMessage(Text.literal("§b--- Collection debug: $finalIgn ---"))
                                        val cata = StringBuilder("§7cata: ")
                                        for (i in 0..7) cata.append(if (i == 0) "E" else "F$i").append("=").append(data.cataTimes[i]).append(" ")
                                        Misc.addChatMessage(Text.literal(cata.toString()))
                                        val master = StringBuilder("§7master: ")
                                        for (i in 1..7) master.append("M").append(i).append("=").append(data.masterTimes[i]).append(" ")
                                        Misc.addChatMessage(Text.literal(master.toString()))
                                        Misc.addChatMessage(Text.literal("§7cataTotal=§f$cataTotal §7masterTotal=§f$masterTotal"))
                                        Misc.addChatMessage(Text.literal("§7computed col=§f$col §7(cata×1 + master×2)"))
                                    }
                                }
                                return@executes Constants.SUCCESS
                            }
                            if (parts[0] == "runs") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.send { Misc.addChatMessage(Text.literal("§cUsage: /fmdbg runs <ign>")) }; return@executes Constants.SUCCESS
                                }
                                val finalIgn = ign
                                fishmod.utils.HypixelApi.getByName(mc, ign) { data ->
                                    mc.send {
                                        Misc.addChatMessage(Text.literal("§b--- Runs debug: $finalIgn ---"))
                                        Misc.addChatMessage(Text.literal("§7totalRuns: §f" + data.totalRuns))
                                        val cata = StringBuilder("§7cataTimes: ")
                                        for (i in 0..7) cata.append("F").append(if (i == 0) "E" else i.toString()).append("=").append(data.cataTimes[i]).append(" ")
                                        Misc.addChatMessage(Text.literal(cata.toString()))
                                        val master = StringBuilder("§7masterTimes: ")
                                        for (i in 1..7) master.append("M").append(i).append("=").append(data.masterTimes[i]).append(" ")
                                        Misc.addChatMessage(Text.literal(master.toString()))
                                        Misc.addChatMessage(Text.literal("§b--- End ---"))
                                    }
                                }
                            }
                            Constants.SUCCESS
                        }
                )
            )

            // ── Local lookup /commands (native tab-complete; result shown in your own chat) ──
            val playerSuggest = SuggestionProvider<FabricClientCommandSource> { c, b ->
                val mc = MinecraftClient.getInstance()
                if (mc.networkHandler != null) {
                    val rem = b.remaining.lowercase()
                    val seen = HashSet<String>()
                    for (e: PlayerListEntry in mc.networkHandler!!.playerList) {
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

            // ── Party-action name-list management for .kick/.warp/.transfer/.promote/.demote ──
            // /fmcmd whitelist|blacklist [add|remove|list] <name> — manages FishSettings.pcPartyActionsWhitelist/
            // Blacklist; the "Who Can Trigger" dropdown in /fm > Party > Party Commands picks which list applies.
            dispatcher.register(
                ClientCommandManager.literal("fmcmd")
                    .then(
                        ClientCommandManager.literal("whitelist")
                            .executes { ctx -> printNameList("Whitelist", fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist); Constants.SUCCESS }
                            .then(ClientCommandManager.literal("list").executes { ctx -> printNameList("Whitelist", fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist); Constants.SUCCESS })
                            .then(
                                ClientCommandManager.literal("add").then(
                                    ClientCommandManager.argument("name", StringArgumentType.word()).suggests(playerSuggest)
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist =
                                                fishmod.utils.NameList.add(fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            Misc.addChatMessage(Text.literal("§7[FM] Added §f$name §7to the party-action whitelist."))
                                            Constants.SUCCESS
                                        }
                                )
                            )
                            .then(
                                ClientCommandManager.literal("remove").then(
                                    ClientCommandManager.argument("name", StringArgumentType.word())
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist =
                                                fishmod.utils.NameList.remove(fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            Misc.addChatMessage(Text.literal("§7[FM] Removed §f$name §7from the party-action whitelist."))
                                            Constants.SUCCESS
                                        }
                                )
                            )
                    )
                    .then(
                        ClientCommandManager.literal("blacklist")
                            .executes { ctx -> printNameList("Blacklist", fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist); Constants.SUCCESS }
                            .then(ClientCommandManager.literal("list").executes { ctx -> printNameList("Blacklist", fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist); Constants.SUCCESS })
                            .then(
                                ClientCommandManager.literal("add").then(
                                    ClientCommandManager.argument("name", StringArgumentType.word()).suggests(playerSuggest)
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist =
                                                fishmod.utils.NameList.add(fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            Misc.addChatMessage(Text.literal("§7[FM] Added §f$name §7to the party-action blacklist."))
                                            Constants.SUCCESS
                                        }
                                )
                            )
                            .then(
                                ClientCommandManager.literal("remove").then(
                                    ClientCommandManager.argument("name", StringArgumentType.word())
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist =
                                                fishmod.utils.NameList.remove(fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            Misc.addChatMessage(Text.literal("§7[FM] Removed §f$name §7from the party-action blacklist."))
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
                    ClientCommandManager.literal(name)
                        .executes { c -> runLocalLookup(name, null, null) }
                        .then(
                            ClientCommandManager.argument("player", StringArgumentType.word()).suggests(playerSuggest)
                                .executes { c -> runLocalLookup(name, StringArgumentType.getString(c, "player"), null) }
                        )
                )
            }
            // NOTE: no "collection" here — Hypixel already owns /collection. The party-chat
            // ".collection" command still works via the chat handler.
            for (name in arrayOf("pb", "runs")) {
                dispatcher.register(
                    ClientCommandManager.literal(name)
                        .executes { c -> runLocalLookup(name, null, null) }
                        .then(
                            ClientCommandManager.argument("player", StringArgumentType.word()).suggests(playerSuggest)
                                .executes { c -> runLocalLookup(name, StringArgumentType.getString(c, "player"), null) }
                                .then(
                                    ClientCommandManager.argument("floor", StringArgumentType.word()).suggests(floorSuggest)
                                        .executes { c -> runLocalLookup(name, StringArgumentType.getString(c, "player"), StringArgumentType.getString(c, "floor")) }
                                )
                        )
                )
            }
            dispatcher.register(
                ClientCommandManager.literal("rtc")
                    .executes { c -> runLocalLookup("rtc", null, null) }
                    .then(
                        ClientCommandManager.argument("player", StringArgumentType.word()).suggests(playerSuggest)
                            .executes { c -> runLocalLookup("rtc", StringArgumentType.getString(c, "player"), null) }
                            .then(
                                ClientCommandManager.argument("level", StringArgumentType.word())
                                    .executes { c -> runLocalLookup("rtc", StringArgumentType.getString(c, "player"), StringArgumentType.getString(c, "level")) }
                            )
                    )
            )
            // /crtc [name] [class] [level] — XP for one class to reach a level (default 50).
            // Smart-parses in PartyCommandHandler: a leading class arg means "self".
            val classSuggest = SuggestionProvider<FabricClientCommandSource> { _, b ->
                val rem = b.remaining.lowercase()
                for (cl in arrayOf("healer", "mage", "berserk", "archer", "tank"))
                    if (cl.startsWith(rem)) b.suggest(cl)
                b.buildFuture()
            }
            dispatcher.register(
                ClientCommandManager.literal("crtc")
                    .executes { c -> runLocalLookup("crtc", null, null, null) }
                    .then(
                        ClientCommandManager.argument("a1", StringArgumentType.word()).suggests(classSuggest)
                            .executes { c -> runLocalLookup("crtc", StringArgumentType.getString(c, "a1"), null, null) }
                            .then(
                                ClientCommandManager.argument("a2", StringArgumentType.word()).suggests(classSuggest)
                                    .executes { c -> runLocalLookup("crtc", StringArgumentType.getString(c, "a1"), StringArgumentType.getString(c, "a2"), null) }
                                    .then(
                                        ClientCommandManager.argument("a3", StringArgumentType.word())
                                            .executes { c ->
                                                runLocalLookup(
                                                    "crtc",
                                                    StringArgumentType.getString(c, "a1"),
                                                    StringArgumentType.getString(c, "a2"),
                                                    StringArgumentType.getString(c, "a3")
                                                )
                                            }
                                    )
                            )
                    )
            )
            // No-arg commands: self metrics, party actions, and join-floor/Kuudra shortcuts.
            for (name in arrayOf(
                "fps", "tps", "ping", "dprofit", "ai", "allinv", "d",
                "e", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "m1", "m2", "m3", "m4", "m5", "m6", "m7",
                "t1", "t2", "t3", "t4", "t5"
            )) {
                dispatcher.register(ClientCommandManager.literal(name).executes { c -> runLocalLookup(name, null, null) })
            }
            // /warp — bare-form runs the local party action; with an argument, forward to
            // Hypixel's server-side /warp <dest> so the client command doesn't shadow it
            // with "Incorrect argument for command at position 5: warp <--[HERE]".
            dispatcher.register(
                ClientCommandManager.literal("warp")
                    .executes { c -> runLocalLookup("warp", null, null) }
                    .then(
                        ClientCommandManager.argument("dest", StringArgumentType.greedyString())
                            .executes { c ->
                                val dest = StringArgumentType.getString(c, "dest")
                                val mc = MinecraftClient.getInstance()
                                // Send the command packet DIRECTLY, bypassing Fabric's client command
                                // dispatcher — otherwise it re-matches our /warp literal and infinitely
                                // recurses into this same lambda, blowing the stack.
                                if (mc.player != null && mc.player!!.networkHandler != null)
                                    mc.player!!.networkHandler.sendPacket(CommandExecutionC2SPacket("warp $dest"))
                                Constants.SUCCESS
                            }
                    )
            )
        })

        // ── Override OdinClient's /cata ───────────────────────────────────────
        // Both mods register a client-side /cata; Brigadier hands the executes() to whoever
        // registers LAST, which isn't deterministic at init. Re-register ours on each server
        // join — that runs after every mod's init-time registration, so ours wins.
        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { handler, sender, client ->
            val d = ClientCommandManager.getActiveDispatcher() ?: return@Join
            try {
                d.register(
                    ClientCommandManager.literal("cata")
                        .executes { c -> runLocalLookup("cata", null, null) }
                        .then(
                            ClientCommandManager.argument("player", StringArgumentType.word())
                                .executes { c -> runLocalLookup("cata", StringArgumentType.getString(c, "player"), null) }
                        )
                )
            } catch (ignored: Exception) {
            }
        })


        // ── Warp Map HUD + click detection ───────────────────────────────────
        // HudRenderCallback.EVENT.register((ctx, tickCounter) -> WarpMapFeature.renderHud(ctx, tickCounter));
        // ClientTickEvents.END_CLIENT_TICK.register(WarpMapFeature::tickClickDetection);
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, tickCounter -> SoulflowHud.renderHud(ctx, tickCounter) })
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, tickCounter -> PetHud.renderHud(ctx, tickCounter) })
        // Rarity background is drawn behind items via DrawContextMixin (hotbar) + INVENTORY_SLOT_BEFORE
        // (inventory) — see ItemRarityHotbar.init(). No HudRenderCallback (that draws over the items).
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, tickCounter -> CooldownOverlay.renderHotbar(ctx, tickCounter) })
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, _ -> fishmod.features.BossBarFeature.renderHud(ctx) })
        // Splits panel + Maxor/Storm/Terminals split-time HUDs. Rendered here (not via practical-config's
        // HudElementRegistry auto-render, which doesn't fire reliably) — their condition-suppliers are
        // forced false in Phase so this is the single render path.
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, _ -> Phase.renderHud(ctx) })
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, _ -> fishmod.features.dungeon.f7.F7Huds.renderHud(ctx) })
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, _ -> fishmod.features.dungeon.DungeonWaypoints.renderOverlay(ctx) })
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, tickCounter -> SessionStats.renderHud(ctx, tickCounter) })
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, tickCounter -> fishmod.features.dungeon.DungeonScore.renderHud(ctx, tickCounter) })
        fishmod.features.dungeon.DungeonScore.init()
        fishmod.utils.SkyblockItems.initAsync()

        // ── PB Pace (live delta vs personal-best splits) ─────────────────────
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, tickCounter -> fishmod.features.PbPaceHud.renderHud(ctx, tickCounter) })
        FishHudEditor.register(
            "PB Pace",
            { fishmod.utils.config.values.FishSettings.pbPaceHudX },
            { v: Int -> fishmod.utils.config.values.FishSettings.pbPaceHudX = v },
            { fishmod.utils.config.values.FishSettings.pbPaceHudY },
            { v: Int -> fishmod.utils.config.values.FishSettings.pbPaceHudY = v },
            130, 14 * 3,
            { fishmod.utils.config.values.FishSettings.pbPaceScale },
            { v: Double -> fishmod.utils.config.values.FishSettings.pbPaceScale = v },
            { fishmod.features.PbPaceHud.isVisible() }
        )

        // Tracker overlay (reset button) for HandledScreens — fires after full render chain
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { client, screen, w, h ->
            if (screen !is net.minecraft.client.gui.screen.ingame.HandledScreen<*>) return@AfterInit
            fishmod.features.croesus.CroesusLootDetector.onScreenInit(screen)
            ScreenEvents.afterRender(screen).register(ScreenEvents.AfterRender { s, ctx, mx, my, delta ->
                SessionStats.renderInScreen(ctx, mx, my)
                LootTrackerOverlay.renderInScreen(ctx, mx, my)
            })
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { s, click ->
                if (click.button() != 0) return@AllowMouseClick true // only left click resets
                val mx = click.x()
                val my = click.y()
                if (SessionStats.handleScreenClick(mx, my)) return@AllowMouseClick false
                true
            })
        })

        // Always init FishMod's own framework. (Pre-rename this was skipped when blade-addons was
        // present because the classes were shared as blade.addon.*; after renaming to fishmod.* they
        // are separate, so FishMod must initialize its own — otherwise Location/Config/Keybinds/etc.
        // never run and features like the warp map silently break.) Each init is guarded so a single
        // duplicate-registration clash with blade-addons can't take down the whole entrypoint.
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
