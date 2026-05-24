package blade.addon.features;

import blade.addon.utils.config.Config;
import blade.addon.utils.config.FishConfig;
import blade.addon.features.BridgeBot;
import blade.addon.utils.config.values.*;
import blade.addon.utils.dungeon.Phase;
import blade.addon.utils.dungeon.Split;
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
    static final int PILL_OFF        = 0xCC1B1D24;
    static final int PILL_ON         = 0xCC3AA0FF;
    static final int PILL_OFF_HOVER  = 0xCC252832;
    static final int PILL_ON_HOVER   = 0xCC55B0FF;
    static final int PANEL_BG        = 0xD815171C;
    static final int COL_HEADER_BG   = 0xCC15171C;
    static final int BORDER_COLOR    = 0xFF2A2D38;
    static final int TEXT_COLOR      = 0xFFFFFFFF;
    static final int SUBTEXT_COLOR   = 0xFF8B92A5;
    static final int ACCENT          = 0xFF3AA0FF;
    static final int ACCENT_HOVER    = 0xFF55B0FF;
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
    static final int PILL_GAP       = 3;
    static final int ITEM_HEIGHT    = 28;
    static final int TOGGLE_W       = 36;
    static final int TOGGLE_H       = 14;
    static final int SLIDER_W       = 64;
    static final int SLIDER_H       = 5;
    static final int INPUT_W        = 70;
    static final int INPUT_H        = 14;
    static final int SUBCAT_HEIGHT  = 16;
    static final int SEARCH_H       = 22;
    static final int SCALE_BTN_W    = 28;

    // ----- state -----
    private final List<Column> columns = new ArrayList<>();
    private Feature selectedFeature = null;
    private int detailScroll = 0;
    private String searchText = "";
    private boolean searchFocused = false;
    private Setting activeSlider = null;
    private int activeSliderX = 0;
    private Setting activeInput = null;
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
        {
            Feature f = new Feature("Croesus Overlay",
                    () -> FishSettings.croesusOverlayEnabled,
                    v -> FishSettings.croesusOverlayEnabled = v);
            f.sub.add(new ToggleSetting("Hide in Dungeon", "",
                    () -> FishSettings.croesusOverlayHideInDungeon,
                    v -> FishSettings.croesusOverlayHideInDungeon = v));
            dungeon.features.add(f);
        }
        dungeon.features.add(new Feature("Send Lag to Party",
                () -> FishSettings.sendLagToParty,
                v -> FishSettings.sendLagToParty = v));
        dungeon.features.add(new Feature("Puzzle Overlay",
                () -> FishSettings.showPuzzles,
                v -> FishSettings.showPuzzles = v));
        {
            Feature f = new Feature("Death Message",
                    () -> FishSettings.deathMessageEnabled,
                    v -> FishSettings.deathMessageEnabled = v);
            f.sub.add(new InputSetting("Template", "",
                    () -> FishSettings.deathMessageTemplate,
                    v -> FishSettings.deathMessageTemplate = v));
            f.sub.add(new ToggleSetting("To Party", "",
                    () -> FishSettings.deathMessageToParty,
                    v -> FishSettings.deathMessageToParty = v));
            dungeon.features.add(f);
        }
        {
            Feature f = new Feature("Splits",
                    () -> Phase.enableSplits,
                    v -> Phase.enableSplits = v);
            f.sub.add(new ToggleSetting("Total Time", "",
                    () -> Phase.includeTotalTime, v -> Phase.includeTotalTime = v));
            f.sub.add(new ToggleSetting("Send in Chat", "",
                    () -> Phase.sendSplitInChat, v -> Phase.sendSplitInChat = v));
            f.sub.add(new DropdownSetting<>("Tick Timer", "",
                    Split.TimerType.values(),
                    () -> Split.timerType, v -> Split.timerType = v));
            f.sub.add(new ToggleSetting("Activated Only", "",
                    () -> Phase.onlyShowActivatedSplits,
                    v -> Phase.onlyShowActivatedSplits = v));
            dungeon.features.add(f);
        }
        {
            Feature f = new Feature("Simon Says",
                    () -> FishSettings.simonSaysEnabled,
                    v -> FishSettings.simonSaysEnabled = v);
            f.sub.add(new ToggleSetting("Show HUD", "",
                    () -> FishSettings.simonSaysHudEnabled,
                    v -> FishSettings.simonSaysHudEnabled = v));
            f.sub.add(new ToggleSetting("To Party", "",
                    () -> FishSettings.simonSaysPartyChat,
                    v -> FishSettings.simonSaysPartyChat = v));
            dungeon.features.add(f);
        }
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
            f.sub.add(new ToggleSetting(".help / .?", "", () -> FishSettings.pcHelp, v -> FishSettings.pcHelp = v));
            f.sub.add(new ToggleSetting("Party Actions", "", () -> FishSettings.pcPartyActions, v -> FishSettings.pcPartyActions = v));
            dungeon.features.add(f);
        }
        {
            Feature f = new Feature("Chat Commands", null, null);
            f.sub.add(new ToggleSetting("Guild Chat", "", () -> FishSettings.chatGuild, v -> FishSettings.chatGuild = v));
            f.sub.add(new ToggleSetting("Officer Chat", "", () -> FishSettings.chatOfficer, v -> FishSettings.chatOfficer = v));
            f.sub.add(new ToggleSetting("Private Msgs", "", () -> FishSettings.chatPrivate, v -> FishSettings.chatPrivate = v));
            f.sub.add(new ToggleSetting("All Chat", "", () -> FishSettings.chatAll, v -> FishSettings.chatAll = v));
            f.sub.add(new ToggleSetting("Meow Replies", "", () -> FishSettings.chatMeow, v -> FishSettings.chatMeow = v));
            dungeon.features.add(f);
        }
        columns.add(dungeon);

        // ===== HUDs =====
        Column huds = new Column("HUDs");
        {
            Feature f = new Feature("Soulflow HUD",
                    () -> FishSettings.soulflowHudEnabled, v -> FishSettings.soulflowHudEnabled = v);
            f.sub.add(new InputIntSetting("Warning", "",
                    () -> FishSettings.soulflowWarningThreshold, v -> FishSettings.soulflowWarningThreshold = v));
            f.sub.add(new ToggleSetting("Missing Warn", "",
                    () -> FishSettings.soulflowMissingNotifier, v -> FishSettings.soulflowMissingNotifier = v));
            huds.features.add(f);
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
            huds.features.add(f);
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
            huds.features.add(f);
        }
        huds.features.add(new Feature("Dungeon Score",
                () -> FishSettings.dungeonScoreEnabled, v -> FishSettings.dungeonScoreEnabled = v));
        {
            Feature f = new Feature("Cooldown",
                    () -> FishSettings.cooldownOverlayEnabled, v -> FishSettings.cooldownOverlayEnabled = v);
            f.sub.add(new ToggleSetting("Show Number", "",
                    () -> FishSettings.cooldownShowText, v -> FishSettings.cooldownShowText = v));
            f.sub.add(new ToggleSetting("Under 3s Only", "",
                    () -> FishSettings.cooldownOnlyUnder3s, v -> FishSettings.cooldownOnlyUnder3s = v));
            f.sub.add(new ToggleSetting("In Inventory", "",
                    () -> FishSettings.cooldownInInventory, v -> FishSettings.cooldownInInventory = v));
            huds.features.add(f);
        }
        {
            Feature f = new Feature("Croesus Loot", null, null);
            f.sub.add(new ButtonSetting("Open Loot", "",
                    () -> MinecraftClient.getInstance().setScreen(
                            new blade.addon.features.croesus.CroesusLootScreen(MinecraftClient.getInstance().currentScreen))));
            f.sub.add(new DropdownSetting<>("Price", "",
                    FishSettings.PriceMode.values(),
                    () -> FishSettings.trackerPriceModeEnum,
                    v -> { FishSettings.trackerPriceModeEnum = v; blade.addon.features.croesus.CroesusPrices.applyPriceMode(); }));
            huds.features.add(f);
        }
        columns.add(huds);

        // ===== Skyblock =====
        Column skyblock = new Column("Skyblock");
        {
            Feature f = new Feature("Pet XP Helper",
                    () -> FishSettings.petXpAutoDetect, v -> FishSettings.petXpAutoDetect = v);
            f.sub.add(new SliderIntSetting("Taming", "",
                    () -> FishSettings.petXpTamingLevel, v -> FishSettings.petXpTamingLevel = v, 0, 60));
            f.sub.add(new SliderIntSetting("Beastm %", "",
                    () -> FishSettings.petXpBeastmasterBonus, v -> FishSettings.petXpBeastmasterBonus = v, 0, 60));
            f.sub.add(new SliderIntSetting("Pet Item %", "",
                    () -> FishSettings.petXpPetItemBonus, v -> FishSettings.petXpPetItemBonus = v, 0, 100));
            f.sub.add(new ToggleSetting("Cookie", "",
                    () -> FishSettings.petXpBoosterCookie, v -> FishSettings.petXpBoosterCookie = v));
            skyblock.features.add(f);
        }
        skyblock.features.add(new Feature("Rarity Hotbar",
                () -> Visual.itemRarityBackground, v -> Visual.itemRarityBackground = v));
        // Slayer XP tracks XP, not items — no price source needed.
        skyblock.features.add(new Feature("Slayer XP Tracker",
                () -> FishSettings.slayerXpEnabled, v -> FishSettings.slayerXpEnabled = v));
        skyblock.features.add(new Feature("Skill XP Tracker",
                () -> FishSettings.skillTrackerEnabled, v -> FishSettings.skillTrackerEnabled = v));
        skyblock.features.add(new Feature("Fire Freeze Timer",
                () -> FishSettings.fireFreezeTimerEnabled, v -> FishSettings.fireFreezeTimerEnabled = v));
        {
            Feature f = new Feature("Powder Tracker",
                    () -> FishSettings.powderTrackerEnabled, v -> FishSettings.powderTrackerEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "",
                    FishSettings.PriceMode.values(),
                    () -> FishSettings.powderPriceMode, v -> FishSettings.powderPriceMode = v));
            skyblock.features.add(f);
        }
        {
            Feature f = new Feature("Farming Tracker",
                    () -> FishSettings.farmingTrackerEnabled, v -> FishSettings.farmingTrackerEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "",
                    FishSettings.PriceMode.values(),
                    () -> FishSettings.farmingPriceMode, v -> FishSettings.farmingPriceMode = v));
            skyblock.features.add(f);
        }
        {
            Feature f = new Feature("Harvest Feast",
                    () -> FishSettings.harvestFeastEnabled, v -> FishSettings.harvestFeastEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "",
                    FishSettings.PriceMode.values(),
                    () -> FishSettings.harvestFeastPriceMode, v -> FishSettings.harvestFeastPriceMode = v));
            skyblock.features.add(f);
        }
        {
            Feature f = new Feature("Mining Tracker",
                    () -> FishSettings.miningTrackerEnabled, v -> FishSettings.miningTrackerEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "",
                    FishSettings.PriceMode.values(),
                    () -> FishSettings.miningPriceMode, v -> FishSettings.miningPriceMode = v));
            skyblock.features.add(f);
        }
        columns.add(skyblock);

        // ===== Misc =====
        Column misc = new Column("Misc");
        {
            Feature f = new Feature("Bridge Bot",
                    () -> FishSettings.bridgeBotEnabled, v -> FishSettings.bridgeBotEnabled = v);
            f.sub.add(new InputSetting("Bot IGN", "",
                    () -> FishSettings.bridgeBotName,
                    v -> { FishSettings.bridgeBotName = v; BridgeBot.rebuildPattern(); }));
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
            Feature f = new Feature("Chat Channels", null, null);
            f.sub.add(new ToggleSetting("Guild", "", () -> FishSettings.chatGuild, v -> FishSettings.chatGuild = v));
            f.sub.add(new ToggleSetting("Officer", "", () -> FishSettings.chatOfficer, v -> FishSettings.chatOfficer = v));
            f.sub.add(new ToggleSetting("Private", "", () -> FishSettings.chatPrivate, v -> FishSettings.chatPrivate = v));
            f.sub.add(new ToggleSetting("All Chat", "", () -> FishSettings.chatAll, v -> FishSettings.chatAll = v));
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
            renderColumn(ctx, columns.get(i), colX(i), colsTopY(), mouseX, mouseY, filter);
        }
        renderBottomBar(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderColumn(DrawContext ctx, Column col, int x, int y, int mouseX, int mouseY, String filter) {
        // Header strip
        ctx.fill(x, y, x + COL_W, y + COL_HEADER_H, COL_HEADER_BG);
        ctx.fill(x, y + COL_HEADER_H - 1, x + COL_W, y + COL_HEADER_H, BORDER_COLOR);
        int titleX = x + (COL_W - this.textRenderer.getWidth(col.name)) / 2;
        ctx.drawText(this.textRenderer, col.name, titleX, y + (COL_HEADER_H - 8) / 2, TEXT_COLOR, false);

        int py = y + COL_HEADER_H + 3;
        int colBottom = colsBottomY();
        for (Feature f : col.features) {
            if (!filter.isEmpty() && !f.name.toLowerCase().contains(filter)) continue;
            if (py + PILL_H > colBottom) break;

            boolean on = f.hasMaster() && f.get.get();
            boolean hov = mouseX >= x && mouseX <= x + COL_W && mouseY >= py && mouseY <= py + PILL_H;
            int bg = on ? (hov ? PILL_ON_HOVER : PILL_ON) : (hov ? PILL_OFF_HOVER : PILL_OFF);
            ctx.fill(x, py, x + COL_W, py + PILL_H, bg);

            // Pill label, truncated to column width
            String label = f.name;
            int maxW = COL_W - 12;
            if (this.textRenderer.getWidth(label) > maxW) {
                label = this.textRenderer.trimToWidth(label, maxW - 4) + "…";
            }
            int tx = x + 6;
            int ty = py + (PILL_H - 8) / 2;
            ctx.drawText(this.textRenderer, label, tx, ty, TEXT_COLOR, false);
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
                            if (this.textRenderer.getWidth(name) > nameMaxW) {
                                name = this.textRenderer.trimToWidth(name, nameMaxW - 4) + "…";
                            }
                            ctx.drawText(this.textRenderer, name,
                                    leftX + 2, sy + (sh - 8) / 2, TEXT_COLOR, false);
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
        ctx.fill(bx, by, bx + barW, by + SEARCH_H, COL_HEADER_BG);
        ctx.fill(bx, by, bx + barW, by + 1, searchFocused ? ACCENT : BORDER_COLOR);
        ctx.fill(bx, by + SEARCH_H - 1, bx + barW, by + SEARCH_H, searchFocused ? ACCENT : BORDER_COLOR);
        ctx.fill(bx, by, bx + 1, by + SEARCH_H, BORDER_COLOR);
        ctx.fill(bx + barW - 1, by, bx + barW, by + SEARCH_H, BORDER_COLOR);
        if (searchText.isEmpty() && !searchFocused) {
            ctx.drawText(this.textRenderer, "Search here...", bx + 6, by + (SEARCH_H - 8) / 2, SUBTEXT_COLOR, false);
        } else {
            searchField.render(ctx, mouseX, mouseY, 0);
        }

        // Scale icon → opens existing HUD editor
        int sx = bx + barW + 6;
        boolean sHov = mouseX >= sx && mouseX <= sx + SCALE_BTN_W && mouseY >= by && mouseY <= by + SEARCH_H;
        ctx.fill(sx, by, sx + SCALE_BTN_W, by + SEARCH_H, sHov ? PILL_OFF_HOVER : COL_HEADER_BG);
        ctx.fill(sx, by, sx + SCALE_BTN_W, by + 1, sHov ? ACCENT : BORDER_COLOR);
        ctx.fill(sx, by + SEARCH_H - 1, sx + SCALE_BTN_W, by + SEARCH_H, sHov ? ACCENT : BORDER_COLOR);
        ctx.fill(sx, by, sx + 1, by + SEARCH_H, BORDER_COLOR);
        ctx.fill(sx + SCALE_BTN_W - 1, by, sx + SCALE_BTN_W, by + SEARCH_H, BORDER_COLOR);
        // expand-arrows glyph
        int gx = sx + SCALE_BTN_W / 2 - 4;
        int gy = by + SEARCH_H / 2 - 4;
        int gc = sHov ? ACCENT_HOVER : ACCENT;
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
                                        || s instanceof InputDoubleSetting || s instanceof ColorSetting) {
                                    activeInput = s;
                                }
                            }
                            if (s.onClick(mx, my, leftX, rightX, sy, btn)) return true;
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
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        activeSlider = null;
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
            ctx.fill(leftX, sy, rightX, sy + SUBCAT_HEIGHT, 0xCC11131A);
            ctx.fill(leftX, sy + SUBCAT_HEIGHT - 1, rightX, sy + SUBCAT_HEIGHT, BORDER_COLOR);
            ctx.fill(leftX, sy, leftX + 2, sy + SUBCAT_HEIGHT, ACCENT);
            ctx.drawText(tr, name, leftX + 6, sy + (SUBCAT_HEIGHT - 8) / 2, ACCENT, false);
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
            int bg = on ? (hov ? ACCENT_HOVER : TOGGLE_ON) : (hov ? 0xFF3A3D48 : TOGGLE_OFF);
            ctx.fill(tx, ty, tx + TOGGLE_W, ty + TOGGLE_H, bg);
            String t = on ? "ON" : "OFF";
            ctx.drawText(tr, t, tx + (TOGGLE_W - tr.getWidth(t)) / 2, ty + (TOGGLE_H - 8) / 2, TOGGLE_TEXT, false);
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
            ctx.drawText(tr, val, slx + SLIDER_W - tr.getWidth(val), sly - 9, SUBTEXT_COLOR, false);
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
            ctx.drawText(tr, val, slx + SLIDER_W - tr.getWidth(val), sly - 9, SUBTEXT_COLOR, false);
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
            ctx.drawText(tr, current, bx + 4, by + (TOGGLE_H - 8) / 2, TEXT_COLOR, false);
            // tiny chevron on the right
            ctx.drawText(tr, "›", bx + bw - 7, by + (TOGGLE_H - 8) / 2, ACCENT, false);
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
            textField.setX(ix); textField.setY(iy);
            textField.render(ctx, mx, my, 0);
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
            ctx.drawText(tr, "Open", bx + (bw - tr.getWidth("Open")) / 2, by + (TOGGLE_H - 8) / 2, TEXT_COLOR, false);
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
            textField.setX(ix); textField.setY(iy);
            textField.render(ctx, mx, my, 0);
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
            textField.setX(ix); textField.setY(iy);
            textField.render(ctx, mx, my, 0);
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
