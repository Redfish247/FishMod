package fishmod.utils.debug

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

    @JvmField
    var sendNotiDebug = false

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
                    Minecraft.getInstance().schedule { Minecraft.getInstance().setScreen(fishmod.features.FishModScreen()) }
                    Constants.SUCCESS
                }
                .then(
                    ClientCommands.literal("bossbars")
                        .executes { _ ->
                            val mc = Minecraft.getInstance()
                            val accessor = mc.gui.bossOverlay as BossBarHudAccessor
                            val bars = accessor.bossBars
                            if (bars == null || bars.isEmpty()) {
                                Misc.addChatMessage(Component.literal("§cNo boss bars active."))
                            } else {
                                bars.values.forEach { bar ->
                                    val stripped = bar.name.string.replace(Regex("§."), "").trim()
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
