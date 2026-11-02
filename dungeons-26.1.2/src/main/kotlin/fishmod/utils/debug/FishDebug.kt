package fishmod.utils.debug
import fishmod.compat.hud
import fishmod.compat.showScreen

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import fishmod.mixin.accessors.BossBarHudAccessor
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.FishUtil
import fishmod.utils.dungeon.DungeonRole
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.GoldorSection
import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandBuildContext
import net.minecraft.network.chat.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object FishDebug {

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(FishConstants.NAMESPACE)

    private var sendDebug = false

    @JvmField
    var sendSound = false

    @JvmField
    var termInfo = false

    @JvmField
    var renderPositions = false

    var holoTrace = false
    private var holoTraceAt = 0L

    // Prints each nametag text containing "Hunters" at most every 3 s
    fun holoTraceMsg(type: String, text: String) {
        val now = System.currentTimeMillis()
        if (!text.contains("Hunters") || now - holoTraceAt < 3000) return
        holoTraceAt = now
        FishUtil.addChatMessage(Component.literal("holo trace: $type '$text'"))
    }

    @JvmField
    var sendNotiDebug = false

    @JvmField
    var ssDebug = false

    @JvmField
    var roomCores = false

    @JvmField
    var dragonDebug = false

    @JvmField
    var leapDebug = false

    @JvmStatic
    fun init() {
        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, registryAccess ->
            registerCommands(dispatcher, registryAccess)
        })
    }

    private fun registerCommands(
        dispatcher: CommandDispatcher<FabricClientCommandSource>,
        registryAccess: CommandBuildContext
    ) {

        dispatcher.register(
            ClientCommands.literal("fm")
                .executes { _ ->
                    Minecraft.getInstance().schedule { Minecraft.getInstance().showScreen(fishmod.features.FishModScreen()) }
                    FishConstants.SUCCESS
                }
                .then(
                    ClientCommands.literal("bossbars")
                        .executes { _ ->
                            val mc = Minecraft.getInstance()
                            val accessor = mc.hud.bossOverlay as BossBarHudAccessor
                            val bars = accessor.bossBars
                            if (bars == null || bars.isEmpty()) {
                                FishUtil.addChatMessage(Component.literal("§cNo boss bars active."))
                            } else {
                                bars.values.forEach { bar ->
                                    val stripped = bar.name.string.replace(fishmod.utils.FishConstants.STRIP_COLOR_REGEX, "").trim()
                                    FishUtil.addChatMessage(Component.literal("§eBar: §f\"" + stripped + "\" §7(" + String.format("%.1f%%", bar.progress * 100f) + ")"))
                                }
                            }
                            FishConstants.SUCCESS
                        }
                )
        )

        dispatcher.register(ClientCommands.literal("fm").then(BoardReport.command("bug")).then(BoardReport.command("suggest")))

        dispatcher.register(
            ClientCommands.literal("badev")
                .then(ClientCommands.literal("runInfo").executes { _ ->
                    sendRunInfo()
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("debug").executes { _ ->
                    sendDebug = !sendDebug
                    FishUtil.addChatMessage(Component.literal("Send debug: ").append(FishUtil.getStatusText(sendDebug)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("sound").executes { _ ->
                    sendSound = !sendSound
                    FishUtil.addChatMessage(Component.literal("Send Sound: ").append(FishUtil.getStatusText(sendSound)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("ssDebug").executes { _ ->
                    ssDebug = !ssDebug
                    FishUtil.addChatMessage(Component.literal("Simon Says debug: ").append(FishUtil.getStatusText(ssDebug)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("roomCores").executes { _ ->
                    roomCores = !roomCores
                    FishUtil.addChatMessage(Component.literal("Room core hashes: ").append(FishUtil.getStatusText(roomCores)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("dragonDebug").executes { _ ->
                    dragonDebug = !dragonDebug
                    FishUtil.addChatMessage(Component.literal("Wither dragon debug: ").append(FishUtil.getStatusText(dragonDebug)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("leapSort").executes { _ ->
                    leapDebug = !leapDebug
                    FishUtil.addChatMessage(Component.literal("Leap sort debug: ").append(FishUtil.getStatusText(leapDebug)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("termInfo").executes { _ ->
                    termInfo = !termInfo
                    FishUtil.addChatMessage(Component.literal("Terminal info: ").append(FishUtil.getStatusText(termInfo)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("drawPositionBoxes").executes { _ ->
                    renderPositions = !renderPositions
                    FishUtil.addChatMessage(Component.literal("Render positons: ").append(FishUtil.getStatusText(renderPositions)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("testString").then(ClientCommands.argument("message", StringArgumentType.string()).executes { context ->
                    val message = StringArgumentType.getString(context, "message")
                    FishEvents.chat.invoke { gameMessageEvent -> gameMessageEvent.onGameMessage(Component.literal(message)) }
                    FishConstants.SUCCESS
                }))

                .then(
                    ClientCommands.literal("skyblock")
                        .then(ClientCommands.literal("on").executes { _ ->
                            SkyblockLocation.devSetSkyblock(true)
                            FishUtil.addChatMessage(Component.literal("Fake SkyBlock: on"))
                            FishConstants.SUCCESS
                        })
                        .then(ClientCommands.literal("off").executes { _ ->
                            SkyblockLocation.devSetSkyblock(false)
                            FishUtil.addChatMessage(Component.literal("Fake SkyBlock: off"))
                            FishConstants.SUCCESS
                        })
                )

                .then(ClientCommands.literal("latetext").executes { _ ->
                    fishmod.utils.config.values.VisualSettings.lateWorldText = !fishmod.utils.config.values.VisualSettings.lateWorldText
                    FishUtil.addChatMessage(Component.literal("Late world text (26.2): ").append(FishUtil.getStatusText(fishmod.utils.config.values.VisualSettings.lateWorldText)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("gizmos").executes { _ ->
                    fishmod.utils.config.values.VisualSettings.gizmoOverlays = !fishmod.utils.config.values.VisualSettings.gizmoOverlays
                    FishUtil.addChatMessage(Component.literal("Highlights via gizmos (26.2): ").append(FishUtil.getStatusText(fishmod.utils.config.values.VisualSettings.gizmoOverlays)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("holotrace").executes { _ ->
                    holoTrace = !holoTrace
                    FishUtil.addChatMessage(Component.literal("Holo trace: ").append(FishUtil.getStatusText(holoTrace)))
                    FishConstants.SUCCESS
                })

                .then(ClientCommands.literal("holo").then(ClientCommands.argument("q", StringArgumentType.greedyString()).executes { c -> holoDump(StringArgumentType.getString(c, "q")); FishConstants.SUCCESS }).executes { _ ->
                    val mc = net.minecraft.client.Minecraft.getInstance()
                    val p = mc.player
                    val lvl = mc.level
                    if (p != null && lvl != null) {
                        val all = lvl.entitiesForRendering().filter { it !== p && it.distanceTo(p) < 24 }
                        FishUtil.addChatMessage(Component.literal("holo: ${all.size} entities within 24: " + all.groupingBy { it.type.toShortString() }.eachCount()))
                        for (e in all.filter { it.hasCustomName() || it is net.minecraft.world.entity.Display || it.displayName?.string?.contains("Hunter") == true }.take(12)) FishUtil.addChatMessage(Component.literal("${e.type.toShortString()} '${e.customName?.string ?: e.displayName?.string}' d=${"%.1f".format(e.distanceTo(p))} y=${"%.1f".format(e.y - p.y)}"))
                    }
                    FishConstants.SUCCESS
                })

                .then(
                    ClientCommands.literal("location")
                        .then(
                            ClientCommands.literal("current")
                                .executes { _ ->
                                    FishUtil.addChatMessage(Component.literal(SkyblockLocation.getCurrentLocation().toString()))
                                    FishConstants.SUCCESS
                                })

                        .then(
                            ClientCommands.literal("set")
                                .then(
                                    ClientCommands.argument("name", StringArgumentType.string())
                                        .executes { context ->
                                            val name = StringArgumentType.getString(context, "name").uppercase()
                                            val location = SkyblockLocation.getLocation(name)
                                            SkyblockLocation.changeLocation(location)
                                            FishUtil.addChatMessage(Component.literal("Swapped to location: " + location.name))
                                            FishConstants.SUCCESS
                                        }
                                )
                        )
                )

                .then(
                    ClientCommands.literal("chatNoti")
                        .then(
                            ClientCommands.literal("sendDebug")
                                .executes { _ ->
                                    sendNotiDebug = !sendNotiDebug
                                    FishUtil.addChatMessage(Component.literal("Send notification debug: ").append(FishUtil.getStatusText(sendNotiDebug)))
                                    FishConstants.SUCCESS
                                }

                        )
                )

                .then(ClientCommands.literal("estInfo").executes { _ ->
                    fishmod.features.dungeon.FishEstTotal.printDebugInfo()
                    FishConstants.SUCCESS
                })

                .then(
                    ClientCommands.literal("classes")
                        .executes { _ ->
                            DungeonRole.printClasses()
                            FishConstants.SUCCESS
                        }
                )
                .then(
                    ClientCommands.literal("currentClass")
                        .executes { _ ->
                            FishUtil.addChatMessage(Component.literal("Current class: " + DungeonRole.currentClass))
                            FishConstants.SUCCESS
                        }
                )
        )
    }

    @JvmStatic
    fun sendDebugMessage(text: Component) {
        if (sendDebug) {
            FishUtil.addChatMessage(text)
        }
    }

    @JvmStatic
    fun sendRunInfo() {
        FishUtil.addChatMessage(Component.literal("Phase: " + BossPhase.getPhase()))
        FishUtil.addChatMessage(Component.literal("Section: " + GoldorSection.getSection()))
        FishUtil.addChatMessage(Component.literal("Gateblown: " + GoldorSection.isGateBlownUp()))
        FishUtil.addChatMessage(Component.literal("In floor 7: " + BossPhase.isInFloor7()))
    }
}

// /badev holo <text>: nearest entities (64 blocks) whose name contains the text
private fun holoDump(q: String) {
    val mc = net.minecraft.client.Minecraft.getInstance()
    val p = mc.player ?: return
    val lvl = mc.level ?: return
    fun nm(e: net.minecraft.world.entity.Entity) = e.customName?.string ?: e.displayName?.string ?: ""
    val all = lvl.entitiesForRendering().filter { it !== p && it.distanceTo(p) < 64 }
    val hits = all.filter { nm(it).contains(q, true) }.sortedBy { it.distanceTo(p) }
    FishUtil.addChatMessage(Component.literal("holo: ${all.size} entities within 64 ${all.groupingBy { it.type.toShortString() }.eachCount()}; ${hits.size} match '$q'"))
    for (e in hits.take(15)) FishUtil.addChatMessage(Component.literal("${e.type.toShortString()} '${nm(e)}' d=${"%.1f".format(e.distanceTo(p))} dy=${"%.1f".format(e.y - p.y)}"))
}
