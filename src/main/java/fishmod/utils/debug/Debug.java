package fishmod.utils.debug;

import fishmod.features.FishModScreen;
import fishmod.mixin.accessors.BossBarHudAccessor;
import fishmod.utils.Constants;
import net.minecraft.client.MinecraftClient;
import fishmod.utils.Location;
import fishmod.utils.Misc;
import fishmod.utils.dungeon.DungeonClass;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.dungeon.Section;
import fishmod.utils.events.Events;
import net.minecraft.client.gui.hud.ClientBossBar;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
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
                                         CommandRegistryAccess registryAccess) {

        dispatcher.register(ClientCommandManager.literal("fm")
                .executes(context -> {
                    MinecraftClient.getInstance().send(() -> MinecraftClient.getInstance().setScreen(new fishmod.features.FishModScreen()));
                    return Constants.SUCCESS;
                })
                .then(ClientCommandManager.literal("bossbars")
                        .executes(context -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            BossBarHudAccessor accessor = (BossBarHudAccessor) mc.inGameHud.getBossBarHud();
                            var bars = accessor.getBossBars();
                            if (bars == null || bars.isEmpty()) {
                                Misc.addChatMessage(Text.literal("§cNo boss bars active."));
                            } else {
                                bars.values().forEach(bar -> {
                                    String stripped = bar.getName().getString().replaceAll("§.", "").trim();
                                    Misc.addChatMessage(Text.literal("§eBar: §f\"" + stripped + "\" §7(" + String.format("%.1f%%", bar.getPercent() * 100f) + ")"));
                                });
                            }
                            return Constants.SUCCESS;
                        })
                )
        );

        dispatcher.register(ClientCommandManager.literal("badev")
                .then(ClientCommandManager.literal("runInfo").executes(context -> {
                    sendRunInfo();
                    return Constants.SUCCESS;
                }))

                .then(ClientCommandManager.literal("debug").executes(context -> {
                    sendDebug = !sendDebug;
                    Misc.addChatMessage(Text.literal("Send debug: ").append(Misc.getStatusText(sendDebug)));
                    return Constants.SUCCESS;
                }))

                .then(ClientCommandManager.literal("sound").executes(context -> {
                    sendSound = !sendSound;
                    Misc.addChatMessage(Text.literal("Send Sound: ").append(Misc.getStatusText(sendSound)));
                    return Constants.SUCCESS;
                }))

                .then(ClientCommandManager.literal("termInfo").executes(context -> {
                    termInfo = !termInfo;
                    Misc.addChatMessage(Text.literal("Terminal info: ").append(Misc.getStatusText(termInfo)));
                    return Constants.SUCCESS;
                }))

                .then(ClientCommandManager.literal("drawPositionBoxes").executes(context -> {
                    renderPositions = !renderPositions;
                    Misc.addChatMessage(Text.literal("Render positons: ").append(Misc.getStatusText(renderPositions)));
                    return Constants.SUCCESS;
                }))

                .then(ClientCommandManager.literal("testString").then(ClientCommandManager.argument("message", StringArgumentType.string()).executes(context -> {
                    String message = StringArgumentType.getString(context, "message");
                    Events.ON_GAME_MESSAGE.invoke(gameMessageEvent -> gameMessageEvent.onGameMessage(Text.literal(message)));
                    return Constants.SUCCESS;
                })))

                .then(ClientCommandManager.literal("location")
                        .then(ClientCommandManager.literal("current")
                                .executes(context -> {
                                    Misc.addChatMessage(Text.literal(Location.getCurrentLocation().toString()));
                                    return Constants.SUCCESS;
                                        }))

                        .then(ClientCommandManager.literal("set")
                                .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name").toUpperCase();
                                            Location location =  Location.getLocation(name);
                                            Location.changeLocation(location);
                                            Misc.addChatMessage(Text.literal("Swapped to location: " + location.name()));
                                            return Constants.SUCCESS;
                                        })
                                )
                        )
                )

                .then(ClientCommandManager.literal("chatNoti")
                        .then(ClientCommandManager.literal("sendDebug")
                                .executes(context -> {
                                    sendNotiDebug = !sendNotiDebug;
                                    Misc.addChatMessage(Text.literal("Send notification debug: ").append(Misc.getStatusText(sendNotiDebug)));
                                    return Constants.SUCCESS;
                                })

                        )
                )

                .then(ClientCommandManager.literal("classes")
                        .executes(context -> {
                            DungeonClass.printClasses();
                            return Constants.SUCCESS;
                        })
                )
                .then(ClientCommandManager.literal("currentClass")
                        .executes(context -> {
                            Misc.addChatMessage(Text.literal("Current class: " + DungeonClass.currentClass));
                            return Constants.SUCCESS;
                        })
                )
        );
    }


    public static void sendDebugMessage(Text text) {
        if (sendDebug) {
            Misc.addChatMessage(text);
        }
    }

    public static void sendRunInfo() {
        Misc.addChatMessage(Text.literal("Phase: " + Phase.getPhase()));
        Misc.addChatMessage(Text.literal("Section: " + Section.getSection()));
        Misc.addChatMessage(Text.literal("Gateblown: " + Section.isGateBlownUp()));
        Misc.addChatMessage(Text.literal("In floor 7: " + Phase.isInFloor7()));
    }

}
