package fishmod.utils.dungeon;

import fishmod.utils.config.values.Dungeons;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.PartyUtil;
import fishmod.utils.Constants;
import fishmod.utils.JsonUtility;
import fishmod.utils.Misc;
import fishmod.utils.Scheduler;
import fishmod.utils.events.Events;
import fishmod.utils.events.interfaces.PhaseEvent;
import fishmod.utils.events.interfaces.RunEndEvent;
import config.practical.hud.HUDComponent;
import config.practical.manager.ConfigValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Phase {

    private static final Pattern END_PATTERN = Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+)\\s*(\\(NEW RECORD!\\))?$");
    private static final Pattern SEARCH_PATTERN = Pattern.compile("^ ⏣ The Catacombs .*$");

    private static final Split DUMMY_SPLIT = new Split("test split", "if this is called idk", "if this is called idk", 43690, 0.0);

    private static final HashMap<String, ArrayList<Split>> FLOOR_SPLITS = JsonUtility.readSplits("/data/splits.json");

    private static final int DUMMY_SIZE = 10;
    public static final int SPLIT_LENGTH = 165;

    private static ArrayList<Split> currentSplits;
    private static int currentPhase = -1;
    private static String floor = "";

    private static boolean inFloor7 = false;
    private static boolean stormDead = false;
    private static boolean runOver = false;

    @ConfigValue
    public static boolean enableSplits = false;

    @ConfigValue
    public static boolean includeTotalTime = false;

    @ConfigValue
    public static boolean sendSplitInChat = false;

    @ConfigValue
    public static boolean onlyShowActivatedSplits = false;


    public static void init() {
        Events.ON_SERVER_TICK.register(() -> {
            if (currentSplits == null || runOver) return false;
            for (Split split : currentSplits) {
                split.tick();
            }
            return false;
        });

        Events.ON_LOCATION_CHANGE.register(newLocation -> {
            reset();
            return false;
        });

        Events.ON_GAME_MESSAGE.register(Phase::parseGameMessage);
        Events.ON_TEAM.register(Phase::detectFloor);
    }

    private static boolean detectFloor(String line) {
        if (floor != null) return false;

        Matcher matcher = SEARCH_PATTERN.matcher(line);
        if (!matcher.find()) return false;
        int start = line.indexOf("(");
        int end = line.indexOf(")");
        floor = line.substring(start + 1, end);

        currentSplits = FLOOR_SPLITS.get(floor);
        if (currentSplits != null) {
            for (Split split : currentSplits) {
                split.reset();
            }
        }
        if (floor != null) {
            if (floor.contains("7")) inFloor7 = true;
        }

        return false;
    }

    private static void reset() {
        currentSplits = null;
        floor = null;
        currentPhase = -1;
        inFloor7 = false;
        stormDead = false;
        runOver = false;
    }

    public static boolean parseGameMessage(Text message) {
        String string = message.getString();
        if (currentSplits == null) return false;
        if (runOver) return false;

        for (int i = 0; i < currentSplits.size(); i++) {

            Split currentSplit = currentSplits.get(i);
            if (currentSplit.ended()) continue;

            currentSplit.parseMessage(string);

            if (currentSplit.ended()) {
                if (sendSplitInChat) {
                    Misc.addChatMessage(currentSplit.createNameText().append(currentSplit.createTimeText()));
                }

                currentPhase = i + 1;
                Events.ON_PHASE_CHANGE.invoke(PhaseEvent::onPhaseChange);
            }

            //just for starting the run
            if (currentSplit.started() && currentPhase == -1) {
                currentPhase = i;
                Events.ON_PHASE_CHANGE.invoke(PhaseEvent::onPhaseChange);
            }

        }

        Matcher matcher = END_PATTERN.matcher(string);
        if (matcher.find()) {
            endRun();
        }

        if (inP2()) {
            if (string.equals("[BOSS] Storm: I should have known that I stood no chance.")) {
                stormDead = true;
            }
        }

        return false;
    }

    private static void endRun() {
        runOver = true;
        currentPhase = currentSplits.size();
        Scheduler.scheduleTask(Phase::printSplits, 2);
        Events.ON_RUN_END.invoke(RunEndEvent::onRunEnd);
    }

    public static String getFloor() { return floor; }

    private static void printSplits() {
        Misc.addChatMessage(Text.literal("§aSplits: "));
        for (Split split : currentSplits) {
            split.end();
            Misc.addChatMessage(split.createNameText().append(split.createTimeText()));
        }
        // Save this run to personal history
        RunHistory.saveSplits(floor, currentSplits);
        if (!currentSplits.isEmpty()) {
            double time = currentSplits.getLast().getTimeDiffrence();
            String formattedTime = Constants.DECIMAL_FORMAT.format(time);
            Text timeLost = Text.literal("§aApproximately §e" + formattedTime + "s §alost to lag.");
            Misc.addChatMessage(timeLost);

        }
    }

    public static int getPhase() {
        return currentPhase;
    }

    public static boolean isInFloor7() {
        return inFloor7;
    }

    public static boolean runStarted() {
        return currentPhase >= 0;
    }

    public static boolean runJustStarted() {
        return currentPhase == 0;
    }

    public static boolean inBoss() {
        return currentPhase > 3;
    }

    public static boolean inP1() {
        return currentPhase == 4 && inFloor7;
    }

    public static boolean inP2() {
        return currentPhase == 5 && inFloor7;
    }

    public static boolean stormDead() {
        return stormDead;
    }

    public static boolean inTerminals() {
        return currentPhase == 6 && inFloor7;
    }

    public static boolean inGoldorTunnel() {
        return currentPhase == 7 && inFloor7;
    }

    public static boolean inP3() {
        return (currentPhase == 6 || currentPhase == 7) && inFloor7;
    }

    public static boolean inP5() {
        return currentPhase == 9 && inFloor7;
    }

    public static boolean runOver() {
        return runOver;
    }


    public static double getPhaseTime(int index) {
        if (index < 0 ||index >= currentSplits.size()) return 0;
        return currentSplits.get(index).getRealTime();
    }

    // Splits panel. Rendered explicitly via Phase.renderHud (HudRenderCallback in FishModInit) — the
    // proven path every other FishMod HUD uses — so its condition-supplier is forced false to keep
    // practical-config's HudElementRegistry auto-render (unreliable here) from double-drawing it.
    // (The per-phase Maxor/Storm/Terminals timers live in the Floor 7 tab as F7Huds tick timers.)
    @ConfigValue
    public static HUDComponent splitTimer = new HUDComponent(0, 0, SPLIT_LENGTH, 100, 1, "Splits",
            () -> false,
            ((hudComponent, drawContext) -> {
                renderSplitRows(drawContext, hudComponent.getScaledX(), hudComponent.getScaledY());
            }), () -> enableSplits
    );

    /** Explicit HUD render for the splits panel (auto-render is disabled above). */
    public static void renderHud(net.minecraft.client.gui.DrawContext ctx) {
        if (enableSplits && runStarted()) {
            renderScaled(ctx, splitTimer, () -> renderSplitRows(ctx, splitTimer.getScaledX(), splitTimer.getScaledY()));
        }
    }

    private static void renderScaled(net.minecraft.client.gui.DrawContext ctx, HUDComponent c, Runnable draw) {
        org.joml.Matrix3x2fStack stack = ctx.getMatrices();
        stack.pushMatrix();
        stack.scale(c.getScale(), c.getScale());
        draw.run();
        stack.popMatrix();
    }

    /** Renders split rows + separator. Called by splitTimer HUD (with blade) and renderSplitsHud (standalone). */
    public static void renderSplitRows(net.minecraft.client.gui.DrawContext ctx, int x, int y) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        if (currentSplits != null) {
            int splitCount = currentSplits.size();
            if (!includeTotalTime) splitCount--;
            int height = 0;
            for (int i = 0; i < splitCount; i++) {
                Split split = currentSplits.get(i);
                if (onlyShowActivatedSplits && !(split.started() || split.ended())) continue;
                split.drawSplit(ctx, textRenderer, x, y + Constants.TEXT_HEIGHT * height, SPLIT_LENGTH);
                height++;
            }
            if (height > 0) {
                ctx.fill(x, y + Constants.TEXT_HEIGHT * height + 3,
                        x + SPLIT_LENGTH, y + Constants.TEXT_HEIGHT * height + 4, 0x44FFFFFF);
            }
        } else {
            for (int i = 0; i < DUMMY_SIZE; i++) {
                DUMMY_SPLIT.drawSplit(ctx, textRenderer, x, y + Constants.TEXT_HEIGHT * i, SPLIT_LENGTH);
            }
        }
    }

    /** Returns how many split rows are currently visible (for FishEstTotal snap position). */
    public static int getVisibleRowCount() {
        if (currentSplits == null) return 0;
        int splitCount = currentSplits.size();
        if (!includeTotalTime) splitCount--;
        if (!onlyShowActivatedSplits) return splitCount;
        int visible = 0;
        for (int i = 0; i < splitCount; i++) {
            Split s = currentSplits.get(i);
            if (s.started() || s.ended()) visible++;
        }
        return visible;
    }

    /** Direct HUD render for standalone mode (no blade / HUDComponent system). */
    public static void renderSplitsHud(net.minecraft.client.gui.DrawContext ctx, int x, int y) {
        if (!enableSplits || !runStarted()) return;
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;
        renderSplitRows(ctx, x, y);
    }
}
