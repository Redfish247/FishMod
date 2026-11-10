package fishmod
import fishmod.utils.events.chatRaw
import fishmod.compat.showScreen

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
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
import fishmod.features.dungeon.SessionStats
import fishmod.utils.FishConstants
import fishmod.utils.FishKeys
import fishmod.utils.SkyblockLocation
import fishmod.utils.MayorApi
import fishmod.utils.FishUtil
import fishmod.utils.TickScheduler
import fishmod.utils.config.ConfigRoot
import fishmod.utils.config.FishConfig
import fishmod.utils.config.FishPaths
import fishmod.utils.data.PartyTools
import fishmod.utils.debug.FishDebug
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.GoldorSection
import fishmod.utils.events.EventDispatch
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
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

class FishModInit : ClientModInitializer {

    companion object {
        @JvmStatic
        private fun runLocalLookup(cmd: String, arg1: String?, arg2: String?): Int =
            runLocalLookup(cmd, arg1, arg2, null)

        @JvmStatic
        private fun runLocalLookup(cmd: String, arg1: String?, arg2: String?, arg3: String?): Int {
            val mc = Minecraft.getInstance()
            val self = mc.player?.gameProfile?.name ?: return FishConstants.SUCCESS
            if (!fishmod.features.dungeon.PartyCommandHandler.localEnabled(cmd)) {
                // raw packet: sendCommand() would re-enter our own client command and recurse forever
                mc.connection?.send(net.minecraft.network.protocol.game.ServerboundChatCommandPacket(listOfNotNull(cmd, arg1, arg2, arg3).joinToString(" ")))
                return FishConstants.SUCCESS
            }
            fishmod.features.dungeon.PartyCommandHandler.onPartyCommand(
                self, cmd, arg1, arg2, arg3, fishmod.features.dungeon.PartyCommandHandler.LOCAL
            )
            return FishConstants.SUCCESS
        }

        @JvmStatic
        private fun printNameList(label: String, csv: String) {
            val names = fishmod.utils.NameList.toList(csv)
            FishUtil.addChatMessage(
                Component.literal(
                    "§b[FM] Party-Action $label §7(${names.size}): §f" +
                        (if (names.isEmpty()) "(empty)" else names.joinToString(", "))
                )
            )
        }

        private val HELP_CMD_TOKEN: Pattern = Pattern.compile("[/.][a-zA-Z][a-zA-Z0-9]*")

        @JvmStatic
        private fun helpLine(text: String) {
            val m: Matcher = HELP_CMD_TOKEN.matcher(text)
            var cmd: String? = null
            if (m.find()) {
                cmd = m.group()
                if (m.find()) cmd = null
            }
            if (cmd == null) {
                FishUtil.addChatMessage(Component.literal(text))
                return
            }
            val suggest = cmd
            val t: MutableComponent = Component.literal(text)
            t.setStyle(
                t.style
                    .withClickEvent(ClickEvent.SuggestCommand(suggest))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal("§7Click to put §f$suggest§7 in chat")))
            )
            FishUtil.addChatMessage(t)
        }

        // Builds an Odin-style /fm <alias> [n] get-from-sacks subcommand.
        @JvmStatic
        private fun sackSubcommand(alias: String): com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> =
            ClientCommands.literal(alias)
                .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1)).executes { ctx ->
                    fishmod.features.other.SackFill.fill(alias, IntegerArgumentType.getInteger(ctx, "amount"))
                    FishConstants.SUCCESS
                })
                .executes { fishmod.features.other.SackFill.fill(alias, null); FishConstants.SUCCESS }

        @JvmStatic
        private fun waypointSubcommand(name: String): com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> {
            val tree = ClientCommands.literal(name)
                .executes { fishmod.features.dungeon.DungeonWaypoints.toggleEdit(); FishConstants.SUCCESS }
                .then(ClientCommands.literal("edit").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleEdit(); FishConstants.SUCCESS
                })
                .then(ClientCommands.literal("fill").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleFill(); FishConstants.SUCCESS
                })
                .then(
                    ClientCommands.literal("size")
                        .then(
                            ClientCommands.argument("value", DoubleArgumentType.doubleArg(0.1, 1.0))
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setSize(
                                        DoubleArgumentType.getDouble(ctx, "value")
                                    )
                                    FishConstants.SUCCESS
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
                                    FishConstants.SUCCESS
                                }
                        )
                )
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
                                    FishConstants.SUCCESS
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
                                    FishConstants.SUCCESS
                                }
                        )
                )
                .then(ClientCommands.literal("useblocksize").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleUseBlockSize(); FishConstants.SUCCESS
                })
                .then(ClientCommands.literal("pixel").executes {
                    fishmod.features.dungeon.DungeonWaypoints.togglePixelMode(); FishConstants.SUCCESS
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
                                                    FishConstants.SUCCESS
                                                }
                                        )
                                )
                        )
                )
                .then(ClientCommands.literal("through").executes {
                    fishmod.features.dungeon.DungeonWaypoints.toggleThrough(); FishConstants.SUCCESS
                })
                .then(
                    ClientCommands.literal("linesize")
                        .then(
                            ClientCommands.argument("value", DoubleArgumentType.doubleArg(0.01, 0.5))
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setLineWidth(
                                        DoubleArgumentType.getDouble(ctx, "value")
                                    )
                                    FishConstants.SUCCESS
                                }
                        )
                )
                .then(
                    ClientCommands.literal("color")
                        .then(
                            ClientCommands.argument("hex", StringArgumentType.word())
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.setColor(StringArgumentType.getString(ctx, "hex"))
                                    FishConstants.SUCCESS
                                }
                        )
                )
                .then(ClientCommands.literal("export").executes {
                    fishmod.features.dungeon.DungeonWaypoints.exportToClipboard(); FishConstants.SUCCESS
                })
                .then(ClientCommands.literal("import").executes {
                    fishmod.features.dungeon.DungeonWaypoints.importFromClipboard(); FishConstants.SUCCESS
                }
                    .then(ClientCommands.literal("odin").executes {
                        fishmod.features.dungeon.OdinWaypointImport.importFromOdinConfig(); FishConstants.SUCCESS
                    }
                        .then(ClientCommands.literal("clipboard").executes {
                            fishmod.features.dungeon.OdinWaypointImport.importFromClipboard(); FishConstants.SUCCESS
                        })))
                .then(ClientCommands.literal("reset").executes {
                    fishmod.features.dungeon.DungeonWaypoints.resetCurrentArea(); FishConstants.SUCCESS
                })
                .then(
                    ClientCommands.literal("route")
                        .executes {
                            fishmod.features.dungeon.DungeonWaypoints.toggleRoute(null); FishConstants.SUCCESS
                        }
                        .then(
                            ClientCommands.argument("name", StringArgumentType.word())
                                .executes { ctx ->
                                    fishmod.features.dungeon.DungeonWaypoints.toggleRoute(StringArgumentType.getString(ctx, "name"))
                                    FishConstants.SUCCESS
                                }
                        )
                        .then(
                            ClientCommands.literal("reset")
                                .executes {
                                    fishmod.features.dungeon.DungeonWaypoints.endRoute(null); FishConstants.SUCCESS
                                }
                                .then(
                                    ClientCommands.argument("name", StringArgumentType.word())
                                        .executes { ctx ->
                                            fishmod.features.dungeon.DungeonWaypoints.endRoute(StringArgumentType.getString(ctx, "name"))
                                            FishConstants.SUCCESS
                                        }
                                )
                        )
                        .then(
                            ClientCommands.literal("delete")
                                .then(
                                    ClientCommands.argument("name", StringArgumentType.word())
                                        .executes { ctx ->
                                            fishmod.features.dungeon.DungeonWaypoints.deleteRoute(StringArgumentType.getString(ctx, "name"))
                                            FishConstants.SUCCESS
                                        }
                                )
                        )
                )
            return tree
        }

        private fun chatNotificationsSubcommand(name: String, tab: fishmod.features.ChatCommandsScreen.Tab = fishmod.features.ChatCommandsScreen.Tab.NOTIFICATIONS): com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> {
            return ClientCommands.literal(name)
                .executes {
                    Minecraft.getInstance().schedule {
                        Minecraft.getInstance().showScreen(fishmod.features.ChatCommandsScreen(tab))
                    }
                    FishConstants.SUCCESS
                }
                .then(ClientCommands.literal("on").executes {
                    fishmod.features.chat.ChatRuleStore.setMasterEnabled(true)
                    FishUtil.addChatMessage(Component.literal("§b[FM] Chat Notifications: §aON"))
                    FishConstants.SUCCESS
                })
                .then(ClientCommands.literal("off").executes {
                    fishmod.features.chat.ChatRuleStore.setMasterEnabled(false)
                    FishUtil.addChatMessage(Component.literal("§b[FM] Chat Notifications: §cOFF"))
                    FishConstants.SUCCESS
                })
        }

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
            line.accept("§3§lGet From Sacks §8(Odin-style)")
            line.accept("§e/fm twap §8[n] §7— Twilight Arrow Poison §8(64)  §7·  §e/fm tap §8[n] §7— Toxic Arrow Poison §8(64)")
            line.accept("§e/fm ep §8[n] §8(16)  §7·  §e/fm sl §8[n] §8(16)  §7·  §e/fm sb §8[n] §8(64)  §7·  §e/fm dd §8[n] §8(64)  §7·  §e/fm ij §8[n] §8(64)")
            line.accept("§8        tops your inventory up to n by pulling the shortfall from your sacks")

            line.accept("")
            line.accept("§3§lDungeon / Kuudra Joins §8(party chat)")
            line.accept("§f.e §8·§f .f1-.f7 §8·§f .m1-.m7 §7— join Catacombs floor")
            line.accept("§f.t1-.t5 §7— join Kuudra tier")

            line.accept("")
            line.accept("§3§lParty Actions")
            line.accept("§e/pk §f<player> §7— kick  §8·§7  §e/pw §7— warp  §8·§7  §e/pt §f<player> §7— transfer  §8·§7  §e/pp §f<player> §7— promote  §8·§7  §e/pd §f<player> §7— demote")
            line.accept("§7In party chat: §f.ai §7(allinvite), §f.d §7(disband), §f.kick(.k)/.warp(.w)/.transfer(.pt/.ptme)/.promote(.pro)/.demote(.dem)")
            line.accept("§7Control who else can trigger them: §f/fm §8> §7Party §8> §7Party Commands, and §f/fmcmd whitelist|blacklist add|remove|list")

            line.accept("")
            line.accept("§3§lScreens & Misc")
            line.accept("§e/fm §7— config GUI  §8·§7  §e/fmloot §7— Croesus loot")
            line.accept("§e/fm commandkeys §7— bind keys/mouse buttons to run slash commands")
            line.accept("§e/fm aliases §7— make short commands (e.g. §f/dh§7) run longer ones (e.g. §f/warp dh§7)")
            line.accept("§e/nick §8<name>|reset")
            line.accept("§e/fm debug §8[clear] §7— copy a problem report to send RedFish")
            line.accept("§e/fm commandhelp §7— this list  §8·§7  party chat: §f.help §7lists enabled party commands")
            line.accept("§b§m                                                                          ")
        }

        @JvmStatic
        private fun safeInit(name: String, init: () -> Unit) {
            try {
                init()
            } catch (t: Throwable) {
                fishmod.utils.debug.FishDiag.fail("Init.$name", "init failed", t)
            }
        }
    }

    override fun onInitializeClient() {
        FishDiag.guard("FishModInit.1", "FishConfig.manager.load() failed") { FishConfig.manager.load() }
        safeInit("Config") { ConfigRoot.manager.load() }
        if (!fishmod.pv.ProfileViewer.standaloneLoaded()) safeInit("ProfileViewer") { fishmod.pv.PvClient.init() }
        else fishmod.pv.PvClient.registerStandaloneNote()
        safeInit("EnchantColors") { fishmod.pv.gui.inventories.EnchantTooltip.init() }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register { FishConfig.manager.save() }
        FishDiag.guard("FishModInit.2", "fishmod.utils.IoExecutor.init() failed") { fishmod.utils.IoExecutor.init() }

        fishmod.cosmetic.NickData.load()
        // Rebuild from settings so a nick saved by an older gradient build picks up the current colours
        if (fishmod.cosmetic.NickState.isActive()) fishmod.cosmetic.NickState.applyFromSettings()
        FishDiag.guard("FishModInit.3", "fishmod.cosmetic.RemoteNicks.init() failed") { fishmod.cosmetic.RemoteNicks.init() }
        FishDiag.guard("FishModInit.3b", "fishmod.cosmetic.RemoteItems.init() failed") { fishmod.cosmetic.RemoteItems.init() }
        FishDiag.guard("FishModInit.4", "fishmod.cosmetic.PlayerSize.init() failed") { fishmod.cosmetic.PlayerSize.init() }
        FishDiag.guard("FishModInit.MeleeHit", "MeleeHitSound.init() failed") { fishmod.features.MeleeHitSound.init() }
        FishDiag.guard("FishModInit.5", "fishmod.features.NametagStats.init() failed") { fishmod.features.NametagStats.init() }
        FishDiag.guard("FishModInit.6", "fishmod.cosmetic.RemoteSync.init() failed") { fishmod.cosmetic.RemoteSync.init() }
        FishDiag.guard("FishModInit.6b", "fishmod.cosmetic.CosmeticLive.init() failed") { fishmod.cosmetic.CosmeticLive.init() }
        FishDiag.guard("FishModInit.6c", "fishmod.cosmetic.FishChat.init() failed") { fishmod.cosmetic.FishChat.init() }
        FishDiag.guard("FishModInit.7", "fishmod.cosmetic.badge.BadgeRegistry.init() failed") { fishmod.cosmetic.badge.BadgeRegistry.init() }
        FishDiag.guard("FishModInit.8", "fishmod.utils.InstallHeartbeat.init() failed") { fishmod.utils.InstallHeartbeat.init() }
        FishDiag.guard("FishModInit.9", "fishmod.utils.update.UpdateManager.init() failed") { fishmod.utils.update.UpdateManager.init() }
        FishDiag.guard("FishModInit.10", "fishmod.utils.TabListCache.register() failed") { fishmod.utils.TabListCache.register() }
        FishDiag.guard("FishModInit.205", "fishmod.utils.SidebarCache.register() failed") { fishmod.utils.SidebarCache.register() }

        FishDiag.guard("FishModInit.11", "LagTracker.init() failed") { LagTracker.init() }
        FishDiag.guard("FishModInit.12", "SessionStats.init() failed") { SessionStats.init() }
        FishDiag.guard("FishModInit.13", "FishPuzzleDisplay.init() failed") { FishPuzzleDisplay.init() }
        FishDiag.guard("FishModInit.14", "FishEstTotal.init() failed") { FishEstTotal.init() }
        FishDiag.guard("FishModInit.15", "DungeonDeathMessage.init() failed") { DungeonDeathMessage.init() }
        FishDiag.guard("FishModInit.16", "fishmod.features.ExplosiveShotHud.init() failed") { fishmod.features.ExplosiveShotHud.init() }
        FishDiag.guard("FishModInit.17", "fishmod.features.CritTracker.init() failed") { fishmod.features.CritTracker.init() }
        FishDiag.guard("FishModInit.18", "PartyCommandHandler.init() failed") { PartyCommandHandler.init() }
        FishDiag.guard("FishModInit.19", "SoulflowHud.init() failed") { SoulflowHud.init() }
        FishDiag.guard("FishModInit.CalTimes", "CalendarTimes.init() failed") { fishmod.features.extras.CalendarTimes.init() }
        FishDiag.guard("FishModInit.Extras", "Extras.init() failed") { fishmod.features.extras.Extras.init() }
        FishDiag.guard("FishModInit.20", "PetHud.init() failed") { PetHud.init() }
        FishDiag.guard("FishModInit.201", "ExperimentProfitTracker.init() failed") { fishmod.features.ExperimentProfitTracker.init() }
        FishDiag.guard("FishModInit.203", "AnitaShop.init() failed") { fishmod.features.item.AnitaShop.init() }
        FishDiag.guard("FishModInit.21", "CooldownOverlay.init() failed") { CooldownOverlay.init() }
        FishDiag.guard("FishModInit.HuntaxeLock", "HuntaxeLock.init() failed") { fishmod.features.HuntaxeLock.init() }
        FishDiag.guard("FishModInit.22", "fishmod.features.croesus.CroesusLootDetector.init() failed") { fishmod.features.croesus.CroesusLootDetector.init() }
        FishDiag.guard("FishModInit.23", "fishmod.features.CatacombsOverflowOverlay.init() failed") { fishmod.features.CatacombsOverflowOverlay.init() }
        FishDiag.guard("FishModInit.24", "fishmod.features.scoreboard.SkillLevels.init() failed") { fishmod.features.scoreboard.SkillLevels.init() }
        FishDiag.guard("FishModInit.25", "fishmod.features.scoreboard.BestiaryProgress.init() failed") { fishmod.features.scoreboard.BestiaryProgress.init() }
        FishDiag.guard("FishModInit.26", "fishmod.features.scoreboard.CollectionsProgress.init() failed") { fishmod.features.scoreboard.CollectionsProgress.init() }
        FishDiag.guard("FishModInit.27", "fishmod.features.scoreboard.FireSaleInfo.init() failed") { fishmod.features.scoreboard.FireSaleInfo.init() }
        FishDiag.guard("FishModInit.28", "fishmod.features.other.CommandKeys.init() failed") { fishmod.features.other.CommandKeys.init() }
        FishDiag.guard("FishModInit.29", "fishmod.features.other.WardrobeHotkeys.init() failed") { fishmod.features.other.WardrobeHotkeys.init() }
        FishDiag.guard("FishModInit.30", "ItemRarityHotbar.init() failed") { ItemRarityHotbar.init() }
        FishDiag.guard("FishModInit.31", "fishmod.features.item.ItemQualityTooltip.init() failed") { fishmod.features.item.ItemQualityTooltip.init() }
        FishDiag.guard("FishModInit.31b", "fishmod.features.item.ExoticTooltip.init() failed") { fishmod.features.item.ExoticTooltip.init() }
        FishDiag.guard("FishModInit.31c", "fishmod.features.item.OriginalNameTooltip.init() failed") { fishmod.features.item.OriginalNameTooltip.init() }
        FishDiag.guard("FishModInit.31b", "fishmod.features.item.ItemOverlays.init() failed") { fishmod.features.item.ItemOverlays.init() }
        FishDiag.guard("FishModInit.32", "fishmod.features.item.ItemPriceTooltip.init() failed") { fishmod.features.item.ItemPriceTooltip.init() }
        FishDiag.guard("FishModInit.32b", "fishmod.features.item.RecipeViewer.init() failed") { fishmod.features.item.RecipeViewer.init() }
        FishDiag.guard("FishModInit.32c", "fishmod.features.item.SkyItemList.init() failed") { fishmod.features.item.SkyItemList.init() }
        FishDiag.guard("FishModInit.32c", "fishmod.features.item.TooltipPinBottom.init() failed") { fishmod.features.item.TooltipPinBottom.init() }
        FishDiag.guard("FishModInit.33", "fishmod.features.item.ContainerValue.init() failed") { fishmod.features.item.ContainerValue.init() }
        FishDiag.guard("FishModInit.34", "fishmod.features.item.AuctionPriceAutofill.init() failed") { fishmod.features.item.AuctionPriceAutofill.init() }
        FishDiag.guard("FishModInit.35", "MayorApi.init() failed") { MayorApi.init() }
        FishDiag.guard("FishModInit.36", "fishmod.features.FireFreezeTimer.init() failed") { fishmod.features.FireFreezeTimer.init() }
        FishDiag.guard("FishModInit.37", "fishmod.features.LoadoutTitle.init() failed") { fishmod.features.LoadoutTitle.init() }
        FishDiag.guard("FishModInit.38", "fishmod.features.AutoSprint.init() failed") { fishmod.features.AutoSprint.init() }
        FishDiag.guard("FishModInit.39", "fishmod.features.PartyWarpTimer.init() failed") { fishmod.features.PartyWarpTimer.init() }
        FishDiag.guard("FishModInit.40", "fishmod.features.dungeon.DungeonBreaker.init() failed") { fishmod.features.dungeon.DungeonBreaker.init() }
        FishDiag.guard("FishModInit.41", "fishmod.features.BlockOverlay.init() failed") { fishmod.features.BlockOverlay.init() }
        FishDiag.guard("FishModInit.42", "fishmod.features.CameraTweaks.init() failed") { fishmod.features.CameraTweaks.init() }
        FishDiag.guard("FishModInit.43", "fishmod.features.GyroHelper.init() failed") { fishmod.features.GyroHelper.init() }
        FishDiag.guard("FishModInit.44", "fishmod.features.dungeon.MageBeam.init() failed") { fishmod.features.dungeon.MageBeam.init() }
        FishDiag.guard("FishModInit.45", "fishmod.features.SpringBoots.init() failed") { fishmod.features.SpringBoots.init() }
        FishDiag.guard("FishModInit.46", "fishmod.features.Ragnarock.init() failed") { fishmod.features.Ragnarock.init() }
        FishDiag.guard("FishModInit.47", "fishmod.features.VisualTweaks.init() failed") { fishmod.features.VisualTweaks.init() }
        FishDiag.guard("FishModInit.48", "fishmod.features.RenderOptimizer.init() failed") { fishmod.features.RenderOptimizer.init() }
        FishDiag.guard("FishModInit.49", "fishmod.features.NoCursorReset.init() failed") { fishmod.features.NoCursorReset.init() }
        FishDiag.guard("FishModInit.50", "fishmod.features.SlotBinds.init() failed") { fishmod.features.SlotBinds.init() }
        FishDiag.guard("FishModInit.51", "fishmod.features.SlotLocking.init() failed") { fishmod.features.SlotLocking.init() }
        FishDiag.guard("FishModInit.52", "fishmod.features.other.InvSearchBar.init() failed") { fishmod.features.other.InvSearchBar.init() }
        FishDiag.guard("FishModInit.53", "fishmod.features.BridgeBot.init() failed") { fishmod.features.BridgeBot.init() }
        FishDiag.guard("FishModInit.54", "twitchbridge.TwitchBridgeClient.init() failed") { twitchbridge.TwitchBridgeClient.init() }
        FishDiag.guard("FishModInit.55", "fishmod.features.LavaToWater.init() failed") { fishmod.features.LavaToWater.init() }
        FishDiag.guard("FishModInit.56", "fishmod.features.storage.StorageCache.init() failed") { fishmod.features.storage.StorageCache.init() }
        FishDiag.guard("FishModInit.57", "fishmod.features.dungeon.ExtraStats.init() failed") { fishmod.features.dungeon.ExtraStats.init() }
        FishDiag.guard("FishModInit.57c", "ExperimentSolver.init() failed") { fishmod.features.ExperimentSolver.init() }
        FishDiag.guard("FishModInit.58", "fishmod.features.EtherwarpHelper.init() failed") { fishmod.features.EtherwarpHelper.init() }
        FishDiag.guard("FishModInit.59", "fishmod.features.dungeon.DungeonAbilities.init() failed") { fishmod.features.dungeon.DungeonAbilities.init() }
        FishDiag.guard("FishModInit.60", "fishmod.features.dungeon.f6.TerracottaTimer.init() failed") { fishmod.features.dungeon.f6.TerracottaTimer.init() }
        FishDiag.guard("FishModInit.61", "fishmod.features.dungeon.f5.LividSolver.init() failed") { fishmod.features.dungeon.f5.LividSolver.init() }
        FishDiag.guard("FishModInit.62", "fishmod.features.dungeon.f4.SpiritBear.init() failed") { fishmod.features.dungeon.f4.SpiritBear.init() }
        FishDiag.guard("FishModInit.63", "fishmod.features.dungeon.f7.dragons.WitherDragons.init() failed") { fishmod.features.dungeon.f7.dragons.WitherDragons.init() }
        FishDiag.guard("FishModInit.64", "fishmod.features.TacTimer.init() failed") { fishmod.features.TacTimer.init() }
        FishDiag.guard("FishModInit.65", "fishmod.features.dungeon.ArchitectDraft.init() failed") { fishmod.features.dungeon.ArchitectDraft.init() }
        FishDiag.guard("FishModInit.66", "fishmod.features.dungeon.LeapAnnounce.init() failed") { fishmod.features.dungeon.LeapAnnounce.init() }
        FishDiag.guard("FishModInit.67", "fishmod.features.dungeon.MimicAnnounce.init() failed") { fishmod.features.dungeon.MimicAnnounce.init() }
        FishDiag.guard("FishModInit.68", "fishmod.features.dungeon.RoomTimer.init() failed") { fishmod.features.dungeon.RoomTimer.init() }
        FishDiag.guard("FishModInit.69", "fishmod.features.dungeon.KeyPickupAlert.init() failed") { fishmod.features.dungeon.KeyPickupAlert.init() }
        FishDiag.guard("FishModInit.70", "fishmod.features.dungeon.DungeonRequeue.init() failed") { fishmod.features.dungeon.DungeonRequeue.init() }
        FishDiag.guard("FishModInit.71", "fishmod.features.dungeon.Blessings.init() failed") { fishmod.features.dungeon.Blessings.init() }
        FishDiag.guard("FishModInit.72", "fishmod.features.dungeon.QuizHud.init() failed") { fishmod.features.dungeon.QuizHud.init() }
        FishDiag.guard("FishModInit.73", "fishmod.features.dungeon.SecretOverlay.init() failed") { fishmod.features.dungeon.SecretOverlay.init() }
        FishDiag.guard("FishModInit.74", "fishmod.features.dungeon.IceSprayTimer.init() failed") { fishmod.features.dungeon.IceSprayTimer.init() }
        FishDiag.guard("FishModInit.75", "fishmod.features.PetSwapTitle.init() failed") { fishmod.features.PetSwapTitle.init() }
        FishDiag.guard("FishModInit.76", "fishmod.features.PetIcons.init() failed") { fishmod.features.PetIcons.init() }
        FishDiag.guard("FishModInit.77", "fishmod.features.croesus.LootIcons.init() failed") { fishmod.features.croesus.LootIcons.init() }
        FishDiag.guard("FishModInit.78", "fishmod.features.dungeon.f7.StormOverAlert.init() failed") { fishmod.features.dungeon.f7.StormOverAlert.init() }
        FishDiag.guard("FishModInit.79", "fishmod.features.dungeon.f7.VenoStackCount.init() failed") { fishmod.features.dungeon.f7.VenoStackCount.init() }
        FishDiag.guard("FishModInit.80", "fishmod.features.PerformanceHud.init() failed") { fishmod.features.PerformanceHud.init() }
        FishDiag.guard("FishModInit.80s", "fishmod.features.SpotifyLyricsHud.init() failed") { fishmod.features.SpotifyLyricsHud.init() }
        FishDiag.guard("FishModInit.81", "fishmod.features.dungeon.InvincibilityTracker.init() failed") { fishmod.features.dungeon.InvincibilityTracker.init() }
        FishDiag.guard("FishModInit.82", "fishmod.features.dungeon.SecretClicked.init() failed") { fishmod.features.dungeon.SecretClicked.init() }
        FishDiag.guard("FishModInit.83", "fishmod.features.dungeon.RouteRecorder.init() failed") { fishmod.features.dungeon.RouteRecorder.init() }
        FishDiag.guard("FishModInit.84", "fishmod.features.dungeon.f7.terminal.TerminalSolver.init() failed") { fishmod.features.dungeon.f7.terminal.TerminalSolver.init() }
        FishDiag.guard("FishModInit.85", "fishmod.features.dungeon.f7.ArrowAlign.init() failed") { fishmod.features.dungeon.f7.ArrowAlign.init() }
        FishDiag.guard("FishModInit.86", "fishmod.features.dungeon.f7.ArrowsDevice.init() failed") { fishmod.features.dungeon.f7.ArrowsDevice.init() }
        FishDiag.guard("FishModInit.87", "fishmod.features.dungeon.f7.SimonSaysSolver.init() failed") { fishmod.features.dungeon.f7.SimonSaysSolver.init() }
        FishDiag.guard("FishModInit.88", "fishmod.features.dungeon.f7.MelodyMessage.init() failed") { fishmod.features.dungeon.f7.MelodyMessage.init() }
        FishDiag.guard("FishModInit.89", "fishmod.features.dungeon.PartyFinderStats.init() failed") { fishmod.features.dungeon.PartyFinderStats.init() }
        FishDiag.guard("FishModInit.90", "fishmod.features.dungeon.PartyFinder.init() failed") { fishmod.features.dungeon.PartyFinder.init() }
        FishDiag.guard("FishModInit.91", "fishmod.features.dungeon.KickListManager.init() failed") { fishmod.features.dungeon.KickListManager.init() }
        FishDiag.guard("FishModInit.92", "fishmod.features.dungeon.PartyMemberTracker.init() failed") { fishmod.features.dungeon.PartyMemberTracker.init() }
        FishDiag.guard("FishModInit.93", "fishmod.features.dungeon.PartyFinderPanel.init() failed") { fishmod.features.dungeon.PartyFinderPanel.init() }
        FishDiag.guard("FishModInit.94", "fishmod.features.dungeon.f7.WitherESP.init() failed") { fishmod.features.dungeon.f7.WitherESP.init() }
        FishDiag.guard("FishModInit.StormLb", "StormLbWaypoint.init() failed") { fishmod.features.dungeon.f7.StormLbWaypoint.init() }
        FishDiag.guard("FishModInit.95", "fishmod.features.dungeon.f7.M7Relics.init() failed") { fishmod.features.dungeon.f7.M7Relics.init() }
        FishDiag.guard("FishModInit.96", "fishmod.features.dungeon.puzzles.PuzzleSolvers.init() failed") { fishmod.features.dungeon.puzzles.PuzzleSolvers.init() }
        FishDiag.guard("FishModInit.97", "fishmod.features.dungeon.SimonSaysTracker.init() failed") { fishmod.features.dungeon.SimonSaysTracker.init() }
        FishDiag.guard("FishModInit.98", "fishmod.features.chat.ChatRuleHandler.init() failed") { fishmod.features.chat.ChatRuleHandler.init() }
        FishDiag.guard("FishModInit.99", "fishmod.features.dungeon.M7LeverWaypoints.init() failed") { fishmod.features.dungeon.M7LeverWaypoints.init() }
        FishDiag.guard("FishModInit.100", "fishmod.features.dungeon.DungeonWaypoints.init() failed") { fishmod.features.dungeon.DungeonWaypoints.init() }
        FishDiag.guard("FishModInit.101", "fishmod.features.dungeon.StarredMobHighlight.init() failed") { fishmod.features.dungeon.StarredMobHighlight.init() }
        FishDiag.guard("FishModInit.MiningBoxes", "MiningBoxes.init() failed") { fishmod.features.mining.MiningBoxes.init() }
        FishDiag.guard("FishModInit.102", "fishmod.features.slayers.SlayerManager.init() failed") { fishmod.features.slayers.SlayerManager.init() }
        FishDiag.guard("FishModInit.156", "fishmod.features.diana.Diana.init() failed") { fishmod.features.diana.Diana.init() }
        FishDiag.guard("FishModInit.Fishing", "fishmod.features.fishing.Fishing.init() failed") { fishmod.features.fishing.Fishing.init() }
        FishDiag.guard("FishModInit.158", "TrackerResetButton.init() failed") { fishmod.features.other.TrackerResetButton.init() }
        FishDiag.guard("FishModInit.157", "fishmod.features.mining.Mining.init() failed") { fishmod.features.mining.Mining.init() }
        FishDiag.guard("FishModInit.157k", "fishmod.features.kuudra.Kuudra.init() failed") { fishmod.features.kuudra.Kuudra.init() }
        FishDiag.guard("FishModInit.R1", "fishmod.features.rift.Rift.init() failed") { fishmod.features.rift.Rift.init() }
        FishDiag.guard("FishModInit.garden", "fishmod.features.garden.Garden.init() failed") { fishmod.features.garden.Garden.init() }
        FishDiag.guard("FishModInit.ach", "AchEngine.init() failed") { fishmod.features.achievements.AchEngine.init() }
        FishDiag.guard("FishModInit.gamble", "GambleEngine.init() failed") { fishmod.features.gamble.GambleEngine.init() }
        FishDiag.guard("FishModInit.103", "fishmod.features.dungeon.f7.F7Huds.init() failed") { fishmod.features.dungeon.f7.F7Huds.init() }
        FishDiag.guard("FishModInit.104", "fishmod.utils.config.values.InvButtonSettings.init() failed") { fishmod.utils.config.values.InvButtonSettings.init() }
        FishDiag.guard("FishModInit.104b", "EquipmentDisplay.init() failed") { fishmod.features.other.EquipmentDisplay.init() }
        FishHudEditor.register("Boss Tick Clock", fishmod.features.dungeon.f7.F7Huds.tickTimer)
        FishHudEditor.register("Crystal Respawn", fishmod.features.dungeon.f7.F7Huds.crystalRespawnBox)
        FishHudEditor.register("Crystal Not Placed", fishmod.features.dungeon.f7.F7Huds.crystalReminder)
        FishHudEditor.register("Storm Kill Time", fishmod.features.dungeon.f7.F7Huds.stormKillBox)
        FishHudEditor.register("LB Release Timer", fishmod.features.dungeon.f7.F7Huds.lbReleaseTimer)
        FishHudEditor.register("Py Tick Timer", fishmod.features.dungeon.f7.F7Huds.pyTimer)
        FishHudEditor.register("Necron LB Timer", fishmod.features.dungeon.f7.F7Huds.necronLbTimer)
        FishHudEditor.register("Necron Drag Timer", fishmod.features.dungeon.f7.F7Huds.necronDragTimer)
        FishHudEditor.register("Necron Drop Lava/Platform", fishmod.features.dungeon.f7.F7Huds.necronDropLanding)
        FishHudEditor.register("Storm Crush Banner", fishmod.features.dungeon.f7.F7Huds.stormCrush)
        FishHudEditor.register("Pillar Blast Timer", fishmod.features.dungeon.f7.F7Huds.pillarExplosion)
        FishHudEditor.register("Terminals Open In", fishmod.features.dungeon.f7.F7Huds.termOpenBox)
        FishHudEditor.register("Section Count", fishmod.features.dungeon.f7.F7Huds.sectionProgress)
        FishHudEditor.register("P3 Section", fishmod.features.dungeon.f7.F7Huds.currentSection)
        FishHudEditor.register("Device Done", fishmod.features.dungeon.f7.F7Huds.deviceNotifier)
        FishHudEditor.register("Melody Callout", fishmod.features.dungeon.f7.F7Huds.melodyWarning)
        FishHudEditor.register("Section Cleared", fishmod.features.dungeon.f7.F7Huds.sectionCompletion)
        FishHudEditor.register("Players Leaped", fishmod.features.dungeon.f7.F7Huds.playersLeaped)
        FishHudEditor.register("Goldor Splits", fishmod.utils.dungeon.GoldorSection.goldorSplitsBox)
        FishHudEditor.register("Terminal Tracker", fishmod.features.dungeon.f7.TerminalTracker.terminalTrackerHud)
        FishDiag.guard("FishModInit.105", "fishmod.utils.dungeon.DungeonRole.init() failed") { fishmod.utils.dungeon.DungeonRole.init() }
        FishDiag.guard("FishModInit.106", "fishmod.features.ClassColoredBoots.init() failed") { fishmod.features.ClassColoredBoots.init() }
        FishDiag.guard("FishModInit.107", "fishmod.features.dungeon.DupeClassDetector.init() failed") { fishmod.features.dungeon.DupeClassDetector.init() }

        FishHudEditor.register("Run Splits", BossPhase.runSplitsBox)
        FishHudEditor.registerLocked(
            "Est. Total (follows Splits)",
            { try { BossPhase.runSplitsBox.scaledX } catch (t: Throwable) { FishDiag.fail("FishModInit.108", "Est. Total x lookup failed", t); 0 } },
            {
                try {
                    // outside a run the editor shows the example Splits rows
                    BossPhase.runSplitsBox.scaledY + FishConstants.TEXT_HEIGHT * BossPhase.getVisibleRowCount().let { if (it > 0) it else BossPhase.previewRowCount() } + 8
                } catch (t: Throwable) {
                    FishDiag.fail("FishModInit.109", "Est. Total y lookup failed", t)
                    BossPhase.runSplitsBox.scaledY + 20
                }
            },
            BossPhase.SPLIT_LENGTH, FishConstants.TEXT_HEIGHT + 4
        )
        FishHudEditor.register("Puzzles", FishPuzzleDisplay.puzzleHud)

        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, _ ->
            fishmod.features.other.CommandAliases.registerAll(dispatcher)
            dispatcher.register(
                ClientCommands.literal("fm")
                    .then(ClientCommands.literal("stats").executes {
                        Minecraft.getInstance().execute { fishmod.features.dungeon.SessionStats.printToChat() }
                        FishConstants.SUCCESS
                    })
                    .then(ClientCommands.literal("commandkeys").executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().showScreen(fishmod.features.ChatCommandsScreen(fishmod.features.ChatCommandsScreen.Tab.KEYS))
                        }
                        FishConstants.SUCCESS
                    })
                    .then(ClientCommands.literal("customize").executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().showScreen(fishmod.features.item.ItemCustomizeScreen())
                        }
                        FishConstants.SUCCESS
                    })
                    .then(ClientCommands.literal("aliases").executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().showScreen(fishmod.features.ChatCommandsScreen(fishmod.features.ChatCommandsScreen.Tab.ALIASES))
                        }
                        FishConstants.SUCCESS
                    })
                    .then(ClientCommands.literal("whitelist").executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().showScreen(fishmod.features.PartyLootScreen(fishmod.features.PartyLootScreen.Tab.WHITELIST))
                        }
                        FishConstants.SUCCESS
                    })
                    .then(ClientCommands.literal("blacklist").executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().showScreen(fishmod.features.PartyLootScreen(fishmod.features.PartyLootScreen.Tab.BLACKLIST))
                        }
                        FishConstants.SUCCESS
                    })
                    .then(ClientCommands.literal("kicklist").executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().showScreen(fishmod.features.PartyLootScreen(fishmod.features.PartyLootScreen.Tab.KICK))
                        }
                        FishConstants.SUCCESS
                    })
                    .then(ClientCommands.literal("commandhelp").executes {
                        printCommandHelp()
                        FishConstants.SUCCESS
                    })
                    .then(ClientCommands.literal("help").executes {
                        printCommandHelp()
                        FishConstants.SUCCESS
                    })
                    .then(ClientCommands.literal("debug")
                        .executes {
                            debugReport()
                            FishConstants.SUCCESS
                        }
                        .then(ClientCommands.literal("report").executes { debugReport(); FishConstants.SUCCESS })
                        .then(ClientCommands.literal("clear").executes {
                            fishmod.utils.debug.FishDiag.clear()
                            fishmod.utils.FishUtil.addChatMessage(Component.literal("§a[FishMod] Debug codes cleared."))
                            FishConstants.SUCCESS
                        }))
                    .then(waypointSubcommand("wp"))
                    .then(ClientCommands.literal("pm")
                        .executes { fishmod.features.dungeon.DungeonWaypoints.togglePmEdit(); FishConstants.SUCCESS }
                        .then(ClientCommands.literal("message")
                            .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes { ctx ->
                                fishmod.features.dungeon.DungeonWaypoints.setPmMessage(StringArgumentType.getString(ctx, "text")); FishConstants.SUCCESS
                            })))
                    .then(waypointSubcommand("waypoint"))
                    .then(waypointSubcommand("waypoints"))
                    .then(fishmod.features.dungeon.RouteRecorder.command())
                    .then(fishmod.features.dungeon.f7.StormLbWaypoint.command())
                    .then(fishmod.features.dungeon.f7.NecronDropPredict.command())
                    .then(fishmod.features.diana.Diana.command())
                    .then(fishmod.features.achievements.AchEngine.command())
                    .then(ClientCommands.literal("dianaloot").executes { fishmod.features.diana.DianaTracker.openPastEvents(); 1 })
                    .then(fishmod.features.diana.DianaTracker.command())
                    .then(fishmod.features.diana.CrownOfAvarice.command())
                    .then(chatNotificationsSubcommand("chatnotifications"))
                    .then(chatNotificationsSubcommand("cn"))
                    .then(chatNotificationsSubcommand("chatfilter", fishmod.features.ChatCommandsScreen.Tab.FILTER))
                    .then(chatNotificationsSubcommand("cf", fishmod.features.ChatCommandsScreen.Tab.FILTER))
                    .then(sackSubcommand("ep"))
                    .then(sackSubcommand("ij"))
                    .then(sackSubcommand("sl"))
                    .then(sackSubcommand("sb"))
                    .then(sackSubcommand("dd"))
                    .then(sackSubcommand("tap"))
                    .then(sackSubcommand("twap"))
                    .executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().showScreen(fishmod.features.FishModScreen())
                        }
                        FishConstants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("trackcarry")
                    .then(ClientCommands.literal("clear").executes { fishmod.features.slayers.SlayerCarryTracker.clear(); FishConstants.SUCCESS })
                    .then(ClientCommands.literal("debug").executes { fishmod.features.slayers.SlayerCarryTracker.debug(); FishConstants.SUCCESS })
                    .then(ClientCommands.literal("remove").then(ClientCommands.argument("ign", StringArgumentType.word()).executes { ctx ->
                        fishmod.features.slayers.SlayerCarryTracker.remove(StringArgumentType.getString(ctx, "ign")); FishConstants.SUCCESS
                    }))
                    .then(ClientCommands.argument("ign", StringArgumentType.word())
                        .then(ClientCommands.argument("amount", IntegerArgumentType.integer(0, 1000)).executes { ctx ->
                            fishmod.features.slayers.SlayerCarryTracker.add(StringArgumentType.getString(ctx, "ign"), IntegerArgumentType.getInteger(ctx, "amount")); FishConstants.SUCCESS
                        })
                        .executes { ctx -> fishmod.features.slayers.SlayerCarryTracker.add(StringArgumentType.getString(ctx, "ign"), -1); FishConstants.SUCCESS })
                    .executes { fishmod.features.slayers.SlayerCarryTracker.list(); FishConstants.SUCCESS }
            )
            dispatcher.register(ClientCommands.literal("termreplay")
                .then(ClientCommands.argument("replay", StringArgumentType.greedyString())
                    .suggests { _, b -> (listOf("latest") + fishmod.features.dungeon.f7.TerminalTracker.replayNames()).forEach(b::suggest); b.buildFuture() }
                    .executes { ctx -> fishmod.features.dungeon.f7.TerminalTracker.replay(StringArgumentType.getString(ctx, "replay")); FishConstants.SUCCESS })
                .executes { fishmod.features.dungeon.f7.TerminalTracker.replay(); FishConstants.SUCCESS })
            for (cmd in arrayOf("pbsplits", "splitspb")) dispatcher.register(
                ClientCommands.literal(cmd)
                    .then(ClientCommands.argument("floor", StringArgumentType.word()).executes { ctx ->
                        fishmod.utils.dungeon.BossPhase.pbSplitsCommand(StringArgumentType.getString(ctx, "floor"))
                        FishConstants.SUCCESS
                    })
                    .executes {
                        fishmod.utils.dungeon.BossPhase.pbSplitsCommand(null)
                        FishConstants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("fmloot")
                    .executes {
                        Minecraft.getInstance().schedule {
                            Minecraft.getInstance().showScreen(fishmod.features.PartyLootScreen(fishmod.features.PartyLootScreen.Tab.LOOT))
                        }
                        FishConstants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("pfs")
                    .then(ClientCommands.argument("name", StringArgumentType.word()).executes { ctx ->
                        fishmod.features.dungeon.PartyFinderStats.command(StringArgumentType.getString(ctx, "name"))
                        FishConstants.SUCCESS
                    })
                    .executes {
                        fishmod.features.dungeon.PartyFinderStats.command(null)
                        FishConstants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("fmpractice")
                    .then(ClientCommands.argument("arg", StringArgumentType.word()).executes { ctx ->
                        fishmod.utils.dungeon.PracticeMode.command(StringArgumentType.getString(ctx, "arg"))
                        FishConstants.SUCCESS
                    })
                    .executes {
                        fishmod.utils.dungeon.PracticeMode.command(null)
                        FishConstants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("fmtermsim")
                    .then(ClientCommands.argument("type", StringArgumentType.word()).executes { ctx ->
                        val a = StringArgumentType.getString(ctx, "type")
                        Minecraft.getInstance().schedule { fishmod.features.dungeon.f7.terminal.TermSimScreen.open(a) }
                        FishConstants.SUCCESS
                    })
                    .executes {
                        Minecraft.getInstance().schedule { fishmod.features.dungeon.f7.terminal.TermSimScreen.open(null) }
                        FishConstants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("storageview")
                    .executes {
                        Minecraft.getInstance().schedule { fishmod.features.storage.StorageViewerScreen.open() }
                        FishConstants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("storageload")
                    .then(ClientCommands.literal("stop").executes {
                        fishmod.features.storage.StorageAutoLoader.stop()
                        FishConstants.SUCCESS
                    })
                    .executes {
                        fishmod.features.storage.StorageAutoLoader.start()
                        FishConstants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("fmnicktest")
                    .executes { ctx ->
                        if (fishmod.utils.DevOnly.deny(ctx.source)) return@executes FishConstants.SUCCESS
                        val mc = Minecraft.getInstance()
                        if (mc.player == null || mc.connection == null) {
                            FishUtil.addChatMessage(Component.literal("§cNot in a world."))
                            return@executes FishConstants.SUCCESS
                        }
                        val remoteOn = fishmod.utils.config.values.FishSettings.remoteNicksEnabled
                        FishUtil.addChatMessage(
                            Component.literal(
                                "§b[fmnicktest] §7See Others: §f$remoteOn" +
                                    " §8|§7 own nick active: §f" + fishmod.cosmetic.NickState.isActive() +
                                    " §8|§7 raw: §f" + (fishmod.cosmetic.NickState.getRaw() ?: "(none)")
                            )
                        )
                        fishmod.cosmetic.RemoteNicks.uploadOwn()
                        FishUtil.addChatMessage(Component.literal("§b[fmnicktest] §7re-uploaded own nick."))
                        fishmod.cosmetic.RemoteNicks.forceRefresh()
                        FishUtil.addChatMessage(Component.literal("§b[fmnicktest] §7triggered RemoteNicks.refresh()…"))
                        TickScheduler.scheduleTask({
                            val cache = fishmod.cosmetic.RemoteNicks.snapshot()
                            FishUtil.addChatMessage(Component.literal("§b[fmnicktest] §7styledByName cache: §f" + cache.size + " §7entries"))
                            var count = 0
                            for (e in cache.entries) {
                                val line: MutableComponent = Component.literal("§7  " + e.key + " §8→ ").copy()
                                line.append(e.value)
                                FishUtil.addChatMessage(line)
                                if (++count > 10) {
                                    FishUtil.addChatMessage(Component.literal("§8  (…more)")); break
                                }
                            }
                            // Per nearby player: live socket copy vs a direct /nicks fetch
                            FishUtil.addChatMessage(Component.literal("§b[fmnicktest] §7live ready: §f" + fishmod.cosmetic.CosmeticLive.ready + " §8|§7 rev: §f" + fishmod.cosmetic.CosmeticLive.rev))
                            val self = mc.player?.uuid
                            val me = mc.player
                            mc.level?.players()?.filter { it.uuid != self }?.sortedBy { if (me == null) 0.0 else it.distanceToSqr(me) }?.take(8)?.forEach { p ->
                                val u = p.uuid.toString().replace("-", "")
                                val live = fishmod.cosmetic.CosmeticLive.slice(setOf(u))?.nicks?.get(u)
                                fishmod.utils.HypixelApi.fetchNicks(setOf(u)) { nicks ->
                                    mc.execute {
                                        FishUtil.addChatMessage(Component.literal("§7  " + p.gameProfile.name() + " §8v" + p.uuid.version() +
                                            " §7live: §f" + (live ?: "-") + " §7server: §f" + (nicks[u] ?: "-")))
                                    }
                                }
                            }
                            if (cache.isEmpty()) {
                                FishUtil.addChatMessage(Component.literal("§c[fmnicktest] cache is empty — chat rewrite has nothing to apply. Check See Others toggle."))
                            } else {
                                FishUtil.addChatMessage(Component.literal("§a[fmnicktest] cache populated. If chat still shows IGNs, the mixin path isn't covering Hypixel's chat handler — paste a chat screenshot."))
                            }
                        }, 24)
                        FishConstants.SUCCESS
                    }
            )
            dispatcher.register(
                ClientCommands.literal("pk")
                    .then(
                        ClientCommands.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val name = StringArgumentType.getString(ctx, "name")
                                val mc = Minecraft.getInstance()
                                if (mc.connection != null) mc.connection!!.sendCommand("p kick $name")
                                FishConstants.SUCCESS
                            }
                    )
            )
            dispatcher.register(
                ClientCommands.literal("pw")
                    .executes {
                        val mc = Minecraft.getInstance()
                        if (mc.connection != null) mc.connection!!.sendCommand("p warp")
                        FishConstants.SUCCESS
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
                                FishConstants.SUCCESS
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
                                FishConstants.SUCCESS
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
                                FishConstants.SUCCESS
                            }
                    )
            )

            dispatcher.register(
                ClientCommands.literal("fmpet").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    val mc = Minecraft.getInstance()
                    mc.schedule {
                        FishUtil.addChatMessage(Component.literal("§b--- Pet HUD ---"))
                        FishUtil.addChatMessage(Component.literal("§7" + PetHud.debugState()))
                        FishUtil.addChatMessage(Component.literal("§b--- Cooldown Overlay ---"))
                        FishUtil.addChatMessage(Component.literal("§7" + CooldownOverlay.debugState()))
                    }
                    FishConstants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmpetdump").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    PetHud.debugDumpPetLines = !PetHud.debugDumpPetLines
                    FishUtil.addChatMessage(Component.literal("§b[fmpet] dump pet-related chat lines: §f" + PetHud.debugDumpPetLines))
                    FishConstants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmcddump").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    CooldownOverlay.debugDumpSound = !CooldownOverlay.debugDumpSound
                    FishUtil.addChatMessage(Component.literal("§b[fmcd] dump cooldown sound events: §f" + CooldownOverlay.debugDumpSound))
                    FishConstants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmcatadump").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    fishmod.features.CatacombsOverflowOverlay.debugDumpLines = !fishmod.features.CatacombsOverflowOverlay.debugDumpLines
                    FishUtil.addChatMessage(Component.literal("§b[fmcata] dump Catacombs/class menu item lines: §f" + fishmod.features.CatacombsOverflowOverlay.debugDumpLines))
                    FishConstants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmblocks").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    val mc = Minecraft.getInstance()
                    mc.schedule {
                        if (mc.player == null || mc.level == null) {
                            FishUtil.addChatMessage(Component.literal("§cNo world")); return@schedule
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
                        FishUtil.addChatMessage(Component.literal("§b--- Blocks within $R (top 20) ---"))
                        counts.entries.sortedByDescending { it.value }.take(20)
                            .forEach { e -> FishUtil.addChatMessage(Component.literal("§7" + e.value + "x §f" + e.key)) }
                    }
                    FishConstants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmssdebug").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    fishmod.features.dungeon.SimonSaysTracker.debug = !fishmod.features.dungeon.SimonSaysTracker.debug
                    FishUtil.addChatMessage(Component.literal("§b[ssdbg] log Simon Says block transitions: §f" + fishmod.features.dungeon.SimonSaysTracker.debug))
                    FishConstants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmnuc").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    fishmod.utils.HypixelApi.dumpNucleus(Minecraft.getInstance())
                    FishConstants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmgarden").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    fishmod.utils.HypixelApi.dumpGarden(Minecraft.getInstance())
                    FishConstants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmprofile").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    fishmod.utils.HypixelApi.dumpEconomy(Minecraft.getInstance())
                    FishConstants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmtabdump").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    val mc = Minecraft.getInstance()
                    mc.schedule {
                        if (mc.connection == null) {
                            FishUtil.addChatMessage(Component.literal("§cNo network")); return@schedule
                        }
                        FishUtil.addChatMessage(Component.literal("§b--- Tab entries (non-empty) ---"))
                        var n = 0
                        for (te in fishmod.utils.TabListCache.entries) {
                            if (te.info.tabListDisplayName == null) continue
                            val s = te.stripped.trim()
                            if (s.isEmpty()) continue
                            if (s.lowercase().contains("pet") || s.contains("Lvl") || s.contains("XP") || s.contains("/")) {
                                FishUtil.addChatMessage(Component.literal("§7$s"))
                                if (++n > 30) break
                            }
                        }
                        FishUtil.addChatMessage(Component.literal("§b--- End ($n) ---"))
                    }
                    FishConstants.SUCCESS
                }
            )

            dispatcher.register(
                ClientCommands.literal("fmdbg").executes { context ->
                    if (fishmod.utils.DevOnly.deny(context.source)) return@executes FishConstants.SUCCESS
                    val mc = Minecraft.getInstance()
                    mc.schedule {
                        FishUtil.addChatMessage(Component.literal("§b--- FishMod Debug ---"))
                        FishUtil.addChatMessage(Component.literal("§7Location: §f" + SkyblockLocation.getCurrentLocation()))
                        FishUtil.addChatMessage(Component.literal("§7inSkyblock: §f" + SkyblockLocation.inSkyblock()))
                        FishUtil.addChatMessage(Component.literal("§7inDungeon: §f" + SkyblockLocation.inDungeon()))
                        FishUtil.addChatMessage(Component.literal("§7showPuzzles: §f" + fishmod.utils.config.values.FishSettings.showPuzzles))
                        FishUtil.addChatMessage(Component.literal("§7Puzzle list (" + FishPuzzleDisplay.getPuzzles().size + "): §f" + FishPuzzleDisplay.getPuzzles()))
                        try {
                            FishUtil.addChatMessage(Component.literal("§7Phase.runStarted: §f" + BossPhase.runStarted()))
                        } catch (t: Throwable) {
                            FishDiag.fail("FishModInit.110", "fmdbg BossPhase.runStarted failed", t)
                            FishUtil.addChatMessage(Component.literal("§cPhase.runStarted ERR: " + t.message))
                        }
                        try {
                            FishUtil.addChatMessage(Component.literal("§7Phase.enableSplits: §f" + BossPhase.runSplitsEnabled))
                        } catch (t: Throwable) {
                            FishDiag.fail("FishModInit.111", "fmdbg BossPhase.enableSplits failed", t)
                            FishUtil.addChatMessage(Component.literal("§cPhase.enableSplits ERR: " + t.message))
                        }
                        try {
                            FishUtil.addChatMessage(Component.literal("§7Blade compat: §f" + fishmod.compat.BladeCompat.active))
                        } catch (t: Throwable) {
                            FishDiag.fail("FishModInit.112", "fmdbg mod loader query failed", t)
                            FishUtil.addChatMessage(Component.literal("§cloader ERR"))
                        }
                        val handler: ClientPacketListener? = mc.connection
                        if (handler == null) {
                            FishUtil.addChatMessage(Component.literal("§cNo network handler"))
                        } else {
                            var total = 0
                            var nullName = 0
                            for (te in fishmod.utils.TabListCache.entries) {
                                total++
                                if (te.info.tabListDisplayName == null) {
                                    nullName++; continue
                                }
                                val clean = te.stripped.trim()
                                if (clean.isNotEmpty())
                                    FishUtil.addChatMessage(Component.literal("§8TAB: §7$clean"))
                            }
                            FishUtil.addChatMessage(Component.literal("§7Tab entries: §f$total (§c$nullName null§7)"))
                        }
                        if (mc.level != null) {
                            val sb: Scoreboard = mc.level!!.scoreboard
                            val sidebar: Objective? = sb.getDisplayObjective(DisplaySlot.SIDEBAR)
                            if (sidebar == null) {
                                FishUtil.addChatMessage(Component.literal("§7Sidebar: §cnone"))
                            } else {
                                FishUtil.addChatMessage(Component.literal("§7Sidebar obj: §f" + sidebar.displayName.string))
                                for (entry: PlayerScoreEntry in sb.listPlayerScores(sidebar)) {
                                    val owner = entry.owner
                                    val team: PlayerTeam? = sb.getPlayersTeam(owner)
                                    val line = if (team != null)
                                        team.playerPrefix.string + owner + team.playerSuffix.string
                                    else
                                        entry.ownerName().string
                                    val clean = line.replace(fishmod.utils.FishConstants.STRIP_COLOR_REGEX, "").trim()
                                    if (clean.isNotEmpty())
                                        FishUtil.addChatMessage(Component.literal("§8SB: §7$clean"))
                                }
                            }
                        }
                        FishUtil.addChatMessage(Component.literal("§b--- End Debug ---"))
                    }
                    FishConstants.SUCCESS
                }.then(
                    ClientCommands.argument("sub", StringArgumentType.greedyString())
                        .executes { ctx ->
                            val arg = StringArgumentType.getString(ctx, "sub")
                            val mc = Minecraft.getInstance()
                            val parts = arg.trim().split(Regex("\\s+"), 2)
                            if (parts[0] == "cprice") {
                                if (parts.size < 2) {
                                    mc.schedule { FishUtil.addChatMessage(Component.literal("§cUsage: /fmdbg cprice <ITEM_ID>")) }
                                    return@executes FishConstants.SUCCESS
                                }
                                val pid = parts[1].trim().uppercase()
                                fishmod.features.croesus.CroesusPrices.refreshIfStale().whenComplete { _, _ ->
                                    mc.schedule {
                                        FishUtil.addChatMessage(
                                            Component.literal(
                                                "§b$pid §7→ §f" + fishmod.features.croesus.CroesusPrices.debugSource(pid)
                                            )
                                        )
                                    }
                                }
                                return@executes FishConstants.SUCCESS
                            }
                            if (parts[0] == "mp") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.schedule { FishUtil.addChatMessage(Component.literal("§cUsage: /fmdbg mp <ign>")) }
                                    return@executes FishConstants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.getByName(mc, ign) { data ->
                                    mc.schedule { FishUtil.addChatMessage(Component.literal("§b$ign magicalPower=§f" + data.magicalPower)) }
                                }
                                return@executes FishConstants.SUCCESS
                            }
                            if (parts[0] == "mpraw") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.schedule { FishUtil.addChatMessage(Component.literal("§cUsage: /fmdbg mpraw <ign>")) }
                                    return@executes FishConstants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.dumpMemberKeys(mc, ign)
                                return@executes FishConstants.SUCCESS
                            }
                            if (parts[0] == "sklraw") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.schedule { FishUtil.addChatMessage(Component.literal("§cUsage: /fmdbg sklraw <ign>")) }
                                    return@executes FishConstants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.dumpSkillKeys(mc, ign)
                                return@executes FishConstants.SUCCESS
                            }
                            if (parts[0] == "skl") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.schedule { FishUtil.addChatMessage(Component.literal("§cUsage: /fmdbg skl <ign>")) }
                                    return@executes FishConstants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.getByName(mc, ign) { data ->
                                    mc.schedule { FishUtil.addChatMessage(Component.literal("§b$ign skillAverage=§f" + data.skillAverage)) }
                                }
                                return@executes FishConstants.SUCCESS
                            }
                            if (parts[0] == "col") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.schedule { FishUtil.addChatMessage(Component.literal("§cUsage: /fmdbg col <ign>")) }
                                    return@executes FishConstants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.getByName(mc, ign) { data ->
                                    mc.schedule {
                                        var cataTotal = 0L
                                        for (t in data.cataTimes) cataTotal += t
                                        var masterTotal = 0L
                                        for (i in 1..7) masterTotal += data.masterTimes[i]
                                        val col = cataTotal + masterTotal * 2
                                        FishUtil.addChatMessage(Component.literal("§b--- Collection debug: $ign ---"))
                                        val cata = StringBuilder("§7cata: ")
                                        for (i in 0..7) cata.append(if (i == 0) "E" else "F$i").append("=").append(data.cataTimes[i]).append(" ")
                                        FishUtil.addChatMessage(Component.literal(cata.toString()))
                                        val master = StringBuilder("§7master: ")
                                        for (i in 1..7) master.append("M").append(i).append("=").append(data.masterTimes[i]).append(" ")
                                        FishUtil.addChatMessage(Component.literal(master.toString()))
                                        FishUtil.addChatMessage(Component.literal("§7cataTotal=§f$cataTotal §7masterTotal=§f$masterTotal"))
                                        FishUtil.addChatMessage(Component.literal("§7computed col=§f$col §7(cata×1 + master×2)"))
                                    }
                                }
                                return@executes FishConstants.SUCCESS
                            }
                            if (parts[0] == "runs") {
                                val ign = if (parts.size > 1) parts[1] else mc.player?.name?.string
                                if (ign == null) {
                                    mc.schedule { FishUtil.addChatMessage(Component.literal("§cUsage: /fmdbg runs <ign>")) }
                                    return@executes FishConstants.SUCCESS
                                }
                                fishmod.utils.HypixelApi.getByName(mc, ign) { data ->
                                    mc.schedule {
                                        FishUtil.addChatMessage(Component.literal("§b--- Runs debug: $ign ---"))
                                        FishUtil.addChatMessage(Component.literal("§7totalRuns: §f" + data.totalRuns))
                                        val cata = StringBuilder("§7cataTimes: ")
                                        for (i in 0..7) cata.append("F").append(if (i == 0) "E" else i.toString()).append("=").append(data.cataTimes[i]).append(" ")
                                        FishUtil.addChatMessage(Component.literal(cata.toString()))
                                        val master = StringBuilder("§7masterTimes: ")
                                        for (i in 1..7) master.append("M").append(i).append("=").append(data.masterTimes[i]).append(" ")
                                        FishUtil.addChatMessage(Component.literal(master.toString()))
                                        FishUtil.addChatMessage(Component.literal("§b--- End ---"))
                                    }
                                }
                            }
                            FishConstants.SUCCESS
                        }
                )
            )

            val playerSuggest = SuggestionProvider<FabricClientCommandSource> { _, b ->
                val mc = Minecraft.getInstance()
                if (mc.connection != null) {
                    val rem = b.remaining.lowercase()
                    val seen = HashSet<String>()
                    for (te in fishmod.utils.TabListCache.entries) {
                        val n = te.info.profile.name
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

            dispatcher.register(
                ClientCommands.literal("fmcmd")
                    .then(
                        ClientCommands.literal("whitelist")
                            .executes { printNameList("Whitelist", fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist); FishConstants.SUCCESS }
                            .then(ClientCommands.literal("list").executes { printNameList("Whitelist", fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist); FishConstants.SUCCESS })
                            .then(
                                ClientCommands.literal("add").then(
                                    ClientCommands.argument("name", StringArgumentType.word()).suggests(playerSuggest)
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist =
                                                fishmod.utils.NameList.add(fishmod.utils.config.values.FishSettings.pcPartyActionsWhitelist, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            FishUtil.addChatMessage(Component.literal("§7[FM] Added §f$name §7to the party-action whitelist."))
                                            FishConstants.SUCCESS
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
                                            FishUtil.addChatMessage(Component.literal("§7[FM] Removed §f$name §7from the party-action whitelist."))
                                            FishConstants.SUCCESS
                                        }
                                )
                            )
                    )
                    .then(
                        ClientCommands.literal("blacklist")
                            .executes { printNameList("Blacklist", fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist); FishConstants.SUCCESS }
                            .then(ClientCommands.literal("list").executes { printNameList("Blacklist", fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist); FishConstants.SUCCESS })
                            .then(
                                ClientCommands.literal("add").then(
                                    ClientCommands.argument("name", StringArgumentType.word()).suggests(playerSuggest)
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist =
                                                fishmod.utils.NameList.add(fishmod.utils.config.values.FishSettings.pcPartyActionsBlacklist, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            FishUtil.addChatMessage(Component.literal("§7[FM] Added §f$name §7to the party-action blacklist."))
                                            FishConstants.SUCCESS
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
                                            FishUtil.addChatMessage(Component.literal("§7[FM] Removed §f$name §7from the party-action blacklist."))
                                            FishConstants.SUCCESS
                                        }
                                )
                            )
                    )
                    .then(
                        ClientCommands.literal("kicklist")
                            .executes { printNameList("Kick List", fishmod.utils.config.values.FishSettings.pcKickList); FishConstants.SUCCESS }
                            .then(ClientCommands.literal("list").executes { printNameList("Kick List", fishmod.utils.config.values.FishSettings.pcKickList); FishConstants.SUCCESS })
                            .then(
                                ClientCommands.literal("add").then(
                                    ClientCommands.argument("name", StringArgumentType.word()).suggests(playerSuggest)
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcKickList =
                                                fishmod.utils.NameList.add(fishmod.utils.config.values.FishSettings.pcKickList, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            FishUtil.addChatMessage(Component.literal("§7[FM] Added §f$name §7to the kick list."))
                                            FishConstants.SUCCESS
                                        }
                                )
                            )
                            .then(
                                ClientCommands.literal("remove").then(
                                    ClientCommands.argument("name", StringArgumentType.word())
                                        .executes { ctx ->
                                            val name = StringArgumentType.getString(ctx, "name")
                                            fishmod.utils.config.values.FishSettings.pcKickList =
                                                fishmod.utils.NameList.remove(fishmod.utils.config.values.FishSettings.pcKickList, name) ?: ""
                                            fishmod.utils.config.FishConfig.manager.save()
                                            FishUtil.addChatMessage(Component.literal("§7[FM] Removed §f$name §7from the kick list."))
                                            FishConstants.SUCCESS
                                        }
                                )
                            )
                    )
            )

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
            // no "ai": Hypixel's /ai <uuid> accepts island invites
            for (name in arrayOf(
                "fps", "tps", "ping", "dprofit", "crit", "allinv", "d",
                "e", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "m1", "m2", "m3", "m4", "m5", "m6", "m7",
                "t1", "t2", "t3", "t4", "t5"
            )) {
                dispatcher.register(ClientCommands.literal(name).executes { c -> runLocalLookup(name, null, null) })
            }
        })

        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, _ ->
            fishmod.utils.config.values.DungeonMapSettings.mapLegitMode = true
            fishmod.utils.TickScheduler.scheduleTask({ fishmod.compat.BladeCompat.tellOnce() }, 60)
        })

        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "soulflow_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) SoulflowHud.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.113", "soulflow_hud render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "dungeon_breaker_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.DungeonBreaker.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.114", "dungeon_breaker_hud render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "pet_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) PetHud.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.115", "pet_hud render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "cooldown_overlay_hotbar")) { ctx, tickCounter -> try { CooldownOverlay.renderHotbar(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.116", "cooldown_overlay_hotbar render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "phase_splits")) { ctx, _ -> try { if (!fishmod.features.FishHudEditor.isOpen()) BossPhase.renderHud(ctx) } catch (t: Throwable) { FishDiag.fail("FishModInit.117", "phase_splits render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "f7_huds")) { ctx, _ -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.f7.F7Huds.renderHud(ctx) } catch (t: Throwable) { FishDiag.fail("FishModInit.118", "f7_huds render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "dungeon_waypoints_overlay")) { ctx, _ -> try { fishmod.features.dungeon.DungeonWaypoints.renderOverlay(ctx) } catch (t: Throwable) { FishDiag.fail("FishModInit.119", "dungeon_waypoints_overlay render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "session_stats")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) SessionStats.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.120", "session_stats render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "warp_cooldown")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.PartyWarpTimer.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.121", "warp_cooldown render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "blessings")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.Blessings.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.122", "blessings render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "quiz_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.QuizHud.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.123", "quiz_hud render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "secret_overlay")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.SecretOverlay.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.124", "secret_overlay render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "storm_over")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.f7.StormOverAlert.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.125", "storm_over render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "veno_stacks")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.f7.VenoStackCount.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.126", "veno_stacks render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "performance")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.PerformanceHud.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.127", "performance render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "invincibility")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.InvincibilityTracker.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.128", "invincibility render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "relic_timer")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.f7.M7Relics.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.129", "relic_timer render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "spring_boots")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.SpringBoots.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.130", "spring_boots render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "stopwatch")) { ctx, _ -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.other.Stopwatch.renderHud(ctx) } catch (t: Throwable) { FishDiag.fail("FishModInit.204", "stopwatch render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "tac_timer")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.TacTimer.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.131", "tac_timer render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "rag_timer")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.Ragnarock.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.132", "rag_timer render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "spirit_bear")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.f4.SpiritBear.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.133", "spirit_bear render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "wither_dragons")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.dungeon.f7.dragons.WitherDragons.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.134", "wither_dragons render failed", t) } }
        FishDiag.guard("FishModInit.135", "fishmod.utils.networth.ItemsDb.initAsync() failed") { fishmod.utils.networth.ItemsDb.initAsync() }

        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "pb_pace_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.PbPaceHud.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.136", "pb_pace_hud render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "slayer_spawn_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.slayers.SlayerHuds.renderSpawn(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.137", "slayer_spawn_hud render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "slayer_stats_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.slayers.SlayerHuds.renderStats(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.138", "slayer_stats_hud render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "slayer_rng_hud")) { ctx, tickCounter -> if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.slayers.SlayerHuds.renderRng(ctx, tickCounter) }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "slayer_timer_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.slayers.SlayerHuds.renderTimer(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.139", "slayer_timer_hud render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "experiment_profit_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.ExperimentProfitTracker.render(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.202", "experiment_profit_hud render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "slayer_carry_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.slayers.SlayerCarryTracker.render(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.200", "slayer_carry_hud render failed", t) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "slayer_profit_hud")) { ctx, tickCounter -> try { if (!fishmod.features.FishHudEditor.isOpen()) fishmod.features.slayers.SlayerHuds.renderProfit(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.140", "slayer_profit_hud render failed", t) } }
        fishmod.features.PbPaceHud.registerPreview()
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

        FishDiag.guard("FishModInit.141", "fishmod.features.dungeon.map.DungeonMap.init() failed") { fishmod.features.dungeon.map.DungeonMap.init() }
        FishDiag.guard("FishModInit.142", "fishmod.features.dungeon.map.Scan.register() failed") { fishmod.features.dungeon.map.Scan.register() }
        FishDiag.guard("FishModInit.143", "fishmod.features.dungeon.map.Mimic.register() failed") { fishmod.features.dungeon.map.Mimic.register() }
        FishDiag.guard("FishModInit.144", "fishmod.features.dungeon.map.MapHud.register() failed") { fishmod.features.dungeon.map.MapHud.register() }
        FishDiag.guard("FishModInit.145", "fishmod.features.dungeon.map.MapInfoHud.register() failed") { fishmod.features.dungeon.map.MapInfoHud.register() }
        FishDiag.guard("FishModInit.146", "fishmod.features.dungeon.map.MapImageLoader.init() failed") { fishmod.features.dungeon.map.MapImageLoader.init() }
        FishDiag.guard("FishModInit.147", "fishmod.features.CrosshairImageLoader.init() failed") { fishmod.features.CrosshairImageLoader.init() }
        FishDiag.guard("FishModInit.148", "fishmod.features.CustomCrosshair.register() failed") { fishmod.features.CustomCrosshair.register() }
        FishDiag.guard("FishModInit.149", "fishmod.features.dungeon.map.DungeonScore.register() failed") { fishmod.features.dungeon.map.DungeonScore.register() }
        FishDiag.guard("FishModInit.150", "fishmod.features.dungeon.map.DoorHighlight.init() failed") { fishmod.features.dungeon.map.DoorHighlight.init() }
        fishmod.utils.events.FishEvents.chat.register { message ->
            try {
                fishmod.features.dungeon.map.DungeonState.onChatMessage(message.chatRaw)
                fishmod.features.dungeon.map.DungeonScore.onChatMessage(message.chatRaw)
            } catch (t: Throwable) {
                FishDiag.fail("FishModInit.152", "dungeon map chat handler failed", t)
            }
            false
        }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "dungeon_map_score_messages")) { ctx, tickCounter -> try { if (!FishHudEditor.isOpen()) fishmod.features.dungeon.map.ScoreMessages.renderHud(ctx, tickCounter) } catch (t: Throwable) { FishDiag.fail("FishModInit.151", "dungeon_map_score_messages render failed", t) } }

        fishmod.features.dungeon.map.ScoreMessages.registerPreview()
        FishHudEditor.register(
            "Dungeon Score Title",
            {
                val mc = net.minecraft.client.Minecraft.getInstance()
                Math.round(fishmod.features.dungeon.map.ScoreMessages.resolvedX(mc) - fishmod.features.dungeon.map.ScoreMessages.previewWidth() / 2f * fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleScale)
            },
            { v -> fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleX = v + fishmod.features.dungeon.map.ScoreMessages.previewWidth() / 2f * fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleScale },
            { Math.round(fishmod.features.dungeon.map.ScoreMessages.resolvedY(net.minecraft.client.Minecraft.getInstance())) },
            { v -> fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleY = v.toFloat() },
            FishHudEditor.SCORE_TITLE_W, 9,
            { fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleScale.toDouble() },
            { v -> fishmod.utils.config.values.DungeonMapSettings.mapScoreTitleScale = v.toFloat() },
            { fishmod.utils.config.values.DungeonMapSettings.mapScoreMessages }
        )

        fishmod.features.scoreboard.CustomScoreboard.registerPreview()
        FishHudEditor.register(
            "Custom Scoreboard",
            { fishmod.features.scoreboard.CustomScoreboard.previewBoxX() },
            { v -> fishmod.features.scoreboard.CustomScoreboard.setPreviewBoxX(v) },
            { fishmod.features.scoreboard.CustomScoreboard.previewBoxY() },
            { v -> fishmod.features.scoreboard.CustomScoreboard.setPreviewBoxY(v) },
            FishHudEditor.SCOREBOARD_W, 130,
            { fishmod.utils.config.values.FishSettings.customScoreboardEnabled }
        )

        fishmod.features.dungeon.map.MapHud.registerPreview()
        fishmod.features.dungeon.map.MapInfoHud.registerPreview()
        FishHudEditor.register(
            "Dungeon Map",
            java.util.function.IntSupplier { fishmod.utils.config.values.DungeonMapSettings.mapX.toInt() },
            java.util.function.IntConsumer { v -> fishmod.utils.config.values.DungeonMapSettings.mapX = v.toFloat() },
            java.util.function.IntSupplier { fishmod.utils.config.values.DungeonMapSettings.mapY.toInt() },
            java.util.function.IntConsumer { v -> fishmod.utils.config.values.DungeonMapSettings.mapY = v.toFloat() },
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

        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) return@AfterInit
            FishDiag.guard("FishModInit.153", "CroesusLootDetector.onScreenInit failed") { fishmod.features.croesus.CroesusLootDetector.onScreenInit(screen) }
            ScreenEvents.beforeExtract(screen).register(ScreenEvents.BeforeExtract { _, ctx, mx, my, _ ->
                try { SessionStats.renderInScreen(ctx, mx, my) } catch (t: Throwable) { FishDiag.fail("FishModInit.154", "SessionStats.renderInScreen failed", t) }
            })
        })

        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
            if (screen !is net.minecraft.client.gui.screens.ChatScreen) return@AfterInit
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click ->
                val handled = try {
                    fishmod.features.slayers.SlayerHuds.onProfitClick(click.x(), click.y(), fishmod.compat.GLFW.btn(click))
                } catch (t: Throwable) {
                    FishDiag.fail("FishModInit.155", "SlayerHuds.onProfitClick failed", t); false
                }
                if (handled)
                    return@AllowMouseClick false
                true
            })
        })

        safeInit("FolderUtility") { FishPaths.init() }
        safeInit("Keybinds") { FishKeys.init() }
        safeInit("CustomEvents") { EventDispatch.init() }
        safeInit("Debug") { FishDebug.init() }
        safeInit("Location") { SkyblockLocation.init() }
        safeInit("Phase") { BossPhase.init() }
        safeInit("PracticeMode") { fishmod.utils.dungeon.PracticeMode.init() }
        safeInit("Section") { GoldorSection.init() }
        safeInit("TerminalTracker") { fishmod.features.dungeon.f7.TerminalTracker.init() }
        safeInit("Stopwatch") { fishmod.features.other.Stopwatch.init() }
        safeInit("PartyUtil") { PartyTools.init() }
        safeInit("RenderingEvents") { RenderHooks.init() }
        safeInit("Scheduler") { TickScheduler.init() }
        safeInit("ChatQueue") { fishmod.utils.ChatQueue.init() }
        safeInit("PrestigeChatFade") { fishmod.cosmetic.prestige.PrestigeChatFade.init() }
        safeInit("NametagCullingCompat") { fishmod.cosmetic.NametagCullingCompat.init() }
    }
}

// /fm debug and /fm debug report: copy the FishDiag report
private fun debugReport() {
    val mc = Minecraft.getInstance()
    val n = fishmod.utils.debug.FishDiag.count()
    if (n == 0) {
        fishmod.utils.FishUtil.addChatMessage(Component.literal("§a[FishMod] No problems recorded this session."))
    } else {
        mc.execute { mc.keyboardHandler.clipboard = fishmod.utils.debug.FishDiag.buildReport() }
        fishmod.utils.FishUtil.addChatMessage(Component.literal("§e[FishMod] Debug report copied ($n issue codes). Paste it to RedFish on Discord."))
        fishmod.utils.debug.FishDiag.summaryLines(5).forEach { fishmod.utils.FishUtil.addChatMessage(Component.literal("§7  $it")) }
        fishmod.utils.FishUtil.addChatMessage(Component.literal("§8  Full log: ${fishmod.utils.debug.FishDiag.logPath()}"))
    }
}
