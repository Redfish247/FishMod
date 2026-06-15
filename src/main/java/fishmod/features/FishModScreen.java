package fishmod.features;

import fishmod.utils.config.Config;
import fishmod.utils.config.FishConfig;
import fishmod.cosmetic.NickState;
import fishmod.features.BridgeBot;
import fishmod.utils.config.values.*;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.dungeon.Split;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Odin-inspired column layout. Transparent background, no header.
 * Left-click pill = toggle master (or open detail if no master).
 * Right-click pill = expand inline detail panel directly under it.
 */
public class FishModScreen extends Screen {

    // ----- colors (alpha-blended over the live world) -----
    static final int PILL_OFF        = 0xFF1B1D24;
    static final int PILL_ON         = 0xFF0D7377;
    static final int PILL_OFF_HOVER  = 0xFF252832;
    static final int PILL_ON_HOVER   = 0xFF119BA0;
    static final int PANEL_BG        = 0xFF15171C;
    static final int COL_HEADER_BG   = 0xFF15171C;
    static final int BORDER_COLOR    = 0xFF2A2D38;
    static final int TEXT_COLOR      = 0xFFFFFFFF;
    static final int SUBTEXT_COLOR   = 0xFF8B92A5;
    static final int ACCENT          = 0xFF0D7377;  // slate teal
    static final int ACCENT_HOVER    = 0xFF119BA0;

    // Menu text is rendered at this scale so longer labels stop getting cut off with "…".
    static final float TEXT_SCALE = 0.75f;

    // ----- open animation -----
    private final long openStartMs = System.currentTimeMillis();
    private static final float COL_STAGGER_MS = 55f;  // delay added per column (diagonal feel)
    private static final float ROW_STAGGER_MS = 34f;  // delay added per pill row within a column
    private static final float ROW_MS         = 210f; // each element's own reveal duration
    private static final float SLIDE_PX        = 9f;   // how far each element drops in from above

    private float elapsedMs() { return System.currentTimeMillis() - openStartMs; }

