package blade.addon.features;

import config.practical.hud.ComponentEditScreen;
import blade.addon.utils.config.Config;
import blade.addon.utils.config.FishConfig;
import blade.addon.utils.config.values.*;
import blade.addon.utils.dungeon.Phase;
import blade.addon.utils.dungeon.Section;
import blade.addon.utils.dungeon.Split;
import blade.addon.utils.rendering.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.widget.TextFieldWidget;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FishModScreen extends Screen {

    // Colors
    private static final int BG_COLOR = 0xFF1A1A1A;
    private static final int SIDEBAR_COLOR = 0xFF111111;
    private static final int PANEL_COLOR = 0xFF1E1E1E;
    private static final int BORDER_COLOR = 0xFF2A2A2A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SUBTEXT_COLOR = 0xFF888888;
    private static final int ACCENT = 0xFF00AACC;
    private static final int ACCENT_HOVER = 0xFF00CCEE;
    private static final int TOGGLE_ON = 0xFF00AACC;
    private static final int TOGGLE_OFF = 0xFF333333;
    private static final int TOGGLE_TEXT = 0xFFFFFFFF;
    private static final int SLIDER_BG = 0xFF222222;
    private static final int SLIDER_FILL = 0xFF00AACC;
    private static final int INPUT_BG = 0xFF222222;

    private static final int SIDEBAR_WIDTH = 160;
    private static final int HEADER_HEIGHT = 50;
    private static final int ITEM_HEIGHT = 56;
    private static final int TOGGLE_W = 50;
    private static final int TOGGLE_H = 18;
    private static final int PADDING = 16;
    private static final int SLIDER_W = 120;
    private static final int SLIDER_H = 8;
    private static final int INPUT_W = 150;
    private static final int INPUT_H = 18;
    private static final int SUBCAT_HEIGHT = 28;

    private int selectedCategory = 0;
    private String searchText = "";
    private boolean searchFocused = false;
    private int scrollOffset = 0;
    private Setting activeSlider = null;
    private Setting activeInput = null;
    private int lastMouseX = 0;
    private TextFieldWidget searchField;

    private final List<Category> categories = new ArrayList<>();

    public FishModScreen() {
        super(Text.literal("FishMod"));
        buildCategories();
    }

    private void buildCategories() {
        Category myFeatures = new Category("My Features");
        myFeatures.add(new ToggleSetting("Send Lag to Party", "Sends lag message to party chat after dungeon run", () -> FishSettings.sendLagToParty, v -> FishSettings.sendLagToParty = v));
        myFeatures.add(new ToggleSetting("Show Puzzles", "Displays dungeon puzzles on screen", () -> FishSettings.showPuzzles, v -> FishSettings.showPuzzles = v));
        myFeatures.add(new ToggleSetting("Toggleable search bar (Ctrl+F)", "", () -> ExtraOptions.toggleableSearchBar, v -> ExtraOptions.toggleableSearchBar = v));
        myFeatures.add(new ToggleSetting("Auto Accept Party Invite", "Accepts any party invite, says message, then leaves", () -> FishSettings.autoAcceptPartyInvite, v -> FishSettings.autoAcceptPartyInvite = v));
        myFeatures.add(new ToggleSetting("Party Finder Stats", "Shows secrets/run and M7 PB in Party Finder tooltips (API key required)", () -> FishSettings.partyFinderStats, v -> FishSettings.partyFinderStats = v));
        categories.add(myFeatures);

        // Splits
        Category splits = new Category("Splits");
        splits.add(new ToggleSetting("Enable splits", "", () -> Phase.enableSplits, v -> Phase.enableSplits = v));
        splits.add(new ToggleSetting("Include total time", "", () -> Phase.includeTotalTime, v -> Phase.includeTotalTime = v));
        splits.add(new ToggleSetting("Send split in chat", "", () -> Phase.sendSplitInChat, v -> Phase.sendSplitInChat = v));
        splits.add(new DropdownSetting<>("Tick timer type", "", Split.TimerType.values(), () -> Split.timerType, v -> Split.timerType = v));
        splits.add(new ToggleSetting("Only show activated splits", "", () -> Phase.onlyShowActivatedSplits, v -> Phase.onlyShowActivatedSplits = v));
        categories.add(splits);

        // Dungeons
        Category dungeons = new Category("Dungeons");
        dungeons.add(new SubcategoryHeader("Death Message"));
        dungeons.add(new ToggleSetting("Enable Death Message", "Shows a message when a player dies", () -> FishSettings.deathMessageEnabled, v -> FishSettings.deathMessageEnabled = v));
        dungeons.add(new InputSetting("Death Message", "Use {name} for the player who died", () -> FishSettings.deathMessageTemplate, v -> FishSettings.deathMessageTemplate = v));
        dungeons.add(new ToggleSetting("Send to Party Chat", "Broadcasts the message to /pc instead of showing locally", () -> FishSettings.deathMessageToParty, v -> FishSettings.deathMessageToParty = v));
        categories.add(dungeons);

        // Party Commands
        Category partyCat = new Category("Party Commands");
        partyCat.add(new LabelSetting("Trigger", "Type . or ! before any command in chat"));
        partyCat.add(new SubcategoryHeader("Commands"));
        partyCat.add(new ToggleSetting(".ai / .allinv", "Runs /party settings allinvite", () -> FishSettings.pcAllinvite, v -> FishSettings.pcAllinvite = v));
        partyCat.add(new ToggleSetting(".pb", "Sends your M7 personal best to party", () -> FishSettings.pcPb, v -> FishSettings.pcPb = v));
        partyCat.add(new ToggleSetting(".cata", "Sends your cata level to party (API key required)", () -> FishSettings.pcCata, v -> FishSettings.pcCata = v));
        partyCat.add(new ToggleSetting(".rtca", "Sends runs-to-class-50 to party (API key required)", () -> FishSettings.pcRtca, v -> FishSettings.pcRtca = v));
        partyCat.add(new ToggleSetting(".f1-.f7 / .m1-.m7", "Joins a dungeon instance (30s after entry)", () -> FishSettings.pcJoinFloor, v -> FishSettings.pcJoinFloor = v));
        partyCat.add(new ToggleSetting(".fps", "Sends your current FPS to party", () -> FishSettings.pcFps, v -> FishSettings.pcFps = v));
        partyCat.add(new ToggleSetting(".tps", "Sends estimated server TPS to party", () -> FishSettings.pcTps, v -> FishSettings.pcTps = v));
        partyCat.add(new ToggleSetting(".ping", "Sends your ping to party", () -> FishSettings.pcPing, v -> FishSettings.pcPing = v));
        partyCat.add(new SubcategoryHeader("API Settings"));
        partyCat.add(new InputSetting("Hypixel API Key", "Required for .cata and .rtca", () -> FishSettings.hypixelApiKey, v -> FishSettings.hypixelApiKey = v));
        partyCat.add(new SliderIntSetting("RTCA XP / run", "Class XP per M7 run as that class",
                () -> FishSettings.rtcaClassXpPerRun, v -> FishSettings.rtcaClassXpPerRun = v, 10_000, 500_000));
        categories.add(partyCat);

        // Credits
        Category credits = new Category("Credits");
        credits.add(new LabelSetting("FishMod", "Made by RedFish2471"));
        credits.add(new LabelSetting("Discord", "discord.gg/3mSuQUB8kk"));
        credits.add(new SubcategoryHeader("Based on blade-addons"));
        credits.add(new LabelSetting("blade", "Splits system, SearchBar, and core mod infrastructure"));
        categories.add(credits);
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int w = this.width;
        int h = this.height;

        context.fill(0, 0, w, h, BG_COLOR);
        context.fill(0, 0, SIDEBAR_WIDTH, h, SIDEBAR_COLOR);
        context.fill(SIDEBAR_WIDTH, 0, SIDEBAR_WIDTH + 1, h, BORDER_COLOR);
        context.fill(SIDEBAR_WIDTH + 1, 0, w, HEADER_HEIGHT, PANEL_COLOR);
        context.fill(SIDEBAR_WIDTH + 1, HEADER_HEIGHT, w, HEADER_HEIGHT + 1, BORDER_COLOR);

        String screenTitle = "FishMod";
        String screenSub   = "Made by RedFish2471 DM for suggestions";
        context.drawText(this.textRenderer, screenTitle, SIDEBAR_WIDTH + PADDING, 10, ACCENT, false);
        context.drawText(this.textRenderer, screenSub, SIDEBAR_WIDTH + PADDING, 24, SUBTEXT_COLOR, false);

        // Search bar
        int searchX = PADDING;
        int searchY = 12;
        int searchW = SIDEBAR_WIDTH - PADDING * 2;
        int searchH = 16;
        context.fill(searchX, searchY, searchX + searchW, searchY + searchH, 0xFF222222);
        context.fill(searchX, searchY, searchX + searchW, searchY + 1, searchFocused ? ACCENT : BORDER_COLOR);
        context.fill(searchX, searchY + searchH - 1, searchX + searchW, searchY + searchH, searchFocused ? ACCENT : BORDER_COLOR);
        String displaySearch = searchText.isEmpty() ? "Search..." : searchText;
        context.drawText(this.textRenderer, displaySearch, searchX + 4, searchY + 4, searchText.isEmpty() ? SUBTEXT_COLOR : TEXT_COLOR, false);

        // Categories
        String lowerSearch = searchText.isEmpty() ? "" : searchText.toLowerCase();
        int catY = 40;
        for (int i = 0; i < categories.size(); i++) {
            Category cat = categories.get(i);
            if (!lowerSearch.isEmpty() && cat.settings.stream().noneMatch(s -> s.name.toLowerCase().contains(lowerSearch))) continue;
            boolean selected = i == selectedCategory;
            boolean hovered = mouseX >= 0 && mouseX < SIDEBAR_WIDTH && mouseY >= catY && mouseY < catY + 28;
            context.fill(0, catY, SIDEBAR_WIDTH, catY + 28, selected ? 0xFF252525 : hovered ? 0xFF1D1D1D : 0xFF000000);
            if (selected) context.fill(0, catY, 3, catY + 28, ACCENT);
            context.drawText(this.textRenderer, cat.name, PADDING, catY + 10, selected ? ACCENT : TEXT_COLOR, false);
            catY += 28;
        }

        // Settings
        context.enableScissor(SIDEBAR_WIDTH + 1, HEADER_HEIGHT + 1, w, h);
        Category current = categories.get(selectedCategory);
        int settingY = HEADER_HEIGHT + PADDING - scrollOffset;
        for (Setting setting : current.settings) {
            if (!lowerSearch.isEmpty() && !setting.name.toLowerCase().contains(lowerSearch)) continue;
            int sh = setting.getHeight();
            if (settingY + sh < HEADER_HEIGHT || settingY > h) { settingY += sh; continue; }

            if (setting instanceof SubcategoryHeader) {
                setting.render(context, w, settingY, mouseX, mouseY, this.textRenderer);
            } else {
                boolean rowHovered = mouseX > SIDEBAR_WIDTH + 1 && mouseX < w && mouseY > settingY && mouseY < settingY + sh;
                if (rowHovered) context.fill(SIDEBAR_WIDTH + 1, settingY, w, settingY + sh, 0xFF232323);
                context.fill(SIDEBAR_WIDTH + 1, settingY + sh - 1, w, settingY + sh, BORDER_COLOR);
                context.drawText(this.textRenderer, setting.name, SIDEBAR_WIDTH + PADDING, settingY + 8, TEXT_COLOR, false);
                if (!setting.description.isEmpty())
                    context.drawText(this.textRenderer, setting.description, SIDEBAR_WIDTH + PADDING, settingY + 20, SUBTEXT_COLOR, false);
                setting.render(context, w, settingY, mouseX, mouseY, this.textRenderer);
            }
            settingY += sh;
        }
        context.disableScissor();

        // Edit HUD button at bottom of sidebar
        int btnW = SIDEBAR_WIDTH - PADDING * 2;
        int btnH = 20;
        int btnX = PADDING;
        int btnY = h - PADDING - btnH;
        boolean hudBtnHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        context.fill(btnX, btnY, btnX + btnW, btnY + btnH, hudBtnHovered ? ACCENT_HOVER : ACCENT);
        String btnLabel = "Edit HUD";
        context.drawText(this.textRenderer, btnLabel, btnX + (btnW - this.textRenderer.getWidth(btnLabel)) / 2, btnY + (btnH - 8) / 2, 0xFFFFFFFF, false);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int mx = (int) click.x();
        int my = (int) click.y();

        activeInput = null;

        int searchX = PADDING;
        int searchY = 12;
        int searchW = SIDEBAR_WIDTH - PADDING * 2;
        searchFocused = mx >= searchX && mx <= searchX + searchW && my >= searchY && my <= searchY + 16;
        if (searchField == null) {
            searchField = new TextFieldWidget(this.textRenderer, PADDING, 12, SIDEBAR_WIDTH - PADDING * 2, 16, Text.empty());
            searchField.setChangedListener(s -> searchText = s);
        }
        searchField.setFocused(searchFocused);

        String lowerSearch = searchText.isEmpty() ? "" : searchText.toLowerCase();
        int catY = 40;
        for (int i = 0; i < categories.size(); i++) {
            Category cat = categories.get(i);
            if (!lowerSearch.isEmpty() && cat.settings.stream().noneMatch(s -> s.name.toLowerCase().contains(lowerSearch))) continue;
            if (mx >= 0 && mx < SIDEBAR_WIDTH && my >= catY && my < catY + 28) {
                selectedCategory = i;
                scrollOffset = 0;
                return true;
            }
            catY += 28;
        }

        Category current = categories.get(selectedCategory);
        int settingY = HEADER_HEIGHT + PADDING - scrollOffset;
        for (Setting setting : current.settings) {
            int sh = setting.getHeight();
            if (!lowerSearch.isEmpty() && !setting.name.toLowerCase().contains(lowerSearch)) { settingY += sh; continue; }
            if (setting instanceof InputSetting && mx > this.width - PADDING - INPUT_W && mx < this.width - PADDING && my > settingY && my < settingY + sh) {
                activeInput = setting;
            }
            if (setting.onClick(mx, my, this.width, settingY, click.button())) return true;
            if (setting instanceof SliderIntSetting || setting instanceof SliderDoubleSetting) {
                int sx = this.width - PADDING - SLIDER_W;
                int sy = settingY + (ITEM_HEIGHT - SLIDER_H) / 2;
                if (mx >= sx && mx <= sx + SLIDER_W && my >= sy - 4 && my <= sy + SLIDER_H + 4) {
                    activeSlider = setting;
                    lastMouseX = mx;
                    setting.onDrag(mx, sx, SLIDER_W);
                    return true;
                }
            }
            settingY += sh;
        }

        // Edit HUD button
        int btnW = SIDEBAR_WIDTH - PADDING * 2;
        int btnH = 20;
        int btnX = PADDING;
        int btnY = this.height - PADDING - btnH;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            MinecraftClient.getInstance().setScreen(new ComponentEditScreen(this));
            return true;
        }

        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (activeSlider != null) {
            int sx = this.width - PADDING - SLIDER_W;
            activeSlider.onDrag((int) click.x(), sx, SLIDER_W);
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
    public boolean keyPressed(KeyInput input) {
        if (activeInput instanceof InputSetting is && is.textField != null) {
            is.textField.keyPressed(input);
            return true;
        }
        if (searchFocused && searchField != null) {
            searchField.keyPressed(input);
            return true;
        }
        return super.keyPressed(input);
    }

    public boolean charTyped(CharInput input) {
        if (activeInput instanceof InputSetting is && is.textField != null) {
            is.textField.charTyped(input);
            is.setter.accept(is.textField.getText());
            return true;
        }
        if (searchFocused && searchField != null) {
            searchField.charTyped(input);
            searchText = searchField.getText();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = MathHelper.clamp(
                (int)(scrollOffset - verticalAmount * 10),
                0,
                Math.max(0, categories.get(selectedCategory).settings.stream().mapToInt(Setting::getHeight).sum() - (this.height - HEADER_HEIGHT))
        );
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        Config.manager.save();
        FishConfig.manager.save();
        super.close();
    }

    // Base setting class
    static abstract class Setting {
        String name, description;
        Setting(String name, String description) { this.name = name; this.description = description; }
        abstract void render(DrawContext context, int w, int settingY, int mouseX, int mouseY, net.minecraft.client.font.TextRenderer tr);
        boolean onClick(int mx, int my, int w, int settingY, int button) { return false; }
        void onDrag(int mx, int sx, int sliderW) {}
        int getHeight() { return ITEM_HEIGHT; }
    }

    // Subcategory header
    static class SubcategoryHeader extends Setting {
        SubcategoryHeader(String name) { super(name, ""); }
        @Override int getHeight() { return SUBCAT_HEIGHT; }
        @Override
        void render(DrawContext ctx, int w, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            ctx.fill(SIDEBAR_WIDTH + 1, sy, w, sy + SUBCAT_HEIGHT, 0xFF141414);
            ctx.fill(SIDEBAR_WIDTH + 1, sy + SUBCAT_HEIGHT - 1, w, sy + SUBCAT_HEIGHT, BORDER_COLOR);
            ctx.fill(SIDEBAR_WIDTH + 1, sy, SIDEBAR_WIDTH + 3, sy + SUBCAT_HEIGHT, ACCENT);
            ctx.drawText(tr, name, SIDEBAR_WIDTH + PADDING, sy + (SUBCAT_HEIGHT - 8) / 2, ACCENT, false);
        }
    }

    // Toggle
    static class ToggleSetting extends Setting {
        Supplier<Boolean> getter; Consumer<Boolean> setter;
        ToggleSetting(String name, String desc, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            super(name, desc); this.getter = getter; this.setter = setter;
        }
        @Override
        void render(DrawContext ctx, int w, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int tx = w - PADDING - TOGGLE_W;
            int ty = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            boolean on = getter.get();
            boolean hov = mx >= tx && mx <= tx + TOGGLE_W && my >= ty && my <= ty + TOGGLE_H;
            int bg = on ? (hov ? ACCENT_HOVER : TOGGLE_ON) : (hov ? 0xFF444444 : TOGGLE_OFF);
            ctx.fill(tx, ty, tx + TOGGLE_W, ty + TOGGLE_H, bg);
            String t = on ? "ON" : "OFF";
            ctx.drawText(tr, t, tx + (TOGGLE_W - tr.getWidth(t)) / 2, ty + (TOGGLE_H - 8) / 2, TOGGLE_TEXT, false);
        }
        @Override
        boolean onClick(int mx, int my, int w, int sy, int button) {
            int tx = w - PADDING - TOGGLE_W;
            int ty = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            if (mx >= tx && mx <= tx + TOGGLE_W && my >= ty && my <= ty + TOGGLE_H) {
                setter.accept(!getter.get()); return true;
            }
            return false;
        }
    }

    // Int Slider
    static class SliderIntSetting extends Setting {
        Supplier<Integer> getter; Consumer<Integer> setter; int min, max;
        SliderIntSetting(String name, String desc, Supplier<Integer> getter, Consumer<Integer> setter, int min, int max) {
            super(name, desc); this.getter = getter; this.setter = setter; this.min = min; this.max = max;
        }
        @Override
        void render(DrawContext ctx, int w, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int sx = w - PADDING - SLIDER_W;
            int sliderY = sy + (ITEM_HEIGHT - SLIDER_H) / 2;
            float pct = (float)(getter.get() - min) / (max - min);
            ctx.fill(sx, sliderY, sx + SLIDER_W, sliderY + SLIDER_H, SLIDER_BG);
            ctx.fill(sx, sliderY, sx + (int)(SLIDER_W * pct), sliderY + SLIDER_H, SLIDER_FILL);
            String val = String.valueOf(getter.get());
            ctx.drawText(tr, val, sx - tr.getWidth(val) - 6, sliderY, TEXT_COLOR, false);
        }
        @Override
        void onDrag(int mx, int sx, int sliderW) {
            float pct = MathHelper.clamp((float)(mx - sx) / sliderW, 0, 1);
            setter.accept(min + (int)(pct * (max - min)));
        }
    }

    // Double Slider
    static class SliderDoubleSetting extends Setting {
        Supplier<Double> getter; Consumer<Double> setter; double min, max;
        SliderDoubleSetting(String name, String desc, Supplier<Double> getter, Consumer<Double> setter, double min, double max) {
            super(name, desc); this.getter = getter; this.setter = setter; this.min = min; this.max = max;
        }
        @Override
        void render(DrawContext ctx, int w, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int sx = w - PADDING - SLIDER_W;
            int sliderY = sy + (ITEM_HEIGHT - SLIDER_H) / 2;
            float pct = (float)((getter.get() - min) / (max - min));
            ctx.fill(sx, sliderY, sx + SLIDER_W, sliderY + SLIDER_H, SLIDER_BG);
            ctx.fill(sx, sliderY, sx + (int)(SLIDER_W * pct), sliderY + SLIDER_H, SLIDER_FILL);
            String val = String.format("%.1f", getter.get());
            ctx.drawText(tr, val, sx - tr.getWidth(val) - 6, sliderY, TEXT_COLOR, false);
        }
        @Override
        void onDrag(int mx, int sx, int sliderW) {
            float pct = MathHelper.clamp((float)(mx - sx) / sliderW, 0, 1);
            setter.accept(min + pct * (max - min));
        }
    }

    // Dropdown
    static class DropdownSetting<T> extends Setting {
        T[] values; Supplier<T> getter; Consumer<T> setter; boolean open = false;
        DropdownSetting(String name, String desc, T[] values, Supplier<T> getter, Consumer<T> setter) {
            super(name, desc); this.values = values; this.getter = getter; this.setter = setter;
        }
        @Override
        void render(DrawContext ctx, int w, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int bx = w - PADDING - 100;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            ctx.fill(bx, by, bx + 100, by + TOGGLE_H, 0xFF222222);
            ctx.fill(bx, by, bx + 100, by + 1, ACCENT);
            ctx.fill(bx, by + TOGGLE_H - 1, bx + 100, by + TOGGLE_H, ACCENT);
            String current = getter.get().toString();
            ctx.drawText(tr, current, bx + 4, by + (TOGGLE_H - 8) / 2, TEXT_COLOR, false);
            if (open) {
                int dy = by + TOGGLE_H;
                for (T val : values) {
                    boolean hov = mx >= bx && mx <= bx + 100 && my >= dy && my <= dy + TOGGLE_H;
                    ctx.fill(bx, dy, bx + 100, dy + TOGGLE_H, hov ? 0xFF333333 : 0xFF1A1A1A);
                    ctx.drawText(tr, val.toString(), bx + 4, dy + (TOGGLE_H - 8) / 2, TEXT_COLOR, false);
                    dy += TOGGLE_H;
                }
            }
        }
        @Override
        boolean onClick(int mx, int my, int w, int sy, int button) {
            int bx = w - PADDING - 100;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            if (mx >= bx && mx <= bx + 100 && my >= by && my <= by + TOGGLE_H) {
                open = !open; return true;
            }
            if (open) {
                int dy = by + TOGGLE_H;
                for (T val : values) {
                    if (mx >= bx && mx <= bx + 100 && my >= dy && my <= dy + TOGGLE_H) {
                        setter.accept(val); open = false; return true;
                    }
                    dy += TOGGLE_H;
                }
            }
            return false;
        }
    }

    // Text Input
    static class InputSetting extends Setting {
        Supplier<String> getter; Consumer<String> setter;
        TextFieldWidget textField;
        InputSetting(String name, String desc, Supplier<String> getter, Consumer<String> setter) {
            super(name, desc); this.getter = getter; this.setter = setter;
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
        void render(DrawContext ctx, int w, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            initField(tr);
            int ix = w - PADDING - INPUT_W;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            textField.setX(ix);
            textField.setY(iy);
            textField.render(ctx, mx, my, 0);
        }
        @Override
        boolean onClick(int mx, int my, int w, int sy, int button) {
            int ix = w - PADDING - INPUT_W;
            int iy = sy + (ITEM_HEIGHT - INPUT_H) / 2;
            if (mx >= ix && mx <= ix + INPUT_W && my >= iy && my <= iy + INPUT_H) {
                if (textField != null) textField.setFocused(true);
                return true;
            }
            return false;
        }
    }

    // Button
    static class ButtonSetting extends Setting {
        Runnable action;
        ButtonSetting(String name, String desc, Runnable action) {
            super(name, desc); this.action = action;
        }
        @Override
        void render(DrawContext ctx, int w, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int bx = w - PADDING - 80;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            boolean hov = mx >= bx && mx <= bx + 80 && my >= by && my <= by + TOGGLE_H;
            ctx.fill(bx, by, bx + 80, by + TOGGLE_H, hov ? ACCENT_HOVER : ACCENT);
            ctx.drawText(tr, "Open", bx + (80 - tr.getWidth("Open")) / 2, by + (TOGGLE_H - 8) / 2, TEXT_COLOR, false);
        }
        @Override
        boolean onClick(int mx, int my, int w, int sy, int button) {
            int bx = w - PADDING - 80;
            int by = sy + (ITEM_HEIGHT - TOGGLE_H) / 2;
            if (mx >= bx && mx <= bx + 80 && my >= by && my <= by + TOGGLE_H) {
                action.run(); return true;
            }
            return false;
        }
    }

    // Label (text only, no interactive element)
    static class LabelSetting extends Setting {
        LabelSetting(String name, String desc) { super(name, desc); }
        @Override
        void render(DrawContext ctx, int w, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {}
    }

    // Compact single-row label: name left, value right, 18px tall
    static class CompactLabelSetting extends Setting {
        private final String value;
        CompactLabelSetting(String name, String value) { super(name, ""); this.value = value; }
        @Override int getHeight() { return 24; }
        @Override
        void render(DrawContext ctx, int w, int sy, int mx, int my, net.minecraft.client.font.TextRenderer tr) {
            int textY = sy + (24 - 8) / 2;  // = sy+8, matches main loop name draw exactly
            ctx.drawText(tr, value, w - PADDING - tr.getWidth(value), textY, SUBTEXT_COLOR, false);
        }
    }

    static class Category {
        String name;
        List<Setting> settings = new ArrayList<>();
        Category(String name) { this.name = name; }
        void add(Setting s) { settings.add(s); }
    }
}