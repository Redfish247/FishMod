package fishmod.utils.debug;

import fishmod.features.FishModScreen;
import fishmod.mixin.accessors.BossBarHudAccessor;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.Misc;
import fishmod.utils.dungeon.DungeonClass;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.dungeon.Section;
import fishmod.utils.events.Events;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Debug {

    public static final Logger LOGGER = LoggerFactory.getLogger(Constants.NAMESPACE);
    private static boolean sendDebug = false;
    public static boolean sendSound = false;
    public static boolean termInfo = false;
    public static boolean renderPositions = false;
    public static boolean sendNotiDebug = false;

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register(Debug::registerCommands);
    }

    private static void registerCommands(@NotNull CommandDispatcher<FabricClientCommandSource> dispatcher,
                                         CommandBuildContext registryAccess) {

        dispatcher.register(ClientCommands.literal("fm")
                .executes(context -> {
                    Minecraft.getInstance().schedule(() -> Minecraft.getInstance().gui.setScreen(new fishmod.features.FishModScreen()));
                    return Constants.SUCCESS;
                })
                .then(ClientCommands.literal("bossbars")
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            BossBarHudAccessor accessor = (BossBarHudAccessor) mc.gui.hud.getBossOverlay();
                            var bars = accessor.getBossBars();
                            if (bars == null || bars.isEmpty()) {
                                Misc.addChatMessage(Component.literal("§cNo boss bars active."));
                            } else {
                                bars.values().forEach(bar -> {
                                    String stripped = bar.getName().getString().replaceAll("§.", "").trim();
                                    Misc.addChatMessage(Component.literal("§eBar: §f\"" + stripped + "\" §7(" + String.format("%.1f%%", bar.getProgress() * 100f) + ")"));
                                });
                            }
                            return Constants.SUCCESS;
                        })
                )
        );

        dispatcher.register(ClientCommands.literal("badev")
                .then(ClientCommands.literal("runInfo").executes(context -> {
                    sendRunInfo();
                    return Constants.SUCCESS;
                }))

                .then(ClientCommands.literal("debug").executes(context -> {
                    sendDebug = !sendDebug;
                    Misc.addChatMessage(Component.literal("Send debug: ").append(Misc.getStatusText(sendDebug)));
                    return Constants.SUCCESS;
                }))

                .then(ClientCommands.literal("sound").executes(context -> {
                    sendSound = !sendSound;
                    Misc.addChatMessage(Component.literal("Send Sound: ").append(Misc.getStatusText(sendSound)));
                    return Constants.SUCCESS;
                }))

                .then(ClientCommands.literal("termInfo").executes(context -> {
                    termInfo = !termInfo;
                    Misc.addChatMessage(Component.literal("Terminal info: ").append(Misc.getStatusText(termInfo)));
                    return Constants.SUCCESS;
                }))

                .then(ClientCommands.literal("drawPositionBoxes").executes(context -> {
                    renderPositions = !renderPositions;
                    Misc.addChatMessage(Component.literal("Render positons: ").append(Misc.getStatusText(renderPositions)));
                    return Constants.SUCCESS;
                }))

                .then(ClientCommands.literal("testString").then(ClientCommands.argument("message", StringArgumentType.string()).executes(context -> {
                    String message = StringArgumentType.getString(context, "message");
                    Events.ON_GAME_MESSAGE.invoke(gameMessageEvent -> gameMessageEvent.onGameMessage(Component.literal(message)));
                    return Constants.SUCCESS;
                })))

                .then(ClientCommands.literal("location")
                        .then(ClientCommands.literal("current")
                                .executes(context -> {
                                    Misc.addChatMessage(Component.literal(Location.getCurrentLocation().toString()));
                                    return Constants.SUCCESS;
                                        }))

                        .then(ClientCommands.literal("set")
                                .then(ClientCommands.argument("name", StringArgumentType.string())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name").toUpperCase();
                                            Location location =  Location.getLocation(name);
                                            Location.changeLocation(location);
                                            Misc.addChatMessage(Component.literal("Swapped to location: " + location.name()));
                                            return Constants.SUCCESS;
                                        })
                                )
                        )
                )

                .then(ClientCommands.literal("chatNoti")
                        .then(ClientCommands.literal("sendDebug")
                                .executes(context -> {
                                    sendNotiDebug = !sendNotiDebug;
                                    Misc.addChatMessage(Component.literal("Send notification debug: ").append(Misc.getStatusText(sendNotiDebug)));
                                    return Constants.SUCCESS;
                                })

                        )
                )

                .then(ClientCommands.literal("classes")
                        .executes(context -> {
                            DungeonClass.printClasses();
                            return Constants.SUCCESS;
                        })
                )
                .then(ClientCommands.literal("currentClass")
                        .executes(context -> {
                            Misc.addChatMessage(Component.literal("Current class: " + DungeonClass.currentClass));
                            return Constants.SUCCESS;
                        })
                )
        );
    }


    public static void sendDebugMessage(Component text) {
        if (sendDebug) {
            Misc.addChatMessage(text);
        }
    }

    public static void sendRunInfo() {
        Misc.addChatMessage(Component.literal("Phase: " + Phase.getPhase()));
        Misc.addChatMessage(Component.literal("Section: " + Section.getSection()));
        Misc.addChatMessage(Component.literal("Gateblown: " + Section.isGateBlownUp()));
        Misc.addChatMessage(Component.literal("In floor 7: " + Phase.isInFloor7()));
    }

}
