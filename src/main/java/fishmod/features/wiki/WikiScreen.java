package fishmod.features.wiki;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import org.lwjgl.glfw.GLFW;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

public class WikiScreen extends Screen {

    private static final int BAR_H      = 26;
    private static final int FIND_BAR_H = 22;
    private static final int SIDEBAR_W  = 140;
    private static final int BTN_W      = 24;
    private static final int BTN_H      = 20;
    private static final int COL_BAR    = 0xFF111827;
    private static final int COL_BTN    = 0xFF2a3a6a;
    private static final int COL_INPUT  = 0xFF0d1420;
    private static final int COL_SIDE   = 0xFF0e1520;

    private static String  lastUrl      = null;
    private static double  savedZoom    = 0.0;
    private static boolean sidebarOpen  = false;

    private final Screen parent;
    private final String  initialQuery;
    private MCEFBrowser   browser;

    private double pendingScroll  = 0.0;
    private double lastMouseX     = 0.0;
    private double lastMouseY     = 0.0;
    private long   lastFrameNanos = 0L;
    private double zoomLevel      = savedZoom;
    private boolean findVisible   = false;
    private String  lastFindText  = "";
    private int     bookmarkScroll = 0;

    private EditBox findField;

    private static final String WIKI_HOST = "hypixelskyblock.minecraft.wiki";
    private static final String WIKI_HOME = "https://hypixelskyblock.minecraft.wiki/w/Main_Page";

    public WikiScreen(Screen parent, String initialQuery) {
        super(Component.literal("Wiki"));
        this.parent       = parent;
        this.initialQuery = (initialQuery == null) ? "" : initialQuery;
    }

    // ── URL helpers ────────────────────────────────────────────────────────────

    private String buildUrl(String raw) {
        String q = raw.trim();
        if (q.isEmpty()) return WIKI_HOME;
        // Treat raw input as a wiki search — never honor arbitrary http(s) URLs from the caller.
        String enc = URLEncoder.encode(q, StandardCharsets.UTF_8);
        return "https://hypixelskyblock.minecraft.wiki/w/Special:Search?search=" + enc + "&go=Go";
    }

