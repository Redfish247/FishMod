package fishmod.features;

import fishmod.utils.config.Config;
import fishmod.utils.config.FishConfig;
import fishmod.cosmetic.NickState;
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
import fishmod.utils.Easing;

/**
 * Multi-column config screen (matches the FishMod design mockup).
 *
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  FishMod                                       [ search ]  │  title bar
 *  ├──────┬──────┬──────┬──────┬──────┬───────────────────────┤
 *  │ Genl │ Dngn │ Cosm │Party │ Vis. │  Floor7  │ each column  │
 *  │ [ ]  │ [ ]  │ [ ]  │ [ ]  │ [ ]  │   [ ]    │ scrolls on   │
 *  │ [ ]  │ [ ]  │ [ ]  │ [ ]  │ [ ]  │   [ ]    │ its own      │
 *  ├──────┴──────┴──────┴──────┴──────┴───────────────────────┤
 *  │  Edit HUD                       Reset      Save & Close    │  footer
 *  └──────────────────────────────────────────────────────────┘
 *
 * All columns render simultaneously; each scrolls independently. Left-click a feature
 * toggle = master on/off. Left-click a feature row body (when it has sub-settings) =
 * expand an inline panel beneath it with the rich controls (sliders, dropdowns, colour
 * pickers, text inputs), animated open/closed with a cubic ease-in-out (see
 * {@link Easing}). Multiple features (in the same or different columns) can be expanded
 * at once — expanding one never collapses another.
 */
public class FishModScreen extends Screen {

    // ----- palette (recolored teal — matches the mod's existing accent, not a reference-repo copy) -----
    static final int ACCENT          = 0xFF24B6B0;  // bright slate-teal
    static final int ACCENT_HOVER    = 0xFF3AD8D1;
    static final int DIM_TOP         = 0x2E000000;  // light scrim over the blurred game, just enough for text contrast
    static final int DIM_BOT         = 0x50000000;
    static final int CARD_BG         = 0xFF14181D;  // floating card body
    static final int ROW_HOVER       = 0x1EFFFFFF;  // translucent hover wash over a row
    static final int ROW_ENABLED     = 0x2624B6B0;  // translucent accent tint over an enabled row
    static final int SUBROW_BG       = 0xFF0F1317;
    static final int TRACK_OFF       = 0xFF3A3F48;  // toggle/keybind/dropdown pill track when inactive
    static final int TEXT_COLOR      = 0xFFEDF1F5;
    static final int SUBTEXT_COLOR   = 0xFF8A96A3;
    static final int CHEVRON_COLOR   = 0xFF6C7885;

    static final float TEXT_SCALE = 0.75f;

    // ----- screen chrome (floating elements, no bordered modal box) -----
    static final int MARGIN         = 16;
    static final int TOP_BAR_H      = 26;   // reserved space for wordmark + top-right pill buttons
    static final int BOTTOM_RESERVE = 46;   // reserved space for the floating search pill

    // ----- multi-column layout -----
    static final int COLUMN_GUTTER  = 12;   // px between column cards
    static final int CARD_RADIUS    = 7;
    static final int HEADER_H       = 24;   // header bar height (icon + name)
    static final int HEADER_STRIP_H = 3;    // accent strip thickness at header top
    static final int MIN_COLUMN_W   = 136;  // floor so controls don't clip

    // ----- row / setting-widget geometry -----
    static final int ROW_H       = 22;
    static final int ROW_GAP     = 3;
    static final int ITEM_HEIGHT = 22;
    static final int PILL_H      = 18;   // sub-setting toggle/dropdown/keybind pill height
    static final int OPTION_H    = 16;   // dropdown option-list row height
    static final int SLIDER_BG   = 0xFF2C3138;
    static final int SLIDER_FILL = ACCENT;
    static final int SLIDER_W    = 56;
    static final int SLIDER_H    = 5;
    static final int INPUT_W     = 62;
    static final int INPUT_H     = 14;
    static final int SUBCAT_HEIGHT = 13;

    // ----- state -----
    private final List<Column> columns = new ArrayList<>();
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
    private String hoverDesc = null;
    private int hoverDescX = 0, hoverDescY = 0;

    public FishModScreen() {
        super(Component.literal("FishMod"));
        buildCategories();
    }

    // -----------------------------------------------------------------------------------
    // Drawing helpers
    // -----------------------------------------------------------------------------------