    /** Eased reveal for an element delayed by {@code delayMs}: returns progress 0..1 (easeOutCubic). */
    private float reveal(float delayMs) {
        float t = MathHelper.clamp((elapsedMs() - delayMs) / ROW_MS, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** Scales the alpha channel of an ARGB color by {@code a} (0..1). */
    static int fade(int argb, float a) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * MathHelper.clamp(a, 0f, 1f));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Draws menu text at TEXT_SCALE, vertically nudged to stay centered in its row slot. */
    static void st(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, String s, int x, int y, int color) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y + 1f);
        ctx.getMatrices().scale(TEXT_SCALE, TEXT_SCALE);
        ctx.drawText(tr, s, 0, 0, color, false);
        ctx.getMatrices().popMatrix();
    }

    /** Pixel width of text as drawn by {@link #st} (scaled). */
    static int stw(net.minecraft.client.font.TextRenderer tr, String s) {
        return (int) Math.ceil(tr.getWidth(s) * TEXT_SCALE);
    }

    /** Fills a rectangle with rounded (~2px) corners. */
    static void roundRect(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 4 || y2 - y1 < 4) { ctx.fill(x1, y1, x2, y2, color); return; }
        ctx.fill(x1 + 2, y1,     x2 - 2, y1 + 1, color); // top row (inset 2)
        ctx.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color); // 2nd row (inset 1)
        ctx.fill(x1,     y1 + 2, x2,     y2 - 2, color); // middle (full width)
        ctx.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color); // 2nd-last row (inset 1)
        ctx.fill(x1 + 2, y2 - 1, x2 - 2, y2,     color); // bottom row (inset 2)
    }
    static final int TOGGLE_ON       = ACCENT;
    static final int TOGGLE_OFF      = 0xFF2A2D38;
    static final int TOGGLE_TEXT     = 0xFFFFFFFF;
    static final int SLIDER_BG       = 0xFF1B1D24;
    static final int SLIDER_FILL     = ACCENT;

    // ----- sizes -----
    static final int PADDING        = 12;
    static final int COL_W          = 150;
    static final int COL_GAP        = 10;
    static final int COL_HEADER_H   = 18;
    static final int PILL_H         = 20;
    static final int PILL_GAP       = 1;
    static final int ITEM_HEIGHT    = 20;
    static final int TOGGLE_W       = 36;
    static final int TOGGLE_H       = 14;
    static final int SLIDER_W       = 64;
    static final int SLIDER_H       = 5;
    static final int INPUT_W        = 70;
    static final int INPUT_H        = 14;
    static final int SUBCAT_HEIGHT  = 13;
    static final int SEARCH_H       = 22;
    static final int SCALE_BTN_W    = 28;

    // Minecraft &-code → RGB, for the legend at the bottom of the screen.
    private static final int[][] CODE_COLORS = {
            {'0', 0x000000}, {'1', 0x0000AA}, {'2', 0x00AA00}, {'3', 0x00AAAA},
            {'4', 0xAA0000}, {'5', 0xAA00AA}, {'6', 0xFFAA00}, {'7', 0xAAAAAA},
            {'8', 0x555555}, {'9', 0x5555FF}, {'a', 0x55FF55}, {'b', 0x55FFFF},
            {'c', 0xFF5555}, {'d', 0xFF55FF}, {'e', 0xFFFF55}, {'f', 0xFFFFFF},
    };
    // Format codes shown as live styled samples.
    private static final String[][] CODE_FORMATS = {
            {"&l", "§lBold"}, {"&o", "§oItalic"}, {"&n", "§nUnder"},
            {"&m", "§mStrike"}, {"&k", "§kMagic"}, {"&r", "§rReset"}, {"&#rrggbb", "Hex"},
    };

    // ----- state -----
    private final List<Column> columns = new ArrayList<>();
    private Feature selectedFeature = null;
    private int detailScroll = 0;
    private String searchText = "";
    private boolean searchFocused = false;
    private Setting activeSlider = null;
    private int activeSliderX = 0;
    private Setting activeInput = null;
    private ColorPickerSetting activePicker = null;
    private TextFieldWidget searchField;

    public FishModScreen() {
        super(Text.literal("FishMod"));
        buildColumns();
    }

    // -----------------------------------------------------------------------------------
    // Column / feature graph
    // -----------------------------------------------------------------------------------
    private void buildColumns() {
        // ===== Dungeon =====
        Column dungeon = new Column("Dungeon");
        dungeon.features.add(new Feature("Dungeon Score",
                () -> FishSettings.dungeonScoreEnabled, v -> FishSettings.dungeonScoreEnabled = v));
        dungeon.features.add(new Feature("Puzzle Overlay",
                () -> FishSettings.showPuzzles, v -> FishSettings.showPuzzles = v));
        {
            Feature f = new Feature("Death Message",
                    () -> FishSettings.deathMessageEnabled, v -> FishSettings.deathMessageEnabled = v);
            f.sub.add(new InputSetting("Template", "",
                    () -> FishSettings.deathMessageTemplate, v -> FishSettings.deathMessageTemplate = v));
            f.sub.add(new ToggleSetting("To Party", "",
                    () -> FishSettings.deathMessageToParty, v -> FishSettings.deathMessageToParty = v));
            dungeon.features.add(f);
        }
        dungeon.features.add(new Feature("Send Lag to Party",
                () -> FishSettings.sendLagToParty, v -> FishSettings.sendLagToParty = v));
        {
            Feature f = new Feature("Splits",
                    () -> Phase.enableSplits, v -> Phase.enableSplits = v);
            f.sub.add(new ToggleSetting("Total Time", "",
                    () -> Phase.includeTotalTime, v -> Phase.includeTotalTime = v));
            f.sub.add(new ToggleSetting("Send in Chat", "",
                    () -> Phase.sendSplitInChat, v -> Phase.sendSplitInChat = v));
            f.sub.add(new DropdownSetting<>("Tick Timer", "",
                    Split.TimerType.values(), () -> Split.timerType, v -> Split.timerType = v));
            f.sub.add(new ToggleSetting("Activated Only", "",
                    () -> Phase.onlyShowActivatedSplits, v -> Phase.onlyShowActivatedSplits = v));
            dungeon.features.add(f);
        }
        {
            Feature f = new Feature("Session Stats",
                    () -> FishSettings.sessionStatsEnabled, v -> FishSettings.sessionStatsEnabled = v);
            f.sub.add(new ToggleSetting("In Dungeon", "",
                    () -> FishSettings.sessionStatsInDungeon, v -> FishSettings.sessionStatsInDungeon = v));
            f.sub.add(new ToggleSetting("In D Hub", "",
                    () -> FishSettings.sessionStatsInDungeonHub, v -> FishSettings.sessionStatsInDungeonHub = v));
            f.sub.add(new ToggleSetting("Reset Relog", "",
                    () -> FishSettings.sessionStatsResetOnRelog, v -> FishSettings.sessionStatsResetOnRelog = v));
            dungeon.features.add(f);
        }
        {
            Feature f = new Feature("Croesus Overlay",
                    () -> FishSettings.croesusOverlayEnabled, v -> FishSettings.croesusOverlayEnabled = v);
            f.sub.add(new ToggleSetting("Hide in Dungeon", "",
                    () -> FishSettings.croesusOverlayHideInDungeon, v -> FishSettings.croesusOverlayHideInDungeon = v));
            dungeon.features.add(f);
        }
        {
            Feature f = new Feature("Croesus Loot", null, null);
            f.sub.add(new ButtonSetting("Open Loot", "",
                    () -> MinecraftClient.getInstance().setScreen(
                            new fishmod.features.croesus.CroesusLootScreen(MinecraftClient.getInstance().currentScreen))));
            f.sub.add(new DropdownSetting<>("Price", "",
                    FishSettings.PriceMode.values(),
                    () -> FishSettings.trackerPriceModeEnum,
                    v -> { FishSettings.trackerPriceModeEnum = v; fishmod.features.croesus.CroesusPrices.applyPriceMode(); }));
            dungeon.features.add(f);
        }
        {
            Feature f = new Feature("Simon Says",
                    () -> FishSettings.simonSaysEnabled, v -> FishSettings.simonSaysEnabled = v);
            f.sub.add(new ToggleSetting("Show HUD", "",
                    () -> FishSettings.simonSaysHudEnabled, v -> FishSettings.simonSaysHudEnabled = v));
            f.sub.add(new ToggleSetting("To Party", "",
                    () -> FishSettings.simonSaysPartyChat, v -> FishSettings.simonSaysPartyChat = v));
            f.sub.add(new ToggleSetting("Fail Msg", "",
                    () -> FishSettings.simonSaysFailEnabled, v -> FishSettings.simonSaysFailEnabled = v));
            f.sub.add(new InputSetting("Fail Text", "",
                    () -> FishSettings.simonSaysFailMessage, v -> FishSettings.simonSaysFailMessage = v));
            dungeon.features.add(f);
        }
        columns.add(dungeon);

        // ===== Trackers =====
        Column trackers = new Column("Trackers");
        trackers.features.add(new Feature("Slayer XP Tracker",
                () -> FishSettings.slayerXpEnabled, v -> FishSettings.slayerXpEnabled = v));
        trackers.features.add(new Feature("Skill XP Tracker",
                () -> FishSettings.skillTrackerEnabled, v -> FishSettings.skillTrackerEnabled = v));
        {
            Feature f = new Feature("Powder Tracker",
                    () -> FishSettings.powderTrackerEnabled, v -> FishSettings.powderTrackerEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "", FishSettings.PriceMode.values(),
                    () -> FishSettings.powderPriceMode, v -> FishSettings.powderPriceMode = v));
            trackers.features.add(f);
        }
        {
            Feature f = new Feature("Farming Tracker",
                    () -> FishSettings.farmingTrackerEnabled, v -> FishSettings.farmingTrackerEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "", FishSettings.PriceMode.values(),
                    () -> FishSettings.farmingPriceMode, v -> FishSettings.farmingPriceMode = v));
            trackers.features.add(f);
        }
        {
            Feature f = new Feature("Harvest Feast Tracker",
                    () -> FishSettings.harvestFeastEnabled, v -> FishSettings.harvestFeastEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "", FishSettings.PriceMode.values(),
                    () -> FishSettings.harvestFeastPriceMode, v -> FishSettings.harvestFeastPriceMode = v));
            trackers.features.add(f);
        }
        {
            Feature f = new Feature("Mining Tracker",
                    () -> FishSettings.miningTrackerEnabled, v -> FishSettings.miningTrackerEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "", FishSettings.PriceMode.values(),
                    () -> FishSettings.miningPriceMode, v -> FishSettings.miningPriceMode = v));
            trackers.features.add(f);
        }
        trackers.features.add(new Feature("Trophy Frogs",
                () -> FishSettings.trophyFrogEnabled, v -> FishSettings.trophyFrogEnabled = v));
        columns.add(trackers);

        // ===== QOL =====
        Column qol = new Column("QOL");
        qol.features.add(new Feature("Rarity Hotbar",
                () -> Visual.itemRarityBackground, v -> Visual.itemRarityBackground = v));
        {
            Feature f = new Feature("Cooldown Overlay",
                    () -> FishSettings.cooldownOverlayEnabled, v -> FishSettings.cooldownOverlayEnabled = v);
            f.sub.add(new ToggleSetting("Show Number", "",
                    () -> FishSettings.cooldownShowText, v -> FishSettings.cooldownShowText = v));
            f.sub.add(new ToggleSetting("Under 3s Only", "",
                    () -> FishSettings.cooldownOnlyUnder3s, v -> FishSettings.cooldownOnlyUnder3s = v));
            f.sub.add(new ToggleSetting("In Inventory", "",
                    () -> FishSettings.cooldownInInventory, v -> FishSettings.cooldownInInventory = v));
            qol.features.add(f);
        }
        {
            Feature f = new Feature("Pet HUD",
                    () -> FishSettings.petHudEnabled, v -> FishSettings.petHudEnabled = v);
            f.sub.add(new ToggleSetting("Show Level", "",
                    () -> FishSettings.petHudShowLevel, v -> FishSettings.petHudShowLevel = v));
            f.sub.add(new ToggleSetting("Fade Idle", "",
                    () -> FishSettings.petHudFadeIdle, v -> FishSettings.petHudFadeIdle = v));
            f.sub.add(new SliderIntSetting("Fade ms", "",
                    () -> FishSettings.petHudFadeMs, v -> FishSettings.petHudFadeMs = v, 1000, 30000));
            qol.features.add(f);
        }
        {
            Feature f = new Feature("Soulflow HUD",
                    () -> FishSettings.soulflowHudEnabled, v -> FishSettings.soulflowHudEnabled = v);
            f.sub.add(new InputIntSetting("Warning", "",
                    () -> FishSettings.soulflowWarningThreshold, v -> FishSettings.soulflowWarningThreshold = v));
            f.sub.add(new ToggleSetting("Missing Warn", "",
                    () -> FishSettings.soulflowMissingNotifier, v -> FishSettings.soulflowMissingNotifier = v));
            qol.features.add(f);
        }
        {
            Feature f = new Feature("Bridge Bot",
                    () -> FishSettings.bridgeBotEnabled, v -> FishSettings.bridgeBotEnabled = v);
            f.sub.add(new InputSetting("Bot IGN", "",
                    () -> FishSettings.bridgeBotName,
                    v -> { FishSettings.bridgeBotName = v; BridgeBot.rebuildPattern(); }));
            qol.features.add(f);
        }
        qol.features.add(new Feature("Fire Freeze Timer",
                () -> FishSettings.fireFreezeTimerEnabled, v -> FishSettings.fireFreezeTimerEnabled = v));
        columns.add(qol);

        // ===== Chat/Misc =====
        Column misc = new Column("Chat/Misc");
        {
            // Recolors your real username with a Start→End gradient. Custom names are no longer
            // allowed (color only) — toggling on applies the gradient, off restores the plain IGN.
            Feature f = new Feature("Name Color",
                    NickState::isActive,
                    v -> { if (!v) NickState.reset(); else NickState.applyFromSettings(); });
            f.sub.add(new LimitedInputSetting("Custom Name", "", 18,
                    () -> FishSettings.nickCustomName,
                    v -> { FishSettings.nickCustomName = v == null ? "" : v;
                           if (NickState.isActive()) NickState.applyFromSettings(); }));
            f.sub.add(new DropdownSetting<>("Color Mode", "", new String[]{"GRADIENT", "SOLID"},
                    () -> FishSettings.nickColorMode,
                    v -> { FishSettings.nickColorMode = v; if (NickState.isActive()) NickState.applyFromSettings(); }));
            f.sub.add(new ColorPickerSetting("Color", "",
                    () -> FishSettings.nickColorStart,
                    v -> { FishSettings.nickColorStart = v; if (NickState.isActive()) NickState.applyFromSettings(); }));
            // Second picker is only shown in Gradient mode.
            f.sub.add(new ConditionalColorPickerSetting("End Color", "",
                    () -> "GRADIENT".equalsIgnoreCase(FishSettings.nickColorMode),
                    () -> FishSettings.nickColorEnd,
                    v -> { FishSettings.nickColorEnd = v; if (NickState.isActive()) NickState.applyFromSettings(); }));
            f.sub.add(new ToggleSetting("See Others", "",
                    () -> FishSettings.remoteNicksEnabled, v -> FishSettings.remoteNicksEnabled = v));
            misc.features.add(f);
        }
        // Show other mod users' custom item/armor cosmetics (dye, trim, model, name, stars) on their
        // worn armor + held items. Your own customizations still work locally regardless of this.
        {
            Feature f = new Feature("See Others' Items",
                    () -> FishSettings.remoteItemsEnabled,
                    v -> { FishSettings.remoteItemsEnabled = v;
                           if (v) fishmod.cosmetic.RemoteSync.forceSync();
                           else fishmod.cosmetic.RemoteItems.clearAll(); });
            misc.features.add(f);
        }
        // Show your own nametag above your head (with [level] + emblem). Height adjustable; text size
        // is fixed by ImmediatelyFast so there's no scale control.
        {
            Feature f = new Feature("Nametag",
                    () -> FishSettings.nickPreviewEnabled, v -> FishSettings.nickPreviewEnabled = v);
            f.sub.add(new SliderDoubleSetting("Height", "",
                    () -> FishSettings.nickPreviewYOffset, v -> FishSettings.nickPreviewYOffset = v, -1.5, 1.0));
            misc.features.add(f);
        }
        {
            Feature f = new Feature("Warp Map",
                    () -> FishSettings.warpMapHudEnabled, v -> FishSettings.warpMapHudEnabled = v);
            f.sub.add(new ColorSetting("Dot Color", "",
                    () -> FishSettings.warpMapDotColor, v -> FishSettings.warpMapDotColor = v));
            misc.features.add(f);
        }
        {
            Feature f = new Feature("Mod Prefix",
                    () -> FishSettings.modPrefixEnabled, v -> FishSettings.modPrefixEnabled = v);
            f.sub.add(new InputSetting("Prefix", "",
                    () -> FishSettings.modPrefix,
                    v -> FishSettings.modPrefix = (v != null && v.length() > 10) ? v.substring(0, 10) : v));
            misc.features.add(f);
        }
        misc.features.add(new Feature("Auto Meow",
                () -> FishSettings.chatMeow, v -> FishSettings.chatMeow = v));
        {
            Feature f = new Feature("Compact Tab",
                    () -> FishSettings.compactTabEnabled, v -> FishSettings.compactTabEnabled = v);
            f.sub.add(new SliderIntSetting("Opacity %", "",
                    () -> FishSettings.compactTabOpacity, v -> FishSettings.compactTabOpacity = v, 0, 100));
            misc.features.add(f);
        }
        misc.features.add(new Feature("Smart Copy Chat",
                () -> FishSettings.smartCopyChat, v -> FishSettings.smartCopyChat = v));
        // Party Commands + Chat Channels pinned to the bottom.
        {
            Feature f = new Feature("Party Commands", null, null);
            f.sub.add(new ToggleSetting(".ai", "", () -> FishSettings.pcAllinvite, v -> FishSettings.pcAllinvite = v));
            f.sub.add(new ToggleSetting(".pb", "", () -> FishSettings.pcPb, v -> FishSettings.pcPb = v));
            f.sub.add(new ToggleSetting(".cata", "", () -> FishSettings.pcCata, v -> FishSettings.pcCata = v));
            f.sub.add(new ToggleSetting(".rtca", "", () -> FishSettings.pcRtca, v -> FishSettings.pcRtca = v));
            f.sub.add(new ToggleSetting(".rtc", "", () -> FishSettings.pcRtc, v -> FishSettings.pcRtc = v));
            f.sub.add(new ToggleSetting(".dprofit", "", () -> FishSettings.pcDprofit, v -> FishSettings.pcDprofit = v));
            f.sub.add(new ToggleSetting(".corpse", "", () -> FishSettings.pcCorpse, v -> FishSettings.pcCorpse = v));
            f.sub.add(new ToggleSetting(".f# / .m#", "", () -> FishSettings.pcJoinFloor, v -> FishSettings.pcJoinFloor = v));
            f.sub.add(new ToggleSetting(".fps", "", () -> FishSettings.pcFps, v -> FishSettings.pcFps = v));
            f.sub.add(new ToggleSetting(".tps", "", () -> FishSettings.pcTps, v -> FishSettings.pcTps = v));
            f.sub.add(new ToggleSetting(".ping", "", () -> FishSettings.pcPing, v -> FishSettings.pcPing = v));
            f.sub.add(new ToggleSetting(".secrets", "", () -> FishSettings.pcSecrets, v -> FishSettings.pcSecrets = v));
            f.sub.add(new ToggleSetting(".runs", "", () -> FishSettings.pcRuns, v -> FishSettings.pcRuns = v));
            f.sub.add(new ToggleSetting(".d", "", () -> FishSettings.pcDisband, v -> FishSettings.pcDisband = v));
            f.sub.add(new ToggleSetting(".mp", "", () -> FishSettings.pcMp, v -> FishSettings.pcMp = v));
            f.sub.add(new ToggleSetting(".collection", "", () -> FishSettings.pcCollection, v -> FishSettings.pcCollection = v));
            f.sub.add(new ToggleSetting(".nw", "", () -> FishSettings.pcNw, v -> FishSettings.pcNw = v));
            f.sub.add(new ToggleSetting(".bank", "", () -> FishSettings.pcBank, v -> FishSettings.pcBank = v));
            f.sub.add(new ToggleSetting(".powder", "", () -> FishSettings.pcPowder, v -> FishSettings.pcPowder = v));
            f.sub.add(new ToggleSetting(".level", "", () -> FishSettings.pcLevel, v -> FishSettings.pcLevel = v));
            f.sub.add(new ToggleSetting(".farming", "", () -> FishSettings.pcFarming, v -> FishSettings.pcFarming = v));
            f.sub.add(new ToggleSetting(".nuc", "", () -> FishSettings.pcNuc, v -> FishSettings.pcNuc = v));
            f.sub.add(new ToggleSetting(".help / .?", "", () -> FishSettings.pcHelp, v -> FishSettings.pcHelp = v));
            f.sub.add(new ToggleSetting("Party Actions", "", () -> FishSettings.pcPartyActions, v -> FishSettings.pcPartyActions = v));
            misc.features.add(f);
        }
        {
            Feature f = new Feature("Chat Channels", null, null);
            f.sub.add(new ToggleSetting("Personal Messages", "", () -> FishSettings.chatPrivate, v -> FishSettings.chatPrivate = v));
            f.sub.add(new ToggleSetting("Party", "", () -> FishSettings.chatParty, v -> FishSettings.chatParty = v));
            f.sub.add(new ToggleSetting("Guild", "", () -> FishSettings.chatGuild, v -> FishSettings.chatGuild = v));
            f.sub.add(new ToggleSetting("All", "", () -> FishSettings.chatAll, v -> FishSettings.chatAll = v));
            misc.features.add(f);
        }
        columns.add(misc);
    }

    // -----------------------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------------------
    private int totalColsWidth() {
        return columns.size() * COL_W + (columns.size() - 1) * COL_GAP;
    }
    private int colsStartX() {
        return Math.max(PADDING, (this.width - totalColsWidth()) / 2);
    }
    private int colsTopY()   { return PADDING; }
    private int colsBottomY() { return this.height - PADDING * 2 - SEARCH_H; }
    private int colX(int i)  { return colsStartX() + i * (COL_W + COL_GAP); }

    private int detailHeightForSelected() {
        if (selectedFeature == null) return 0;
        int total = 0;
        for (Setting s : selectedFeature.sub) total += s.getHeight();
        return total + 8;
    }

    // -----------------------------------------------------------------------------------
    // Transparent background: no-op
    // -----------------------------------------------------------------------------------
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override
    public void renderInGameBackground(DrawContext ctx) { }

    // -----------------------------------------------------------------------------------
    // Render
    // -----------------------------------------------------------------------------------
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        String filter = searchText.toLowerCase();
        for (int i = 0; i < columns.size(); i++) {
            renderColumn(ctx, columns.get(i), colX(i), colsTopY(), mouseX, mouseY, filter, i);
        }
        if (selectedFeature != null && "Nickname".equals(selectedFeature.name)) renderColorKey(ctx);
        renderBottomBar(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderColumn(DrawContext ctx, Column col, int x, int y, int mouseX, int mouseY, String filter, int colIndex) {
        float colDelay = colIndex * COL_STAGGER_MS;

        // Header strip (first thing to reveal in the column)
        float hr = reveal(colDelay);
        int hdy = Math.round((1f - hr) * -SLIDE_PX);
        ctx.fill(x, y + hdy, x + COL_W, y + COL_HEADER_H + hdy, fade(COL_HEADER_BG, hr));
        ctx.fill(x, y + COL_HEADER_H - 1 + hdy, x + COL_W, y + COL_HEADER_H + hdy, fade(BORDER_COLOR, hr));
        int titleX = x + (COL_W - stw(this.textRenderer, col.name)) / 2;
        st(ctx, this.textRenderer, col.name, titleX, y + (COL_HEADER_H - 8) / 2 + hdy, fade(TEXT_COLOR, hr));

        int py = y + COL_HEADER_H + 3;
        int colBottom = colsBottomY();
        int rowIndex = 0;
        for (Feature f : col.features) {
            if (!filter.isEmpty() && !f.name.toLowerCase().contains(filter)) continue;
            if (py + PILL_H > colBottom) break;

            // Per-row staggered reveal: fade in + drop down a few px into place.
            float rr = reveal(colDelay + (rowIndex + 1) * ROW_STAGGER_MS);
            rowIndex++;
            int rdy = Math.round((1f - rr) * -SLIDE_PX);

            boolean on = f.hasMaster() && f.get.get();
            boolean hov = mouseX >= x && mouseX <= x + COL_W && mouseY >= py && mouseY <= py + PILL_H;
            int bg = on ? (hov ? PILL_ON_HOVER : PILL_ON) : (hov ? PILL_OFF_HOVER : PILL_OFF);
            roundRect(ctx, x, py + rdy, x + COL_W, py + PILL_H + rdy, fade(bg, rr));

            // Pill label, truncated to column width
            String label = f.name;
            int maxW = COL_W - 12;
            if (stw(this.textRenderer, label) > maxW) {
                label = this.textRenderer.trimToWidth(label, (int) ((maxW - 4) / TEXT_SCALE)) + "…";
            }
            int tx = x + 6;
            int ty = py + (PILL_H - 8) / 2 + rdy;
            st(ctx, this.textRenderer, label, tx, ty, fade(TEXT_COLOR, rr));
            py += PILL_H + PILL_GAP;

            // Inline detail panel right under the selected pill
            if (f == selectedFeature && !f.sub.isEmpty()) {
                int dH = Math.min(detailHeightForSelected(), colBottom - py);
                if (dH > 8) {
                    ctx.fill(x, py, x + COL_W, py + dH, PANEL_BG);
                    ctx.fill(x, py, x + COL_W, py + 1, BORDER_COLOR);
                    ctx.fill(x, py + dH - 1, x + COL_W, py + dH, BORDER_COLOR);
                    ctx.fill(x, py, x + 1, py + dH, BORDER_COLOR);
                    ctx.fill(x + COL_W - 1, py, x + COL_W, py + dH, BORDER_COLOR);

                    ctx.enableScissor(x + 1, py + 1, x + COL_W - 1, py + dH - 1);
                    int leftX = x + 4;
                    int rightX = x + COL_W - 4;
                    int sy = py + 4 - detailScroll;
                    for (Setting s : f.sub) {
                        int sh = s.getHeight();
                        if (sy + sh < py || sy > py + dH) { sy += sh; continue; }
                        if (!(s instanceof SubcategoryHeader)) {
                            // Truncated row name on the left
                            int nameMaxW = (rightX - leftX) - 78;
                            if (nameMaxW < 30) nameMaxW = 30;
                            String name = s.name;
                            if (stw(this.textRenderer, name) > nameMaxW) {
                                name = this.textRenderer.trimToWidth(name, (int) ((nameMaxW - 4) / TEXT_SCALE)) + "…";
                            }
                            st(ctx, this.textRenderer, name, leftX + 2, sy + (sh - 8) / 2, TEXT_COLOR);
                        }
                        s.render(ctx, leftX, rightX, sy, mouseX, mouseY, this.textRenderer);
                        sy += sh;
                    }
                    ctx.disableScissor();
                    py += dH + PILL_GAP;
                }
            }
        }
    }

    /** Readable color-code key, shown in its own panel under the Nickname section when expanded. */
    private void renderColorKey(DrawContext ctx) {
        var tr = this.textRenderer;
        int lineH = 12;
        int cols = 2;                       // color swatches per row (narrow so it fits the margin)
        int cellW = 48;                     // swatch + " &x" label
        int padX = 8, padY = 6;
        int panelW = padX * 2 + cols * cellW;
        int colorRows = (CODE_COLORS.length + cols - 1) / cols;
        int fmtRows = CODE_FORMATS.length;  // one per row
        int panelH = padY * 2 + lineH        // title
                + colorRows * lineH
                + 4                          // gap
                + fmtRows * lineH;

        // Place it in the empty margin to the RIGHT of the last column so it never covers a tab.
        // If there isn't room on the right, fall back to the left margin.
        int colsRight = colX(columns.size() - 1) + COL_W;
        int px = colsRight + COL_GAP;
        if (px + panelW > this.width - PADDING) {
            int leftMargin = colsStartX() - PADDING;
            px = (leftMargin >= panelW + COL_GAP) ? colsStartX() - COL_GAP - panelW : this.width - PADDING - panelW;
            if (px < PADDING) px = PADDING;
        }
        int py = colsTopY();
        if (py + panelH > this.height - PADDING) py = this.height - PADDING - panelH;

        // Panel background + border.
        ctx.fill(px, py, px + panelW, py + panelH, PANEL_BG);
        ctx.fill(px, py, px + panelW, py + 1, ACCENT);
        ctx.fill(px, py + panelH - 1, px + panelW, py + panelH, ACCENT);
        ctx.fill(px, py, px + 1, py + panelH, BORDER_COLOR);
        ctx.fill(px + panelW - 1, py, px + panelW, py + panelH, BORDER_COLOR);

        int ox = px + padX, oy = py + padY;
        ctx.drawText(tr, "Color Codes", ox, oy, TEXT_COLOR, false);
        oy += lineH;

        int swatch = 8;
        for (int i = 0; i < CODE_COLORS.length; i++) {
            // Column-major: left column &0-&7, right column &8-&f (the familiar code chart).
            int r = i % colorRows, c = i / colorRows;
            int x = ox + c * cellW;
            int yy = oy + r * lineH;
            ctx.fill(x, yy, x + swatch, yy + swatch, 0xFF000000 | CODE_COLORS[i][1]);
            ctx.fill(x, yy, x + swatch, yy + 1, BORDER_COLOR);
            ctx.fill(x, yy + swatch - 1, x + swatch, yy + swatch, BORDER_COLOR);
            ctx.drawText(tr, "&" + (char) CODE_COLORS[i][0], x + swatch + 3, yy, TEXT_COLOR, false);
        }
        oy += colorRows * lineH + 4;

        // Format codes as live styled samples, one per row.
        for (int i = 0; i < CODE_FORMATS.length; i++) {
            int yy = oy + i * lineH;
            ctx.drawText(tr, CODE_FORMATS[i][0], ox, yy, ACCENT, false);
            ctx.drawText(tr, CODE_FORMATS[i][1], ox + tr.getWidth(CODE_FORMATS[i][0]) + 3, yy, TEXT_COLOR, false);
        }
    }

    private void renderBottomBar(DrawContext ctx, int mouseX, int mouseY) {
        int by = this.height - PADDING - SEARCH_H;
        int barW = Math.min(360, this.width - PADDING * 2 - SCALE_BTN_W - 6);
        int bx = (this.width - barW - SCALE_BTN_W - 6) / 2;
        if (searchField == null) {
            searchField = new TextFieldWidget(this.textRenderer, bx + 4, by + 4, barW - 8, SEARCH_H - 6, Text.empty());
            searchField.setMaxLength(64);
            searchField.setDrawsBackground(false);
            searchField.setChangedListener(s -> searchText = s);
        } else {
            searchField.setX(bx + 4);
            searchField.setY(by + 4);
            searchField.setWidth(barW - 8);
        }
        // Bottom bar reveals after the columns have begun cascading in.
        float br = reveal(columns.size() * COL_STAGGER_MS + 60f);
        ctx.fill(bx, by, bx + barW, by + SEARCH_H, fade(COL_HEADER_BG, br));
        ctx.fill(bx, by, bx + barW, by + 1, fade(searchFocused ? ACCENT : BORDER_COLOR, br));
        ctx.fill(bx, by + SEARCH_H - 1, bx + barW, by + SEARCH_H, fade(searchFocused ? ACCENT : BORDER_COLOR, br));
        ctx.fill(bx, by, bx + 1, by + SEARCH_H, fade(BORDER_COLOR, br));
        ctx.fill(bx + barW - 1, by, bx + barW, by + SEARCH_H, fade(BORDER_COLOR, br));
        if (searchText.isEmpty() && !searchFocused) {
            st(ctx, this.textRenderer, "Search here...", bx + 6, by + (SEARCH_H - 8) / 2, fade(SUBTEXT_COLOR, br));
        } else {
            searchField.render(ctx, mouseX, mouseY, 0);
        }

        // Scale icon → opens existing HUD editor
        int sx = bx + barW + 6;
        boolean sHov = mouseX >= sx && mouseX <= sx + SCALE_BTN_W && mouseY >= by && mouseY <= by + SEARCH_H;
        ctx.fill(sx, by, sx + SCALE_BTN_W, by + SEARCH_H, fade(sHov ? PILL_OFF_HOVER : COL_HEADER_BG, br));
        ctx.fill(sx, by, sx + SCALE_BTN_W, by + 1, fade(sHov ? ACCENT : BORDER_COLOR, br));
        ctx.fill(sx, by + SEARCH_H - 1, sx + SCALE_BTN_W, by + SEARCH_H, fade(sHov ? ACCENT : BORDER_COLOR, br));
        ctx.fill(sx, by, sx + 1, by + SEARCH_H, fade(BORDER_COLOR, br));
        ctx.fill(sx + SCALE_BTN_W - 1, by, sx + SCALE_BTN_W, by + SEARCH_H, fade(BORDER_COLOR, br));
        // expand-arrows glyph
        int gx = sx + SCALE_BTN_W / 2 - 4;
        int gy = by + SEARCH_H / 2 - 4;
        int gc = fade(sHov ? ACCENT_HOVER : ACCENT, br);
        ctx.fill(gx, gy, gx + 8, gy + 1, gc);
        ctx.fill(gx, gy, gx + 1, gy + 4, gc);
        ctx.fill(gx, gy + 7, gx + 4, gy + 8, gc);
        ctx.fill(gx, gy + 4, gx + 1, gy + 8, gc);
        ctx.fill(gx + 7, gy, gx + 8, gy + 4, gc);
        ctx.fill(gx + 4, gy + 7, gx + 8, gy + 8, gc);
        ctx.fill(gx + 7, gy + 4, gx + 8, gy + 8, gc);
    }

    // -----------------------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------------------
    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int mx = (int) click.x();
        int my = (int) click.y();
        int btn = click.button();
        activeInput = null;

        // Bottom bar
        int by = this.height - PADDING - SEARCH_H;
        int barW = Math.min(360, this.width - PADDING * 2 - SCALE_BTN_W - 6);
        int bx = (this.width - barW - SCALE_BTN_W - 6) / 2;
        searchFocused = mx >= bx && mx <= bx + barW && my >= by && my <= by + SEARCH_H;
        if (searchField != null) searchField.setFocused(searchFocused);
        if (searchFocused) return true;
        int sxBtn = bx + barW + 6;
        if (mx >= sxBtn && mx <= sxBtn + SCALE_BTN_W && my >= by && my <= by + SEARCH_H) {
            MinecraftClient.getInstance().setScreen(new FishHudEditor(this));
            return true;
        }

        String filter = searchText.toLowerCase();
        for (int i = 0; i < columns.size(); i++) {
            Column col = columns.get(i);
            int cx = colX(i);
            int colBottom = colsBottomY();
            int py = colsTopY() + COL_HEADER_H + 3;
            for (Feature f : col.features) {
                if (!filter.isEmpty() && !f.name.toLowerCase().contains(filter)) continue;
                if (py + PILL_H > colBottom) break;

                // Pill hit-test
                if (mx >= cx && mx <= cx + COL_W && my >= py && my <= py + PILL_H) {
                    if (btn == 1) {
                        // Right-click → expand inline detail (toggle)
                        if (!f.sub.isEmpty()) {
                            selectedFeature = (selectedFeature == f ? null : f);
                            detailScroll = 0;
                        }
                    } else {
                        // Left-click → master toggle, or open detail if no master
                        if (f.hasMaster()) {
                            f.set.accept(!f.get.get());
                        } else if (!f.sub.isEmpty()) {
                            selectedFeature = (selectedFeature == f ? null : f);
                            detailScroll = 0;
                        }
                    }
                    return true;
                }
                py += PILL_H + PILL_GAP;

                // Detail hit-test
                if (f == selectedFeature && !f.sub.isEmpty()) {
                    int dH = Math.min(detailHeightForSelected(), colBottom - py);
                    if (dH > 8 && mx >= cx && mx <= cx + COL_W && my >= py && my <= py + dH) {
                        int leftX = cx + 4;
                        int rightX = cx + COL_W - 4;
                        int sy = py + 4 - detailScroll;
                        for (Setting s : f.sub) {
                            int sh = s.getHeight();
                            if (my >= sy && my <= sy + sh) {
                                if (s instanceof InputSetting || s instanceof InputIntSetting
                                        || s instanceof InputDoubleSetting || s instanceof ColorSetting
                                        || s instanceof ColorPickerSetting) {
                                    activeInput = s;
                                }
                            }
                            if (s.onClick(mx, my, leftX, rightX, sy, btn)) {
                                if (s instanceof ColorPickerSetting cps && cps.dragMode != 0) activePicker = cps;
                                return true;
                            }
                            if (s instanceof SliderIntSetting || s instanceof SliderDoubleSetting) {
                                int slx = rightX - SLIDER_W - 2;
                                int sly = sy + (ITEM_HEIGHT - SLIDER_H) / 2;
                                if (mx >= slx && mx <= slx + SLIDER_W && my >= sly - 4 && my <= sly + SLIDER_H + 4) {
                                    activeSlider = s;
                                    activeSliderX = slx;
                                    s.onDrag(mx, slx, SLIDER_W);
                                    return true;
                                }
                            }
                            sy += sh;
                        }
                        return true; // swallow clicks inside the detail panel
                    }
                    if (dH > 8) py += dH + PILL_GAP;
                }
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (activeSlider != null) {
            activeSlider.onDrag((int) click.x(), activeSliderX, SLIDER_W);
            return true;
        }
        if (activePicker != null) {
            activePicker.updateFromMouse((int) click.x(), (int) click.y());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        activeSlider = null;
        if (activePicker != null) { activePicker.dragMode = 0; activePicker = null; }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedFeature == null) return true;
        String filter = searchText.toLowerCase();
        for (int i = 0; i < columns.size(); i++) {
            Column col = columns.get(i);
            int cx = colX(i);
            int colBottom = colsBottomY();
            int py = colsTopY() + COL_HEADER_H + 3;
            for (Feature f : col.features) {
                if (!filter.isEmpty() && !f.name.toLowerCase().contains(filter)) continue;
                if (py + PILL_H > colBottom) break;
                py += PILL_H + PILL_GAP;
                if (f == selectedFeature && !f.sub.isEmpty()) {
                    int dH = Math.min(detailHeightForSelected(), colBottom - py);
                    if (mouseX >= cx && mouseX <= cx + COL_W && mouseY >= py && mouseY <= py + dH) {
                        int viewH = dH - 8;
                        int total = detailHeightForSelected() - 8;
                        int max = Math.max(0, total - viewH);
                        detailScroll = MathHelper.clamp((int) (detailScroll - verticalAmount * 12), 0, max);
                        return true;
                    }
                    py += dH + PILL_GAP;
                }
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (activeInput instanceof InputSetting is && is.textField != null) { is.textField.keyPressed(input); return true; }
        if (activeInput instanceof InputIntSetting iis && iis.textField != null) { iis.textField.keyPressed(input); return true; }
        if (activeInput instanceof InputDoubleSetting ids && ids.textField != null) { ids.textField.keyPressed(input); return true; }
        if (activeInput instanceof ColorSetting cs && cs.textField != null) { cs.textField.keyPressed(input); return true; }
        if (activeInput instanceof ColorPickerSetting cp && cp.textField != null) { cp.textField.keyPressed(input); return true; }
        if (searchFocused && searchField != null) { searchField.keyPressed(input); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (activeInput instanceof InputSetting is && is.textField != null) {
            is.textField.charTyped(input); is.setter.accept(is.textField.getText()); return true;
        }
        if (activeInput instanceof InputIntSetting iis && iis.textField != null) { iis.textField.charTyped(input); return true; }
        if (activeInput instanceof InputDoubleSetting ids && ids.textField != null) { ids.textField.charTyped(input); return true; }
        if (activeInput instanceof ColorSetting cs && cs.textField != null) { cs.textField.charTyped(input); return true; }
        if (activeInput instanceof ColorPickerSetting cp && cp.textField != null) { cp.textField.charTyped(input); return true; }
        if (searchFocused && searchField != null) {
            searchField.charTyped(input);
            searchText = searchField.getText();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        Config.manager.save();
        FishConfig.manager.save();
        super.close();
    }

    // -----------------------------------------------------------------------------------
    // Model
    // -----------------------------------------------------------------------------------
    static class Column {
        final String name;
        final List<Feature> features = new ArrayList<>();
        Column(String name) { this.name = name; }
    }

    static class Feature {
        final String name;
        final Supplier<Boolean> get;
        final Consumer<Boolean> set;
        final List<Setting> sub = new ArrayList<>();
        Feature(String name, Supplier<Boolean> get, Consumer<Boolean> set) {
            this.name = name; this.get = get; this.set = set;
        }
        boolean hasMaster() { return get != null && set != null; }
    }

    // -----------------------------------------------------------------------------------
    // Setting widgets
    // -----------------------------------------------------------------------------------
    static abstract class Setting {
        String name, description;
        Setting(String name, String description) { this.name = name; this.description = description; }
        abstract void render(DrawContext ctx, int leftX, int rightX, int settingY, int mouseX, int mouseY, net.minecraft.client.font.TextRenderer tr);
        boolean onClick(int mx, int my, int leftX, int rightX, int settingY, int button) { return false; }
        void onDrag(int mx, int sx, int sliderW) {}
        int getHeight() { return ITEM_HEIGHT; }
    }

    static class SubcategoryHeader extends Setting {
        SubcategoryHeader(String name) { super(name, ""); }
        @Override int getHeight() { return SUBCAT_HEIGHT; }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            ctx.fill(leftX, sy, rightX, sy + SUBCAT_HEIGHT, 0xFF11131A);
            ctx.fill(leftX, sy + SUBCAT_HEIGHT - 1, rightX, sy + SUBCAT_HEIGHT, BORDER_COLOR);
            ctx.fill(leftX, sy, leftX + 2, sy + SUBCAT_HEIGHT, ACCENT);
            st(ctx, tr, name, leftX + 6, sy + (SUBCAT_HEIGHT - 8) / 2, ACCENT);
        }
    }

    static class ToggleSetting extends Setting {
        Supplier<Boolean> getter; Consumer<Boolean> setter;
        ToggleSetting(String name, String desc, Supplier<Boolean> g, Consumer<Boolean> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int tx = rightX - TOGGLE_W - 2;
            int ty = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            boolean on = getter.get();
            boolean hov = mx >= tx && mx <= tx + TOGGLE_W && my >= ty && my <= ty + TOGGLE_H;
            // Pill switch: rounded capsule track + a circular knob that sits right (on) / left (off).
            int track = on ? (hov ? ACCENT_HOVER : TOGGLE_ON) : (hov ? 0xFF3A3D48 : TOGGLE_OFF);
            roundRect(ctx, tx, ty, tx + TOGGLE_W, ty + TOGGLE_H, track);
            int knob = TOGGLE_H - 4;                 // knob diameter (2px inset top/bottom)
            int kx = on ? tx + TOGGLE_W - knob - 2 : tx + 2;
            int ky = ty + 2;
            roundRect(ctx, kx, ky, kx + knob, ky + knob, 0xFFE8ECF2); // light knob
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int tx = rightX - TOGGLE_W - 2;
            int ty = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            if (mx >= tx && mx <= tx + TOGGLE_W && my >= ty && my <= ty + TOGGLE_H) {
                setter.accept(!getter.get()); return true;
            }
            return false;
        }
    }

    static class SliderIntSetting extends Setting {
        Supplier<Integer> getter; Consumer<Integer> setter; int min, max;
        SliderIntSetting(String name, String desc, Supplier<Integer> g, Consumer<Integer> s, int mn, int mx) {
            super(name, desc); this.getter = g; this.setter = s; this.min = mn; this.max = mx;
        }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int slx = rightX - SLIDER_W - 2;
            int sly = sy + (ITEM_HEIGHT - SLIDER_H) / 2;
            float pct = (float)(getter.get() - min) / (max - min);
            ctx.fill(slx, sly, slx + SLIDER_W, sly + SLIDER_H, SLIDER_BG);
            ctx.fill(slx, sly, slx + (int)(SLIDER_W * pct), sly + SLIDER_H, SLIDER_FILL);
            // value text small, above the bar
            String val = String.valueOf(getter.get());
            st(ctx, tr, val, slx + SLIDER_W - stw(tr, val), sly - 9, SUBTEXT_COLOR);
        }
        @Override
        void onDrag(int mx, int sx, int sliderW) {
            float pct = MathHelper.clamp((float)(mx - sx) / sliderW, 0, 1);
            setter.accept(min + (int)(pct * (max - min)));
        }
    }

    static class SliderDoubleSetting extends Setting {
        Supplier<Double> getter; Consumer<Double> setter; double min, max;
        SliderDoubleSetting(String name, String desc, Supplier<Double> g, Consumer<Double> s, double mn, double mx) {
            super(name, desc); this.getter = g; this.setter = s; this.min = mn; this.max = mx;
        }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int slx = rightX - SLIDER_W - 2;
            int sly = sy + (ITEM_HEIGHT - SLIDER_H) / 2;
            float pct = (float)((getter.get() - min) / (max - min));
            ctx.fill(slx, sly, slx + SLIDER_W, sly + SLIDER_H, SLIDER_BG);
            ctx.fill(slx, sly, slx + (int)(SLIDER_W * pct), sly + SLIDER_H, SLIDER_FILL);
            String val = String.format("%.1f", getter.get());
            st(ctx, tr, val, slx + SLIDER_W - stw(tr, val), sly - 9, SUBTEXT_COLOR);
        }
        @Override
        void onDrag(int mx, int sx, int sliderW) {
            float pct = MathHelper.clamp((float)(mx - sx) / sliderW, 0, 1);
            setter.accept(min + pct * (max - min));
        }
    }

    // Click to advance to the next value; right-click goes back one.
    // Avoids popup overflow problems in narrow columns.
    static class DropdownSetting<T> extends Setting {
        T[] values; Supplier<T> getter; Consumer<T> setter;
        DropdownSetting(String name, String desc, T[] vals, Supplier<T> g, Consumer<T> s) {
            super(name, desc); this.values = vals; this.getter = g; this.setter = s;
        }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int bw = 80;
            int bx = rightX - bw - 2;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + TOGGLE_H;
            ctx.fill(bx, by, bx + bw, by + TOGGLE_H, hov ? 0xFF252832 : SLIDER_BG);
            ctx.fill(bx, by, bx + bw, by + 1, ACCENT);
            ctx.fill(bx, by + TOGGLE_H - 1, bx + bw, by + TOGGLE_H, ACCENT);
            String current = getter.get().toString();
            if (tr.getWidth(current) > bw - 14) current = tr.trimToWidth(current, bw - 18) + "…";
            st(ctx, tr, current, bx + 4, by + (TOGGLE_H - 8) / 2, TEXT_COLOR);
            // tiny chevron on the right
            st(ctx, tr, "›", bx + bw - 7, by + (TOGGLE_H - 8) / 2, ACCENT);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int bw = 80;
            int bx = rightX - bw - 2;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + TOGGLE_H) {
                int idx = 0;
                T cur = getter.get();
                for (int i = 0; i < values.length; i++) if (values[i] == cur || values[i].equals(cur)) { idx = i; break; }
                int next = (btn == 1) ? (idx - 1 + values.length) % values.length : (idx + 1) % values.length;
                setter.accept(values[next]);
                return true;
            }
            return false;
        }
    }

    static class InputSetting extends Setting {
        Supplier<String> getter; Consumer<String> setter;
        TextFieldWidget textField;
        InputSetting(String name, String desc, Supplier<String> g, Consumer<String> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        void initField(net.minecraft.client.font.TextRenderer tr) {
            if (textField == null) {
                textField = new TextFieldWidget(tr, 0, 0, INPUT_W, INPUT_H, Text.empty());
                textField.setMaxLength(256);
                textField.setText(getter.get());
                textField.setChangedListener(setter);
            }
        }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            initField(tr);
            int ix = rightX - INPUT_W - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            // Render the field text scaled down so the whole value is visible (it scrolled off
            // before). Backing width is enlarged then scaled to the same visual box size.
            float fs = 0.7f;
            textField.setWidth((int) (INPUT_W / fs));
            textField.setHeight((int) (INPUT_H / fs));
            textField.setX(0); textField.setY(0);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((float) ix, (float) iy);
            ctx.getMatrices().scale(fs, fs);
            textField.render(ctx, mx, my, 0);
            ctx.getMatrices().popMatrix();
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int ix = rightX - INPUT_W - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                if (textField != null) textField.setFocused(true);
                return true;
            }
            return false;
        }
    }

    /**
     * Text input with a visible-character cap + two-line layout: label on top, "N/MAX" counter on
     * the bottom row (so they don't overlap on narrow columns). External row-label drawing is
     * suppressed by passing an empty name to super().
     */
    static class LimitedInputSetting extends InputSetting {
        final int maxVisible;
        final String displayLabel;
        LimitedInputSetting(String name, String desc, int maxVisible, Supplier<String> g, Consumer<String> s) {
            super("", desc, g, capWrapper(s, maxVisible));
            this.maxVisible = maxVisible;
            this.displayLabel = name;
        }
        static int visibleLen(String s) {
            if (s == null) return 0;
            return s.replaceAll("&#[0-9a-fA-F]{6}", "").replaceAll("[&§][0-9a-fk-orxA-FK-ORX]", "").length();
        }
        private static Consumer<String> capWrapper(Consumer<String> inner, int max) {
            return v -> {
                String s = v == null ? "" : v;
                while (!s.isEmpty() && visibleLen(s) > max) s = s.substring(0, s.length() - 1);
                inner.accept(s);
            };
        }
        @Override int getHeight() { return ITEM_HEIGHT + 9; } // room for stacked label + counter
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            // Label on top
            st(ctx, tr, displayLabel, leftX + 2, sy + 1, TEXT_COLOR);
            // Field anchored to top so it doesn't push the counter off the row
            initField(tr);
            int ix = rightX - INPUT_W - 2;
            int iy = sy + 2;
            float fs = 0.7f;
            textField.setWidth((int) (INPUT_W / fs));
            textField.setHeight((int) (INPUT_H / fs));
            textField.setX(0); textField.setY(0);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((float) ix, (float) iy);
            ctx.getMatrices().scale(fs, fs);
            textField.render(ctx, mx, my, 0);
            ctx.getMatrices().popMatrix();
            // Counter on the bottom row
            int len = visibleLen(getter.get());
            String counter = len + "/" + maxVisible;
            int color = len >= maxVisible ? 0xFFFF5555 : SUBTEXT_COLOR;
            st(ctx, tr, counter, leftX + 2, sy + getHeight() - 9, color);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int ix = rightX - INPUT_W - 2;
            int iy = sy + 2;
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                if (textField != null) textField.setFocused(true);
                return true;
            }
            return false;
        }
    }

    static class ColorSetting extends Setting {
        Supplier<Integer> getter; Consumer<Integer> setter;
        TextFieldWidget textField;
        ColorSetting(String name, String desc, Supplier<Integer> g, Consumer<Integer> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        void initField(net.minecraft.client.font.TextRenderer tr) {
            if (textField == null) {
                textField = new TextFieldWidget(tr, 0, 0, 50, INPUT_H, Text.empty());
                textField.setMaxLength(6);
                textField.setText(String.format("%06X", getter.get() & 0xFFFFFF));
                textField.setChangedListener(s -> {
                    if (s.length() == 6) {
                        try { setter.accept(0xFF000000 | (int) Long.parseLong(s, 16)); }
                        catch (NumberFormatException ignored) {}
                    }
                });
            }
        }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            initField(tr);
            int ix = rightX - 50 - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            ctx.fill(ix - 18, iy, ix - 2, iy + INPUT_H, 0xFF000000);
            ctx.fill(ix - 17, iy + 1, ix - 3, iy + INPUT_H - 1, getter.get());
            textField.setX(ix); textField.setY(iy);
            textField.render(ctx, mx, my, 0);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int ix = rightX - 50 - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            if (mx >= ix && mx <= ix + 50 && my >= iy && my <= iy + INPUT_H) {
                if (textField != null) textField.setFocused(true);
                return true;
            }
            return false;
        }
    }

    /** Visual color picker: saturation/brightness square + vertical hue bar + swatch + editable hex. */
    static class ColorPickerSetting extends Setting {
        Supplier<Integer> getter; Consumer<Integer> setter;
        TextFieldWidget textField;
        float hsbH, hsbS, hsbV;          // current picker state
        int lastColor = 0;               // detect external changes to resync
        int dragMode = 0;                // 0 none, 1 SV square, 2 hue bar
        // region geometry (set each render for hit-testing)
        int sqX, sqY, sqW = 96, sqH = 46, hueX, hueY, hueW = 10, hueH = 46;

        ColorPickerSetting(String name, String desc, Supplier<Integer> g, Consumer<Integer> s) {
            super(name, desc); this.getter = g; this.setter = s;
            syncFromColor(getter.get());
        }
        @Override int getHeight() { return ITEM_HEIGHT + sqH + 6; }

        private void syncFromColor(int argb) {
            float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
            hsbH = hsb[0]; hsbS = hsb[1]; hsbV = hsb[2];
            lastColor = argb;
        }
        private void commit() {
            int rgb = java.awt.Color.HSBtoRGB(hsbH, hsbS, hsbV) & 0xFFFFFF;
            int argb = 0xFF000000 | rgb;
            lastColor = argb;
            setter.accept(argb);
            if (textField != null) textField.setText(String.format("%06X", rgb));
        }
        void initField(net.minecraft.client.font.TextRenderer tr) {
            if (textField == null) {
                textField = new TextFieldWidget(tr, 0, 0, 46, INPUT_H, Text.empty());
                textField.setMaxLength(6);
                textField.setText(String.format("%06X", getter.get() & 0xFFFFFF));
                textField.setChangedListener(t -> {
                    if (t.length() == 6) {
                        try {
                            int argb = 0xFF000000 | (int) Long.parseLong(t, 16);
                            setter.accept(argb); syncFromColor(argb);
                        } catch (NumberFormatException ignored) {}
                    }
                });
            }
        }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            initField(tr);
            // External change (e.g. toggled on) → resync the picker state.
            if (getter.get() != lastColor) { syncFromColor(getter.get()); textField.setText(String.format("%06X", getter.get() & 0xFFFFFF)); }

            // Top row: label + swatch + hex field.
            st(ctx, tr, name, leftX, sy + (ITEM_HEIGHT - 8) / 2, TEXT_COLOR);
            int ix = rightX - 46 - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            ctx.fill(ix - 18, iy, ix - 2, iy + INPUT_H, 0xFF000000);
            ctx.fill(ix - 17, iy + 1, ix - 3, iy + INPUT_H - 1, getter.get());
            textField.setX(ix); textField.setY(iy);
            textField.render(ctx, mx, my, 0);

            // SV square: per-column gradient from bright-saturated (top) to black (bottom).
            sqX = leftX; sqY = sy + ITEM_HEIGHT + 2;
            for (int c = 0; c < sqW; c++) {
                float sat = (float) c / sqW;
                int top = 0xFF000000 | (java.awt.Color.HSBtoRGB(hsbH, sat, 1f) & 0xFFFFFF);
                ctx.fillGradient(sqX + c, sqY, sqX + c + 1, sqY + sqH, top, 0xFF000000);
            }
            // SV marker.
            int msx = sqX + Math.round(hsbS * sqW);
            int msy = sqY + Math.round((1 - hsbV) * sqH);
            ctx.fill(msx - 2, msy - 1, msx + 2, msy, 0xFFFFFFFF);
            ctx.fill(msx - 2, msy + 1, msx + 2, msy + 2, 0xFFFFFFFF);
            ctx.fill(msx - 2, msy, msx - 1, msy + 1, 0xFFFFFFFF);
            ctx.fill(msx + 1, msy, msx + 2, msy + 1, 0xFFFFFFFF);

            // Vertical hue bar.
            hueX = sqX + sqW + 6; hueY = sqY;
            for (int r = 0; r < sqH; r++) {
                int col = 0xFF000000 | (java.awt.Color.HSBtoRGB((float) r / sqH, 1f, 1f) & 0xFFFFFF);
                ctx.fill(hueX, hueY + r, hueX + hueW, hueY + r + 1, col);
            }
            int hmy = hueY + Math.round(hsbH * sqH);
            ctx.fill(hueX - 1, hmy - 1, hueX + hueW + 1, hmy + 1, 0xFFFFFFFF);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int ix = rightX - 46 - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            if (mx >= ix && mx <= ix + 46 && my >= iy && my <= iy + INPUT_H) {
                if (textField != null) textField.setFocused(true); return true;
            }
            if (mx >= sqX && mx <= sqX + sqW && my >= sqY && my <= sqY + sqH) {
                dragMode = 1; updateFromMouse(mx, my); return true;
            }
            if (mx >= hueX && mx <= hueX + hueW && my >= hueY && my <= hueY + hueH) {
                dragMode = 2; updateFromMouse(mx, my); return true;
            }
            return false;
        }
        void updateFromMouse(int mx, int my) {
            if (dragMode == 1) {
                hsbS = MathHelper.clamp((float) (mx - sqX) / sqW, 0f, 1f);
                hsbV = MathHelper.clamp(1f - (float) (my - sqY) / sqH, 0f, 1f);
            } else if (dragMode == 2) {
                hsbH = MathHelper.clamp((float) (my - hueY) / sqH, 0f, 1f);
            }
            commit();
        }
    }

    /** ColorPickerSetting that collapses to zero height (invisible + non-interactive) when the
     *  supplied predicate returns false. Used to hide the End picker while in Solid mode. */
    static class ConditionalColorPickerSetting extends ColorPickerSetting {
        final Supplier<Boolean> visible;
        final String shownName;
        ConditionalColorPickerSetting(String name, String desc, Supplier<Boolean> visible,
                                      Supplier<Integer> g, Consumer<Integer> s) {
            super(name, desc, g, s);
            this.visible = visible;
            this.shownName = name;
        }
        @Override int getHeight() {
            if (!visible.get()) { this.name = ""; return 0; }
            this.name = shownName;
            return super.getHeight();
        }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my,
                    net.minecraft.client.font.TextRenderer tr) {
            if (!visible.get()) return;
            super.render(ctx, leftX, rightX, sy, mx, my, tr);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            if (!visible.get()) return false;
            return super.onClick(mx, my, leftX, rightX, sy, btn);
        }
    }

    static class ButtonSetting extends Setting {
        Runnable action;
        ButtonSetting(String name, String desc, Runnable a) { super(name, desc); this.action = a; }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int bw = 60;
            int bx = rightX - bw - 2;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + TOGGLE_H;
            ctx.fill(bx, by, bx + bw, by + TOGGLE_H, hov ? ACCENT_HOVER : ACCENT);
            st(ctx, tr, "Open", bx + (bw - stw(tr, "Open")) / 2, by + (TOGGLE_H - 8) / 2, TEXT_COLOR);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int bw = 60;
            int bx = rightX - bw - 2;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + TOGGLE_H) {
                action.run(); return true;
            }
            return false;
        }
    }

    static class InputIntSetting extends Setting {
        Supplier<Integer> getter; Consumer<Integer> setter;
        TextFieldWidget textField;
        InputIntSetting(String name, String desc, Supplier<Integer> g, Consumer<Integer> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        void initField(net.minecraft.client.font.TextRenderer tr) {
            if (textField == null) {
                textField = new TextFieldWidget(tr, 0, 0, INPUT_W, INPUT_H, Text.empty());
                textField.setMaxLength(10);
                textField.setText(String.valueOf(getter.get()));
                textField.setChangedListener(s -> {
                    try { setter.accept(Integer.parseInt(s.trim())); }
                    catch (NumberFormatException ignored) {}
                });
            }
        }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            initField(tr);
            int ix = rightX - INPUT_W - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            // Render the field text scaled down so the whole value is visible (it scrolled off
            // before). Backing width is enlarged then scaled to the same visual box size.
            float fs = 0.7f;
            textField.setWidth((int) (INPUT_W / fs));
            textField.setHeight((int) (INPUT_H / fs));
            textField.setX(0); textField.setY(0);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((float) ix, (float) iy);
            ctx.getMatrices().scale(fs, fs);
            textField.render(ctx, mx, my, 0);
            ctx.getMatrices().popMatrix();
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int ix = rightX - INPUT_W - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                if (textField != null) textField.setFocused(true);
                return true;
            }
            return false;
        }
    }

    static class InputDoubleSetting extends Setting {
        Supplier<Double> getter; Consumer<Double> setter;
        TextFieldWidget textField;
        InputDoubleSetting(String name, String desc, Supplier<Double> g, Consumer<Double> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        void initField(net.minecraft.client.font.TextRenderer tr) {
            if (textField == null) {
                textField = new TextFieldWidget(tr, 0, 0, INPUT_W, INPUT_H, Text.empty());
                textField.setMaxLength(12);
                textField.setText(String.valueOf(getter.get()));
                textField.setChangedListener(s -> {
                    try { setter.accept(Double.parseDouble(s.trim())); }
                    catch (NumberFormatException ignored) {}
                });
            }
        }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            initField(tr);
            int ix = rightX - INPUT_W - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            // Render the field text scaled down so the whole value is visible (it scrolled off
            // before). Backing width is enlarged then scaled to the same visual box size.
            float fs = 0.7f;
            textField.setWidth((int) (INPUT_W / fs));
            textField.setHeight((int) (INPUT_H / fs));
            textField.setX(0); textField.setY(0);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((float) ix, (float) iy);
            ctx.getMatrices().scale(fs, fs);
            textField.render(ctx, mx, my, 0);
            ctx.getMatrices().popMatrix();
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int ix = rightX - INPUT_W - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                if (textField != null) textField.setFocused(true);
                return true;
            }
            return false;
        }
    }

    static class LabelSetting extends Setting {
        LabelSetting(String name, String desc) { super(name, desc); }
        @Override
        void render(DrawContext ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {}
    }
}
