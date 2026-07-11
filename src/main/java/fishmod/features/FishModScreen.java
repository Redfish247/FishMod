package fishmod.features;

import fishmod.utils.config.Config;
import fishmod.utils.config.FishConfig;
import fishmod.cosmetic.NickState;
import fishmod.features.BridgeBot;
import fishmod.utils.config.values.*;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.dungeon.Split;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Sidebar + detail-list config screen (matches the FishMod design mockup).
 *
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  FishMod                                       [ search ]  │  title bar
 *  ├───────────┬──────────────────────────────────────────────┤
 *  │  General  │  [icon]  Label                        ( ON )  │
 *  │  Dungeon  │          description                          │  scrollable
 *  │  Cosmetic │  [icon]  Label                        ( OFF)  │  feature rows
 *  │  Party    │  ...                                          │
 *  │  Visuals  │                                               │
 *  ├───────────┴──────────────────────────────────────────────┤
 *  │  Edit HUD                       Reset      Save & Close    │  footer
 *  └──────────────────────────────────────────────────────────┘
 *
 * Left-click a category to switch pages. Left-click a feature toggle = master on/off.
 * Left-click a feature row body (when it has sub-settings) = expand an inline panel
 * beneath it with the rich controls (sliders, dropdowns, colour pickers, text inputs).
 */
public class FishModScreen extends Screen {

    // ----- palette -----
    static final int ACCENT          = 0xFF24B6B0;  // bright slate-teal
    static final int ACCENT_HOVER    = 0xFF3AD8D1;
    static final int BG_TOP          = 0xFF0C1318;
    static final int BG_BOT          = 0xFF06090C;
    static final int SIDEBAR_BG      = 0xFF0A0F14;
    static final int CONTENT_BG      = 0xFF090E12;
    static final int ROW_BG          = 0xFF0E151B;
    static final int ROW_BG_HOVER    = 0xFF142028;
    static final int SUBROW_BG       = 0xFF0A1015;
    static final int TILE_BG         = 0xFF10282B;
    static final int TILE_BG_ON      = 0xFF123A3C;
    static final int DIVIDER         = 0xFF18222C;
    static final int SCRIM           = 0xB3000000;  // dim the live game behind the overlay
    static final int PANEL_BORDER    = 0xFF24333C;  // 1px frame around the overlay
    static final int TEXT_COLOR      = 0xFFEDF1F5;
    static final int SUBTEXT_COLOR   = 0xFF7E8A98;
    static final int CHEVRON_COLOR   = 0xFF5A6675;
    static final int PANEL_BG        = 0xFF12141A;  // (legacy, referenced by helpers)
    static final int BORDER_COLOR    = 0xFF2A2D38;  // (legacy)

    // ----- setting-widget palette (consumed by the Setting subclasses below) -----
    static final int TOGGLE_ON       = ACCENT;
    static final int TOGGLE_OFF      = 0xFF2A2D38;
    static final int TOGGLE_TEXT     = 0xFFFFFFFF;
    static final int SLIDER_BG       = 0xFF1B1D24;
    static final int SLIDER_FILL     = ACCENT;

    // Sub-panel menu text scale (the rich controls render compactly).
    static final float TEXT_SCALE = 0.75f;

    // ----- layout -----
    static final int SIDEBAR_W   = 150;
    static final int TITLE_H     = 38;
    static final int FOOTER_H    = 40;
    static final int CONTENT_PAD = 12;
    static final int ROW_H       = 38;
    static final int ROW_GAP     = 5;
    static final int CAT_ITEM_H  = 34;
    static final int TOG2_W      = 40;   // big row toggle
    static final int TOG2_H      = 18;

    // ----- overlay panel geometry (fixed-size, centred; the game shows through behind) -----
    static final int PANEL_W     = 700;
    static final int PANEL_H     = 460;

    // ----- setting-widget geometry (consumed below) -----
    static final int ITEM_HEIGHT   = 20;
    static final int TOGGLE_W       = 36;
    static final int TOGGLE_H       = 14;
    static final int SLIDER_W       = 64;
    static final int SLIDER_H       = 5;
    static final int INPUT_W        = 70;
    static final int INPUT_H        = 14;
    static final int SUBCAT_HEIGHT  = 13;

    // ----- state -----
    private final List<Column> columns = new ArrayList<>();
    private int selectedCat = 0;
    private int scroll = 0;
    private Feature selectedFeature = null;
    private String searchText = "";
    private boolean searchFocused = false;
    private Setting activeSlider = null;
    private int activeSliderX = 0;
    private Setting activeInput = null;
    private ColorPickerSetting activePicker = null;
    private KeybindSetting capturingKeybind = null;
    private EditBox searchField;
    private boolean resetArmed = false;
    private long resetArmedAt = 0;

    public FishModScreen() {
        super(Component.literal("FishMod"));
        buildCategories();
    }

    // -----------------------------------------------------------------------------------
    // Drawing helpers
    // -----------------------------------------------------------------------------------