    /** True filled rounded rectangle via horizontal scanlines (genuine rounding, not a faked square). */
    static void roundedRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { ctx.fill(x, y, x + w, y + h, color); return; }
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int dy = r - i;
            int dx = (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - (double) dy * dy)));
            int inset = r - dx;
            int topY = y + i, botY = y + h - 1 - i;
            ctx.fill(x + inset, topY, x + r, topY + 1, color);
            ctx.fill(x + w - r, topY, x + w - inset, topY + 1, color);
            ctx.fill(x + inset, botY, x + r, botY + 1, color);
            ctx.fill(x + w - r, botY, x + w - inset, botY + 1, color);
        }
    }

    /** Corner-coordinate overload matching {@code ctx.fill}'s (x1,y1,x2,y2) convention. */
    static void roundRect(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int r, int color) {
        roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, r, color);
    }

    /** A rounded rect with a hollow accent-colored ring of {@code strokeW} around it. */
    static void roundedRectRing(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int strokeW, int fillColor, int ringColor) {
        roundedRect(ctx, x - strokeW, y - strokeW, w + strokeW * 2, h + strokeW * 2, r + strokeW, ringColor);
        roundedRect(ctx, x, y, w, h, r, fillColor);
    }

    /** True pill (fully rounded rectangle whose radius is half its height). Takes corner coordinates, like {@code ctx.fill}. */
    static void pill(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        int h = y2 - y1;
        roundedRect(ctx, x1, y1, x2 - x1, h, h / 2, color);
    }

    /** 1px border frame around a fill (square corners — used for tiny non-decorative frames). */
    static void panel(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int r, int fill, int border) {
        roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, r, border);
        roundedRect(ctx, x1 + 1, y1 + 1, x2 - x1 - 2, y2 - y1 - 2, Math.max(0, r - 1), fill);
    }

    /** True filled circle via horizontal scanlines — used for glyphs and toggle knobs. */
    static void disc(GuiGraphicsExtractor ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - (double) dy * dy)));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
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
            case "Dungeon Map" -> "Fixed 6x6 room/door map read from the vanilla map item";
            case "Maxor Tick Timer" -> "Tick timer during Maxor (P1)";
            case "Crystal Spawn" -> "Crystal spawn countdown + reminder";
            case "Storm Tick Timer" -> "Tick timer during Storm (P2)";
            case "Storm Death Time" -> "Show when Storm died";
            case "LB Release Timer" -> "Countdown to the Last Breath shot: Archer 34.35s, Healer 34.05s";
            case "Storm Crushed Noti" -> "Alert when Storm is crushed";
            case "Goldor Tick Timer" -> "Terminal-phase tick timer";
            case "Goldor Leap Timer" -> "Countdown from Goldor's death to when to leap";
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
        {
            Feature f = new Feature("Dungeon Score",
                    () -> FishSettings.dungeonScoreEnabled, v -> FishSettings.dungeonScoreEnabled = v);
            f.sub.add(new ToggleSetting("Score Missing Msg (1min)", "",
                    () -> FishSettings.dungeonScoreMissingMsg, v -> FishSettings.dungeonScoreMissingMsg = v));
            f.sub.add(new ToggleSetting("Score Left (not total secrets)", "",
                    () -> FishSettings.dungeonScoreShowLeft, v -> FishSettings.dungeonScoreShowLeft = v));
            f.sub.add(new ToggleSetting("270 Title", "",
                    () -> FishSettings.score270TitleEnabled, v -> FishSettings.score270TitleEnabled = v));
            f.sub.add(new ToggleSetting("270 Chat Msg", "",
                    () -> FishSettings.score270ChatEnabled, v -> FishSettings.score270ChatEnabled = v));
            InputSetting t270 = new InputSetting("270 Text", "",
                    () -> FishSettings.score270Text, v -> FishSettings.score270Text = v);
            t270.hint = "& color codes ok";
            f.sub.add(t270);
            f.sub.add(new ToggleSetting("300 Title", "",
                    () -> FishSettings.score300TitleEnabled, v -> FishSettings.score300TitleEnabled = v));
            f.sub.add(new ToggleSetting("300 Chat Msg", "",
                    () -> FishSettings.score300ChatEnabled, v -> FishSettings.score300ChatEnabled = v));
            InputSetting t300 = new InputSetting("300 Text", "",
                    () -> FishSettings.score300Text, v -> FishSettings.score300Text = v);
            t300.hint = "& color codes ok";
            f.sub.add(t300);
            dungeon.features.add(f);
        }
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
            f.sub.add(new SubcategoryHeader("Party Actions"));
            f.sub.add(new ToggleSetting(".kick", "", () -> FishSettings.pcActionKick, v -> FishSettings.pcActionKick = v));
            f.sub.add(new ToggleSetting(".warp / .w", "", () -> FishSettings.pcActionWarp, v -> FishSettings.pcActionWarp = v));
            f.sub.add(new ToggleSetting(".transfer / .pt / .ptme", "", () -> FishSettings.pcActionTransfer, v -> FishSettings.pcActionTransfer = v));
            f.sub.add(new ToggleSetting(".promote", "", () -> FishSettings.pcActionPromote, v -> FishSettings.pcActionPromote = v));
            f.sub.add(new ToggleSetting(".demote", "", () -> FishSettings.pcActionDemote, v -> FishSettings.pcActionDemote = v));
            f.sub.add(new DropdownSetting<>("Who Can Trigger", "", new String[]{"off","self","whitelist","blacklist","everyone"},
                    () -> FishSettings.pcPartyActionsMode, v -> FishSettings.pcPartyActionsMode = v));
            InputSetting paWhitelist = new InputSetting("Whitelist", "",
                    () -> FishSettings.pcPartyActionsWhitelist, v -> FishSettings.pcPartyActionsWhitelist = v);
            paWhitelist.hint = "or /fmcmd whitelist add|remove|list";
            f.sub.add(paWhitelist);
            InputSetting paBlacklist = new InputSetting("Blacklist", "",
                    () -> FishSettings.pcPartyActionsBlacklist, v -> FishSettings.pcPartyActionsBlacklist = v);
            paBlacklist.hint = "or /fmcmd blacklist add|remove|list";
            f.sub.add(paBlacklist);
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
        visuals.features.add(new Feature("Catacombs Overflow Levels",
                () -> FishSettings.catacombsOverflowEnabled, v -> FishSettings.catacombsOverflowEnabled = v));
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
        floor7.features.add(new Feature("Goldor Leap Timer",
                () -> Floor7.leapNotifications, v -> Floor7.leapNotifications = v));
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

        columns.add(general);
        columns.add(dungeon);
        columns.add(cosmetics);
        columns.add(party);
        columns.add(visuals);
        columns.add(floor7);
    }

    // -----------------------------------------------------------------------------------
    // Region geometry — floating over the full screen, no bordered modal box
    // -----------------------------------------------------------------------------------
    private int left()    { return 0; }
    private int top()     { return 0; }
    private int right()   { return this.width; }
    private int bottom()  { return this.height; }

    private int cx0()   { return left() + MARGIN; }
    private int cx1()   { return right() - MARGIN; }
    private int cyTop() { return top() + TOP_BAR_H + MARGIN + HEADER_H; }
    private int cyBot() { return bottom() - BOTTOM_RESERVE; }

    private List<Feature> visibleFeatures(Column c) {
        String f = searchText.toLowerCase();
        List<Feature> out = new ArrayList<>();
        for (Feature ft : c.features) {
            if (f.isEmpty() || ft.name.toLowerCase().contains(f)) out.add(ft);
        }
        return out;
    }

    private List<Column> visibleColumns() {
        List<Column> out = new ArrayList<>();
        for (Column c : columns) {
            if (!visibleFeatures(c).isEmpty() || searchText.isEmpty()) out.add(c);
        }
        return out;
    }

    private int columnWidth() {
        int n = visibleColumns().size();
        if (n == 0) return 0;
        int avail = (cx1() - cx0()) - (n - 1) * COLUMN_GUTTER;
        return Math.max(MIN_COLUMN_W, avail / n);
    }

    private int columnX0(int visibleIndex) {
        return cx0() + visibleIndex * (columnWidth() + COLUMN_GUTTER);
    }

    /** A single computed row rect within a column; the one source of truth both render and hit-testing consume. */
    private static final class RowLayout {
        final Feature feature;
        final int rowTop, rowBottom;
        final int subTop, subBottom;
        RowLayout(Feature feature, int rowTop, int rowBottom, int subTop, int subBottom) {
            this.feature = feature; this.rowTop = rowTop; this.rowBottom = rowBottom;
            this.subTop = subTop; this.subBottom = subBottom;
        }
    }

    private List<RowLayout> layoutColumn(Column c, int scrollOffset) {
        List<RowLayout> out = new ArrayList<>();
        int y = cyTop() - scrollOffset;
        for (Feature f : visibleFeatures(c)) {
            int rowTop = y, rowBottom = y + ROW_H;
            y = rowBottom;
            int subH = f.animatedSubHeight();
            int subTop = y, subBottom = y + subH;
            if (subH > 0) y = subBottom;
            y += ROW_GAP;
            out.add(new RowLayout(f, rowTop, rowBottom, subTop, subBottom));
        }
        return out;
    }

    private int columnContentHeight(Column c) {
        List<RowLayout> rows = layoutColumn(c, 0);
        if (rows.isEmpty()) return 6;
        RowLayout last = rows.get(rows.size() - 1);
        return Math.max(last.rowBottom, last.subBottom) - cyTop() + ROW_GAP + 6;
    }
    private int maxScrollFor(Column c) { return Math.max(0, columnContentHeight(c) - (cyBot() - cyTop())); }
    private void clampScroll(Column c) { c.scroll = Mth.clamp(c.scroll, 0, maxScrollFor(c)); }

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
        for (Column c : visibleColumns()) clampScroll(c);

        // blur the live game behind the columns instead of just darkening it, plus a light scrim for text contrast
        extractBlurredBackground(ctx);
        ctx.fillGradient(0, 0, this.width, this.height, DIM_TOP, DIM_BOT);

        hoverDesc = null;
        renderTopBar(ctx, mouseX, mouseY);
        renderContent(ctx, mouseX, mouseY);
        renderSearchBar(ctx, mouseX, mouseY);
        renderHoverTooltip(ctx);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    /** Geometry for the 4 top-right pill buttons — the one source of truth for both render and hit-testing. */
    private int[][] topBarButtonRects() {
        String[] labels = { "Edit HUD", "Credits", resetArmed ? "Confirm?" : "Reset", "Save & Close" };
        int bh = 20, gap = 8, y = MARGIN - 2;
        int[][] rects = new int[4][];
        int x = right() - MARGIN;
        for (int i = 3; i >= 0; i--) {
            int w = sw(this.font, labels[i], 0.85f) + 20;
            x -= w;
            rects[i] = new int[]{x, y, w, bh};
            x -= gap;
        }
        return rects;
    }

    private void renderTopBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        // wordmark "FishMod" (top-left, no bar/border)
        float ws = 1.3f;
        sst(ctx, this.font, "Fish", MARGIN, MARGIN, TEXT_COLOR, ws);
        int fw = sw(this.font, "Fish", ws);
        sst(ctx, this.font, "Mod", MARGIN + fw, MARGIN, ACCENT, ws);

        String[] labels = { "Edit HUD", "Credits", resetArmed ? "Confirm?" : "Reset", "Save & Close" };
        boolean[] filled = { false, false, false, true };
        int[] accents = { ACCENT, ACCENT, resetArmed ? 0xFFE05A5A : ACCENT, ACCENT };
        int[][] rects = topBarButtonRects();
        for (int i = 0; i < 4; i++) {
            int[] r = rects[i];
            boolean hover = mouseX >= r[0] && mouseX <= r[0] + r[2] && mouseY >= r[1] && mouseY <= r[1] + r[3];
            drawPillButton(ctx, r[0], r[1], r[2], r[3], labels[i], filled[i], accents[i], hover);
        }
    }

    private void drawPillButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean filled, int accent, boolean hover) {
        int textW = sw(this.font, label, 0.85f);
        if (filled) {
            roundedRect(ctx, x, y, w, h, h / 2, hover ? ACCENT_HOVER : accent);
            sst(ctx, this.font, label, x + (w - textW) / 2, y + (h - 8) / 2, 0xFF06302F, 0.85f);
        } else {
            roundedRectRing(ctx, x, y, w, h, h / 2 - 1, 1, hover ? 0xFF20272E : 0xFF171C21, hover ? ACCENT_HOVER : accent);
            sst(ctx, this.font, label, x + (w - textW) / 2, y + (h - 8) / 2, hover ? ACCENT_HOVER : TEXT_COLOR, 0.85f);
        }
    }

    private void renderSearchBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int bw = 190, bh = 24;
        int bx = (this.width - bw) / 2;
        int by = this.height - BOTTOM_RESERVE + (BOTTOM_RESERVE - bh) / 2 - 8;
        roundedRectRing(ctx, bx, by, bw, bh, bh / 2, 1, 0xFF14181D, searchFocused ? ACCENT : 0xFF3A3F48);

        int gx = bx + 16, gy = by + bh / 2 - 1;
        disc(ctx, gx, gy, 3, SUBTEXT_COLOR);
        ctx.fill(gx + 2, gy + 2, gx + 6, gy + 3, SUBTEXT_COLOR);

        if (searchField == null) {
            searchField = new EditBox(this.font, bx + 30, by + 6, bw - 40, bh - 12, Component.empty());
            searchField.setMaxLength(48);
            searchField.setBordered(false);
            searchField.setResponder(s -> { searchText = s; for (Column c : columns) c.scroll = 0; });
        } else {
            searchField.setX(bx + 30); searchField.setY(by + 6); searchField.setWidth(bw - 40);
        }
        if (searchText.isEmpty() && !searchFocused) {
            sst(ctx, this.font, "Search…", bx + 30, by + (bh - 8) / 2, SUBTEXT_COLOR, 0.9f);
        } else {
            searchField.extractRenderState(ctx, mouseX, mouseY, 0);
        }
    }

    private void renderColumnCard(GuiGraphicsExtractor ctx, Column c, int x0, int x1, int cardBottom, int mouseX, int mouseY) {
        int hy = cyTop() - HEADER_H;
        int w = x1 - x0;
        roundedRect(ctx, x0, hy, w, cardBottom - hy, CARD_RADIUS, CARD_BG);
        ctx.fill(x0 + CARD_RADIUS, hy, x1 - CARD_RADIUS, hy + HEADER_STRIP_H, ACCENT);
        sst(ctx, this.font, c.name, x0 + 10, hy + HEADER_STRIP_H + 6, TEXT_COLOR, 1f);
    }

    private void renderColumnScrollbar(GuiGraphicsExtractor ctx, Column c, int x0, int x1, int top, int bot) {
        int ms = maxScrollFor(c);
        if (ms <= 0) return;
        int trackX = x1 - 3;
        int vp = bot - top;
        int barH = Math.max(20, (int) ((long) vp * vp / columnContentHeight(c)));
        int barY = top + (int) ((long) (vp - barH) * c.scroll / ms);
        ctx.fill(trackX, top, trackX + 2, bot, 0xFF141A20);
        ctx.fill(trackX, barY, trackX + 2, barY + barH, ACCENT);
    }

    private void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<Column> cols = visibleColumns();
        int top = cyTop(), bot = cyBot(), colW = columnWidth();

        for (int i = 0; i < cols.size(); i++) {
            Column c = cols.get(i);
            int x0 = columnX0(i), x1 = x0 + colW;
            int colBottom = Math.min(top + columnContentHeight(c), bot);

            renderColumnCard(ctx, c, x0, x1, colBottom, mouseX, mouseY);

            ctx.enableScissor(x0, top, x1, colBottom);
            for (RowLayout rl : layoutColumn(c, c.scroll)) {
                if (rl.rowBottom > top && rl.rowTop < colBottom) renderRow(ctx, rl.feature, x0, x1, rl.rowTop, mouseX, mouseY);
                int animH = rl.subBottom - rl.subTop;
                if (animH > 0 && rl.subBottom > top && rl.subTop < colBottom) {
                    renderSubPanel(ctx, rl.feature, x0, x1, rl.subTop, animH, mouseX, mouseY);
                    ctx.enableScissor(x0, top, x1, colBottom); // restore the column-wide clip renderSubPanel may have narrowed
                }
            }
            ctx.disableScissor();

            renderColumnScrollbar(ctx, c, x0, x1, top, colBottom);
        }
    }

    private void renderRow(GuiGraphicsExtractor ctx, Feature f, int x0, int x1, int top, int mouseX, int mouseY) {
        boolean on = f.hasMaster() && f.get.get();
        boolean inView = mouseY >= cyTop() && mouseY <= cyBot();
        boolean hover = inView && mouseX >= x0 && mouseX <= x1 && mouseY >= top && mouseY <= top + ROW_H;

        if (on) ctx.fill(x0 + 2, top, x1 - 2, top + ROW_H, ROW_ENABLED);
        if (hover) ctx.fill(x0 + 2, top, x1 - 2, top + ROW_H, ROW_HOVER);
        if (on) ctx.fill(x0 + 2, top + 3, x0 + 4, top + ROW_H - 3, ACCENT);

        String label = f.name;
        int maxTextW = x1 - x0 - 20;
        if (stw(this.font, label) > maxTextW) {
            while (label.length() > 1 && stw(this.font, label + "…") > maxTextW) label = label.substring(0, label.length() - 1);
            label = label + "…";
        }
        ctx.text(this.font, label, x0 + 10, top + (ROW_H - 8) / 2, on ? TEXT_COLOR : SUBTEXT_COLOR, false);
        if (hover) {
            String d = descFor(f.name);
            if (!d.isEmpty()) { hoverDesc = d; hoverDescX = x1 + 8; hoverDescY = top; }
        }

        if (!f.sub.isEmpty()) {
            drawChevron(ctx, x1 - 14, top + ROW_H / 2 - 2, f.expanded(), f.expanded() ? ACCENT : CHEVRON_COLOR);
        }
    }

    /** Small floating tooltip drawn last, on top of everything, for the row the mouse is hovering. */
    private void renderHoverTooltip(GuiGraphicsExtractor ctx) {
        if (hoverDesc == null) return;
        int tw = stw(this.font, hoverDesc);
        int bw = tw + 16, bh = 18;
        int bx = Math.min(hoverDescX, this.width - bw - 4);
        int by = hoverDescY;
        roundedRectRing(ctx, bx, by, bw, bh, 5, 1, 0xFF14181D, ACCENT);
        st(ctx, this.font, hoverDesc, bx + 8, by + 5, TEXT_COLOR);
    }

    /** Odin-style rounded pill with a hollow accent ring and a sliding circular knob. Static so the
     *  static nested Setting subclasses (which have no outer-instance reference) can call it too. */
    static void drawTogglePill(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean on, float knobProgress, boolean hover) {
        int track = on ? (hover ? ACCENT_HOVER : ACCENT) : TRACK_OFF;
        int ring = on ? (hover ? ACCENT_HOVER : ACCENT) : (hover ? 0xFF565C68 : 0xFF464C56);
        roundedRectRing(ctx, x, y, w, h, h / 2, 2, track, ring);
        int knobR = h / 2 - 3;
        int knobX = x + knobR + 3 + Math.round(knobProgress * (w - 2 * (knobR + 3)));
        disc(ctx, knobX, y + h / 2, knobR, 0xFFFFFFFF);
    }

    /** Note: while animating this narrows the caller's scissor to hide the not-yet-revealed
     *  portion of the panel; it never disables scissoring, so the caller is responsible for
     *  re-establishing its own (wider) clip immediately afterward. */
    private void renderSubPanel(GuiGraphicsExtractor ctx, Feature f, int x0, int x1, int top, int animatedH, int mouseX, int mouseY) {
        if (f.expandAnim.isAnimating()) ctx.enableScissor(x0, top, x1, top + animatedH);

        int subH = f.naturalSubHeight();
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

    private boolean hovBtn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
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

        // ----- search (floating pill, bottom-center) -----
        int swW = 190, swH = 24;
        int sx = (this.width - swW) / 2;
        int sy = this.height - BOTTOM_RESERVE + (BOTTOM_RESERVE - swH) / 2 - 8;
        searchFocused = mx >= sx && mx <= sx + swW && my >= sy && my <= sy + swH;
        if (searchField != null) searchField.setFocused(searchFocused);
        if (searchFocused) return true;

        // ----- top-right pill buttons -----
        int[][] rects = topBarButtonRects();
        if (hovBtn(mx, my, rects[0][0], rects[0][1], rects[0][2], rects[0][3])) { Minecraft.getInstance().setScreen(new FishHudEditor(this)); return true; }
        if (hovBtn(mx, my, rects[1][0], rects[1][1], rects[1][2], rects[1][3])) { Minecraft.getInstance().setScreen(new CreditsScreen(this)); return true; }
        if (hovBtn(mx, my, rects[2][0], rects[2][1], rects[2][2], rects[2][3])) {
            if (resetArmed) { resetAllColumns(); resetArmed = false; }
            else { resetArmed = true; resetArmedAt = System.currentTimeMillis(); }
            return true;
        }
        if (hovBtn(mx, my, rects[3][0], rects[3][1], rects[3][2], rects[3][3])) { onClose(); return true; }

        // ----- content columns / rows / sub-panels -----
        if (my >= cyTop() && my <= cyBot()) {
            List<Column> cols = visibleColumns();
            int colW = columnWidth();
            for (int ci = 0; ci < cols.size(); ci++) {
                Column col = cols.get(ci);
                int x0 = columnX0(ci), x1 = x0 + colW;
                if (mx < x0 || mx > x1) continue;

                for (RowLayout rl : layoutColumn(col, col.scroll)) {
                    Feature f = rl.feature;
                    // row hit — left-click toggles master on/off, right-click toggles the expand panel
                    // (features with no master toggle expand on either click)
                    if (my >= rl.rowTop && my <= rl.rowBottom) {
                        if (f.hasMaster()) {
                            if (btn == 1 && !f.sub.isEmpty()) f.toggleExpanded();
                            else f.set.accept(!f.get.get());
                        } else if (!f.sub.isEmpty()) {
                            f.toggleExpanded();
                        }
                        return true;
                    }
                    // sub-panel hit
                    int subH = rl.subBottom - rl.subTop;
                    if (subH > 0 && my >= rl.subTop && my <= rl.subBottom) {
                        int leftX = x0 + 14, rightX = x1 - 12, ssy = rl.subTop + 6;
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
                }
                return true; // swallow clicks in the column's empty space
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
        List<Column> cols = visibleColumns();
        int colW = columnWidth();
        for (int i = 0; i < cols.size(); i++) {
            int x0 = columnX0(i), x1 = x0 + colW;
            if (mouseX >= x0 && mouseX <= x1) {
                Column c = cols.get(i);
                c.scroll = Mth.clamp((int) (c.scroll - verticalAmount * 18), 0, maxScrollFor(c));
                return true;
            }
        }
        return true;
    }

    private void resetAllColumns() {
        for (Column c : columns) for (Feature f : c.features) {
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
        if (searchFocused && searchField != null) { searchField.charTyped(input); searchText = searchField.getValue(); for (Column c : columns) c.scroll = 0; return true; }
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
        int scroll = 0;
        Column(String name, String icon) { this.name = name; this.icon = icon; }
    }

    static class Feature {
        final String name;
        final Supplier<Boolean> get;
        final Consumer<Boolean> set;
        final List<Setting> sub = new ArrayList<>();
        final Easing.Anim expandAnim = new Easing.Anim(250);
        Feature(String name, Supplier<Boolean> get, Consumer<Boolean> set) {
            this.name = name; this.get = get; this.set = set;
        }
        boolean hasMaster() { return get != null && set != null; }

        boolean expanded() { return expandAnim.target(); }
        void toggleExpanded() { expandAnim.setTarget(!expandAnim.target()); }
        int naturalSubHeight() {
            if (sub.isEmpty()) return 0;
            int total = 0;
            for (Setting s : sub) total += s.getHeight();
            return total + 10;
        }
        int animatedSubHeight() {
            int natural = naturalSubHeight();
            return natural == 0 ? 0 : Math.round(natural * expandAnim.progress());
        }
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

    /** Odin-style rounded pill toggle with a hollow accent ring and an animated sliding knob. */
    static class ToggleSetting extends Setting {
        static final int W = 34;
        Supplier<Boolean> getter; Consumer<Boolean> setter;
        private final Easing.Anim knobAnim = new Easing.Anim(150);
        private Boolean lastValue = null;
        ToggleSetting(String name, String desc, Supplier<Boolean> g, Consumer<Boolean> s) {
            super(name, desc); this.getter = g; this.setter = s;
        }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            boolean on = getter.get();
            if (lastValue == null) { lastValue = on; knobAnim.setTarget(on); }
            else if (lastValue != on) { lastValue = on; knobAnim.setTarget(on); }
            int tx = rightX - W - 2;
            int ty = sy + (ITEM_HEIGHT - PILL_H) / 2;
            boolean hov = mx >= tx && mx <= tx + W && my >= ty && my <= ty + PILL_H;
            drawTogglePill(ctx, tx, ty, W, PILL_H, on, knobAnim.progress(), hov);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int tx = rightX - W - 2;
            int ty = sy + (ITEM_HEIGHT - PILL_H) / 2;
            if (mx >= tx && mx <= tx + W && my >= ty && my <= ty + PILL_H) {
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
    /** Odin-style selector: a rounded pill showing the current value; click expands an animated
     *  inline list of every option beneath it (right-click quick-cycles without expanding). */
    static class DropdownSetting<T> extends Setting {
        T[] values; Supplier<T> getter; Consumer<T> setter;
        private final Easing.Anim expandAnim = new Easing.Anim(200);
        private boolean expanded = false;
        private int pillX = 0, pillW = 0;

        DropdownSetting(String name, String desc, T[] vals, Supplier<T> g, Consumer<T> s) {
            super(name, desc); this.values = vals; this.getter = g; this.setter = s;
        }

        private int indexOfCurrent() {
            T cur = getter.get();
            for (int i = 0; i < values.length; i++) if (values[i] == cur || values[i].equals(cur)) return i;
            return 0;
        }

        @Override int getHeight() {
            return ITEM_HEIGHT + Math.round(values.length * OPTION_H * expandAnim.progress());
        }

        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            String current = getter.get().toString();
            int textW = stw(tr, current);
            pillW = textW + 22;
            pillX = rightX - pillW - 2;
            int by = sy + (ITEM_HEIGHT - PILL_H) / 2;
            boolean hov = mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H;
            roundedRectRing(ctx, pillX, by, pillW, PILL_H, PILL_H / 2, 2, TRACK_OFF, hov ? ACCENT_HOVER : ACCENT);
            st(ctx, tr, current, pillX + 10, by + (PILL_H - 8) / 2 - 1, TEXT_COLOR);

            boolean animating = expandAnim.isAnimating();
            if (expanded || animating) {
                int animH = Math.round(values.length * OPTION_H * expandAnim.progress());
                int oy = sy + ITEM_HEIGHT;
                if (animating) ctx.enableScissor(leftX, oy, rightX, oy + animH);
                roundedRect(ctx, leftX + 2, oy, rightX - leftX - 4, values.length * OPTION_H, 5, SUBROW_BG);
                int curIdx = indexOfCurrent();
                for (int i = 0; i < values.length; i++) {
                    int rowY = oy + i * OPTION_H;
                    boolean selected = i == curIdx;
                    boolean rowHov = mx >= leftX + 2 && mx <= rightX - 2 && my >= rowY && my <= rowY + OPTION_H;
                    if (rowHov) roundedRect(ctx, leftX + 4, rowY + 1, rightX - leftX - 8, OPTION_H - 2, 4, ROW_HOVER);
                    st(ctx, tr, values[i].toString(), leftX + 10, rowY + (OPTION_H - 8) / 2,
                            selected ? ACCENT_HOVER : (rowHov ? TEXT_COLOR : SUBTEXT_COLOR));
                    if (selected) ctx.fill(leftX + 2, rowY + 3, leftX + 4, rowY + OPTION_H - 3, ACCENT);
                }
                if (animating) ctx.disableScissor();
            }
        }

        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int by = sy + (ITEM_HEIGHT - PILL_H) / 2;
            if (mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H) {
                if (btn == 1) {
                    setter.accept(values[(indexOfCurrent() + 1) % values.length]);
                } else {
                    expanded = !expanded;
                    expandAnim.setTarget(expanded);
                }
                return true;
            }
            if (expanded) {
                int oy = sy + ITEM_HEIGHT;
                for (int i = 0; i < values.length; i++) {
                    int rowY = oy + i * OPTION_H;
                    if (mx >= leftX && mx <= rightX && my >= rowY && my <= rowY + OPTION_H) {
                        setter.accept(values[i]);
                        expanded = false;
                        expandAnim.setTarget(false);
                        return true;
                    }
                }
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
        int sqX, sqY, sqW = 82, sqH = 46, hueX, hueY, hueW = 8, hueH = 46;

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

            hueX = sqX + sqW + 5; hueY = sqY;
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
            int by = sy + (ITEM_HEIGHT - PILL_H) / 2;
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + PILL_H;
            roundedRect(ctx, bx, by, bw, PILL_H, PILL_H / 2, hov ? ACCENT_HOVER : ACCENT);
            st(ctx, tr, "Open", bx + (bw - stw(tr, "Open")) / 2, by + (PILL_H - 8) / 2 - 1, 0xFF06302F);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int bw = 60;
            int bx = rightX - bw - 2;
            int by = sy + (ITEM_HEIGHT - PILL_H) / 2;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + PILL_H) {
                action.run(); return true;
            }
            return false;
        }
    }

    /** In-GUI rebind box for a vanilla {@link net.minecraft.client.KeyMapping} — click, then
     *  press a key or mouse button to bind it (Esc unbinds). Stays in sync with Options > Controls
     *  since it edits the same KeyMapping object. */
    /** Odin-style rounded pill rebind box — click, then press a key/mouse button (Esc unbinds). */
    static class KeybindSetting extends Setting {
        Supplier<net.minecraft.client.KeyMapping> getter;
        boolean capturing = false;
        private int pillX = 0, pillW = 0;
        KeybindSetting(String name, String desc, Supplier<net.minecraft.client.KeyMapping> g) {
            super(name, desc); this.getter = g;
        }
        private String label() {
            if (capturing) return "...";
            net.minecraft.client.KeyMapping kb = getter.get();
            if (kb == null) return "-";
            return kb.isUnbound() ? "Not Bound" : kb.getTranslatedKeyMessage().getString();
        }
        @Override
        void render(GuiGraphicsExtractor ctx, int leftX, int rightX, int sy, int mx, int my, net.minecraft.client.gui.Font tr) {
            String t = label();
            int textW = stw(tr, t);
            pillW = textW + 20;
            pillX = rightX - pillW - 2;
            int by = sy + (ITEM_HEIGHT - PILL_H) / 2;
            boolean hov = mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H;
            int ring = capturing ? ACCENT_HOVER : (hov ? ACCENT : 0xFF464C56);
            roundedRectRing(ctx, pillX, by, pillW, PILL_H, PILL_H / 2, 2, TRACK_OFF, ring);
            st(ctx, tr, t, pillX + (pillW - textW) / 2, by + (PILL_H - 8) / 2 - 1, capturing ? ACCENT_HOVER : TEXT_COLOR);
        }
        @Override
        boolean onClick(int mx, int my, int leftX, int rightX, int sy, int btn) {
            int by = sy + (ITEM_HEIGHT - PILL_H) / 2;
            if (mx >= pillX && mx <= pillX + pillW && my >= by && my <= by + PILL_H) {
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
