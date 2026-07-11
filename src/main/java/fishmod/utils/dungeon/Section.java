package fishmod.utils.dungeon;

import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.Misc;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.debug.Debug;
import fishmod.utils.events.Events;
import fishmod.utils.events.interfaces.SectionEvent;
import config.practical.hud.HUDComponent;
import config.practical.manager.ConfigValue;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class Section {
    public enum DisplayTerminalSplitsWhen {
        BOSS("Entire Boss"), TERMINALS_ONLY("Terminals");

        private final String label;

        DisplayTerminalSplitsWhen(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Split[] splits = {
            new Split("S1", "", "", 16755200, 0.0),
            new Split("S2", "", "", 16755200, 0.0),
            new Split("S3", "", "", 16755200, 0.0),
            new Split("S4", "", "", 16755200, 0.0),
            new Split("Total", "", "", Split.GREEN, 0.0),};
    private static final Pattern TERMINALS_DONE_PATTERN = Pattern.compile("^(\\w+) (activated|completed) a (terminal|device|lever)! \\((\\d)/(\\d)\\)$");

    public static int SPLIT_LENGTH = 120;
    private static final int TERM_PHASE_INDEX = 6;
    /** Number of per-section splits (S1-S4) at the front of {@link #splits}; the trailing "Total" split is separate. */
    private static final int SECTION_COUNT = 4;

    private static int currentSection = -1;
    private static int completed = 0;
    private static int total = 7;
    private static boolean gateBlownUp = false;

    @ConfigValue
    public static boolean enableTerminalSplits = false;

    @ConfigValue
    public static boolean includeTotalTime = false;

    @ConfigValue
    public static DisplayTerminalSplitsWhen displayTerminalSplitsWhen = DisplayTerminalSplitsWhen.TERMINALS_ONLY;

    public static void init() {
        Events.ON_GAME_MESSAGE.register(Section::parseMessage);
        Events.ON_LOCATION_CHANGE.register(newLocation -> {
            reset();
            return false;
        });
        Events.ON_PHASE_CHANGE.register(() -> {
            if (Phase.inP2()) {
                currentSection = 0;
            }

            if (Phase.inTerminals()) {
                if (Debug.termInfo) {
                    Misc.addChatMessage(Component.literal("Terminals started"));
                }
                currentSection = 1;
                splits[0].start();
                splits[SECTION_COUNT].start(); // Total: spans the whole terminals phase
            } else if (Phase.inGoldorTunnel()) {
                if (Debug.termInfo) {
                    Misc.addChatMessage(Component.literal("Terminals ended"));
                }
                currentSection = 5;
                endAllSections();
            }
            return false;
        });
        Events.ON_SERVER_TICK.register(() -> {
            if (currentSection < 1 || currentSection > 5) return false;
            for (Split split : splits) {
                split.tick();
            }
            return false;
        });
    }

    private static void reset() {
        currentSection = -1;
        resetSection();
        for (Split split : splits) {
            split.reset();
        }
    }

    private static void resetSection() {
        completed = 0;
        gateBlownUp = false;
    }

    private static void incrementSection() {
        resetSection();
        endSplit(currentSection);
        currentSection++;

        if (Debug.termInfo) {
            Misc.addChatMessage(Component.literal("section: " + currentSection));
        }

        Events.ON_SECTION_CHANGE.invoke(SectionEvent::onSection);
        startSplit(currentSection);
    }

    private static void endSplit(int section) {
        int index = section - 1;
        if (index < 0 || index >= SECTION_COUNT) return;
        splits[index].end();
    }

    private static void startSplit(int section) {
        int index = section - 1;
        if (index < 0 || index >= SECTION_COUNT) return;
        splits[index].start();
    }

    private static void endAllSections() {
        for (Split split : splits) {
            split.end();
        }
        if (Debug.termInfo) {
            Misc.addChatMessage(Component.literal("ending all sections"));
        }
        Events.ON_SECTION_CHANGE.invoke(SectionEvent::onSection);
    }

    private static boolean parseMessage(Component message) {
        if (!Phase.inTerminals()) return false;
        boolean shouldCancelMessage = false;

        String string = message.getString();
        Matcher matcher = TERMINALS_DONE_PATTERN.matcher(string);
        if (matcher.find()) {
            String name = matcher.group(1);
            String action = matcher.group(2);
            String objective = matcher.group(3);
            int currentCompleted;
            int totalNeeded;

            try {
                currentCompleted = Integer.parseInt(matcher.group(4));
                totalNeeded = Integer.parseInt(matcher.group(5));
            } catch (NumberFormatException e) {
                Debug.LOGGER.error("Failed to parse terminal message, {}", e.getMessage());
                return false;
            }

            if (Debug.termInfo) {
                Misc.addChatMessage(Component.literal("name:" + name + ":objective>" + objective + ":(" + currentCompleted + "/" + totalNeeded + ")"));
            }

            Events.ON_TERMINAL.invoke(terminalEvent -> terminalEvent.onComplete(name, action, objective, currentCompleted, totalNeeded));

            if (Floor7.terminalTimeStamps) {
                //have to do it like this because for some reason they have the color in the
                //Style object and not in the string literal
                List<Component> texts = message.getSiblings();
                if (!texts.isEmpty()) {
                    Misc.addChatMessage(Component.literal(name).setStyle(texts.getFirst().getStyle()).append(Component.literal(" §a" + action + " " + objective + "! (§c" + currentCompleted + "§a/" + totalNeeded + ") §8(§7" + getSectionTime() + "s §8| §7" + Phase.getPhaseTime(TERM_PHASE_INDEX) + "s§8)")));
                    shouldCancelMessage = true;
                }
            }

            if (shouldIncrement(currentCompleted)) {
                incrementSection();
                Misc.forceTitle(Component.empty(), message);
            } else {
                total = totalNeeded;
                completed = currentCompleted;
            }


        } else if (!gateBlownUp) {
            if (string.equals("The gate has been destroyed!")) {
                gateBlownUp = true;

                if (completed == total) {
                    incrementSection();
                }

                if (Floor7.terminalTimeStamps) {
                    Misc.addChatMessage(Component.literal("§aThe gate has been destroyed! §8(§7" + getSectionTime() + "s §8| §7" + Phase.getPhaseTime(TERM_PHASE_INDEX) + "s§8)"));
                    shouldCancelMessage = true;
                }
            }
        } else if (string.equals("The Core entrance is opening!")) {
            //so in "goldor tunnel" can be shown after terms are done
            currentSection = 5;
            endAllSections();
            Debug.sendDebugMessage(Component.literal("Core section"));
        }

        return shouldCancelMessage;
    }


    public static int getSection() {
        return currentSection;
    }

    public static boolean isGateBlownUp() {
        return gateBlownUp;
    }

    public static boolean inSection(int section) {
        if (section == 0 && (Phase.inP2() || Phase.inP3())) return true;
        return currentSection == section && Phase.inP3();
    }

    public static double getSectionTime() {
        int index = currentSection - 1;
        if (index < 0 || index >= SECTION_COUNT) return -1;
        return splits[index].getRealTime();
    }

    public static boolean display() {
        if (enableTerminalSplits && Location.inDungeon()) {

            return switch (displayTerminalSplitsWhen) {
                case BOSS -> Phase.inBoss();
                case TERMINALS_ONLY -> Phase.inTerminals();
            };

        }

        return false;
    }

    public static boolean shouldIncrement(int recentlyCompleted) {
        return (recentlyCompleted == total && gateBlownUp) || (recentlyCompleted < completed);
    }

    public static void render(HUDComponent component, GuiGraphicsExtractor context) {
        int x = component.getScaledX();
        int y = component.getScaledY();

        Font textRenderer = Minecraft.getInstance().font;

        int count = includeTotalTime ? splits.length : SECTION_COUNT;
        for (int i = 0; i < count; i++) {
            splits[i].drawSplit(context, textRenderer, x, y + Constants.TEXT_HEIGHT * i, SPLIT_LENGTH);
        }
    }

    // Rendered explicitly via F7Huds.renderHud (HudRenderCallback in FishModInit) — the proven path
    // every other FishMod HUD uses — so the condition-supplier is forced false to keep
    // practical-config's HudElementRegistry auto-render (unreliable here) from double-drawing it.
    @ConfigValue
    public static HUDComponent terminalSplits = new HUDComponent(0, 0, SPLIT_LENGTH, 50, 1, "Term splits", () -> false, Section::render, () -> enableTerminalSplits);
}