    /** Filled rectangle with square corners (per the design). {@code r} ignored — retained for call sites. */
    static void roundRect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int r, int color) { ctx.fill(x1, y1, x2, y2, color); }
    static void roundRect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) { ctx.fill(x1, y1, x2, y2, color); }

    /** Full capsule (square corners here): used by toggle tracks/knobs and slider bars. */
    static void pill(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) { ctx.fill(x1, y1, x2, y2, color); }

    /** 1px border frame around a fill. */
    static void panel(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int r, int fill, int border) {
        ctx.fill(x1, y1, x2, y2, border);
        ctx.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    /** Octagon-ish solid disc (square corners trimmed) — good enough for small circular glyphs. */
    static void disc(GuiGraphicsExtractor ctx, int cx, int cy, int r, int color) {
        ctx.fill(cx - r, cy - r + 1, cx + r, cy + r - 1, color);
        ctx.fill(cx - r + 1, cy - r, cx + r - 1, cy + r, color);
    }

    /** Sub-panel menu text at TEXT_SCALE. */
    static void st(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr, String s, int x, int y, int color) {
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y + 1f);
        ctx.pose().scale(TEXT_SCALE, TEXT_SCALE);
        ctx.text(tr, s, 0, 0, color, false);
        ctx.pose().popMatrix();
    }
    static int stw(net.minecraft.client.gui.Font tr, String s) { return (int) Math.ceil(tr.width(s) * TEXT_SCALE); }

    /** Text at an arbitrary scale. */
    static void sst(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr, String s, int x, int y, int color, float scale) {
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(scale, scale);
        ctx.text(tr, s, 0, 0, color, false);
        ctx.pose().popMatrix();
    }
    static int sw(net.minecraft.client.gui.Font tr, String s, float scale) { return (int) Math.ceil(tr.width(s) * scale); }

    /** Chevron from fills: ▾ when {@code open}, ▸ when closed; {@code cy} is the vertical centre. */
    static void drawChevron(GuiGraphicsExtractor ctx, int gx, int cy, boolean open, int color) {
        if (open) {
            ctx.fill(gx,     cy - 2, gx + 7, cy - 1, color);
            ctx.fill(gx + 1, cy - 1, gx + 6, cy,     color);
            ctx.fill(gx + 2, cy,     gx + 5, cy + 1, color);
            ctx.fill(gx + 3, cy + 1, gx + 4, cy + 2, color);
        } else {
            ctx.fill(gx,     cy - 3, gx + 1, cy + 4, color);
            ctx.fill(gx + 1, cy - 2, gx + 2, cy + 3, color);
            ctx.fill(gx + 2, cy - 1, gx + 3, cy + 2, color);
            ctx.fill(gx + 3, cy,     gx + 4, cy + 1, color);
        }
    }

    /** Tiny vector emblem (~14px) centred at (cx,cy). {@code bg} is the tile fill, for knockouts. */
    private static void drawGlyph(GuiGraphicsExtractor ctx, String t, int cx, int cy, int c, int bg) {
        switch (t) {
            case "gear" -> {
                disc(ctx, cx, cy, 5, c);
                ctx.fill(cx - 1, cy - 7, cx + 1, cy + 7, c); ctx.fill(cx - 7, cy - 1, cx + 7, cy + 1, c);
                ctx.fill(cx - 5, cy - 5, cx - 3, cy - 3, c); ctx.fill(cx + 3, cy - 5, cx + 5, cy - 3, c);
                ctx.fill(cx - 5, cy + 3, cx - 3, cy + 5, c); ctx.fill(cx + 3, cy + 3, cx + 5, cy + 5, c);
                disc(ctx, cx, cy, 2, bg);
            }
            case "arch" -> {
                ctx.fill(cx - 6, cy - 6, cx - 3, cy + 7, c); ctx.fill(cx + 3, cy - 6, cx + 6, cy + 7, c);
                ctx.fill(cx - 6, cy - 6, cx + 6, cy - 3, c);
            }
            case "hanger" -> {
                ctx.fill(cx - 7, cy + 2, cx + 7, cy + 4, c);
                ctx.fill(cx - 1, cy - 5, cx + 1, cy + 3, c);
                ctx.fill(cx - 1, cy - 6, cx + 3, cy - 4, c);
            }
            case "people" -> {
                disc(ctx, cx - 4, cy - 3, 3, c); disc(ctx, cx + 4, cy - 3, 3, c);
                ctx.fill(cx - 7, cy + 2, cx + 7, cy + 6, c);
            }
            case "eye" -> {
                ctx.fill(cx - 7, cy - 1, cx + 7, cy + 1, c); ctx.fill(cx - 5, cy - 3, cx + 5, cy + 3, c);
                disc(ctx, cx, cy, 2, bg); disc(ctx, cx, cy, 1, c);
            }
            case "text" -> {
                ctx.fill(cx - 5, cy - 5, cx + 5, cy - 3, c); ctx.fill(cx - 1, cy - 5, cx + 1, cy + 6, c);
            }
            case "chat" -> {
                ctx.fill(cx - 7, cy - 5, cx + 7, cy + 2, c); ctx.fill(cx - 5, cy + 2, cx - 1, cy + 6, c);
                ctx.fill(cx - 4, cy - 2, cx + 4, cy - 1, bg); ctx.fill(cx - 4, cy, cx + 2, cy + 1, bg);
            }
            case "star" -> {
                ctx.fill(cx - 1, cy - 7, cx + 1, cy + 7, c); ctx.fill(cx - 7, cy - 1, cx + 7, cy + 1, c);
                ctx.fill(cx - 4, cy - 4, cx - 2, cy - 2, c); ctx.fill(cx + 2, cy - 4, cx + 4, cy - 2, c);
                ctx.fill(cx - 4, cy + 2, cx - 2, cy + 4, c); ctx.fill(cx + 2, cy + 2, cx + 4, cy + 4, c);
            }
            case "cube" -> {
                ctx.fill(cx - 6, cy - 6, cx + 6, cy - 4, c); ctx.fill(cx - 6, cy + 4, cx + 6, cy + 6, c);
                ctx.fill(cx - 6, cy - 6, cx - 4, cy + 6, c); ctx.fill(cx + 4, cy - 6, cx + 6, cy + 6, c);
            }
            case "clock" -> {
                disc(ctx, cx, cy, 6, c); disc(ctx, cx, cy, 4, bg);
                ctx.fill(cx - 1, cy - 4, cx + 1, cy + 1, c); ctx.fill(cx - 1, cy - 1, cx + 4, cy + 1, c);
            }
            case "coin" -> {
                disc(ctx, cx, cy, 6, c); disc(ctx, cx, cy, 3, bg); disc(ctx, cx, cy, 1, c);
            }
            case "palette" -> {
                disc(ctx, cx, cy, 6, c);
                ctx.fill(cx - 3, cy - 3, cx - 1, cy - 1, bg); ctx.fill(cx + 1, cy - 3, cx + 3, cy - 1, bg);
                ctx.fill(cx - 1, cy + 1, cx + 1, cy + 3, bg);
            }
            case "tag" -> {
                ctx.fill(cx - 6, cy - 4, cx + 2, cy + 4, c); ctx.fill(cx + 2, cy - 3, cx + 4, cy + 3, c);
                ctx.fill(cx + 4, cy - 1, cx + 6, cy + 1, c); disc(ctx, cx - 3, cy, 1, bg);
            }
            case "slider" -> {
                ctx.fill(cx - 7, cy - 1, cx + 7, cy + 1, c); ctx.fill(cx, cy - 4, cx + 4, cy + 4, c);
            }
            case "bell" -> {
                ctx.fill(cx - 4, cy - 3, cx + 4, cy + 3, c); ctx.fill(cx - 5, cy + 3, cx + 5, cy + 4, c);
                ctx.fill(cx - 1, cy - 6, cx + 1, cy - 4, c); ctx.fill(cx - 1, cy + 4, cx + 1, cy + 6, c);
            }
            case "map" -> {
                ctx.fill(cx - 6, cy - 5, cx + 6, cy + 5, c); ctx.fill(cx - 1, cy - 5, cx + 1, cy + 5, bg);
                ctx.fill(cx - 6, cy - 1, cx + 6, cy + 1, bg);
            }
            default -> { // box
                ctx.fill(cx - 5, cy - 5, cx + 5, cy - 3, c); ctx.fill(cx - 5, cy + 3, cx + 5, cy + 5, c);
                ctx.fill(cx - 5, cy - 5, cx - 3, cy + 5, c); ctx.fill(cx + 3, cy - 5, cx + 5, cy + 5, c);
            }
        }
    }

    /** Per-feature glyph (looked up by name to avoid threading an icon field through every Feature). */
    private static String iconFor(String name) {
        return switch (name) {
            case "Mod Prefix" -> "text";
            case "Inventory Buttons" -> "cube";
            case "Auto Meow", "Smart Copy Chat", "Bridge Bot", "Death Message", "Chat Channels", "Chat Filter" -> "chat";
            case "Explosive Shot" -> "star";
            case "Compact Tab", "Party Commands" -> "people";
            case "Dungeon Score", "Session Stats", "Pet HUD", "Trophy Frogs" -> "star";
            case "Puzzle Overlay", "Simon Says", "M7 Lever Waypoints" -> "cube";
            case "Send Lag to Party", "Splits", "Cooldown Overlay", "Fire Freeze Timer",
                 "Maxor Tick Timer", "Crystal Spawn", "Storm Tick Timer", "Storm Death Time",
                 "Goldor Tick Timer", "Term Start Timer", "Goldor Splits", "LB Release Timer" -> "clock";
            case "Storm Crushed Noti" -> "bell";
            case "Section Progress" -> "star";
            case "Loot Tracker",
                 "Slayer XP Tracker", "Skill XP Tracker", "Powder Tracker",
                 "Farming Tracker", "Harvest Feast Tracker", "Mining Tracker" -> "coin";
            case "Bobber Reminder" -> "bell";
            case "Sea Creatures", "Trophy Fish", "Slayer Drops" -> "coin";
            case "Slayer Alerts" -> "bell";
            case "Class Colored Boots", "Name Color", "Customize", "Rarity Background" -> "palette";
            case "See Others' Items" -> "eye";
            case "Nametag" -> "tag";
            case "Player Size" -> "slider";
            case "Soulflow HUD" -> "bell";
            case "Warp Map" -> "map";
            default -> "box";
        };
    }

    /** Short one-line description shown under each row label. */
    private static String descFor(String name) {
        return switch (name) {
            case "Mod Prefix" -> "Tag FishMod's chat output with a prefix";
            case "Inventory Buttons" -> "Clickable command buttons in your inventory";
            case "Auto Meow" -> "Auto-reply 'meow' when someone meows";
            case "Smart Copy Chat" -> "Right-click a chat line to copy it";
            case "Compact Tab" -> "Cleaner custom tab player list";
            case "Bridge Bot" -> "Relay Discord bridge messages";
            case "Chat Filter" -> "Hide selected chat spam";
            case "Explosive Shot" -> "Title with per-enemy damage";
            case "Dungeon Score" -> "Live S+ score tracker overlay";
            case "Puzzle Overlay" -> "Show solved puzzle names";
            case "Death Message" -> "Announce deaths with a template";
            case "Send Lag to Party" -> "Warn the party when your game lags";
            case "Splits" -> "Phase split timers for runs";
            case "Session Stats" -> "Per-session run statistics HUD";
            case "Loot Tracker" -> "Manual drop & profit tracker (D Hub inv)";
            case "Simon Says" -> "F7 Goldor device solver";
            case "Class Colored Boots" -> "Dye boots by your dungeon class";
            case "M7 Lever Waypoints" -> "See F7/M7 levers through walls";
            case "Maxor Tick Timer" -> "Tick timer during Maxor (P1)";
            case "Crystal Spawn" -> "Crystal spawn countdown + reminder";
            case "Storm Tick Timer" -> "Tick timer during Storm (P2)";
            case "Storm Death Time" -> "Show when Storm died";
            case "LB Release Timer" -> "Countdown 30s–34.35s: when to shoot Last Breath";
            case "Storm Crushed Noti" -> "Alert when Storm is crushed";
            case "Goldor Tick Timer" -> "Terminal-phase tick timer";
            case "Term Start Timer" -> "Countdown to terminals start";
            case "Section Progress" -> "Terminal section completed/total";
            case "Goldor Splits" -> "S1-S4 terminal split timers + total time";
            case "Name Color" -> "Recolor your username gradient";
            case "See Others' Items" -> "Render other users' item cosmetics";
            case "Customize" -> "Rename, dye & re-model your items";
            case "Nametag" -> "Show your own above-head nametag";
            case "Player Size" -> "Resize your model (render only)";
            case "Party Commands" -> "Dot-commands usable in party chat";
            case "Chat Channels" -> "Where dot-commands are allowed";
            case "Rarity Background" -> "Rarity-colored backing on all slots";
            case "Cooldown Overlay" -> "Ability cooldowns on item slots";
            case "Pet HUD" -> "Show your active pet & level";
            case "Soulflow HUD" -> "Track your soulflow count";
            case "Fire Freeze Timer" -> "Fire Freeze staff cooldown timer";
            case "Warp Map" -> "Mini warp map HUD";
            case "Slayer XP Tracker" -> "Slayer XP per hour overlay";
            case "Skill XP Tracker" -> "Skill XP per hour overlay";
            case "Powder Tracker" -> "Powder & gemstone gains";
            case "Farming Tracker" -> "Farming coins per hour";
            case "Harvest Feast Tracker" -> "Harvest Feast event tracker";
            case "Mining Tracker" -> "Mining coins per hour";
            case "Trophy Frogs" -> "Trophy frog catch tracker";
            case "Bobber Reminder" -> "Reel-in countdown, alert & missed HUD";
            case "Sea Creatures" -> "Sea creature counts & rare-catch alert";
            case "Trophy Fish" -> "Trophy fish catch tracker (Crimson)";
            case "Slayer Alerts" -> "Title + ping on slayer boss events";
            case "Slayer Drops" -> "Session rare-drop counter";
            default -> "";
        };
    }

    /** Builds a command-input row for an inventory button (the hint reminds it's a command, no slash). */
    private static InputSetting makeButtonInput(String name, Supplier<String> getter, Consumer<String> setter) {
        InputSetting s = new InputSetting(name, "", getter, setter);
        s.hint = "command without /";
        return s;
    }

    // -----------------------------------------------------------------------------------
    // Category / feature graph
    // -----------------------------------------------------------------------------------
    private void buildCategories() {
        Column general   = new Column("General",   "gear");
        Column dungeon   = new Column("Dungeon",   "arch");
        Column cosmetics = new Column("Cosmetics", "hanger");
        Column party     = new Column("Party",     "people");
        Column visuals   = new Column("Visuals",   "eye");
        Column floor7    = new Column("Floor 7",   "arch");

        // ===== General =====
        {
            Feature f = new Feature("Mod Prefix",
                    () -> FishSettings.modPrefixEnabled, v -> FishSettings.modPrefixEnabled = v);
            f.sub.add(new InputSetting("Prefix", "",
                    () -> FishSettings.modPrefix,
                    v -> FishSettings.modPrefix = (v != null && v.length() > 10) ? v.substring(0, 10) : v));
            general.features.add(f);
        }
        {
            Feature f = new Feature("Inventory Buttons",
                    () -> fishmod.utils.config.values.Buttons.enableInventoryButtons,
                    v -> fishmod.utils.config.values.Buttons.enableInventoryButtons = v);
            f.sub.add(makeButtonInput("Button 1", () -> fishmod.utils.config.values.Buttons.command1, v -> fishmod.utils.config.values.Buttons.command1 = v));
            f.sub.add(makeButtonInput("Button 2", () -> fishmod.utils.config.values.Buttons.command2, v -> fishmod.utils.config.values.Buttons.command2 = v));
            f.sub.add(makeButtonInput("Button 3", () -> fishmod.utils.config.values.Buttons.command3, v -> fishmod.utils.config.values.Buttons.command3 = v));
            f.sub.add(makeButtonInput("Button 4", () -> fishmod.utils.config.values.Buttons.command4, v -> fishmod.utils.config.values.Buttons.command4 = v));
            f.sub.add(makeButtonInput("Button 5", () -> fishmod.utils.config.values.Buttons.command5, v -> fishmod.utils.config.values.Buttons.command5 = v));
            f.sub.add(makeButtonInput("Button 6", () -> fishmod.utils.config.values.Buttons.command6, v -> fishmod.utils.config.values.Buttons.command6 = v));
            f.sub.add(makeButtonInput("Button 7", () -> fishmod.utils.config.values.Buttons.command7, v -> fishmod.utils.config.values.Buttons.command7 = v));
            general.features.add(f);
        }
        {
            Feature f = new Feature("Wardrobe Hotkeys",
                    () -> FishSettings.wardrobeHotkeysEnabled, v -> FishSettings.wardrobeHotkeysEnabled = v);
            f.sub.add(new ToggleSetting("Auto-Close GUI", "",
                    () -> FishSettings.wardrobeHotkeysAutoClose, v -> FishSettings.wardrobeHotkeysAutoClose = v));
            f.sub.add(new SubcategoryHeader("Click a slot, then press a key/mouse button (Esc unbinds)"));
            for (int i = 0; i < fishmod.utils.Keybinds.wardrobeSlots.length; i++) {
                final int idx = i;
                f.sub.add(new KeybindSetting("Slot " + (idx + 1), "",
                        () -> fishmod.utils.Keybinds.wardrobeSlots[idx]));
            }
            general.features.add(f);
        }
        {
            Feature f = new Feature("Location Ping",
                    () -> FishSettings.pingEnabled, v -> FishSettings.pingEnabled = v);
            f.sub.add(new SubcategoryHeader("Key: Options > Controls > FishMod (default middle mouse)"));
            f.sub.add(new ToggleSetting("Play Sound", "",
                    () -> FishSettings.pingSound, v -> FishSettings.pingSound = v));
            f.sub.add(new ToggleSetting("Announce to Party", "",
                    () -> FishSettings.pingAnnounceParty, v -> FishSettings.pingAnnounceParty = v));
            f.sub.add(new ToggleSetting("Share w/ FishMod Users", "",
                    () -> FishSettings.pingShareEnabled, v -> FishSettings.pingShareEnabled = v));
            f.sub.add(new ToggleSetting("Waypoint from Chat Coords", "",
                    () -> FishSettings.pingFromChat, v -> FishSettings.pingFromChat = v));
            f.sub.add(new SliderIntSetting("Duration s", "",
                    () -> FishSettings.pingDurationSeconds, v -> FishSettings.pingDurationSeconds = v, 2, 30));
            general.features.add(f);
        }
        {
            Feature f = new Feature("TTS Callouts",
                    () -> FishSettings.ttsEnabled, v -> FishSettings.ttsEnabled = v);
            f.sub.add(new ToggleSetting("Rare Drops", "",
                    () -> FishSettings.ttsRareDrops, v -> FishSettings.ttsRareDrops = v));
            f.sub.add(new ToggleSetting("Slayer", "",
                    () -> FishSettings.ttsSlayer, v -> FishSettings.ttsSlayer = v));
            f.sub.add(new ToggleSetting("Fishing Reel", "",
                    () -> FishSettings.ttsFishing, v -> FishSettings.ttsFishing = v));
            f.sub.add(new SliderIntSetting("Rate (Win)", "",
                    () -> FishSettings.ttsRate, v -> FishSettings.ttsRate = v, -10, 10));
            general.features.add(f);
        }
        {
            Feature f = new Feature("Streamer Mode",
                    () -> FishSettings.streamerMode, v -> FishSettings.streamerMode = v);
            f.sub.add(new ToggleSetting("Hide Tab Names", "",
                    () -> FishSettings.streamerHideTab, v -> FishSettings.streamerHideTab = v));
            general.features.add(f);
        }
        general.features.add(new Feature("Player Flags",
                () -> FishSettings.repFlagsEnabled, v -> FishSettings.repFlagsEnabled = v));
        general.features.add(new Feature("Auto Meow",
                () -> FishSettings.chatMeow, v -> FishSettings.chatMeow = v));
        general.features.add(new Feature("Smart Copy Chat",
                () -> FishSettings.smartCopyChat, v -> FishSettings.smartCopyChat = v));
        general.features.add(new Feature("Compact Chat",
                () -> FishSettings.chatCompact, v -> FishSettings.chatCompact = v));
        {
            Feature f = new Feature("Compact Tab",
                    () -> FishSettings.compactTabEnabled, v -> FishSettings.compactTabEnabled = v);
            f.sub.add(new SliderIntSetting("Opacity %", "",
                    () -> FishSettings.compactTabOpacity, v -> FishSettings.compactTabOpacity = v, 0, 100));
            general.features.add(f);
        }
        {
            Feature f = new Feature("Bridge Bot",
                    () -> FishSettings.bridgeBotEnabled, v -> FishSettings.bridgeBotEnabled = v);
            f.sub.add(new InputSetting("Bot IGN", "",
                    () -> FishSettings.bridgeBotName,
                    v -> { FishSettings.bridgeBotName = v; BridgeBot.rebuildPattern(); }));
            general.features.add(f);
        }
        {
            Feature f = new Feature("Chat Filter",
                    () -> FishSettings.chatFilterEnabled, v -> FishSettings.chatFilterEnabled = v);
            f.sub.add(new ToggleSetting("Kill Combo", "",
                    () -> FishSettings.cfKillCombo, v -> FishSettings.cfKillCombo = v));
            f.sub.add(new ToggleSetting("Boss Messages", "",
                    () -> FishSettings.cfBossMessages, v -> FishSettings.cfBossMessages = v));
            f.sub.add(new ToggleSetting("Friend Join/Leave", "",
                    () -> FishSettings.cfFriendJoinLeave, v -> FishSettings.cfFriendJoinLeave = v));
            f.sub.add(new ToggleSetting("Bazaar", "",
                    () -> FishSettings.cfBazaar, v -> FishSettings.cfBazaar = v));
            f.sub.add(new ToggleSetting("Warping", "",
                    () -> FishSettings.cfWarping, v -> FishSettings.cfWarping = v));
            general.features.add(f);
        }

        // ===== Dungeon =====
        dungeon.features.add(new Feature("Dungeon Score",
                () -> FishSettings.dungeonScoreEnabled, v -> FishSettings.dungeonScoreEnabled = v));
        dungeon.features.add(new Feature("PB Pace",
                () -> FishSettings.pbPaceEnabled, v -> FishSettings.pbPaceEnabled = v));
        dungeon.features.add(new Feature("Puzzle Overlay",
                () -> FishSettings.showPuzzles, v -> FishSettings.showPuzzles = v));
        {
            Feature f = new Feature("Death Message",
                    () -> FishSettings.deathMessageEnabled, v -> FishSettings.deathMessageEnabled = v);
            InputSetting tmpl = new InputSetting("Template", "",
                    () -> FishSettings.deathMessageTemplate, v -> FishSettings.deathMessageTemplate = v);
            tmpl.hint = "{name} = player who died";
            f.sub.add(tmpl);
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
            Feature f = new Feature("Loot Tracker",
                    () -> FishSettings.lootTrackerEnabled, v -> FishSettings.lootTrackerEnabled = v);
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
        dungeon.features.add(new Feature("Class Colored Boots",
                () -> FishSettings.classColoredBootsEnabled, v -> FishSettings.classColoredBootsEnabled = v));
        {
            Feature f = new Feature("M7 Lever Waypoints",
                    () -> FishSettings.enableM7LeverWaypoints, v -> FishSettings.enableM7LeverWaypoints = v);
            f.sub.add(new ColorPickerSetting("Box Color", "",
                    () -> FishSettings.m7LeverWaypointColor, v -> FishSettings.m7LeverWaypointColor = v));
            dungeon.features.add(f);
        }
        {
            Feature f = new Feature("Dungeon Map",
                    () -> fishmod.utils.config.values.DungeonMapSettings.enabled,
                    v -> fishmod.utils.config.values.DungeonMapSettings.enabled = v);
            f.sub.add(new ToggleSetting("Room Names", "",
                    () -> fishmod.utils.config.values.DungeonMapSettings.showRoomNames,
                    v -> fishmod.utils.config.values.DungeonMapSettings.showRoomNames = v));
            f.sub.add(new ToggleSetting("Secret Counts", "",
                    () -> fishmod.utils.config.values.DungeonMapSettings.showSecretCounts,
                    v -> fishmod.utils.config.values.DungeonMapSettings.showSecretCounts = v));
            f.sub.add(new ToggleSetting("Predict Undiscovered Types", "Blends colors for undiscovered rooms once your local room database narrows them down. Improves the more you dungeon.",
                    () -> fishmod.utils.config.values.DungeonMapSettings.predictionLayerEnabled,
                    v -> fishmod.utils.config.values.DungeonMapSettings.predictionLayerEnabled = v));
            f.sub.add(new ToggleSetting("Player Markers", "",
                    () -> fishmod.utils.config.values.DungeonMapSettings.showPlayerMarkers,
                    v -> fishmod.utils.config.values.DungeonMapSettings.showPlayerMarkers = v));
            f.sub.add(new ColorPickerSetting("Normal Room", "",
                    () -> fishmod.utils.config.values.DungeonMapSettings.normalColor,
                    v -> fishmod.utils.config.values.DungeonMapSettings.normalColor = v));
            f.sub.add(new ColorPickerSetting("Puzzle Room", "",
                    () -> fishmod.utils.config.values.DungeonMapSettings.puzzleColor,
                    v -> fishmod.utils.config.values.DungeonMapSettings.puzzleColor = v));
            f.sub.add(new ColorPickerSetting("Trap Room", "",
                    () -> fishmod.utils.config.values.DungeonMapSettings.trapColor,
                    v -> fishmod.utils.config.values.DungeonMapSettings.trapColor = v));
            f.sub.add(new ColorPickerSetting("Miniboss Room", "",
                    () -> fishmod.utils.config.values.DungeonMapSettings.minibossColor,
                    v -> fishmod.utils.config.values.DungeonMapSettings.minibossColor = v));
            f.sub.add(new ColorPickerSetting("Fairy Room", "",
                    () -> fishmod.utils.config.values.DungeonMapSettings.fairyColor,
                    v -> fishmod.utils.config.values.DungeonMapSettings.fairyColor = v));
            f.sub.add(new ColorPickerSetting("Blood Room", "",
                    () -> fishmod.utils.config.values.DungeonMapSettings.bloodColor,
                    v -> fishmod.utils.config.values.DungeonMapSettings.bloodColor = v));
            dungeon.features.add(f);
        }

        // ===== Cosmetics =====
        {
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
            f.sub.add(new ConditionalColorPickerSetting("End Color", "",
                    () -> "GRADIENT".equalsIgnoreCase(FishSettings.nickColorMode),
                    () -> FishSettings.nickColorEnd,
                    v -> { FishSettings.nickColorEnd = v; if (NickState.isActive()) NickState.applyFromSettings(); }));
            f.sub.add(new ToggleSetting("See Others", "",
                    () -> FishSettings.remoteNicksEnabled, v -> FishSettings.remoteNicksEnabled = v));
            cosmetics.features.add(f);
        }
        {
            Feature f = new Feature("See Others' Items",
                    () -> FishSettings.remoteItemsEnabled,
                    v -> { FishSettings.remoteItemsEnabled = v;
                           if (v) fishmod.cosmetic.RemoteSync.forceSync();
                           else fishmod.cosmetic.RemoteItems.clearAll(); });
            cosmetics.features.add(f);
        }
        {
            Feature f = new Feature("Customize", null, null);
            f.sub.add(new ButtonSetting("Open", "",
                    () -> Minecraft.getInstance().setScreen(new fishmod.features.ItemCustomizeScreen())));
            cosmetics.features.add(f);
        }
        {
            Feature f = new Feature("Nametag",
                    () -> FishSettings.nickPreviewEnabled, v -> FishSettings.nickPreviewEnabled = v);
            f.sub.add(new SliderDoubleSetting("Height", "",
                    () -> FishSettings.nickPreviewYOffset, v -> FishSettings.nickPreviewYOffset = v, -1.5, 1.0));
            cosmetics.features.add(f);
        }
        {
            Feature f = new Feature("Player Size",
                    () -> FishSettings.playerSizeEnabled,
                    v -> { FishSettings.playerSizeEnabled = v; fishmod.cosmetic.PlayerSize.uploadOwn(); });
            f.sub.add(new SliderDoubleSetting("Width (X)", "",
                    () -> FishSettings.playerSizeScaleX,
                    v -> { FishSettings.playerSizeScaleX = v; fishmod.cosmetic.PlayerSize.uploadOwn(); },
                    fishmod.cosmetic.PlayerSize.MIN, fishmod.cosmetic.PlayerSize.MAX));
            f.sub.add(new SliderDoubleSetting("Height (Y)", "",
                    () -> FishSettings.playerSizeScaleY,
                    v -> { FishSettings.playerSizeScaleY = v; fishmod.cosmetic.PlayerSize.uploadOwn(); },
                    fishmod.cosmetic.PlayerSize.MIN, fishmod.cosmetic.PlayerSize.MAX));
            f.sub.add(new SliderDoubleSetting("Depth (Z)", "",
                    () -> FishSettings.playerSizeScaleZ,
                    v -> { FishSettings.playerSizeScaleZ = v; fishmod.cosmetic.PlayerSize.uploadOwn(); },
                    fishmod.cosmetic.PlayerSize.MIN, fishmod.cosmetic.PlayerSize.MAX));
            f.sub.add(new ToggleSetting("Share w/ All", "",
                    () -> FishSettings.playerSizeShared,
                    v -> { FishSettings.playerSizeShared = v;
                           if (v) { fishmod.cosmetic.PlayerSize.uploadOwn(); fishmod.cosmetic.RemoteSync.forceSync(); }
                           else { fishmod.cosmetic.PlayerSize.clearOwnShare(); fishmod.cosmetic.RemoteScales.clearAll(); } }));
            cosmetics.features.add(f);
        }
        {
            Feature f = new Feature("Desk-Buddy",
                    () -> FishSettings.deskBuddyEnabled, v -> FishSettings.deskBuddyEnabled = v);
            f.sub.add(new InputSetting("Name", "",
                    () -> FishSettings.deskBuddyName, v -> FishSettings.deskBuddyName = v));
            f.sub.add(new ToggleSetting("Dance on RNG", "",
                    () -> FishSettings.deskBuddyReactToRng, v -> FishSettings.deskBuddyReactToRng = v));
            f.sub.add(new SliderIntSetting("AFK Sleep s", "",
                    () -> FishSettings.deskBuddyAfkSeconds, v -> FishSettings.deskBuddyAfkSeconds = v, 10, 600));
            cosmetics.features.add(f);
        }

        // ===== Party =====
        {
            Feature f = new Feature("Party Commands", null, null);
            f.sub.add(new ToggleSetting(".ai", "", () -> FishSettings.pcAllinvite, v -> FishSettings.pcAllinvite = v));
            f.sub.add(new ToggleSetting(".pb", "", () -> FishSettings.pcPb, v -> FishSettings.pcPb = v));
            f.sub.add(new ToggleSetting(".cata", "", () -> FishSettings.pcCata, v -> FishSettings.pcCata = v));
            f.sub.add(new ToggleSetting(".rtca", "", () -> FishSettings.pcRtca, v -> FishSettings.pcRtca = v));
            f.sub.add(new ToggleSetting(".rtc", "", () -> FishSettings.pcRtc, v -> FishSettings.pcRtc = v));
            f.sub.add(new ToggleSetting(".crtc", "", () -> FishSettings.pcCrtc, v -> FishSettings.pcCrtc = v));
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
            f.sub.add(new ToggleSetting(".worm / .scatha", "", () -> FishSettings.pcWorm, v -> FishSettings.pcWorm = v));
            f.sub.add(new ToggleSetting(".help / .?", "", () -> FishSettings.pcHelp, v -> FishSettings.pcHelp = v));
            f.sub.add(new ToggleSetting("Party Actions", "", () -> FishSettings.pcPartyActions, v -> FishSettings.pcPartyActions = v));
            party.features.add(f);
        }
        {
            Feature f = new Feature("Chat Channels", null, null);
            f.sub.add(new ToggleSetting("Personal Messages", "", () -> FishSettings.chatPrivate, v -> FishSettings.chatPrivate = v));
            f.sub.add(new ToggleSetting("Party", "", () -> FishSettings.chatParty, v -> FishSettings.chatParty = v));
            f.sub.add(new ToggleSetting("Guild", "", () -> FishSettings.chatGuild, v -> FishSettings.chatGuild = v));
            f.sub.add(new ToggleSetting("All", "", () -> FishSettings.chatAll, v -> FishSettings.chatAll = v));
            party.features.add(f);
        }

        // ===== Visuals =====
        {
            Feature f = new Feature("Rarity Background",
                    () -> Visual.itemRarityBackground, v -> Visual.itemRarityBackground = v);
            f.sub.add(new ToggleSetting("Circular", "",
                    () -> Visual.circularRarityBackground, v -> Visual.circularRarityBackground = v));
            visuals.features.add(f);
        }
        {
            Feature f = new Feature("Cooldown Overlay",
                    () -> FishSettings.cooldownOverlayEnabled, v -> FishSettings.cooldownOverlayEnabled = v);
            f.sub.add(new ToggleSetting("Show Number", "",
                    () -> FishSettings.cooldownShowText, v -> FishSettings.cooldownShowText = v));
            f.sub.add(new ToggleSetting("Under 3s Only", "",
                    () -> FishSettings.cooldownOnlyUnder3s, v -> FishSettings.cooldownOnlyUnder3s = v));
            f.sub.add(new ToggleSetting("In Inventory", "",
                    () -> FishSettings.cooldownInInventory, v -> FishSettings.cooldownInInventory = v));
            visuals.features.add(f);
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
            visuals.features.add(f);
        }
        {
            Feature f = new Feature("Soulflow HUD",
                    () -> FishSettings.soulflowHudEnabled, v -> FishSettings.soulflowHudEnabled = v);
            f.sub.add(new InputIntSetting("Warning", "",
                    () -> FishSettings.soulflowWarningThreshold, v -> FishSettings.soulflowWarningThreshold = v));
            f.sub.add(new ToggleSetting("Missing Warn", "",
                    () -> FishSettings.soulflowMissingNotifier, v -> FishSettings.soulflowMissingNotifier = v));
            visuals.features.add(f);
        }
        visuals.features.add(new Feature("Fire Freeze Timer",
                () -> FishSettings.fireFreezeTimerEnabled, v -> FishSettings.fireFreezeTimerEnabled = v));
        {
            Feature f = new Feature("Explosive Shot",
                    () -> FishSettings.explosiveShotEnabled, v -> FishSettings.explosiveShotEnabled = v);
            f.sub.add(new ToggleSetting("Announce to Party (Archer)", "",
                    () -> FishSettings.explosiveShotAnnounceParty, v -> FishSettings.explosiveShotAnnounceParty = v));
            visuals.features.add(f);
        }
        visuals.features.add(new Feature("Loadout Title",
                () -> FishSettings.loadoutTitleEnabled, v -> FishSettings.loadoutTitleEnabled = v));
        {
            Feature f = new Feature("Warp Map",
                    () -> FishSettings.warpMapHudEnabled, v -> FishSettings.warpMapHudEnabled = v);
            f.sub.add(new ColorSetting("Dot Color", "",
                    () -> FishSettings.warpMapDotColor, v -> FishSettings.warpMapDotColor = v));
            visuals.features.add(f);
        }
        visuals.features.add(new Feature("Slayer XP Tracker",
                () -> FishSettings.slayerXpEnabled, v -> FishSettings.slayerXpEnabled = v));
        visuals.features.add(new Feature("Skill XP Tracker",
                () -> FishSettings.skillTrackerEnabled, v -> FishSettings.skillTrackerEnabled = v));
        {
            Feature f = new Feature("Powder Tracker",
                    () -> FishSettings.powderTrackerEnabled, v -> FishSettings.powderTrackerEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "", FishSettings.PriceMode.values(),
                    () -> FishSettings.powderPriceMode, v -> FishSettings.powderPriceMode = v));
            visuals.features.add(f);
        }
        {
            Feature f = new Feature("Farming Tracker",
                    () -> FishSettings.farmingTrackerEnabled, v -> FishSettings.farmingTrackerEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "", FishSettings.PriceMode.values(),
                    () -> FishSettings.farmingPriceMode, v -> FishSettings.farmingPriceMode = v));
            visuals.features.add(f);
        }
        {
            Feature f = new Feature("Harvest Feast Tracker",
                    () -> FishSettings.harvestFeastEnabled, v -> FishSettings.harvestFeastEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "", FishSettings.PriceMode.values(),
                    () -> FishSettings.harvestFeastPriceMode, v -> FishSettings.harvestFeastPriceMode = v));
            visuals.features.add(f);
        }
        {
            Feature f = new Feature("Mining Tracker",
                    () -> FishSettings.miningTrackerEnabled, v -> FishSettings.miningTrackerEnabled = v);
            f.sub.add(new DropdownSetting<>("Price", "", FishSettings.PriceMode.values(),
                    () -> FishSettings.miningPriceMode, v -> FishSettings.miningPriceMode = v));
            visuals.features.add(f);
        }
        visuals.features.add(new Feature("Trophy Frogs",
                () -> FishSettings.trophyFrogEnabled, v -> FishSettings.trophyFrogEnabled = v));

        // ===== Floor 7 (ported from blade-addons) =====
        {
            Feature f = new Feature("Maxor Tick Timer",
                    () -> Floor7.enableMaxorTickTimer, v -> Floor7.enableMaxorTickTimer = v);
            floor7.features.add(f);
        }
        {
            Feature f = new Feature("Crystal Spawn",
                    () -> Floor7.enableCrystalSpawnTime, v -> Floor7.enableCrystalSpawnTime = v);
            f.sub.add(new ToggleSetting("Place Reminder", "",
                    () -> Floor7.crystalPlaceReminder, v -> Floor7.crystalPlaceReminder = v));
            f.sub.add(new ToggleSetting("Instant Reminder", "",
                    () -> Floor7.instantlyDisplayCrystalReminder, v -> Floor7.instantlyDisplayCrystalReminder = v));
            floor7.features.add(f);
        }
        {
            Feature f = new Feature("Storm Tick Timer",
                    () -> Floor7.enableStormTickTimer, v -> Floor7.enableStormTickTimer = v);
            f.sub.add(new ToggleSetting("Tick Down From 5", "",
                    () -> Floor7.tickDownStormTickTimer, v -> Floor7.tickDownStormTickTimer = v));
            f.sub.add(new ColorPickerSetting("Timer Color", "",
                    () -> Floor7.stormTickTimerColor, v -> Floor7.stormTickTimerColor = v));
            floor7.features.add(f);
        }
        floor7.features.add(new Feature("Storm Death Time",
                () -> Floor7.enableStormDeathTime, v -> Floor7.enableStormDeathTime = v));
        {
            Feature f = new Feature("LB Release Timer",
                    () -> Floor7.enableLbReleaseTimer, v -> Floor7.enableLbReleaseTimer = v);
            f.sub.add(new ColorPickerSetting("Timer Color", "",
                    () -> Floor7.lbReleaseTimerColor, v -> Floor7.lbReleaseTimerColor = v));
            floor7.features.add(f);
        }
        floor7.features.add(new Feature("Storm Crushed Noti",
                () -> Floor7.notifyStormCrush, v -> Floor7.notifyStormCrush = v));
        {
            Feature f = new Feature("Goldor Tick Timer",
                    () -> Floor7.enableGoldorTickTimer, v -> Floor7.enableGoldorTickTimer = v);
            f.sub.add(new ToggleSetting("In 3s Increments", "",
                    () -> Floor7.inDeathTicks, v -> Floor7.inDeathTicks = v));
            f.sub.add(new ToggleSetting("Tick Up", "",
                    () -> Floor7.makeGoldorTickUp, v -> Floor7.makeGoldorTickUp = v));
            floor7.features.add(f);
        }
        floor7.features.add(new Feature("Term Start Timer",
                () -> Floor7.enableTermStartTimer, v -> Floor7.enableTermStartTimer = v));
        {
            Feature f = new Feature("Section Progress",
                    () -> Floor7.showSectionProgress, v -> Floor7.showSectionProgress = v);
            f.sub.add(new ToggleSetting("Color w/ Progress", "",
                    () -> Floor7.sectionColorProgress, v -> Floor7.sectionColorProgress = v));
            f.sub.add(new ToggleSetting("Prev Objective", "",
                    () -> Floor7.sectionPrevObjective, v -> Floor7.sectionPrevObjective = v));
            floor7.features.add(f);
        }
        {
            Feature f = new Feature("Goldor Splits",
                    () -> fishmod.utils.dungeon.Section.enableTerminalSplits, v -> fishmod.utils.dungeon.Section.enableTerminalSplits = v);
            f.sub.add(new ToggleSetting("Total Time", "",
                    () -> fishmod.utils.dungeon.Section.includeTotalTime, v -> fishmod.utils.dungeon.Section.includeTotalTime = v));
            f.sub.add(new DropdownSetting<>("Show During", "",
                    fishmod.utils.dungeon.Section.DisplayTerminalSplitsWhen.values(),
                    () -> fishmod.utils.dungeon.Section.displayTerminalSplitsWhen,
                    v -> fishmod.utils.dungeon.Section.displayTerminalSplitsWhen = v));
            floor7.features.add(f);
        }

        // ===== Fishing =====
        Column fishing = new Column("Fishing", "eye");
        {
            Feature f = new Feature("Bobber Reminder",
                    () -> FishSettings.fishingTimerEnabled, v -> FishSettings.fishingTimerEnabled = v);
            f.sub.add(new SliderIntSetting("Reminder Delay s", "",
                    () -> FishSettings.fishingReminderDelay, v -> FishSettings.fishingReminderDelay = v, 1, 10));
            f.sub.add(new InputSetting("Reminder Text", "",
                    () -> FishSettings.fishingReminderText, v -> FishSettings.fishingReminderText = v));
            f.sub.add(new InputSetting("Missed Text", "",
                    () -> FishSettings.fishingMissedText, v -> FishSettings.fishingMissedText = v));
            f.sub.add(new ToggleSetting("Play Sound", "",
                    () -> FishSettings.fishingReminderSound, v -> FishSettings.fishingReminderSound = v));
            fishing.features.add(f);
        }
        {
            Feature f = new Feature("Sea Creatures",
                    () -> FishSettings.seaCreatureEnabled, v -> FishSettings.seaCreatureEnabled = v);
            f.sub.add(new ToggleSetting("Rare Alert", "",
                    () -> FishSettings.seaCreatureRareAlert, v -> FishSettings.seaCreatureRareAlert = v));
            fishing.features.add(f);
        }
        fishing.features.add(new Feature("Trophy Fish",
                () -> FishSettings.trophyFishEnabled, v -> FishSettings.trophyFishEnabled = v));

        // ===== Slayer =====
        Column slayer = new Column("Slayer", "coin");
        {
            Feature f = new Feature("Slayer Alerts",
                    () -> FishSettings.slayerAlertsEnabled, v -> FishSettings.slayerAlertsEnabled = v);
            f.sub.add(new ToggleSetting("Miniboss", "",
                    () -> FishSettings.slayerAlertMiniboss, v -> FishSettings.slayerAlertMiniboss = v));
            f.sub.add(new ToggleSetting("Boss Spawn", "",
                    () -> FishSettings.slayerAlertBossSpawn, v -> FishSettings.slayerAlertBossSpawn = v));
            f.sub.add(new ToggleSetting("Boss Slain", "",
                    () -> FishSettings.slayerAlertBossSlain, v -> FishSettings.slayerAlertBossSlain = v));
            f.sub.add(new ToggleSetting("Play Sound", "",
                    () -> FishSettings.slayerAlertSound, v -> FishSettings.slayerAlertSound = v));
            slayer.features.add(f);
        }
        slayer.features.add(new Feature("Slayer Drops",
                () -> FishSettings.slayerDropsEnabled, v -> FishSettings.slayerDropsEnabled = v));

        columns.add(general);
        columns.add(dungeon);
        columns.add(cosmetics);
        columns.add(party);
        columns.add(visuals);
        columns.add(fishing);
        columns.add(slayer);
        columns.add(floor7);
    }

    // -----------------------------------------------------------------------------------
    // Region geometry
    // -----------------------------------------------------------------------------------
    // overlay panel bounds (centred, clamped to the window for small GUI scales)
    private int panelW()  { return Math.min(PANEL_W, this.width  - 20); }
    private int panelH()  { return Math.min(PANEL_H, this.height - 20); }
    private int left()    { return (this.width  - panelW()) / 2; }
    private int top()     { return (this.height - panelH()) / 2; }
    private int right()   { return left() + panelW(); }
    private int bottom()  { return top()  + panelH(); }

    private int cx0()   { return left() + SIDEBAR_W + CONTENT_PAD; }
    private int cx1()   { return right() - CONTENT_PAD; }
    private int cyTop() { return top() + TITLE_H + 10; }
    private int cyBot() { return bottom() - FOOTER_H - 6; }

    private Column currentColumn() { return columns.get(selectedCat); }

    private List<Feature> visibleFeatures() {
        String f = searchText.toLowerCase();
        List<Feature> out = new ArrayList<>();
        for (Feature ft : currentColumn().features) {
            if (f.isEmpty() || ft.name.toLowerCase().contains(f)) out.add(ft);
        }
        return out;
    }

    private int detailHeightForSelected() {
        if (selectedFeature == null) return 0;
        int total = 0;
        for (Setting s : selectedFeature.sub) total += s.getHeight();
        return total;
    }
    private int subPanelHeight(Feature f) {
        return (f == selectedFeature && !f.sub.isEmpty()) ? detailHeightForSelected() + 10 : 0;
    }

    private int contentTotalHeight() {
        int h = 6;
        for (Feature f : visibleFeatures()) {
            h += ROW_H + subPanelHeight(f) + ROW_GAP;
        }
        return h;
    }
    private int maxScroll() { return Math.max(0, contentTotalHeight() - (cyBot() - cyTop())); }
    private void clampScroll() { scroll = Mth.clamp(scroll, 0, maxScroll()); }

    // -----------------------------------------------------------------------------------
    // Background: solid dark (matches the mockup), no vanilla blur/dirt
    // -----------------------------------------------------------------------------------
    @Override public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) { }
    @Override public void extractTransparentBackground(GuiGraphicsExtractor ctx) { }

    // -----------------------------------------------------------------------------------
    // Render
    // -----------------------------------------------------------------------------------
    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (resetArmed && System.currentTimeMillis() - resetArmedAt > 3000) resetArmed = false;
        clampScroll();

        // dim the live game behind the overlay
        ctx.fill(0, 0, this.width, this.height, SCRIM);

        // centred overlay panel
        int lx = left(), ty = top(), rx = right(), by = bottom();
        ctx.fillGradient(lx, ty, rx, by, BG_TOP, BG_BOT);
        ctx.fill(lx, ty + TITLE_H, lx + SIDEBAR_W, by, SIDEBAR_BG);
        ctx.fill(lx + SIDEBAR_W, ty + TITLE_H, rx, by - FOOTER_H, CONTENT_BG);
        // 1px frame around the panel
        ctx.fill(lx, ty, rx, ty + 1, PANEL_BORDER);
        ctx.fill(lx, by - 1, rx, by, PANEL_BORDER);
        ctx.fill(lx, ty, lx + 1, by, PANEL_BORDER);
        ctx.fill(rx - 1, ty, rx, by, PANEL_BORDER);

        renderTitleBar(ctx, mouseX, mouseY);
        renderSidebar(ctx, mouseX, mouseY);
        renderContent(ctx, mouseX, mouseY);
        renderFooter(ctx, mouseX, mouseY);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void renderTitleBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int lx = left(), ty = top();
        // wordmark "FishMod"
        float ws = 1.6f;
        int wy = ty + (TITLE_H - (int) (8 * ws)) / 2;
        sst(ctx, this.font, "Fish", lx + 22, wy, TEXT_COLOR, ws);
        int fw = sw(this.font, "Fish", ws);
        sst(ctx, this.font, "Mod", lx + 22 + fw, wy, ACCENT, ws);
        int totalW = fw + sw(this.font, "Mod", ws);

        // divider + teal accent under the wordmark
        ctx.fill(lx, ty + TITLE_H, right(), ty + TITLE_H + 1, DIVIDER);
        ctx.fill(lx + 22, ty + TITLE_H, lx + 22 + totalW, ty + TITLE_H + 1, ACCENT);

        // search field (top-right)
        int swW = 156, swH = 20;
        int sx = right() - CONTENT_PAD - swW;
        int sy = ty + (TITLE_H - swH) / 2;
        if (searchField == null) {
            searchField = new EditBox(this.font, sx + 6, sy + 6, swW - 12, swH - 6, Component.empty());
            searchField.setMaxLength(48);
            searchField.setBordered(false);
            searchField.setResponder(s -> { searchText = s; scroll = 0; });
        } else {
            searchField.setX(sx + 6); searchField.setY(sy + 6); searchField.setWidth(swW - 12);
        }
        panel(ctx, sx, sy, sx + swW, sy + swH, 0, ROW_BG, searchFocused ? ACCENT : DIVIDER);
        // magnifier glyph
        disc(ctx, sx + 11, sy + swH / 2 - 1, 3, SUBTEXT_COLOR);
        disc(ctx, sx + 11, sy + swH / 2 - 1, 1, ROW_BG);
        ctx.fill(sx + 13, sy + swH / 2 + 1, sx + 16, sy + swH / 2 + 2, SUBTEXT_COLOR);
        if (searchText.isEmpty() && !searchFocused) {
            sst(ctx, this.font, "Search…", sx + 22, sy + (swH - 8) / 2 + 1, SUBTEXT_COLOR, 0.9f);
        } else {
            // nudge field right of the glyph
            searchField.setX(sx + 22); searchField.setWidth(swW - 28);
            searchField.extractRenderState(ctx, mouseX, mouseY, 0);
        }
    }

    private void renderSidebar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int lx = left(), ty = top();
        ctx.fill(lx + SIDEBAR_W, ty + TITLE_H, lx + SIDEBAR_W + 1, bottom(), DIVIDER);
        int y = ty + TITLE_H + 10;
        for (int i = 0; i < columns.size(); i++) {
            Column c = columns.get(i);
            int x0 = lx + 8, x1 = lx + SIDEBAR_W - 8;
            boolean sel = i == selectedCat;
            boolean hov = mouseX >= lx && mouseX <= lx + SIDEBAR_W && mouseY >= y && mouseY <= y + CAT_ITEM_H;
            if (sel) {
                ctx.fill(x0, y, x1, y + CAT_ITEM_H, 0xFF12262A);
                ctx.fill(lx, y, lx + 3, y + CAT_ITEM_H, ACCENT);
            } else if (hov) {
                ctx.fill(x0, y, x1, y + CAT_ITEM_H, ROW_BG_HOVER);
            }
            int gx = x0 + 18, gcy = y + CAT_ITEM_H / 2;
            drawGlyph(ctx, c.icon, gx, gcy, sel ? ACCENT_HOVER : 0xFF8893A0, sel ? 0xFF12262A : SIDEBAR_BG);
            sst(ctx, this.font, c.name, gx + 18, gcy - 5, sel ? TEXT_COLOR : 0xFFAEB7C2, 1.1f);
            y += CAT_ITEM_H + 4;
        }
    }

    private void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int x0 = cx0(), x1 = cx1();
        int top = cyTop(), bot = cyBot();
        ctx.enableScissor(left() + SIDEBAR_W + 1, top, right(), bot);

        int y = top - scroll;
        for (Feature f : visibleFeatures()) {
            int rowTop = y;
            if (rowTop + ROW_H > top && rowTop < bot) renderRow(ctx, f, x0, x1, rowTop, mouseX, mouseY);
            y += ROW_H;
            int subH = subPanelHeight(f);
            if (subH > 0) {
                if (y + subH > top && y < bot) renderSubPanel(ctx, f, x0, x1, y, mouseX, mouseY);
                y += subH;
            }
            y += ROW_GAP;
        }
        ctx.disableScissor();

        // scrollbar
        int ms = maxScroll();
        if (ms > 0) {
            int trackX = x1 + 6;
            int vp = bot - top;
            int barH = Math.max(24, (int) ((long) vp * vp / contentTotalHeight()));
            int barY = top + (int) ((long) (vp - barH) * scroll / ms);
            ctx.fill(trackX, top, trackX + 3, bot, 0xFF141A20);
            ctx.fill(trackX, barY, trackX + 3, barY + barH, ACCENT);
        }
    }

    private void renderRow(GuiGraphicsExtractor ctx, Feature f, int x0, int x1, int top, int mouseX, int mouseY) {
        boolean on = f.hasMaster() && f.get.get();
        boolean inView = mouseY >= cyTop() && mouseY <= cyBot();
        boolean hover = inView && mouseX >= x0 && mouseX <= x1 && mouseY >= top && mouseY <= top + ROW_H;

        ctx.fill(x0, top, x1, top + ROW_H, hover ? ROW_BG_HOVER : ROW_BG);
        if (on) ctx.fill(x0, top, x0 + 2, top + ROW_H, ACCENT);

        // icon tile
        int ts = 24, tx = x0 + 9, ty = top + (ROW_H - ts) / 2;
        ctx.fill(tx, ty, tx + ts, ty + ts, on ? TILE_BG_ON : TILE_BG);
        drawGlyph(ctx, iconFor(f.name), tx + ts / 2, ty + ts / 2, on ? ACCENT_HOVER : 0xFF49C9C3, on ? TILE_BG_ON : TILE_BG);

        // texts
        int labelX = tx + ts + 9;
        String d = descFor(f.name);
        if (d.isEmpty()) {
            ctx.text(this.font, f.name, labelX, top + (ROW_H - 8) / 2, TEXT_COLOR, false);
        } else {
            ctx.text(this.font, f.name, labelX, top + 8, TEXT_COLOR, false);
            sst(ctx, this.font, d, labelX, top + 21, SUBTEXT_COLOR, 0.85f);
        }

        // control
        if (f.hasMaster()) {
            int tgx = x1 - 14 - TOG2_W, tgy = top + (ROW_H - TOG2_H) / 2;
            boolean th = inView && mouseX >= tgx && mouseX <= tgx + TOG2_W && mouseY >= tgy && mouseY <= tgy + TOG2_H;
            drawBigToggle(ctx, tgx, tgy, on, th);
            if (!f.sub.isEmpty()) drawChevron(ctx, tgx - 16, top + ROW_H / 2 - 2, f == selectedFeature, CHEVRON_COLOR);
        } else if (!f.sub.isEmpty()) {
            drawChevron(ctx, x1 - 18, top + ROW_H / 2 - 2, f == selectedFeature, f == selectedFeature ? ACCENT : 0xFF7A8694);
        }
    }

    private void drawBigToggle(GuiGraphicsExtractor ctx, int x, int y, boolean on, boolean hover) {
        int track = on ? (hover ? ACCENT_HOVER : ACCENT) : (hover ? 0xFF333D48 : 0xFF252D37);
        pill(ctx, x, y, x + TOG2_W, y + TOG2_H, track);
        int knobD = TOG2_H - 6;
        int kcx = on ? x + TOG2_W - 3 - knobD / 2 : x + 3 + knobD / 2;
        disc(ctx, kcx, y + TOG2_H / 2, knobD / 2 + 1, 0xFFFFFFFF);
        String t = on ? "ON" : "OFF";
        int tw = sw(this.font, t, 0.8f);
        int tx = on ? x + 7 : x + TOG2_W - 7 - tw;
        sst(ctx, this.font, t, tx, y + (TOG2_H - 6) / 2, on ? 0xFF06302F : 0xFF8893A0, 0.8f);
    }

    private void renderSubPanel(GuiGraphicsExtractor ctx, Feature f, int x0, int x1, int top, int mouseX, int mouseY) {
        int subH = detailHeightForSelected() + 10;
        ctx.fill(x0, top, x1, top + subH, SUBROW_BG);
        ctx.fill(x0, top, x0 + 2, top + subH, ACCENT);
        int leftX = x0 + 14, rightX = x1 - 12;
        int sy = top + 6;
        for (Setting s : f.sub) {
            int sh = s.getHeight();
            if (!(s instanceof SubcategoryHeader) && !(s instanceof InputSetting)) {
                st(ctx, this.font, s.name, leftX + 2, sy + (sh - 8) / 2, TEXT_COLOR);
            }
            s.render(ctx, leftX, rightX, sy, mouseX, mouseY, this.font);
            sy += sh;
        }
    }

    private void renderFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int fY = bottom() - FOOTER_H;
        ctx.fill(left() + SIDEBAR_W, fY, right(), fY + 1, DIVIDER);
        int by = fY + (FOOTER_H - 26) / 2, bh = 26;

        // Edit HUD (left)
        int ehW = 96, ehX = cx0();
        drawButton(ctx, ehX, by, ehW, bh, "Edit HUD", false, hovBtn(mouseX, mouseY, ehX, by, ehW, bh));

        // Credits (right of Edit HUD)
        int crW = 76, crX = ehX + ehW + 10;
        drawButton(ctx, crX, by, crW, bh, "Credits", false, hovBtn(mouseX, mouseY, crX, by, crW, bh));

        // Save & Close (far right)
        int scW = 126, scX = cx1() - scW;
        drawButton(ctx, scX, by, scW, bh, "Save & Close", true, hovBtn(mouseX, mouseY, scX, by, scW, bh));

        // Reset (left of Save & Close)
        int rsW = 84, rsX = scX - 10 - rsW;
        String rsLabel = resetArmed ? "Confirm?" : "Reset";
        drawButton(ctx, rsX, by, rsW, bh, rsLabel, false, hovBtn(mouseX, mouseY, rsX, by, rsW, bh), resetArmed ? 0xFFE05A5A : ACCENT);
    }

    private boolean hovBtn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    private void drawButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean filled, boolean hover) {
        drawButton(ctx, x, y, w, h, label, filled, hover, ACCENT);
    }
    private void drawButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean filled, boolean hover, int tint) {
        if (filled) {
            ctx.fill(x, y, x + w, y + h, hover ? ACCENT_HOVER : tint);
            int tw = this.font.width(label);
            ctx.text(this.font, label, x + (w - tw) / 2, y + (h - 8) / 2, 0xFF052A29, false);
        } else {
            int bd = hover ? ACCENT_HOVER : tint;
            ctx.fill(x, y, x + w, y + h, hover ? 0xFF12222A : 0xFF0D141A);
            ctx.fill(x, y, x + w, y + 1, bd); ctx.fill(x, y + h - 1, x + w, y + h, bd);
            ctx.fill(x, y, x + 1, y + h, bd); ctx.fill(x + w - 1, y, x + w, y + h, bd);
            int tw = this.font.width(label);
            ctx.text(this.font, label, x + (w - tw) / 2, y + (h - 8) / 2, bd, false);
        }
    }

    // -----------------------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------------------
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        int mx = (int) click.x();
        int my = (int) click.y();
        int btn = click.button();

        if (capturingKeybind != null) {
            capturingKeybind.applyKey(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(btn));
            capturingKeybind = null;
            return true;
        }

        if (activeInput instanceof InputSetting prevInput && prevInput.textField != null) prevInput.textField.setFocused(false);
        activeInput = null;

        // ----- search -----
        int swW = 156, swH = 20;
        int sx = right() - CONTENT_PAD - swW, sy = top() + (TITLE_H - swH) / 2;
        searchFocused = mx >= sx && mx <= sx + swW && my >= sy && my <= sy + swH;
        if (searchField != null) searchField.setFocused(searchFocused);
        if (searchFocused) return true;

        // ----- footer buttons -----
        int fY = bottom() - FOOTER_H, by = fY + (FOOTER_H - 26) / 2, bh = 26;
        int ehW = 96, ehX = cx0();
        if (hovBtn(mx, my, ehX, by, ehW, bh)) { Minecraft.getInstance().setScreen(new FishHudEditor(this)); return true; }
        int crW = 76, crX = ehX + ehW + 10;
        if (hovBtn(mx, my, crX, by, crW, bh)) { Minecraft.getInstance().setScreen(new CreditsScreen(this)); return true; }
        int scW = 126, scX = cx1() - scW;
        if (hovBtn(mx, my, scX, by, scW, bh)) { onClose(); return true; }
        int rsW = 84, rsX = scX - 10 - rsW;
        if (hovBtn(mx, my, rsX, by, rsW, bh)) {
            if (resetArmed) { resetCurrentCategory(); resetArmed = false; }
            else { resetArmed = true; resetArmedAt = System.currentTimeMillis(); }
            return true;
        }

        // ----- sidebar -----
        if (mx >= left() && mx <= left() + SIDEBAR_W && my >= top() + TITLE_H && my <= bottom()) {
            int y = top() + TITLE_H + 10;
            for (int i = 0; i < columns.size(); i++) {
                if (my >= y && my <= y + CAT_ITEM_H) {
                    if (i != selectedCat) {
                        selectedCat = i; selectedFeature = null; scroll = 0;
                        searchText = ""; if (searchField != null) searchField.setValue("");
                    }
                    return true;
                }
                y += CAT_ITEM_H + 4;
            }
            return true; // swallow clicks in the sidebar gutter
        }

        // ----- content rows / sub-panels -----
        if (my >= cyTop() && my <= cyBot()) {
            int x0 = cx0(), x1 = cx1();
            int y = cyTop() - scroll;
            for (Feature f : visibleFeatures()) {
                int rowTop = y;
                // row hit
                if (mx >= x0 && mx <= x1 && my >= rowTop && my <= rowTop + ROW_H) {
                    if (f.hasMaster()) {
                        int tgx = x1 - 14 - TOG2_W, tgy = rowTop + (ROW_H - TOG2_H) / 2;
                        if (mx >= tgx && mx <= tgx + TOG2_W && my >= tgy && my <= tgy + TOG2_H) {
                            f.set.accept(!f.get.get()); return true;
                        }
                        if (!f.sub.isEmpty()) { selectedFeature = (selectedFeature == f ? null : f); }
                        else { f.set.accept(!f.get.get()); }
                    } else if (!f.sub.isEmpty()) {
                        selectedFeature = (selectedFeature == f ? null : f);
                    }
                    return true;
                }
                y += ROW_H;
                int subH = subPanelHeight(f);
                if (subH > 0) {
                    int subTop = y;
                    if (mx >= x0 && mx <= x1 && my >= subTop && my <= subTop + subH) {
                        int leftX = x0 + 14, rightX = x1 - 12, ssy = subTop + 6;
                        for (Setting s : f.sub) {
                            int sh = s.getHeight();
                            if (my >= ssy && my <= ssy + sh) {
                                if (s instanceof InputSetting || s instanceof InputIntSetting
                                        || s instanceof InputDoubleSetting || s instanceof ColorSetting
                                        || s instanceof ColorPickerSetting) {
                                    activeInput = s;
                                }
                            }
                            if (s.onClick(mx, my, leftX, rightX, ssy, btn)) {
                                if (s instanceof ColorPickerSetting cps && cps.dragMode != 0) activePicker = cps;
                                if (s instanceof KeybindSetting ks && ks.capturing) capturingKeybind = ks;
                                return true;
                            }
                            if (s instanceof SliderIntSetting || s instanceof SliderDoubleSetting) {
                                int slx = rightX - SLIDER_W - 2;
                                int sly = ssy + (ITEM_HEIGHT - SLIDER_H) / 2;
                                if (mx >= slx && mx <= slx + SLIDER_W && my >= sly - 4 && my <= sly + SLIDER_H + 4) {
                                    activeSlider = s; activeSliderX = slx; s.onDrag(mx, slx, SLIDER_W); return true;
                                }
                            }
                            ssy += sh;
                        }
                        return true; // swallow clicks inside the body
                    }
                    y += subH;
                }
                y += ROW_GAP;
            }
            return true;
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (activeSlider != null) { activeSlider.onDrag((int) click.x(), activeSliderX, SLIDER_W); return true; }
        if (activePicker != null) { activePicker.updateFromMouse((int) click.x(), (int) click.y()); return true; }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        activeSlider = null;
        if (activePicker != null) { activePicker.dragMode = 0; activePicker = null; }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Mth.clamp((int) (scroll - verticalAmount * 18), 0, maxScroll());
        return true;
    }

    private void resetCurrentCategory() {
        for (Feature f : currentColumn().features) {
            if (f.hasMaster() && f.get.get()) f.set.accept(false);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (capturingKeybind != null) {
            capturingKeybind.applyKey(input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN
                    : com.mojang.blaze3d.platform.InputConstants.getKey(input));
            capturingKeybind = null;
            return true;
        }
        if (activeInput instanceof InputSetting is && is.textField != null) { is.textField.keyPressed(input); return true; }
        if (activeInput instanceof InputIntSetting iis && iis.textField != null) { iis.textField.keyPressed(input); return true; }
        if (activeInput instanceof InputDoubleSetting ids && ids.textField != null) { ids.textField.keyPressed(input); return true; }
        if (activeInput instanceof ColorSetting cs && cs.textField != null) { cs.textField.keyPressed(input); return true; }
        if (activeInput instanceof ColorPickerSetting cp && cp.textField != null) { cp.textField.keyPressed(input); return true; }
        if (searchFocused && searchField != null) { searchField.keyPressed(input); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (activeInput instanceof InputSetting is && is.textField != null) {
            is.textField.charTyped(input); is.setter.accept(is.textField.getValue()); return true;
        }
        if (activeInput instanceof InputIntSetting iis && iis.textField != null) { iis.textField.charTyped(input); return true; }
        if (activeInput instanceof InputDoubleSetting ids && ids.textField != null) { ids.textField.charTyped(input); return true; }
        if (activeInput instanceof ColorSetting cs && cs.textField != null) { cs.textField.charTyped(input); return true; }
        if (activeInput instanceof ColorPickerSetting cp && cp.textField != null) { cp.textField.charTyped(input); return true; }
        if (searchFocused && searchField != null) { searchField.charTyped(input); searchText = searchField.getValue(); scroll = 0; return true; }
        return super.charTyped(input);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Config.manager.save();
        FishConfig.manager.save();
        super.onClose();
    }

    // -----------------------------------------------------------------------------------
    // Model
    // -----------------------------------------------------------------------------------
    static class Column {
        final String name;
        final String icon;
        final List<Feature> features = new ArrayList<>();
        Column(String name, String icon) { this.name = name; this.icon = icon; }
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
        abstract void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int settingY, int mouseX, int mouseY, net.minecraft.client.gui.Font tr);
        boolean onClick(int mx, int my, int leftX, int rightX, int settingY, int button) { return false; }
        void onDrag(int mx, int sx, int sliderW) {}
        int getHeight() { return ITEM_HEIGHT; }
    }

    static class SubcategoryHeader extends Setting {
        SubcategoryHeader(String name) { super(name, ""); }
        @Override int getHeight() { return SUBCAT_HEIGHT; }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            roundRect(ctx, leftX, sy, rightX, sy + SUBCAT_HEIGHT, 2, 0xFF11131A);
            ctx.fill(leftX + 1, sy + 2, leftX + 3, sy + SUBCAT_HEIGHT - 2, ACCENT);
            st(ctx, tr, name, leftX + 6, sy + (SUBCAT_HEIGHT - 8) / 2, ACCENT);
        }
    }

    static class ToggleSetting extends Setting {
        Supplier<Boolean> getter; Consumer<Boolean> setter;
        ToggleSetting(String name, String desc, Supplier<Boolean> g, Consumer<Boolean> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            int tx = rightX - TOGGLE_W - 2;
            int ty = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            boolean on = getter.get();
            boolean hov = mx >= tx && mx <= tx + TOGGLE_W && my >= ty && my <= ty + TOGGLE_H;
            int track = on ? (hov ? ACCENT_HOVER : TOGGLE_ON) : (hov ? 0xFF3A3D48 : TOGGLE_OFF);
            pill(ctx, tx, ty, tx + TOGGLE_W, ty + TOGGLE_H, track);
            int knob = TOGGLE_H - 4;
            int kx = on ? tx + TOGGLE_W - knob - 2 : tx + 2;
            int ky = ty + 2;
            pill(ctx, kx, ky, kx + knob, ky + knob, 0xFFE8ECF2);
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
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            int slx = rightX - SLIDER_W - 2;
            int sly = sy + (ITEM_HEIGHT - SLIDER_H) / 2;
            float pct = (float)(getter.get() - min) / (max - min);
            pill(ctx, slx, sly, slx + SLIDER_W, sly + SLIDER_H, SLIDER_BG);
            int fillW = (int)(SLIDER_W * pct);
            if (fillW > 0) pill(ctx, slx, sly, slx + Math.max(fillW, SLIDER_H), sly + SLIDER_H, SLIDER_FILL);
            String val = String.valueOf(getter.get());
            st(ctx, tr, val, slx + SLIDER_W - stw(tr, val), sly - 9, SUBTEXT_COLOR);
        }
        @Override
        void onDrag(int mx, int sx, int sliderW) {
            float pct = Mth.clamp((float)(mx - sx) / sliderW, 0, 1);
            setter.accept(min + (int)(pct * (max - min)));
        }
    }

    static class SliderDoubleSetting extends Setting {
        Supplier<Double> getter; Consumer<Double> setter; double min, max;
        SliderDoubleSetting(String name, String desc, Supplier<Double> g, Consumer<Double> s, double mn, double mx) {
            super(name, desc); this.getter = g; this.setter = s; this.min = mn; this.max = mx;
        }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            int slx = rightX - SLIDER_W - 2;
            int sly = sy + (ITEM_HEIGHT - SLIDER_H) / 2;
            float pct = (float)((getter.get() - min) / (max - min));
            pill(ctx, slx, sly, slx + SLIDER_W, sly + SLIDER_H, SLIDER_BG);
            int fillW = (int)(SLIDER_W * pct);
            if (fillW > 0) pill(ctx, slx, sly, slx + Math.max(fillW, SLIDER_H), sly + SLIDER_H, SLIDER_FILL);
            String val = String.format("%.1f", getter.get());
            st(ctx, tr, val, slx + SLIDER_W - stw(tr, val), sly - 9, SUBTEXT_COLOR);
        }
        @Override
        void onDrag(int mx, int sx, int sliderW) {
            float pct = Mth.clamp((float)(mx - sx) / sliderW, 0, 1);
            setter.accept(min + pct * (max - min));
        }
    }

    // Click to advance to the next value; right-click goes back one.
    static class DropdownSetting<T> extends Setting {
        static final int DROP_W = 120;
        static final int DROP_H = 18;
        T[] values; Supplier<T> getter; Consumer<T> setter;
        DropdownSetting(String name, String desc, T[] vals, Supplier<T> g, Consumer<T> s) {
            super(name, desc); this.values = vals; this.getter = g; this.setter = s;
        }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            int bw = DROP_W;
            int bx = rightX - bw - 2;
            int by = sy + (ITEM_HEIGHT - DROP_H) / 2;
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + DROP_H;
            panel(ctx, bx, by, bx + bw, by + DROP_H, 3, hov ? 0xFF252832 : SLIDER_BG, hov ? ACCENT_HOVER : ACCENT);
            String current = getter.get().toString();
            if (tr.width(current) > bw - 20) current = tr.plainSubstrByWidth(current, bw - 24) + "…";
            ctx.text(tr, current, bx + 6, by + (DROP_H - 8) / 2, TEXT_COLOR, false);
            ctx.text(tr, "›", bx + bw - 9, by + (DROP_H - 8) / 2, ACCENT, false);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int bw = DROP_W;
            int bx = rightX - bw - 2;
            int by = sy + (ITEM_HEIGHT - DROP_H) / 2;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + DROP_H) {
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
        EditBox textField;
        String hint = null;
        InputSetting(String name, String desc, Supplier<String> g, Consumer<String> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        void initField(net.minecraft.client.gui.Font tr) {
            if (textField == null) {
                textField = new EditBox(tr, 0, 0, INPUT_W, INPUT_H, Component.empty());
                textField.setMaxLength(256);
                textField.setValue(getter.get());
                textField.setResponder(setter);
            }
        }
        @Override int getHeight() { return hint != null ? 35 : 26; }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            initField(tr);
            st(ctx, tr, name, leftX + 2, sy + 1, TEXT_COLOR);
            int ix = leftX + 2;
            int iy = sy + 11;
            int fieldW = rightX - leftX - 4;
            float fs = 0.7f;
            textField.setWidth((int) (fieldW / fs));
            textField.setHeight((int) (INPUT_H / fs));
            if (!textField.isFocused()) { textField.setCursorPosition(0); textField.setHighlightPos(0); }
            textField.setX(0); textField.setY(0);
            ctx.pose().pushMatrix();
            ctx.pose().translate((float) ix, (float) iy);
            ctx.pose().scale(fs, fs);
            textField.extractRenderState(ctx, mx, my, 0);
            ctx.pose().popMatrix();
            if (hint != null) st(ctx, tr, hint, leftX + 2, sy + 27, SUBTEXT_COLOR);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int ix = leftX + 2;
            int iy = sy + 11;
            int fieldW = rightX - leftX - 4;
            if (mx >= ix && mx <= ix + fieldW && my >= iy && my <= iy + INPUT_H) {
                if (textField != null) {
                    textField.setFocused(true);
                    int len = textField.getValue().length();
                    textField.setCursorPosition(len); textField.setHighlightPos(len);
                }
                return true;
            }
            return false;
        }
    }

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
        @Override int getHeight() { return ITEM_HEIGHT + 9; }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            st(ctx, tr, displayLabel, leftX + 2, sy + 1, TEXT_COLOR);
            initField(tr);
            int ix = rightX - INPUT_W - 2;
            int iy = sy + 2;
            float fs = 0.7f;
            textField.setWidth((int) (INPUT_W / fs));
            textField.setHeight((int) (INPUT_H / fs));
            textField.setX(0); textField.setY(0);
            ctx.pose().pushMatrix();
            ctx.pose().translate((float) ix, (float) iy);
            ctx.pose().scale(fs, fs);
            textField.extractRenderState(ctx, mx, my, 0);
            ctx.pose().popMatrix();
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
        EditBox textField;
        ColorSetting(String name, String desc, Supplier<Integer> g, Consumer<Integer> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        void initField(net.minecraft.client.gui.Font tr) {
            if (textField == null) {
                textField = new EditBox(tr, 0, 0, 50, INPUT_H, Component.empty());
                textField.setMaxLength(6);
                textField.setValue(String.format("%06X", getter.get() & 0xFFFFFF));
                textField.setResponder(s -> {
                    if (s.length() == 6) {
                        try { setter.accept(0xFF000000 | (int) Long.parseLong(s, 16)); }
                        catch (NumberFormatException ignored) {}
                    }
                });
            }
        }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            initField(tr);
            int ix = rightX - 50 - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            ctx.fill(ix - 18, iy, ix - 2, iy + INPUT_H, 0xFF000000);
            ctx.fill(ix - 17, iy + 1, ix - 3, iy + INPUT_H - 1, getter.get());
            textField.setX(ix); textField.setY(iy);
            textField.extractRenderState(ctx, mx, my, 0);
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
        EditBox textField;
        float hsbH, hsbS, hsbV;
        int lastColor = 0;
        int dragMode = 0;
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
            if (textField != null) textField.setValue(String.format("%06X", rgb));
        }
        void initField(net.minecraft.client.gui.Font tr) {
            if (textField == null) {
                textField = new EditBox(tr, 0, 0, 46, INPUT_H, Component.empty());
                textField.setMaxLength(6);
                textField.setValue(String.format("%06X", getter.get() & 0xFFFFFF));
                textField.setResponder(t -> {
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
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            initField(tr);
            if (getter.get() != lastColor) { syncFromColor(getter.get()); textField.setValue(String.format("%06X", getter.get() & 0xFFFFFF)); }

            st(ctx, tr, name, leftX, sy + (ITEM_HEIGHT - 8) / 2, TEXT_COLOR);
            int ix = rightX - 46 - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            ctx.fill(ix - 18, iy, ix - 2, iy + INPUT_H, 0xFF000000);
            ctx.fill(ix - 17, iy + 1, ix - 3, iy + INPUT_H - 1, getter.get());
            textField.setX(ix); textField.setY(iy);
            textField.extractRenderState(ctx, mx, my, 0);

            sqX = leftX; sqY = sy + ITEM_HEIGHT + 2;
            for (int c = 0; c < sqW; c++) {
                float sat = (float) c / sqW;
                int top = 0xFF000000 | (java.awt.Color.HSBtoRGB(hsbH, sat, 1f) & 0xFFFFFF);
                ctx.fillGradient(sqX + c, sqY, sqX + c + 1, sqY + sqH, top, 0xFF000000);
            }
            int msx = sqX + Math.round(hsbS * sqW);
            int msy = sqY + Math.round((1 - hsbV) * sqH);
            ctx.fill(msx - 2, msy - 1, msx + 2, msy, 0xFFFFFFFF);
            ctx.fill(msx - 2, msy + 1, msx + 2, msy + 2, 0xFFFFFFFF);
            ctx.fill(msx - 2, msy, msx - 1, msy + 1, 0xFFFFFFFF);
            ctx.fill(msx + 1, msy, msx + 2, msy + 1, 0xFFFFFFFF);

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
                hsbS = Mth.clamp((float) (mx - sqX) / sqW, 0f, 1f);
                hsbV = Mth.clamp(1f - (float) (my - sqY) / sqH, 0f, 1f);
            } else if (dragMode == 2) {
                hsbH = Mth.clamp((float) (my - hueY) / sqH, 0f, 1f);
            }
            commit();
        }
    }

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
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my,
                    net.minecraft.client.gui.Font tr) {
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
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            int bw = 60;
            int bx = rightX - bw - 2;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + TOGGLE_H;
            roundRect(ctx, bx, by, bx + bw, by + TOGGLE_H, 3, hov ? ACCENT_HOVER : ACCENT);
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

    static class KeybindSetting extends Setting {
        Supplier<net.minecraft.client.KeyMapping> getter;
        boolean capturing = false;
        static final int W = 110;
        KeybindSetting(String name, String desc, Supplier<net.minecraft.client.KeyMapping> g) {
            super(name, desc); this.getter = g;
        }
        private String label() {
            if (capturing) return "> Press <";
            net.minecraft.client.KeyMapping kb = getter.get();
            if (kb == null) return "-";
            return kb.isUnbound() ? "Unbound" : kb.getTranslatedKeyMessage().getString();
        }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            int bx = rightX - W - 2;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            boolean hov = mx >= bx && mx <= bx + W && my >= by && my <= by + TOGGLE_H;
            roundRect(ctx, bx, by, bx + W, by + TOGGLE_H, 3, capturing ? ACCENT_HOVER : (hov ? 0xFF333D48 : 0xFF252D37));
            String t = label();
            st(ctx, tr, t, bx + (W - stw(tr, t)) / 2, by + (TOGGLE_H - 8) / 2, capturing ? 0xFF06302F : TEXT_COLOR);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int bx = rightX - W - 2;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            if (mx >= bx && mx <= bx + W && my >= by && my <= by + TOGGLE_H) {
                capturing = true; return true;
            }
            return false;
        }
        void applyKey(com.mojang.blaze3d.platform.InputConstants.Key key) {
            net.minecraft.client.KeyMapping kb = getter.get();
            if (kb == null) return;
            kb.setKey(key);
            net.minecraft.client.KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
            capturing = false;
        }
    }

    static class InputIntSetting extends Setting {
        Supplier<Integer> getter; Consumer<Integer> setter;
        EditBox textField;
        InputIntSetting(String name, String desc, Supplier<Integer> g, Consumer<Integer> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        void initField(net.minecraft.client.gui.Font tr) {
            if (textField == null) {
                textField = new EditBox(tr, 0, 0, INPUT_W, INPUT_H, Component.empty());
                textField.setMaxLength(10);
                textField.setValue(String.valueOf(getter.get()));
                textField.setResponder(s -> {
                    try { setter.accept(Integer.parseInt(s.trim())); }
                    catch (NumberFormatException ignored) {}
                });
            }
        }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            initField(tr);
            int ix = rightX - INPUT_W - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            float fs = 0.7f;
            textField.setWidth((int) (INPUT_W / fs));
            textField.setHeight((int) (INPUT_H / fs));
            textField.setX(0); textField.setY(0);
            ctx.pose().pushMatrix();
            ctx.pose().translate((float) ix, (float) iy);
            ctx.pose().scale(fs, fs);
            textField.extractRenderState(ctx, mx, my, 0);
            ctx.pose().popMatrix();
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
        EditBox textField;
        InputDoubleSetting(String name, String desc, Supplier<Double> g, Consumer<Double> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        void initField(net.minecraft.client.gui.Font tr) {
            if (textField == null) {
                textField = new EditBox(tr, 0, 0, INPUT_W, INPUT_H, Component.empty());
                textField.setMaxLength(12);
                textField.setValue(String.valueOf(getter.get()));
                textField.setResponder(s -> {
                    try { setter.accept(Double.parseDouble(s.trim())); }
                    catch (NumberFormatException ignored) {}
                });
            }
        }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            initField(tr);
            int ix = rightX - INPUT_W - 2;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            float fs = 0.7f;
            textField.setWidth((int) (INPUT_W / fs));
            textField.setHeight((int) (INPUT_H / fs));
            textField.setX(0); textField.setY(0);
            ctx.pose().pushMatrix();
            ctx.pose().translate((float) ix, (float) iy);
            ctx.pose().scale(fs, fs);
            textField.extractRenderState(ctx, mx, my, 0);
            ctx.pose().popMatrix();
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
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {}
    }
}
