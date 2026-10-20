package fishmod.utils.debug
import fishmod.compat.hud
import fishmod.compat.showScreen

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import fishmod.mixin.accessors.BossBarHudAccessor
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandBuildContext
import net.minecraft.network.chat.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Debug {

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(Constants.NAMESPACE)

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
        Misc.addChatMessage(Component.literal("holo trace: $type '$text'"))
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
                    Constants.SUCCESS
                }
                .then(
                    ClientCommands.literal("bossbars")
                        .executes { _ ->
                            val mc = Minecraft.getInstance()
                            val accessor = mc.hud.bossOverlay as BossBarHudAccessor
                            val bars = accessor.bossBars
                            if (bars == null || bars.isEmpty()) {
                                Misc.addChatMessage(Component.literal("§cNo boss bars active."))
                            } else {
                                bars.values.forEach { bar ->
                                    val stripped = bar.name.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim()
                                    Misc.addChatMessage(Component.literal("§eBar: §f\"" + stripped + "\" §7(" + String.format("%.1f%%", bar.progress * 100f) + ")"))
                                }
                            }
                            Constants.SUCCESS
                        }
                )
        )

        dispatcher.register(
            ClientCommands.literal("badev")
                .then(ClientCommands.literal("runInfo").executes { _ ->
                    sendRunInfo()
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("debug").executes { _ ->
                    sendDebug = !sendDebug
                    Misc.addChatMessage(Component.literal("Send debug: ").append(Misc.getStatusText(sendDebug)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("sound").executes { _ ->
                    sendSound = !sendSound
                    Misc.addChatMessage(Component.literal("Send Sound: ").append(Misc.getStatusText(sendSound)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("ssDebug").executes { _ ->
                    ssDebug = !ssDebug
                    Misc.addChatMessage(Component.literal("Simon Says debug: ").append(Misc.getStatusText(ssDebug)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("roomCores").executes { _ ->
                    roomCores = !roomCores
                    Misc.addChatMessage(Component.literal("Room core hashes: ").append(Misc.getStatusText(roomCores)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("dragonDebug").executes { _ ->
                    dragonDebug = !dragonDebug
                    Misc.addChatMessage(Component.literal("Wither dragon debug: ").append(Misc.getStatusText(dragonDebug)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("leapSort").executes { _ ->
                    leapDebug = !leapDebug
                    Misc.addChatMessage(Component.literal("Leap sort debug: ").append(Misc.getStatusText(leapDebug)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("termInfo").executes { _ ->
                    termInfo = !termInfo
                    Misc.addChatMessage(Component.literal("Terminal info: ").append(Misc.getStatusText(termInfo)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("drawPositionBoxes").executes { _ ->
                    renderPositions = !renderPositions
                    Misc.addChatMessage(Component.literal("Render positons: ").append(Misc.getStatusText(renderPositions)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("testString").then(ClientCommands.argument("message", StringArgumentType.string()).executes { context ->
                    val message = StringArgumentType.getString(context, "message")
                    Events.ON_GAME_MESSAGE.invoke { gameMessageEvent -> gameMessageEvent.onGameMessage(Component.literal(message)) }
                    Constants.SUCCESS
                }))

                .then(
                    ClientCommands.literal("skyblock")
                        .then(ClientCommands.literal("on").executes { _ ->
                            Location.devSetSkyblock(true)
                            Misc.addChatMessage(Component.literal("Fake SkyBlock: on"))
                            Constants.SUCCESS
                        })
                        .then(ClientCommands.literal("off").executes { _ ->
                            Location.devSetSkyblock(false)
                            Misc.addChatMessage(Component.literal("Fake SkyBlock: off"))
                            Constants.SUCCESS
                        })
                )

                .then(ClientCommands.literal("latetext").executes { _ ->
                    fishmod.utils.config.values.Visual.lateWorldText = !fishmod.utils.config.values.Visual.lateWorldText
                    Misc.addChatMessage(Component.literal("Late world text (26.2): ").append(Misc.getStatusText(fishmod.utils.config.values.Visual.lateWorldText)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("gizmos").executes { _ ->
                    fishmod.utils.config.values.Visual.gizmoOverlays = !fishmod.utils.config.values.Visual.gizmoOverlays
                    Misc.addChatMessage(Component.literal("Highlights via gizmos (26.2): ").append(Misc.getStatusText(fishmod.utils.config.values.Visual.gizmoOverlays)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("holotrace").executes { _ ->
                    holoTrace = !holoTrace
                    Misc.addChatMessage(Component.literal("Holo trace: ").append(Misc.getStatusText(holoTrace)))
                    Constants.SUCCESS
                })

                .then(ClientCommands.literal("holo").then(ClientCommands.argument("q", StringArgumentType.greedyString()).executes { c -> holoDump(StringArgumentType.getString(c, "q")); Constants.SUCCESS }).executes { _ ->
                    val mc = net.minecraft.client.Minecraft.getInstance()
                    val p = mc.player
                    val lvl = mc.level
                    if (p != null && lvl != null) {
                        val all = lvl.entitiesForRendering().filter { it !== p && it.distanceTo(p) < 24 }
                        Misc.addChatMessage(Component.literal("holo: ${all.size} entities within 24: " + all.groupingBy { it.type.toShortString() }.eachCount()))
                        for (e in all.filter { it.hasCustomName() || it is net.minecraft.world.entity.Display || it.displayName?.string?.contains("Hunter") == true }.take(12)) Misc.addChatMessage(Component.literal("${e.type.toShortString()} '${e.customName?.string ?: e.displayName?.string}' d=${"%.1f".format(e.distanceTo(p))} y=${"%.1f".format(e.y - p.y)}"))
                    }
                    Constants.SUCCESS
                })

                .then(
                    ClientCommands.literal("location")
                        .then(
                            ClientCommands.literal("current")
                                .executes { _ ->
                                    Misc.addChatMessage(Component.literal(Location.getCurrentLocation().toString()))
                                    Constants.SUCCESS
                                })

                        .then(
                            ClientCommands.literal("set")
                                .then(
                                    ClientCommands.argument("name", StringArgumentType.string())
                                        .executes { context ->
                                            val name = StringArgumentType.getString(context, "name").uppercase()
                                            val location = Location.getLocation(name)
                                            Location.changeLocation(location)
                                            Misc.addChatMessage(Component.literal("Swapped to location: " + location.name))
                                            Constants.SUCCESS
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
                                    Misc.addChatMessage(Component.literal("Send notification debug: ").append(Misc.getStatusText(sendNotiDebug)))
                                    Constants.SUCCESS
                                }

                        )
                )

                .then(ClientCommands.literal("estInfo").executes { _ ->
                    fishmod.features.dungeon.FishEstTotal.printDebugInfo()
                    Constants.SUCCESS
                })

                .then(
                    ClientCommands.literal("classes")
                        .executes { _ ->
                            DungeonClass.printClasses()
                            Constants.SUCCESS
                        }
                )
                .then(
                    ClientCommands.literal("currentClass")
                        .executes { _ ->
                            Misc.addChatMessage(Component.literal("Current class: " + DungeonClass.currentClass))
                            Constants.SUCCESS
                        }
                )
        )
    }

    @JvmStatic
    fun sendDebugMessage(text: Component) {
        if (sendDebug) {
            Misc.addChatMessage(text)
        }
    }

    @JvmStatic
    fun sendRunInfo() {
        Misc.addChatMessage(Component.literal("Phase: " + Phase.getPhase()))
        Misc.addChatMessage(Component.literal("Section: " + Section.getSection()))
        Misc.addChatMessage(Component.literal("Gateblown: " + Section.isGateBlownUp()))
        Misc.addChatMessage(Component.literal("In floor 7: " + Phase.isInFloor7()))
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
    Misc.addChatMessage(Component.literal("holo: ${all.size} entities within 64 ${all.groupingBy { it.type.toShortString() }.eachCount()}; ${hits.size} match '$q'"))
    for (e in hits.take(15)) Misc.addChatMessage(Component.literal("${e.type.toShortString()} '${nm(e)}' d=${"%.1f".format(e.distanceTo(p))} dy=${"%.1f".format(e.y - p.y)}"))
}
