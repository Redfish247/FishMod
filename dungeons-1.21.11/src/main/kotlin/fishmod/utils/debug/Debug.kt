package fishmod.utils.debug

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import config.practical.hud.HUDComponent
import fishmod.mixin.accessors.BossBarHudAccessor
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.text.Text
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
        registryAccess: CommandRegistryAccess
    ) {

        dispatcher.register(
            ClientCommandManager.literal("fm")
                .executes { _ ->
                    MinecraftClient.getInstance().send { MinecraftClient.getInstance().setScreen(fishmod.features.FishModScreen()) }
                    Constants.SUCCESS
                }
                .then(
                    ClientCommandManager.literal("bossbars")
                        .executes { _ ->
                            val mc = MinecraftClient.getInstance()
                            val accessor = mc.inGameHud.bossBarHud as BossBarHudAccessor
                            val bars = accessor.bossBars
                            if (bars == null || bars.isEmpty()) {
                                Misc.addChatMessage(Text.literal("§cNo boss bars active."))
                            } else {
                                bars.values.forEach { bar ->
                                    val stripped = bar.name.string.replace(Regex("§."), "").trim()
                                    Misc.addChatMessage(Text.literal("§eBar: §f\"" + stripped + "\" §7(" + String.format("%.1f%%", bar.percent * 100f) + ")"))
                                }
                            }
                            Constants.SUCCESS
                        }
                )
        )

        dispatcher.register(
            ClientCommandManager.literal("badev")
                .then(ClientCommandManager.literal("runInfo").executes { _ ->
                    sendRunInfo()
                    Constants.SUCCESS
                })

                .then(ClientCommandManager.literal("debug").executes { _ ->
                    sendDebug = !sendDebug
                    Misc.addChatMessage(Text.literal("Send debug: ").append(Misc.getStatusText(sendDebug)))
                    Constants.SUCCESS
                })

                .then(ClientCommandManager.literal("sound").executes { _ ->
                    sendSound = !sendSound
                    Misc.addChatMessage(Text.literal("Send Sound: ").append(Misc.getStatusText(sendSound)))
                    Constants.SUCCESS
                })

                .then(ClientCommandManager.literal("termInfo").executes { _ ->
                    termInfo = !termInfo
                    Misc.addChatMessage(Text.literal("Terminal info: ").append(Misc.getStatusText(termInfo)))
                    Constants.SUCCESS
                })

                .then(ClientCommandManager.literal("drawPositionBoxes").executes { _ ->
                    renderPositions = !renderPositions
                    Misc.addChatMessage(Text.literal("Render positons: ").append(Misc.getStatusText(renderPositions)))
                    Constants.SUCCESS
                })

                .then(ClientCommandManager.literal("testString").then(ClientCommandManager.argument("message", StringArgumentType.string()).executes { context ->
                    val message = StringArgumentType.getString(context, "message")
                    Events.ON_GAME_MESSAGE.invoke { gameMessageEvent -> gameMessageEvent.onGameMessage(Text.literal(message)) }
                    Constants.SUCCESS
                }))

                .then(
                    ClientCommandManager.literal("location")
                        .then(
                            ClientCommandManager.literal("current")
                                .executes { _ ->
                                    Misc.addChatMessage(Text.literal(Location.getCurrentLocation().toString()))
                                    Constants.SUCCESS
                                })

                        .then(
                            ClientCommandManager.literal("set")
                                .then(
                                    ClientCommandManager.argument("name", StringArgumentType.string())
                                        .executes { context ->
                                            val name = StringArgumentType.getString(context, "name").uppercase()
                                            val location = Location.getLocation(name)
                                            Location.changeLocation(location)
                                            Misc.addChatMessage(Text.literal("Swapped to location: " + location.name))
                                            Constants.SUCCESS
                                        }
                                )
                        )
                )

                .then(
                    ClientCommandManager.literal("chatNoti")
                        .then(
                            ClientCommandManager.literal("sendDebug")
                                .executes { _ ->
                                    sendNotiDebug = !sendNotiDebug
                                    Misc.addChatMessage(Text.literal("Send notification debug: ").append(Misc.getStatusText(sendNotiDebug)))
                                    Constants.SUCCESS
                                }

                        )
                )

                .then(
                    ClientCommandManager.literal("classes")
                        .executes { _ ->
                            DungeonClass.printClasses()
                            Constants.SUCCESS
                        }
                )
                .then(
                    ClientCommandManager.literal("currentClass")
                        .executes { _ ->
                            Misc.addChatMessage(Text.literal("Current class: " + DungeonClass.currentClass))
                            Constants.SUCCESS
                        }
                )
        )
    }


    @JvmStatic
    fun sendDebugMessage(text: Text) {
        if (sendDebug) {
            Misc.addChatMessage(text)
        }
    }

    @JvmStatic
    fun sendRunInfo() {
        Misc.addChatMessage(Text.literal("Phase: " + Phase.getPhase()))
        Misc.addChatMessage(Text.literal("Section: " + Section.getSection()))
        Misc.addChatMessage(Text.literal("Gateblown: " + Section.isGateBlownUp()))
        Misc.addChatMessage(Text.literal("In floor 7: " + Phase.isInFloor7()))
    }
}