    private static boolean isWikiUrl(String url) {
        if (url == null || url.isEmpty()) return true;
        if (url.startsWith("about:") || url.startsWith("data:")) return true;
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) return false;
        int hostEnd = url.indexOf('/', schemeEnd + 3);
        String host = hostEnd < 0 ? url.substring(schemeEnd + 3) : url.substring(schemeEnd + 3, hostEnd);
        return host.equalsIgnoreCase(WIKI_HOST);
    }

    // ── coordinate helpers ─────────────────────────────────────────────────────

    private int bx(double guiX) { return (int)(guiX * minecraft.getWindow().getGuiScale()); }
    private int by(double guiY) { return (int)(guiY * minecraft.getWindow().getGuiScale()); }
    private int sideW()         { return sidebarOpen ? SIDEBAR_W : 0; }
    private int browserW()      { return width - sideW(); }
    private int browserH()      { return height - BAR_H - (findVisible ? FIND_BAR_H : 0); }
    private int browserPixelW() { return (int)(browserW() * minecraft.getWindow().getGuiScale()); }
    private int browserPixelH() { return (int)(browserH() * minecraft.getWindow().getGuiScale()); }

    // ── button positions (top bar) ─────────────────────────────────────────────

    private int btnY()          { return (BAR_H - BTN_H) / 2; }
    private int backX()         { return 4; }
    private int fwdX()          { return backX()    + BTN_W + 2; }
    private int reloadX()       { return fwdX()     + BTN_W + 2; }
    private int starX()         { return reloadX()  + BTN_W + 2; }  // bookmark toggle
    private int zoomInX()       { return width - BTN_W - 4; }
    private int zoomOutX()      { return zoomInX()  - BTN_W - 2; }

    // ── find bar positions ─────────────────────────────────────────────────────

    private int findBtnY()      { return height - FIND_BAR_H + (FIND_BAR_H - BTN_H) / 2; }
    private int findCloseX()    { return width - BTN_W - 4; }
    private int findNextX()     { return findCloseX()  - BTN_W - 2; }
    private int findPrevX()     { return findNextX()   - BTN_W - 2; }
    private int findFieldX()    { return 36; }
    private int findFieldW()    { return findPrevX() - 4 - findFieldX(); }

    private boolean over(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x <= bx + bw && y >= by && y <= by + bh;
    }

    // ── lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        String startUrl = (lastUrl != null && initialQuery.isEmpty() && isWikiUrl(lastUrl)) ? lastUrl : buildUrl(initialQuery);

        if (!MCEF.isInitialized()) return;
        if (browser == null) {
            browser = MCEF.createBrowser(startUrl, false);
            if (browser != null) browser.setZoomLevel(zoomLevel);
        }
        if (browser != null) browser.resize(browserPixelW(), browserPixelH());

        int ffY = height - FIND_BAR_H + (FIND_BAR_H - 16) / 2;
        findField = new EditBox(font, findFieldX(), ffY, findFieldW(), 16, Component.literal(""));
        findField.setMaxLength(256);
        findField.setValue(lastFindText);
        findField.setVisible(findVisible);
        addRenderableWidget(findField);
    }

    @Override
    public void resize(int w, int h) {
        String savedFind = findField != null ? findField.getValue() : null;
        super.resize(w, h);
        if (savedFind != null && findField != null) findField.setValue(savedFind);
        if (browser != null) browser.resize(browserPixelW(), browserPixelH());
    }

    @Override
    public void removed() {
        try {
            if (browser != null) {
                String cur = browser.getURL();
                if (cur != null && isWikiUrl(cur)) lastUrl = cur;
            }
        } catch (Exception ignored) {}
        savedZoom = zoomLevel;
        if (browser != null) { browser.close(); browser = null; }
    }

    // ── rendering ──────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        // Smooth scroll
        if (browser != null && pendingScroll != 0.0) {
            long now = System.nanoTime();
            double dt = lastFrameNanos == 0 ? 1.0/60.0 : Math.min(0.1, (now - lastFrameNanos) / 1e9);
            lastFrameNanos = now;
            double factor = 1.0 - Math.exp(-dt * 18.0);
            double step   = pendingScroll * factor;
            if (Math.abs(pendingScroll) < 0.5) { step = pendingScroll; pendingScroll = 0.0; }
            else pendingScroll -= step;
            browser.sendMouseWheel(bx(lastMouseX), by(lastMouseY - BAR_H), step, 0);
        } else {
            lastFrameNanos = 0L;
        }

        // Domain lock: bounce any non-wiki URL back to the wiki home so users can't navigate to
        // YouTube/external sites via in-page links.
        if (browser != null) {
            try {
                String cur = browser.getURL();
                if (cur != null && !cur.isEmpty() && !isWikiUrl(cur)) {
                    browser.loadURL(WIKI_HOME);
                }
            } catch (Exception ignored) {}
        }

        int bH = browserH();
        int bW = browserW();

        // Browser content
        boolean drawn = browser != null && browser.isTextureReady() && drawBrowserTexture(ctx, bW, bH);
        if (!drawn) {
            ctx.fill(0, BAR_H, bW, BAR_H + bH, 0xFF0d0e17);
            if (!MCEF.isInitialized()) {
                ctx.centeredText(font, "§cMCEF not installed!", bW/2, BAR_H + bH/2 - 10, 0xFFFFFFFF);
                ctx.centeredText(font, "§7Install §fMCEF §7from Modrinth for MC 1.21.11", bW/2, BAR_H + bH/2 + 4, 0xFFAAAAAA);
            } else {
                ctx.centeredText(font, "§7Loading...", bW/2, BAR_H + bH/2, 0xFFAAAAAA);
            }
        }

        // Loading progress stripe
        if (browser != null && browser.isLoading()) {
            long t  = System.currentTimeMillis();
            int  sw = bW / 3;
            int  pos = (int)((t % 2000) * (long)(bW + sw) / 2000) - sw;
            ctx.fill(Math.max(0, pos), BAR_H - 2, Math.min(bW, pos + sw), BAR_H, 0xFF4a7adb);
        }

        // Top bar
        ctx.fill(0, 0, width, BAR_H, COL_BAR);
        ctx.fill(0, BAR_H - 1, width, BAR_H, 0xFF2a3a6a);

        int bY = btnY();
        boolean canBack = browser != null && browser.canGoBack();
        boolean canFwd  = browser != null && browser.canGoForward();

        drawBtn(ctx, backX(),    bY, canBack ? "§f←" : "§8←");
        drawBtn(ctx, fwdX(),     bY, canFwd  ? "§f→" : "§8→");
        drawBtn(ctx, reloadX(),  bY, "§f↺");
        drawBtn(ctx, starX(),    bY, sidebarOpen ? "§e★" : "§7☆");   // bookmark sidebar toggle
        drawBtn(ctx, zoomOutX(), bY, "§f−");
        drawBtn(ctx, zoomInX(),  bY, "§f+");

        if (zoomLevel != 0.0) {
            int pct = (int)(Math.pow(1.2, zoomLevel) * 100);
            String lbl = "§7" + pct + "%";
            ctx.text(font, lbl,
                zoomOutX() - font.width(lbl) - 4,
                bY + (BTN_H - 8) / 2, 0xFF888888);
        }

        // Bookmark sidebar
        if (sidebarOpen) renderSidebar(ctx, mx, my);

        // Find bar
        if (findVisible) {
            int fbY = height - FIND_BAR_H;
            ctx.fill(0, fbY, width, height, COL_BAR);
            ctx.fill(0, fbY, width, fbY + 1, 0xFF2a3a6a);
            ctx.text(font, "§7Find:", 4, fbY + (FIND_BAR_H - 8) / 2, 0xFFAAAAAA);
            ctx.fill(findFieldX() - 2, fbY + (FIND_BAR_H - 18) / 2,
                     findFieldX() + findFieldW() + 2, height - (FIND_BAR_H - 18) / 2, COL_INPUT);
            int fb = findBtnY();
            drawBtn(ctx, findPrevX(),  fb, "§f↑");
            drawBtn(ctx, findNextX(),  fb, "§f↓");
            drawBtn(ctx, findCloseX(), fb, "§cX");
        }

        super.extractRenderState(ctx, mx, my, delta);
    }

    /**
     * MCEF's 2.2.1-26.2-fabric build still declares {@code getTextureIdentifier()} as returning the
     * old intermediary name (class_2960) instead of the official {@link net.minecraft.resources.Identifier}
     * — Loom 1.17 dropped the mod-remapping layer that used to paper over exactly this kind of stale
     * third-party API, so javac can no longer resolve the declared return type directly. The class
     * itself is unchanged (Fabric's runtime classloader still resolves it to the real Identifier), so
     * reflection bridges the compile-time gap without needing MCEF to fix their own build.
     */
    private boolean drawBrowserTexture(GuiGraphicsExtractor ctx, int bW, int bH) {
        try {
            Object texId = browser.getClass().getMethod("getTextureIdentifier").invoke(browser);
            ctx.blit(RenderPipelines.GUI_TEXTURED, (net.minecraft.resources.Identifier) texId,
                    0, BAR_H, 0f, 0f, bW, bH, bW, bH);
            return true;
        } catch (ReflectiveOperationException | ClassCastException e) {
            return false;
        }
    }

    private void renderSidebar(GuiGraphicsExtractor ctx, int mx, int my) {
        int sx    = width - SIDEBAR_W;
        int sy    = BAR_H;
        int sh    = height - BAR_H;
        int inner = SIDEBAR_W - 4;

        ctx.fill(sx, sy, width, sy + sh, COL_SIDE);
        ctx.fill(sx, sy, sx + 1, sy + sh, 0xFF2a3a6a);

        // "★ Add" button
        int addY = sy + 3;
        ctx.fill(sx + 2, addY, sx + inner + 2, addY + BTN_H, COL_BTN);
        ctx.centeredText(font, "§e★ §7Add current page",
            sx + SIDEBAR_W / 2, addY + (BTN_H - 8) / 2, 0xFFFFFFFF);

        // Bookmarks list
        List<String[]> marks = WikiBookmarks.all();
        int rowH = 20;
        int listY = addY + BTN_H + 4;
        int maxRows = (sh - BTN_H - 8) / rowH;
        int start = Math.max(0, Math.min(bookmarkScroll, Math.max(0, marks.size() - maxRows)));
        bookmarkScroll = start;

        for (int i = start; i < marks.size() && i < start + maxRows; i++) {
            String[] bm = marks.get(i);
            int rowY = listY + (i - start) * rowH;
            boolean hovered = mx >= sx + 2 && mx <= width - BTN_W - 4 && my >= rowY && my <= rowY + rowH - 2;
            ctx.fill(sx + 2, rowY, width - BTN_W - 4, rowY + rowH - 2, hovered ? 0xFF1e3050 : 0xFF0d1420);

            String title = bm[1].length() > 13 ? bm[1].substring(0, 13) + "…" : bm[1];
            ctx.text(font, "§f" + title, sx + 5, rowY + (rowH - 10) / 2, 0xFFCCCCCC);

            // × remove button
            int xBtnX = width - BTN_W - 2;
            boolean xHov = mx >= xBtnX && mx <= xBtnX + BTN_W - 2 && my >= rowY && my <= rowY + rowH - 2;
            ctx.fill(xBtnX, rowY, xBtnX + BTN_W - 2, rowY + rowH - 2, xHov ? 0xFF501010 : 0xFF200808);
            ctx.centeredText(font, "§c×",
                xBtnX + (BTN_W - 2) / 2, rowY + (rowH - 8) / 2, 0xFFFF4444);
        }

        if (marks.isEmpty()) {
            ctx.centeredText(font, "§8No bookmarks yet",
                sx + SIDEBAR_W / 2, listY + 10, 0xFF666666);
        }
    }

    private void drawBtn(GuiGraphicsExtractor ctx, int x, int y, String label) {
        ctx.fill(x, y, x + BTN_W, y + BTN_H, COL_BTN);
        ctx.centeredText(font, label, x + BTN_W/2, y + (BTN_H - 8)/2, 0xFFFFFFFF);
    }

    // ── input ──────────────────────────────────────────────────────────────────

    @Override
    public void mouseMoved(double mx, double my) {
        lastMouseX = mx; lastMouseY = my;
        if (browser != null && mx < browserW() && my >= BAR_H && my < BAR_H + browserH())
            browser.sendMouseMove(bx(mx), by(my - BAR_H));
        super.mouseMoved(mx, my);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        double cx = click.x(), cy = click.y();
        int bY = btnY();

        if (over(cx, cy, backX(),   bY, BTN_W, BTN_H)) { if (browser != null && browser.canGoBack())    browser.goBack();    return true; }
        if (over(cx, cy, fwdX(),    bY, BTN_W, BTN_H)) { if (browser != null && browser.canGoForward()) browser.goForward(); return true; }
        if (over(cx, cy, reloadX(), bY, BTN_W, BTN_H)) { if (browser != null) browser.reload();          return true; }
        if (over(cx, cy, starX(),   bY, BTN_W, BTN_H)) { toggleSidebar();                                return true; }
        if (over(cx, cy, zoomOutX(), bY, BTN_W, BTN_H)) { adjustZoom(-1); return true; }
        if (over(cx, cy, zoomInX(),  bY, BTN_W, BTN_H)) { adjustZoom(+1); return true; }

        // Sidebar clicks
        if (sidebarOpen && cx >= width - SIDEBAR_W) {
            handleSidebarClick(cx, cy);
            return true;
        }

        if (findVisible) {
            int fb = findBtnY();
            if (over(cx, cy, findPrevX(),  fb, BTN_W, BTN_H)) { doFind(true);   return true; }
            if (over(cx, cy, findNextX(),  fb, BTN_W, BTN_H)) { doFind(false);  return true; }
            if (over(cx, cy, findCloseX(), fb, BTN_W, BTN_H)) { closeFindBar(); return true; }
        }

        if (browser != null && cx < browserW() && cy >= BAR_H && cy < BAR_H + browserH()) {
            browser.sendMousePress(bx(cx), by(cy - BAR_H), click.button());
            browser.setFocus(true);
            return true;
        }
        return super.mouseClicked(click, bl);
    }

    private void handleSidebarClick(double cx, double cy) {
        int sx   = width - SIDEBAR_W;
        int addY = BAR_H + 3;

        // "★ Add" button
        if (cy >= addY && cy <= addY + BTN_H) {
            String url = "";
            try { if (browser != null) url = browser.getURL(); } catch (Exception ignored) {}
            if (url == null) url = "";
            String title = url.contains("/w/") ? url.substring(url.lastIndexOf("/w/") + 3).replace("_", " ") : url;
            WikiBookmarks.add(url, title.isEmpty() ? url : title);
            return;
        }

        // Bookmark rows
        List<String[]> marks = WikiBookmarks.all();
        int rowH  = 20;
        int listY = addY + BTN_H + 4;
        int maxRows = (height - BAR_H - BTN_H - 8) / rowH;

        for (int i = bookmarkScroll; i < marks.size() && i < bookmarkScroll + maxRows; i++) {
            int rowY = listY + (i - bookmarkScroll) * rowH;
            int xBtnX = width - BTN_W - 2;

            if (cy >= rowY && cy <= rowY + rowH - 2) {
                if (cx >= xBtnX) {
                    WikiBookmarks.remove(i);
                } else {
                    navigate(marks.get(i)[0]);
                }
                return;
            }
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (browser != null)
            browser.sendMouseRelease(bx(click.x()), by(click.y() - BAR_H), click.button());
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        // Sidebar scroll
        if (sidebarOpen && mx >= width - SIDEBAR_W) {
            bookmarkScroll = Math.max(0, bookmarkScroll + (vAmt < 0 ? 1 : -1));
            return true;
        }
        long win = minecraft.getWindow().handle();
        boolean ctrl = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL)  == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        if (ctrl) { adjustZoom(vAmt > 0 ? 1 : -1); return true; }
        if (browser != null && mx < browserW() && my >= BAR_H && my < BAR_H + browserH()) {
            lastMouseX = mx; lastMouseY = my;
            pendingScroll += -vAmt * 6.0;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int  key  = input.key();
        int  mods = input.modifiers();
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (findVisible) { closeFindBar(); return true; }
            minecraft.setScreen(parent);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_F)     { toggleFindBar();                       return true; }
        if (ctrl && key == GLFW.GLFW_KEY_R)     { if (browser != null) browser.reload(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_EQUAL) { adjustZoom(+1);                        return true; }
        if (ctrl && key == GLFW.GLFW_KEY_MINUS) { adjustZoom(-1);                        return true; }
        if (ctrl && key == GLFW.GLFW_KEY_0)     { setZoom(0);                            return true; }
        if (ctrl && key == GLFW.GLFW_KEY_D)     { toggleSidebar();                       return true; }

        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && findVisible && findField != null && findField.isFocused())         { doFind(false);               return true; }

        if (findVisible && findField != null && findField.isFocused())             return super.keyPressed(input);

        if (browser != null) { browser.sendKeyPress(key, (long) input.scancode(), mods); browser.setFocus(true); }
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        boolean findFoc = findVisible && findField != null && findField.isFocused();
        if (!findFoc && browser != null)
            browser.sendKeyRelease(input.key(), (long) input.scancode(), input.modifiers());
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (findVisible && findField != null && findField.isFocused())         return super.charTyped(input);
        if (browser != null && input.codepoint() != 0) {
            browser.sendKeyTyped((char) input.codepoint(), 0); // CharacterEvent lost modifiers() in 26.2
            browser.setFocus(true);
            return true;
        }
        return super.charTyped(input);
    }

    // ── actions ────────────────────────────────────────────────────────────────

    private void navigate(String raw) {
        // Only honor wiki URLs; otherwise treat raw text as a wiki search query.
        String url = (raw != null && isWikiUrl(raw)) ? raw : buildUrl(raw);
        if (browser != null) browser.loadURL(url);
    }

    private void adjustZoom(int delta) { setZoom(zoomLevel + delta); }

    private void setZoom(double level) {
        zoomLevel = Math.max(-5, Math.min(5, level));
        if (browser != null) browser.setZoomLevel(zoomLevel);
    }

    private void toggleSidebar() {
        sidebarOpen = !sidebarOpen;
        if (browser != null) browser.resize(browserPixelW(), browserPixelH());
    }

    private void toggleFindBar() {
        findVisible = !findVisible;
        if (findField != null) { findField.setVisible(findVisible); if (findVisible) findField.setFocused(true); }
        if (browser != null) browser.resize(browserPixelW(), browserPixelH());
    }

    private void closeFindBar() {
        findVisible = false;
        if (findField != null) { lastFindText = findField.getValue(); findField.setVisible(false); findField.setFocused(false); }
        if (browser  != null) { browser.stopFinding(true); browser.resize(browserPixelW(), browserPixelH()); }
    }

    private void doFind(boolean reverse) {
        if (findField == null || browser == null) return;
        String text = findField.getValue().trim();
        if (text.isEmpty()) return;
        boolean isNew = !text.equals(lastFindText);
        lastFindText = text;
        browser.find(text, !reverse, false, !isNew);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
